/*
 *
 *  * Copyright (C) 2022-2026 elDoc Limited
 *  *
 *  * This file is part of elDoc IDP OSS.
 *  *
 *  * elDoc IDP OSS is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU Affero General Public License as published
 *  * by the Free Software Foundation, version 3.
 *  *
 *  * elDoc IDP OSS is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 *  * General Public License for more details.
 *  *
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */
package ai.eldoc.service;

import ai.eldoc.TenantContext;
import ai.eldoc.ai.assistants.dto.reco.DocumentFragmentAiDTO;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.ai.service.AiService;
import ai.eldoc.bpm.BpmParams;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.dao.*;
import ai.eldoc.data.LazyResult;
import ai.eldoc.data.WorkflowLazyQuery;
import ai.eldoc.exceptions.*;
import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.*;
import ai.eldoc.model.filemeta.*;
import ai.eldoc.model.reco.*;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.reco.*;
import ai.eldoc.util.FileUtils;
import ai.eldoc.util.I18n;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedThreadFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.*;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static ai.eldoc.bpm.BpmParams.IDP_RECO_REQUIRES_CONVERSION;
import static ai.eldoc.bpm.BpmParams.IDP_RECO_REQUIRES_VALIDATION;
import static ai.eldoc.constants.DirectoryConstants.ELDOC_RECO_BOT;
import static ai.eldoc.model.PropertyConfig.IDP_RECOFORM_KEYWORDS_CLASSIFICATION;
import static ai.eldoc.model.reco.RecoFormResult.FORM_CONFIDENCE_THRESHOLD_INT;
import static java.text.MessageFormat.format;
import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.apache.commons.lang3.StringUtils.*;


/**
 * Recognition Service provides OCR and Text-based recognition capabilities
 */
@ApplicationScoped
public class RecognitionService {

    private static final Logger LOG = LoggerFactory.getLogger(RecognitionService.class);

    public static final String ELDOC_JVM_OPTION_IDP_PROCESSING_THREADS = "ai.eldoc.idp.processing.threads";

    private static final int MAX_PROCESSING_THREADS = Integer.getInteger(ELDOC_JVM_OPTION_IDP_PROCESSING_THREADS, 10);

    public static final String REGEX_VALUE_GROUP_NAME = "value";

    /**
     * Prefix for the keyword or anchor text, that mark this text to be treated as regular expression
     */
    public static final String PREFIX_REGEX = "regex:";

    @Resource
    private ManagedThreadFactory _threadFactory;

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private RecoDataDao _recoDataDao;

    @Inject
    private WorkflowService _workflowService;

    @Inject
    private RecoFormDao _recoFormDao;

    @Inject
    private FilesCleanupService _filesCleanupService;

    @Inject
    private RecoWorkflowService _recoWorkflowService;

    @Inject
    private RecoPostProcessorService _recoPostProcessorService;

    @Inject
    private IdpMonitor _executorMonitor;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private AiService _aiService;

    @Inject
    private TextIndexingService _textIndexingService;

    private ThreadPoolExecutor _executor;

    private final Map<String, Object> _scriptScope = new HashMap<>();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @PostConstruct
    protected void init() {
        _executor = new ThreadPoolExecutor(MAX_PROCESSING_THREADS, MAX_PROCESSING_THREADS, 10, TimeUnit.SECONDS, new LinkedBlockingDeque<>(), _threadFactory);
        _executorMonitor.setRecoProcessorExecutor(_executor);
    }


    /**
     * Processes first several (defined number) pending documents in the Inbox queue of ELDOC_RECO_BOT
     *
     * @return
     */
    public List<String> processPending() {
        return processPending(5);
    }


    /**
     * Processes pending documents in the Inbox queue of ELDOC_RECO_BOT
     *
     * @param pageCount
     * @return
     */
    private List<String> processPending(int pageCount) {
        LOG.trace("RecognitionService: START Processing pending documents");

        List<String> processedList = new ArrayList<>();

        LazyResult<Workflow> lazyResult = _workflowDao.findAllLazy(WorkflowLazyQuery.getInboxLQ(pageCount, true), _directoryService.getAclContext(ELDOC_RECO_BOT));

        if (!lazyResult.getDatasource().isEmpty()) {
            LOG.debug("RecognitionService: Number of documents to be processed: {} (batch size: {})", lazyResult.getDatasource().size(), pageCount);
        }

        for (Workflow recoWF : lazyResult.getDatasource()) {
            if (!_configContext.isIdpProcessingTimerEnabled()) {
                break;
            }
            AclContext aclContext = _directoryService.getAclContext(recoWF.getAuthor());
            AiRequestContext billingRequestContext = AiRequestContext.forSrcIDP(I18n.LANG_SYSTEM_CODE_EN, TenantContext.getCurrentTenantId(), aclContext);

            recognizeAndProcess(recoWF, billingRequestContext);

            // Record successful processing
            processedList.add(recoWF.getId());
        }
        LOG.trace("RecognitionService: END Processing pending documents. Processed documents: {}", processedList);
        return processedList;
    }


    private void recognizeAndProcess(Workflow recoWF, AiRequestContext billingRequestContext) {
        LOG.trace("RecoWorkflow[{}] '{}' processing START", recoWF.getId(), recoWF.getTopic());
        _executorMonitor.notifyProcessingStarted(TenantContext.getCurrentTenantId(), recoWF);

        // Perform document taking into processsing if required / initialization of the WorkflowTask
        WorkflowTask wfTask = _workflowService.workflowTaskCreateAndInitForParticipant(recoWF, null, _directoryService.getAclContext(ELDOC_RECO_BOT));
        if (wfTask == null) {
            LOG.error("RecoWorkflow[{}]: error retrieving WorkflowTask, cannot process document", recoWF.getId());
            LogHelper.setEvent("id00274", null, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
            _executorMonitor.notifyProcessingEnded(TenantContext.getCurrentTenantId(), recoWF);
            return;
        }

        try {
            // In case we perform recognition repeatedly on the same document - we need to clean results from the previous iteration(s)
            _recoWorkflowService.deleteRecoData(null, recoWF);

            // Check whether prepared image is in place
            DbFile recoFileFA = _workflowDao.getFilesDao().getFile(null, recoWF.getFileByTag(Workflow.FILE_TAG.IDP_PREPROCESSED.name()));
            if (recoFileFA == null) {
                LOG.error("RecoWorkflow[{}]: can not perform recognition as preprocessed file was not located", recoWF.getId());
                // Do respective actions
                LogHelper.setEvent("id00034", null, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
                wfTask.setNote("Prepared image was not located, can not perform document recognition");
                _workflowService.actionDecline(recoWF, wfTask, _directoryService.getAclContext(ELDOC_RECO_BOT), null, null);
                return;
            }

            AtomicBoolean requiresValidationAB = new AtomicBoolean(false);
            RecognitionMetaDoc recoMetaDoc = new RecognitionMetaDoc(RecoFileMeta.fromJson(recoFileFA.getMetadataAsJson()));
            recognizeAndProcessInner(recoWF, recoFileFA, recoMetaDoc, wfTask, requiresValidationAB, getRecoForms(), billingRequestContext);

            BpmParams bpmParams = new BpmParams(IDP_RECO_REQUIRES_VALIDATION, requiresValidationAB.get());

            if (requiresValidationAB.get()) {
                LogHelper.setEvent("id00033", null, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
                wfTask.setNote("Document requires manual validation");
            } else {
                LogHelper.setEvent("id00005", null, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
                if (recoMetaDoc.getRecoResultList().stream().anyMatch(recoResult -> isNotBlank(recoResult.getRecoForm().getForm()))) {
                    // Target form was defined at least for one single RecoForm - means document should be converted to it
                    // Passing it to the conversion bot
                    wfTask.setNote("Document recognition done. Ready for conversion");
                    bpmParams.setProperty(IDP_RECO_REQUIRES_CONVERSION, true);
                } else {
                    // Target document was not set - sending document to Archive
                    wfTask.setNote("Document recognition done");
                }
            }

            _workflowService.actionDone(recoWF, wfTask, _directoryService.getAclContext(ELDOC_RECO_BOT), null, bpmParams);

        } catch (NoRecoResultAvailableException ex3) {
            // No RecoForm was matched and RecoForms list selected by keywords is empty
            LogHelper.setEvent("id00050", null, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
            wfTask.setNote("No RecoForm was matched");
            _workflowService.actionDecline(recoWF, wfTask, _directoryService.getAclContext(ELDOC_RECO_BOT), null, null);
        } catch (RecoWorkflowRecognitionException ex1) {
            // No need to log it once again to server console - it was already logged while throwing this type of exception
            LogHelper.setEvent("id00085", ex1, ex1.getMessage(), new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
            wfTask.setNote("Recognition & classification error: " + ex1.getMessage());
            _workflowService.actionDecline(recoWF, wfTask, _directoryService.getAclContext(ELDOC_RECO_BOT), null, null);
        } catch (Throwable ex2) { // catch Throwable in order to cover both Exception and Error cases (Error thrown when out-of-memory happens)
            LOG.error("RecoWorkflow[{}]: recognition & classification error", recoWF.getId(), ex2);
            LogHelper.setEvent("id00051", ex2, ex2.getMessage(), new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
            wfTask.setNote("Recognition & classification error (see log for details)");
            _workflowService.actionDecline(recoWF, wfTask, _directoryService.getAclContext(ELDOC_RECO_BOT), null, null);
        } finally {
            _executorMonitor.notifyProcessingEnded(TenantContext.getCurrentTenantId(), recoWF);
            LOG.trace("RecoWorkflow[{}] '{}' processing END", recoWF.getId(), recoWF.getTopic());
        }
    }


    public RecognitionMetaDoc recognizeAndProcessDiag(Workflow recoWF, DbFile recoFile, WorkflowTask wfTask, AtomicBoolean requiresValidationAB, List<RecoForm> recoFormList, AiRequestContext billingRequestContext) throws IOException {
        RecognitionMetaDoc recoMetaDoc = new RecognitionMetaDoc(RecoFileMeta.fromJson(recoFile.getMetadataAsJson()));
        recoMetaDoc.getRecoDebugger().setDebugLogEnabled(true);
        return recognizeAndProcessInner(recoWF, recoFile, recoMetaDoc, wfTask, requiresValidationAB, recoFormList, billingRequestContext);
    }


    private RecognitionMetaDoc recognizeAndProcessInner(Workflow recoWF, DbFile recoFile, final RecognitionMetaDoc recoMetaDoc, WorkflowTask wfTask, AtomicBoolean requiresValidationAB, List<RecoForm> recoFormList, AiRequestContext billingRequestContext) throws IOException, NoRecoResultAvailableException {
        requiresValidationAB.set(false); // false should be used as default value as logic below relies on it

        processDocument(recoWF, recoFile, recoMetaDoc, requiresValidationAB, recoFormList, billingRequestContext);

        // Check for case when no RecoForms were matched at all - in such case we need to Decline document
        if (recoMetaDoc.getRecoResultList().isEmpty()) {
            // No RecoForm was matched and RecoForms list selected by keywords is empty
            LOG.debug("RecoWorkflow[{}]: No RecoForm was matched", recoWF.getId());
            throw new NoRecoResultAvailableException().setWorkflow(recoWF).setRecoMetaDoc(recoMetaDoc);
        }

        // Check, if RecoFormResults merge needed and merge them, if required
        Iterator<RecoFormResult> recoFormResultIterator = recoMetaDoc.getRecoResultList().iterator();
        RecoFormResult currentResult = null;
        RecoFormResult previousResult;
        while (recoFormResultIterator.hasNext()) {
            previousResult = currentResult;
            currentResult = recoFormResultIterator.next();

            // Skip merge step, if atleast one of recoFormResults is null
            if (previousResult == null || currentResult == null) {
                continue;
            }

            boolean recoFormResultsMerged = mergeRecoFormResultsIfNeeded(previousResult, currentResult);
            if (recoFormResultsMerged) {
                RecoDebugger recoDebugger = recoMetaDoc.getRecoDebugger().forForm(previousResult.getRecoForm().getId());
                recoDebugger.setPageIdx(previousResult.getFirstPageIdx());
                recoDebugger.log(RecoDebugger.ENTRY_TYPE_FORM, format("Merged recoFormResults on pages [{0}-{1}] and [{2}-{3}]", previousResult.getFirstPageIdx() + 1, previousResult.getLastPageIdx() + 1, currentResult.getFirstPageIdx() + 1, currentResult.getLastPageIdx() + 1));

                currentResult = previousResult;
                recoFormResultIterator.remove();
            }
        }

        // Document was matched - process obtained RecoFormResults
        for (RecoFormResult recoFormResult : recoMetaDoc.getRecoResultList()) {

            RecoData recoData = new RecoData(recoWF);

            // Save the matched RecoForm with the RecoWorkflow
            recoData.setRecoForm(new RecoFormDto(recoFormResult.getRecoForm()));
            recoData.setPageIdxStart(recoFormResult.getFirstPageIdx());
            recoData.setPageIdxEnd(recoFormResult.getLastPageIdx());
            _recoDataDao.updateOrCreate(null, recoData);

            // Log messages (with user-friendly page numbers which are 1-based, instead of 0-based)
            LOG.debug("RecoWorkflow[{}]: Page[{}] | RecoForm[{}]({}) was matched", recoWF.getId(), recoData.getPageIdxStart(), recoFormResult.getRecoForm().getId(), recoFormResult.getRecoForm().getName());
            String msg;
            if (recoData.getPageIdxStart() == recoData.getPageIdxEnd()) {
                msg = format("Page[{0}] {1} was matched", (recoData.getPageIdxStart() + 1), formatFormNameForLog(recoFormResult.getRecoForm()));
            } else {
                msg = format("Page[{0}-{1}] {2} was matched", (recoData.getPageIdxStart() + 1), (recoData.getPageIdxEnd() + 1), formatFormNameForLog(recoFormResult.getRecoForm()));
            }
            LogHelper.setEvent("id00032", null, msg, new LogExt(wfTask).setLogAccount(ELDOC_RECO_BOT));

            // Save the matched RecoForm with the RecoWorkflow
            recoData.setRecoForm(new RecoFormDto(recoFormResult.getRecoForm()));

            // Check whether RecoFormResult contains a customly preprocessed file and attach it if present
            DbFileRef preprocessedFinalFile = attachPreprocessedFinalFile(recoData, wfTask, recoFormResult);

            // Check, whether extra files was generated for RecoFormResult (i.e. text index for custom preprocessed file) and attach it, if present
            attachExtraFiles(wfTask, recoFormResult, preprocessedFinalFile);

            // RecoForm was matched - so we need to continue with filling actual results on the RecoWorkflow (in CustomFields)
            // IMPORTANT: RecoFormResult can be adjusted in case Regex logic applies (in terms of shifting confidences for affected fields)
            boolean docRequiresValidation = retrieveAndSaveFields(recoWF, recoData, wfTask, recoFormResult, recoMetaDoc);

            // Save raw results data within RecoWorkflow as well
            attachRecoResultFile(recoData, recoFormResult);

            if (!docRequiresValidation) {
                // Check whether validation is required due to resolution threshold check (as per RecoForm advanced setting)
                docRequiresValidation = isValidationRequiredOnResolutionThreshold(recoWF, recoFormResult, recoMetaDoc);
            }

            // In case at least one single inner-doc requires validation in the whole file - send whole doc for manual validation
            if (!requiresValidationAB.get() && docRequiresValidation) {
                requiresValidationAB.set(true);
            }
        }

        // If there was more than one recoForm matched with document on current step - document should be sent to validation
        // as we can not determine, which of the form is correct. If form was assigned by keywords, recoResultsCount will be
        // equal to 0 and this won't change 'requiresValidation' flag
        if (isValidationRequiredOnOverlappingResults(recoMetaDoc)) {
            LogHelper.setEvent("id00232", null, null, new LogExt(wfTask).setLogAccount(ELDOC_RECO_BOT));
            requiresValidationAB.set(true);
        }

        return recoMetaDoc;
    }


    /**
     * Merges two RecoFormResults, if needed. Conditions to merge RecoFormResults:
     * 1. Results has same RecoForm;
     * 2. RecoForm has 'HeaderRepeated' flag set to true on all table fields;
     * 3. All non-table fields has same value;
     *
     * @param firstResult
     * @param secondResult
     * @return
     */
    private boolean mergeRecoFormResultsIfNeeded(RecoFormResult firstResult, RecoFormResult secondResult) {
        if (!Objects.equals(firstResult.getRecoForm(), secondResult.getRecoForm())) {
            // RecoFormResults belong to different recoForms - quitting
            return false;
        }

        if (!firstResult.getRecoForm().isExtendable()) {
            // RecoForm is not extendable - quitting
            return false;
        }

        boolean allTablesHasRepeatedHeader = firstResult.getRecoForm().getFields().stream()
                .filter(RecoField::isTypeTable)
                .map(RecoField::getTableProp)
                .allMatch(TableProperties::isHeaderRepeated);

        if (!allTablesHasRepeatedHeader) {
            // RecoForm has tables, that doesn't have 'HeaderRepeated' flag set to true - quitting
            return false;
        }

        // Check, if all not extendable fields have same value
        AtomicBoolean notExtendableFieldsEquals = new AtomicBoolean(true);
        firstResult.getFields().forEach(recoFieldResult -> {
            if (recoFieldResult.isTypeTable()) {
                // If current field is table - it shouldn't affect, if we merge recoFormResults
                return;
            }

            RecoFieldResult secondRecoFieldResult = secondResult.getFields().stream()
                    .filter(secondRecoField -> secondRecoField.getRfPageIdx() == recoFieldResult.getRfPageIdx())
                    .filter(secondRecoField -> secondRecoField.getRfFieldIdx() == recoFieldResult.getRfFieldIdx())
                    .findFirst().orElse(null);

            if (secondRecoFieldResult == null) {
                // Second RecoFormResult doesn't have same recoField matched. Should not happen, as if both RecoData belong to same RecoForm - their
                // fields set should be same
                notExtendableFieldsEquals.set(false);
                return;
            }

            if (firstResult.getRecoForm().isFloatingFieldCheckAndGetExtendableField(firstResult.getRecoFieldByResult(recoFieldResult)) != null
                    && secondResult.getRecoForm().isFloatingFieldCheckAndGetExtendableField(secondResult.getRecoFieldByResult(secondRecoFieldResult)) != null) {
                // If both fields are floating fields and one of recoData has null value for this field - it is acceptable
                // for merge as multi-page documents can have repeated document header (with not floating fields) and floating fields value will
                // be present only on the last page (RecoData)
                boolean firstFieldMatched = RecoFormResultUtils.isMatched(recoFieldResult);
                boolean secondFieldMatched = RecoFormResultUtils.isMatched(secondRecoFieldResult);
                if ((!firstFieldMatched && secondFieldMatched) || (firstFieldMatched && !secondFieldMatched)) {
                    return;
                }
            }

            if (!RecoFormResultUtils.equals(recoFieldResult, secondRecoFieldResult, false)) {
                // Reset 'merge recoData' flag to false, as not extendable fields have different values
                notExtendableFieldsEquals.set(false);
            }
        });

        if (!notExtendableFieldsEquals.get()) {
            return false;
        }

        // All table fields has 'HeaderRepeated' flag set, and all other fields has equal values - merge recoFormResults
        // Add RecoPageResult to first RecoFormResult. As long as recoFormResults order kept valid, page order will be valid too
        firstResult.getPages().addAll(secondResult.getPages());

        // Cleanup RecoPageResults - remove duplicating recoFieldResults from resulting recoData
        List<RecoFieldResult> fieldResultsToRemove = new ArrayList<>();
        for (RecoFieldResult currentRecoFieldResult : firstResult.getFields()) {

            // Ignore table fields
            if (currentRecoFieldResult.isTypeTable()) {
                continue;
            }

            // Currently processed field is already marked for removal - skip it
            if (fieldResultsToRemove.contains(currentRecoFieldResult)) {
                continue;
            }

            // Find duplicating recoFieldResult, that appeared due to RecoData merge
            RecoFieldResult secondRecoFieldResult = firstResult.getFields().stream()
                    .filter(not(isEqual(currentRecoFieldResult)))
                    .filter(secondRecoField -> secondRecoField.getRfPageIdx() == currentRecoFieldResult.getRfPageIdx())
                    .filter(secondRecoField -> secondRecoField.getRfFieldIdx() == currentRecoFieldResult.getRfFieldIdx())
                    .findFirst().orElse(null);

            if (secondRecoFieldResult == null) {
                // Skip current field, if it's copy wasn't found - nothing to clean up
                continue;
            }

            if (!RecoFormResultUtils.isMatched(currentRecoFieldResult) && RecoFormResultUtils.isMatched(secondRecoFieldResult)) {
                // First field was not matched - remove 'not matched' field
                fieldResultsToRemove.add(currentRecoFieldResult);
            } else if (RecoFormResultUtils.isMatched(currentRecoFieldResult) && !RecoFormResultUtils.isMatched(secondRecoFieldResult)) {
                // Second field value is NULL, meaning field wasn't matched in second RecoData - remove 'not matched' field
                fieldResultsToRemove.add(secondRecoFieldResult);
            } else if (RecoFormResultUtils.equals(currentRecoFieldResult, secondRecoFieldResult, false)) {
                // Both fields have same values - remove duplicate
                fieldResultsToRemove.add(secondRecoFieldResult);
            }
        }

        firstResult.getPages().forEach(recoPage -> recoPage.getFields().removeAll(fieldResultsToRemove));

        // Merge PREPROCESSED_FINAL files
        if (firstResult.getPreprocessedFile() == null && secondResult.getPreprocessedFile() != null) {
            // If only second RecoResult has Preprocessed Final file - use it as Preprocessed Final of merged RecoResult
            firstResult.setPreprocessedFile(secondResult.getPreprocessedFile());
            firstResult.setPreprocessedFileMeta(secondResult.getPreprocessedFileMeta());
        } else if (firstResult.getPreprocessedFile() != null && secondResult.getPreprocessedFile() != null) {
            // If both RecoResults has Preprocessed Final files - merge them
            File mergedFile = null;
            try (RecoFileProcessor recoFileProcessor = new RecoFileProcessor(firstResult.getPreprocessedFileMeta()); InputStream is = new FileInputStream(new File(firstResult.getPreprocessedFile())); PDDocument secondDocument = PdfUtils.loadPDF(secondResult.getPreprocessedFile())) {
                recoFileProcessor.loadFile(firstResult.getPreprocessedFile(), is);
                recoFileProcessor.addDocumentToSource(secondDocument, secondResult.getPreprocessedFileMeta());
                mergedFile = PdfUtils.saveTempPDF(recoFileProcessor.getPreprocessedFile(), "eldoc-idp-".concat(firstResult.getRecoForm().getId()).concat("-"));
                firstResult.setPreprocessedFileMeta(recoFileProcessor.getPreprocessedMeta());
            } catch (IOException ex) {
                LOG.error("RecoForm[{}] Error during RecoFormResult merge", firstResult.getRecoForm().getId(), ex);
            } finally {
                if (mergedFile != null) {
                    _filesCleanupService.addQueueItem(Paths.get(firstResult.getPreprocessedFile()));
                    _filesCleanupService.addQueueItem(Paths.get(secondResult.getPreprocessedFile()));
                    firstResult.setPreprocessedFile(mergedFile.getPath());
                }
            }
        }

        // Merge generated Extra Files (i.e. text indexes for PREPROCESSED_FINAL files)
        mergeRecoFormResultsTextIndexIfNeeded(firstResult, secondResult);

        return true;
    }


    void mergeRecoFormResultsTextIndexIfNeeded(RecoFormResult firstResult, RecoFormResult secondResult) {
        Triple<String, Integer, FileMeta> firstResultTextIndex = firstResult.getExtraFiles().stream()
                .filter(ti -> TextIndexFileMeta.fromJson(ti.getRight().toJson()).getType() == TextIndexFileMeta.TYPE_TEXT_INDEX)
                .findFirst().orElse(null);
        Triple<String, Integer, FileMeta> secondResultTextIndex = secondResult.getExtraFiles().stream()
                .filter(ti -> TextIndexFileMeta.fromJson(ti.getRight().toJson()).getType() == TextIndexFileMeta.TYPE_TEXT_INDEX)
                .findFirst().orElse(null);

        if (firstResultTextIndex == null && secondResultTextIndex != null) {
            firstResult.getExtraFiles().add(secondResultTextIndex);
        } else if (firstResultTextIndex != null && secondResultTextIndex != null) {
            Path mergedFile = null;
            try {
                // Merge text file content
                mergedFile = Files.createTempFile("eldoc-idp-ti-", TextIndexingService.getFilenameSuffixByFileType(TextIndexFileMeta.TYPE_TEXT_INDEX, firstResultTextIndex.getMiddle()));
                long firstFileLen = Files.size(Paths.get(firstResultTextIndex.getLeft()));

                try (OutputStream out = Files.newOutputStream(
                        mergedFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {

                    Files.copy(Paths.get(firstResultTextIndex.getLeft()), out);

                    // Explicitly add newline, so that second file content will start from new line instead of continuing on last line of first file
                    out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));

                    Files.copy(Paths.get(secondResultTextIndex.getLeft()), out);
                }

                TextIndexFileMeta mergedFileMeta = TextIndexFileMeta.fromJson(firstResultTextIndex.getRight().toJson());
                ((TextIndexFileMeta) secondResultTextIndex.getRight()).getPageOffsets().stream()
                        .mapToInt(offset -> (int) (offset + firstFileLen))
                        .forEach(mergedFileMeta.getPageOffsets()::add);

                Triple newTextIndexFile = Triple.of(mergedFile.toString(), firstResultTextIndex.getMiddle(), mergedFileMeta);
                firstResult.getExtraFiles().remove(firstResultTextIndex);
                firstResult.getExtraFiles().add(newTextIndexFile);
            } catch (IOException ex) {
                LOG.error("RecoForm[{}] Error during RecoFormResult merge", firstResult.getRecoForm().getId(), ex);
            } finally {
                if (mergedFile != null) {
                    _filesCleanupService.addQueueItem(Paths.get(firstResultTextIndex.getLeft()));
                    _filesCleanupService.addQueueItem(Paths.get(secondResultTextIndex.getLeft()));
                }
            }
        }
    }


    /**
     * Retrieves and saves fields from RecoFormResult within Workflow <br>
     * IMPORTANT: RecoFormResult can be adjusted in case Regex logic applies (in terms of shifting confidences for affected fields)
     *
     * @param recoWF
     * @param wfTask
     * @param recoFormResult
     * @return
     */
    private boolean retrieveAndSaveFields(Workflow recoWF, RecoData recoData, WorkflowTask wfTask, RecoFormResult recoFormResult, RecognitionMetaDoc recoMetaDoc) {
        boolean requiresValidation = false;
        RecoDebugger recoDebugger = recoMetaDoc.getRecoDebugger().forForm(recoData.getRecoForm().getId());

        for (int pageIndex = 0; pageIndex < recoFormResult.getPages().size(); pageIndex++) {
            RecoPageResult recoPageResult = recoFormResult.getPages().get(pageIndex);

            for (int fieldIndex = 0; fieldIndex < recoPageResult.getFields().size(); fieldIndex++) {
                RecoFieldResult recoFieldResult = recoPageResult.getFields().get(fieldIndex);
                RecoField recoField = recoFormResult.getRecoFieldByResult(recoFieldResult);

                recoDebugger.setPageIdx(recoData.getPageIdxStart() + pageIndex);
                recoDebugger.setRecoField(recoField);

                // Init Workflow CustomField
                // We should consider fields like tables which spans across several pages, thus such CustomFiled may be already defined
                CustomField cf = recoData.getCfDef().stream()
                        .filter(cField -> Objects.equals(cField.getName().get(_configContext.getDefaultLang()), recoField.getName()))
                        .findFirst().orElse(null);

                // In case Custom Field is not present (was not added during processing of previous pages) - create a new one
                if (cf == null) {
                    cf = new CustomField();

                    _recoWorkflowService.retrieveAndSetCfDefFromRecoField(recoField, recoWF, cf);

                    // Add CustomFiled to the Workflow CustomFields configuration
                    recoData.getCfDef().add(cf);
                }

                // Retrieve recognized value
                Object recoValue;
                if (recoFieldResult.isTypeTable()) {
                    // When processing tables on multimple pages - first we need to locate the existing table
                    recoValue = recoData.getCfData().get(recoData.getCfDsFieldName(cf));

                    // In case this is the first page - then create a new field
                    if (recoValue == null) {
                        recoValue = new ArrayList<ArrayList<String>>();
                    }

                    // Add image-based cell values to the RecoData attachments list
                    recoFieldResult.getTableData().stream()
                            .flatMap(List::stream)
                            .filter(RecoFieldResult::isTypeImage)
                            .forEach(rfr -> addImageToAttachments(recoData, rfr));

                    // Extract table data to existing (if any)
                    recoFieldResult.getTableData().stream()
                            .map(row -> {
                                return row.stream()
                                        .map(RecoFieldResult::getValue)
                                        .collect(Collectors.toList());
                            })
                            .forEach(((List<List<String>>) recoValue)::add);
                } else if (recoFieldResult.isTypeTextExtendable()) {
                    // When text fields on multimple pages - first we need to locate the existing field value
                    recoValue = recoData.getCfData().get(recoData.getCfDsFieldName(cf));

                    // In case this is the first page - then create a new field
                    if (recoValue == null) {
                        recoValue = "";
                    } else if (recoFieldResult.getValue() != null) {
                        recoValue = ((String) recoValue).concat(" ");
                    }

                    if (recoFieldResult.getValue() != null) {
                        recoValue = ((String) recoValue).concat(trim(recoFieldResult.getValue()));
                    }
                } else if (recoFieldResult.isTypeImage()) {
                    addImageToAttachments(recoData, recoFieldResult);
                    recoValue = recoFieldResult.getValue();

                } else if (recoFieldResult.isTypeOmr()) {
                    recoValue = recoFieldResult.getOmrData().stream()
                            .map(opMark -> {
                                if ("T".equals(opMark.getValue())) {
                                    return true;
                                } else if ("F".equals(opMark.getValue())) {
                                    return false;
                                } else {
                                    LOG.warn("Incorrect value provided for OMR-type field: {}", opMark.getValue());
                                    return false;
                                }
                            }).collect(Collectors.toList());
                } else {
                    recoValue = trim(recoFieldResult.getValue());
                }
                _recoWorkflowService.retrieveAndSetCfDataFromRecoField(recoFormResult, recoFieldResult, cf);

                recoValue = applyRegex(recoField, cf, recoValue, wfTask, recoDebugger);

                // Save Field value within Workflow
                recoData.getCfData().put(recoData.getCfDsFieldName(cf), recoValue);

                LOG.trace("RecoFieldResult[{}]={} ({})", recoFieldResult.getName(), recoFieldResult.getValue(), recoFieldResult.getConfidenceAverage());
                recoDebugger.setRecoField(null);
            }
        }

        // Apply post-processing to each extracted field
        for (CustomField cf : recoData.getCfDef()) {
            Object value = recoData.getCfData().get(recoData.getCfDsFieldName(cf));

            AtomicBoolean requiresValidationAB = new AtomicBoolean(requiresValidation);
            value = applyPostProc(
                    RecoWorkflowService.getRecoFieldByCustomField(recoFormResult.getRecoForm(), cf, _configContext.getDefaultLang()),
                    cf,
                    value,
                    recoWF,
                    requiresValidationAB,
                    recoDebugger);
            if (requiresValidationAB.get() && !requiresValidation) {
                requiresValidation = true;
                recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, "Document sent to manual validation due to post-processing error");
            }

            // Save Field value within Workflow
            recoData.getCfData().put(recoData.getCfDsFieldName(cf), value);
        }

        for (CustomField cf : recoData.getCfDef()) {
            // Check if the document requires validation. We need to make sure that we do not reset this flag
            if (requiresValidation == false) {
                requiresValidation = isFieldRequiresValidation(cf);
                if (requiresValidation == true) {
                    recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD,
                            format("Document sent to manual validation due to low field confidence level. Confidence[{0}] < Threshold[{1}]", cf.getRecoProp().getConfidenceMin(), cf.getRecoProp().getConfThreshold()));
                }
            }
        }

        // Remember with the RecoData whether it was auto-validated or not
        recoData.setValidated(!requiresValidation);

        if (recoFormResult.getRecoForm().isUsePostProc() && !isBlank(recoFormResult.getRecoForm().getPostProc())) {
            try (OutputStream out = new ByteArrayOutputStream()) {
                boolean modified = _recoPostProcessorService.postProcessRecoData(recoFormResult.getRecoForm().getPostProcDecoded(), recoData, _configContext.getDefaultLang(), _scriptScope, out);
                if (modified) {
                    recoDebugger.log(RecoDebugger.ENTRY_TYPE_FORM, "Applied form post-processing");
                    if (requiresValidation != !recoData.isValidated()) {
                        String newStatusString = recoData.isValidated() ? "auto-validated" : "requires-validation";
                        recoDebugger.log(RecoDebugger.ENTRY_TYPE_FORM, format("Post-processing: status changed to:"), newStatusString);
                        LogHelper.setEvent("id00278",
                                null,
                                format("Recognition form post-processing: status changed to {0}", newStatusString),
                                new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT)
                        );
                    }
                    requiresValidation = !recoData.isValidated();
                } else {
                    recoDebugger.log(RecoDebugger.ENTRY_TYPE_FORM, "Auto-applied form post-processing, no data changed");
                }
            } catch (TimeoutException ex) {
                recoDebugger.log(RecoDebugger.ENTRY_TYPE_FORM, "Form post-processing interrupted due to timeout: ", ex.getMessage());
                LogHelper.setEvent("id00259", ex, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
            } catch (ParseException ex) {
                recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Form post-processing failed due to unprocessable script: "), ex.getMessage());
                LogHelper.setEvent("id00260", ex, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
            } catch (IOException ex) {
                LogHelper.setEvent("id00261", ex, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
            } catch (ScriptingException ex) {
                recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Field post-processing error: "), ex.getMessage());
                LogHelper.setEvent("id00265",
                        ex,
                        format("Post-processing error: {0}", ex.getMessage()),
                        new LogExt(wfTask).setLogAccount(ELDOC_RECO_BOT)
                );
                recoData.setValidated(!false);
            }
        }

        // Save recognition results in DB
        if (!_recoDataDao.updateOrCreate(null, recoData)) {
            LogHelper.setEvent("id00262", null, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
        }

        return requiresValidation;
    }


    protected void addImageToAttachments(RecoData recoData, RecoFieldResult recoFieldResult) {
        if (isBlank(recoFieldResult.getValue())) {
            return;
        }
        DbFileRef dbRef = _recoDataDao.getFilesDao().createDbFileRef(recoFieldResult.getValue(), List.of(RecoData.FILE_TAG.IDP_FIELD_ATTACHMENT.name()));
        recoData.getFiles().add(dbRef);
    }


    /**
     * Check, if requested cell of table field requires manual validation.<br>
     * <b>IMPORTANT: </b> Cells are treated as text-type CustomFields, without support for other type of cells
     *
     * @param tableField {@link CustomField} with data, retrieved for field
     * @param row index of the row of requested cell
     * @param column index of the column of requested cell
     * @return {@code true}, if field requires manual validation, or false, if all values are passed for autovalidation
     */
    boolean isCellRequiresValidation(CustomField tableField, int row, int column) {

        List<Integer> columnResult = tableField.getRecoProp().getConfidenceTable().get(row).get(column);
        String columnType = tableField.getFields().get(column).getType();

        if (CustomField.TYPE_RECO.equals(columnType) && columnResult.isEmpty()) {
            // Cells with empty value are considered autovalidated regardless of confidence threshold set for column
            return false;
        }
        CustomField cellCf = new CustomField();
        cellCf.setType(columnType);
        cellCf.getRecoProp().setConfThreshold(tableField.getFields().get(column).getRecoProp().getConfThreshold());
        // TODO: Should be adjusted, once table fields support for non-text columns will be added
        cellCf.getRecoProp().setConfidenceText(columnResult);
        return isFieldRequiresValidation(cellCf);
    }


    /**
     * Checks, if provided field requires manual validation.<br>
     * If field is a table, each column checked separately using column-specific confidence threshold.
     * Otherwise, field's confidence threshold is used.
     *
     * @param customField {@link CustomField} with data, retrieved for field
     * @return {@code true}, if field requires manual validation, or false, if all values are passed for autovalidation
     */
    public static boolean isFieldRequiresValidation(CustomField customField) {
        if (CustomField.TYPE_RECO_TABLE.equals(customField.getType())) {

            for (AtomicInteger i = new AtomicInteger(0); i.get() < customField.getFields().size(); i.incrementAndGet()) {
                // Process each column with each of it's value separatedly
                CustomField column = customField.getFields().get(i.get());
                List<List<Integer>> columnResults = customField.getRecoProp().getConfidenceTable().stream()
                        .map(row -> row.get(i.get()))
                        .toList();

                if (column.isRequired() && columnResults.stream().anyMatch(List::isEmpty)) {
                    // Column is required, but table contains no data - mark field for manual validation
                    return true;
                }

                for (List<Integer> columnResult : columnResults) {
                    if (columnResult.isEmpty()) {
                        // Cells with empty value are considered autovalidated, if respective column is NOT marked as required
                        // If cell is empty and column is required - table will be sent to validation on previous step
                        continue;
                    }
                    if (columnResult.stream().mapToInt(Integer::intValue).min().orElse(0) < column.getRecoProp().getConfThreshold()) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            if (!customField.isRequired() && customField.getRecoProp().isEmpty()) {
                // Custom field is empty and is not required - it doesn't require manual validation
                return false;
            } else if (customField.isRequired() && customField.getRecoProp().isEmpty()) {
                // In case Field required but empty - mark that it requires validation
                return true;
            } else {
                // Custom field is not empty - check field's value confidence level
                return customField.getRecoProp().getConfidenceMin() < customField.getRecoProp().getConfThreshold();
            }
        }
    }


    /**
     * Applies regular expression to field value, if it is present.<br>
     * If provided field is Table - it processes table content,
     *
     * @param recoField
     * @param customField
     * @param recoValue
     * @param wfTask
     * @param recoDebugger
     * @return
     */
    protected Object applyRegex(RecoField recoField, CustomField customField, Object recoValue, WorkflowTask wfTask, RecoDebugger recoDebugger) {

        if (recoField.isTypeOmr()) {
            // Regex cannot be applied to OMR fields
            return recoValue;
        }

        if (recoField.isTypeImage()) {
            // Regex cannot be applied to Image fields
            return recoValue;
        }

        if (recoField.isTypeTable()) {
            // If field is a Table - process each column with each of it's value separatedly
            for (AtomicInteger i = new AtomicInteger(0); i.get() < recoField.getColumns().size(); i.incrementAndGet()) {
                RecoField column = recoField.getColumns().get(i.get());
                List<String> columnResults = ((List<List<String>>) recoValue).stream()
                        .map(row -> row.get(i.get()))
                        .collect(Collectors.toList());
                for (int j = 0; j < columnResults.size(); j++) {
                    String columnResult = columnResults.get(j);
                    String newValue = (String) applyRegex(column, customField.getFields().get(i.get()), columnResult, wfTask, recoDebugger);
                    if (!Objects.equals(newValue, columnResult)) {
                        // Update cell value and confidence levels
                        ((List<List<String>>) recoValue).get(j).set(i.get(), newValue);
                        List<Integer> cellConfLevels = customField.getRecoProp().getConfidenceTable().get(j).get(i.get());
                        if (!cellConfLevels.isEmpty()) {
                            int start = Math.min(columnResult.indexOf(newValue), cellConfLevels.size() - 1);
                            int end = Math.min(start + newValue.length(), cellConfLevels.size());
                            List<Integer> newConfidenceLevels = cellConfLevels.subList(start, end);
                            customField.getRecoProp().getConfidenceTable().get(j).set(i.get(), new ArrayList<>(newConfidenceLevels));
                        }
                    }
                }
            }
            return recoValue;
        }

        if (!recoField.getTextProp().isUseRegex()) {
            // Regex is not enabled for field - nothing to do
            return recoValue;
        }

        if (isBlank(recoField.getTextProp().getRegex())) {
            // No regex provided for field - nothing to do
            return recoValue;
        }

        if (isBlank((String) recoValue)) {
            // Value is empty, applying regex is meaningless - nothing to do
            return recoValue;
        }

        try {
            Pattern p = Pattern.compile(recoField.getTextProp().getRegex());
            Matcher m = p.matcher((String) recoValue);
            if (m.find() && isNotBlank(m.group(RecognitionService.REGEX_VALUE_GROUP_NAME))) {
                recoValue = m.group(RecognitionService.REGEX_VALUE_GROUP_NAME);
                int groupStart = m.start(RecognitionService.REGEX_VALUE_GROUP_NAME);
                int groupEnd = m.end(RecognitionService.REGEX_VALUE_GROUP_NAME);
                // IMPORTANT: This check required, as confidence levels for cells stored on table-level CustomField, and not column-level
                if (!customField.getRecoProp().getConfidenceText().isEmpty()) {
                    List<Integer> fieldConfLevel = customField.getRecoProp().getConfidenceText();
                    int start = Math.min(groupStart, fieldConfLevel.size() - 1);
                    int end = Math.min(groupEnd, fieldConfLevel.size());
                    customField.getRecoProp().setConfidenceText(new ArrayList<>(customField.getRecoProp().getConfidenceText().subList(start, end)));
                }
                recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Auto-applying regex, new value: ", recoField.getTextProp().getRegex()), m.group(RecognitionService.REGEX_VALUE_GROUP_NAME));
                return recoValue;
            } else {
                recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Regex[{0}] not matched, keeping original value:", recoField.getTextProp().getRegex()), (String) recoValue);
                LogHelper.setEvent("id00052",
                        null,
                        format("Field[{0}] | Regex[{1}]", recoField.getName(), recoField.getTextProp().getRegex()),
                        new LogExt(wfTask).setLogAccount(ELDOC_RECO_BOT)
                );
            }
        } catch (IllegalArgumentException ex) {
            recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Regex[{0}] processing error:", recoField.getTextProp().getRegex()), ex.getMessage());
            LogHelper.setEvent("id00060",
                    null,
                    format("Field[{0}] | Regex[{1}] | Error: {2}", recoField.getName(), recoField.getTextProp().getRegex(), ex.getMessage()),
                    new LogExt(wfTask).setLogAccount(ELDOC_RECO_BOT)
            );
        }
        return recoValue;
    }


    /**
     * Applies post-processing function to field value, if it is present.<br>
     * If provided field is Table or OMR - processes it's content.
     *
     * @param recoField
     * @param customField
     * @param recoValue
     * @param recoWF
     * @param recoDebugger
     */
    private Object applyPostProc(RecoField recoField, CustomField customField, Object recoValue, Workflow recoWF, AtomicBoolean requiresValidation, RecoDebugger recoDebugger) {

        if (!recoField.getProp().isUsePostProc() || isBlank(recoField.getProp().getPostProc())) {
            // If post-processing is disabled for current field - nothing to do
            return recoValue;
        }

        Object newRecoValue = null;

        try (OutputStream out = new ByteArrayOutputStream()) {
            if (recoField.isTypeText() || recoField.isTypeTextExtendable()) {
                boolean fieldRequiresValidationOld = isFieldRequiresValidation(customField);
                newRecoValue = _recoPostProcessorService.postProcessTextField(customField, recoField.getProp().getPostProcDecoded(), (String) recoValue, _configContext.getDefaultLang(), _scriptScope, out);
                boolean fieldRequiresValidationNew = isFieldRequiresValidation(customField);
                if (fieldRequiresValidationOld != fieldRequiresValidationNew) {
                    String newStatusString = fieldRequiresValidationNew ? "requires-validation" : "auto-validated";
                    recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Post-processing: status changed to:"), newStatusString);
                    LogHelper.setEvent("id00279",
                            null,
                            format("Field[{0}] post-processing: status changed to {1}", recoField.getName(), newStatusString),
                            new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT)
                    );
                }

                if (!Objects.equals(recoValue, newRecoValue)) {
                    recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Post-processing: new field value:"), (String) newRecoValue);
                    return newRecoValue;
                } else {
                    return recoValue;
                }
            } else if (recoField.isTypeTable()) {
                // Calculate per-cell validation status of the original table
                List<List<Boolean>> tableValidationStatusList = new ArrayList<>();
                for (int i = 0; i < ((List<List<String>>) recoValue).size(); i++) {
                    List<Boolean> rowValidationStatusList = new ArrayList<>();
                    tableValidationStatusList.add(rowValidationStatusList);
                    List<String> tableRow = ((List<List<String>>) recoValue).get(i);
                    for (int j = 0; j < tableRow.size(); j++) {
                        rowValidationStatusList.add(isCellRequiresValidation(customField, i, j));
                    }
                }
                // Copy original value to perform per-cell check for modifications
                List<List<String>> oldRecoValue = new ArrayList<>();
                ((List<List<String>>) recoValue).forEach(row -> oldRecoValue.add(new ArrayList<>(row)));

                // Perform post-processing
                newRecoValue = _recoPostProcessorService.postProcessTableField(customField, recoField.getProp().getPostProcDecoded(), (List<List<String>>) recoValue, _configContext.getDefaultLang(), _scriptScope, out);

                // Check post-processing results
                for (int i = 0; i < ((List<List<String>>) newRecoValue).size(); i++) {
                    List<String> row = ((List<List<String>>) newRecoValue).get(i);
                    if (i >= oldRecoValue.size()) {
                        // This is new row - no 'previous values' present, just log new values
                        for (int j = 0; j < row.size(); j++) {
                            recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Post-processing: cell[{0}][{1}]: new value: ", i + 1, j + 1), row.get(j));
                            boolean fieldRequiresValidationNew = isCellRequiresValidation(customField, i, j);
                            String newStatusString = fieldRequiresValidationNew ? "requires-validation" : "auto-validated";
                            recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Post-processing: cell[{0}][{1}]: status is:", i + 1, j + 1), newStatusString);
                        }
                        LogHelper.setEvent("id00280",
                                null,
                                format("Field[{0}]: row[{1}]: post-processing: row added", recoField.getName(), i + 1),
                                new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT)
                        );
                        continue;
                    }
                    for (int j = 0; j < Math.min(row.size(), oldRecoValue.get(i).size()); j++) {
                        // Iterate over 'minimal' number of ceils in a row among original and new row value to trim exceeding ceils from new row (from post-processing)
                        String oldValue = oldRecoValue.get(i).get(j);
                        String newValue = row.get(j);

                        if (!Objects.equals(oldValue, newValue)) {
                            // Cell value changed
                            recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Post-processing: cell[{0}][{1}]: new value: ", i + 1, j + 1), newValue);
                        }

                        boolean fieldRequiresValidationNew = isCellRequiresValidation(customField, i, j);
                        if (tableValidationStatusList.get(i).get(j) != fieldRequiresValidationNew) {
                            String newStatusString = fieldRequiresValidationNew ? "requires-validation" : "auto-validated";
                            recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Post-processing: cell[{0}][{1}]: status changed to:", i + 1, j + 1), newStatusString);
                            LogHelper.setEvent("id00280",
                                    null,
                                    format("Field[{0}]: cell[{1}][{2}]: post-processing: status changed to {3}", recoField.getName(), i + 1, j + 1, newStatusString),
                                    new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT)
                            );
                        }
                    }
                }
            } else if (recoField.isTypeOmr()) {
                List<Boolean> omrValidationStatusList = new ArrayList<>();
                for (int i = 0; i < ((List<Boolean>) recoValue).size(); i++) {
                    omrValidationStatusList.add(customField.getRecoProp().getConfidenceOmr().get(i) < customField.getRecoProp().getConfThreshold());
                }
                newRecoValue = _recoPostProcessorService.postProcessOmrField(customField, recoField.getProp().getPostProcDecoded(), (List<Boolean>) recoValue, _configContext.getDefaultLang(), _scriptScope, out);
                for (int i = 0; i < Math.min(((List<Boolean>) newRecoValue).size(), ((List<Boolean>) recoValue).size()); i++) {
                    Boolean oldValue = ((List<Boolean>) recoValue).get(i);
                    Boolean newValue = ((List<Boolean>) newRecoValue).get(i);
                    if (!Objects.equals(oldValue, newValue)) {
                        recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Post-processing: OMR mark[{0}]: new value: ", i), String.valueOf(newValue));
                    }
                    boolean fieldRequiresValidationNew = isFieldRequiresValidation(customField);
                    if (i >= omrValidationStatusList.size() || omrValidationStatusList.get(i) != fieldRequiresValidationNew) {
                        String newStatusString = fieldRequiresValidationNew ? "requires-validation" : "auto-validated";
                        recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Post-processing: OMR mark[{0}]: status changed to:", i), newStatusString);
                        LogHelper.setEvent("id00281",
                                null,
                                format("Field[{0}] post-processing: status changed to {1}", recoField.getName(), newStatusString),
                                new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT)
                        );
                    }
                }
            }
            return newRecoValue;
        } catch (TimeoutException ex) {
            recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Post-processing interrupted due to timeout, keeping original value: "), recoValue.toString());
            LogHelper.setEvent("id00263",
                    ex,
                    format("Field[{0}] post-processing interrupted due to timeout", recoField.getName()),
                    new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT)
            );
            requiresValidation.set(true);
        } catch (ScriptingException ex) {
            recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Field post-processing error: "), ex.getMessage());
            LogHelper.setEvent("id00277",
                    ex,
                    format("Field[{0}] post-processing error: {1}", recoField.getName(), ex.getMessage()),
                    new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT)
            );
            requiresValidation.set(true);
        } catch (ParseException | IOException ex) {
            recoDebugger.log(RecoDebugger.ENTRY_TYPE_FIELD, format("Field post-processing error: "), ex.getMessage());
            LogHelper.setEvent("id00264",
                    ex,
                    format("Field[{0}] post-processing error due to script parsing exception", recoField.getName()),
                    new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT)
            );
            requiresValidation.set(true);
        }
        return recoValue;
    }


    /**
     * Retrieves all RecoForms available for matching (performs licensing check)
     *
     * @return
     */
    private List<RecoForm> getRecoForms() {
        return _recoFormDao.findAllByStatus(Arrays.asList(RecoForm.ACTIVE));
    }


    /**
     * Updates RecoForm list, basing on tags, for each page, based on following conditions (in the specified order):
     * <ul>
     * <li>Cond 1: If document has no tags defined - all RecoForm remain as is</li>
     * <li>Cond 2: If document has tags but RecoForm doesn't have tags - remove such RecoForm from matching</li>
     * <li>Cond 3: If document has tags - leave only RecoForms which has all tags defined on the document</li>
     * </ul>
     *
     * @param recoWF
     * @param recoMetaDoc
     */
    List<RecoForm> updateRecoFormsSelectedByTags(Workflow recoWF, List<RecoForm> recoFormList, RecognitionMetaDoc recoMetaDoc) {
        List<String> recoWFTags = recoWF.getTags();
        if (CollectionUtils.isEmpty(recoWFTags)) {
            // Cond 1: If document has no tags defined - all RecoForm remain as is
            return new ArrayList<>(recoFormList); // We are returning new instance of the List
        }

        List<RecoForm> recoFormsListByTags = new ArrayList<>();
        recoFormList.forEach(recoForm -> {
            RecoDebugger recoDebugger = recoMetaDoc.getRecoDebugger().forForm(recoForm.getId());

            List<String> recoFormTags = recoForm.getTags();
            if (CollectionUtils.isEmpty(recoFormTags)) {
                // Cond 2: If document has tags but RecoForm doesn't have tags - remove such RecoForm from matching
                recoDebugger.log(RecoDebugger.ENTRY_TYPE_RECOSERVICE, "RecoForm excluded as has no tags defined");
                return;
            }

            if (recoFormTags.containsAll(recoWFTags)) {
                // Cond 3: If document has tags - leave only RecoForms which has all tags defined on the document
                recoFormsListByTags.add(recoForm);
            } else {
                List<String> tags = new ArrayList<>(recoWFTags);
                tags.removeAll(recoFormTags);
                recoDebugger.log(RecoDebugger.ENTRY_TYPE_RECOSERVICE, "RecoForm excluded from matching due to missing tags: ", tags.stream().collect(Collectors.joining("; ")));
            }
        });

        // Prepare formatted message
        String msg = format("Number of recognition forms shortlisted by tags: {0}", recoFormsListByTags.size());
        LogHelper.setEvent("id00295", null, msg, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));

        return recoFormsListByTags;
    }


    /**
     * Select recoForms for matching based on the following conditions:<br>
     * <ul>
     * <li>RecoForm keywords are not set (empty)</li>
     * <li>RecoForm keywords all match with the target document</li>
     * <li>RecoForm minus keywords have no matches (in case at least one minus keyword matches - such form is to be excluded)</li>
     * </ul>
     *
     * @param recoMetaDoc
     * @param recoFormList
     * @return
     */
    void updateRecoFormsSelectedByKeywords(RecognitionMetaDoc recoMetaDoc, List<RecoForm> recoFormList, DbFile recoFileRef) {
        if (recoFormList.size() == 1 && Objects.equals(recoFormList.get(0).getId(), RecoForm.AUTOMATIC_FORM_ID)) {
            // Document contains only auto RecoForm for processing, it should not be filtered by keywords
            RecognitionMetaFragment docFragment = new RecognitionMetaFragment();
            docFragment.setPageStart(0);
            docFragment.setPageEnd(recoMetaDoc.getNumberOfPages() - 1);
            docFragment.setRecoForm(recoFormList.get(0));
            recoMetaDoc.getRecoMetaFragmentList().add(docFragment);
            return;
        }

        try (InputStream is = _workflowDao.getFilesDao().getFileDownloadStream(recoFileRef.getDbFileRef()); PDDocument recoFile = PdfUtils.loadPDF(is)) {
            for (int pageIdx = 0; pageIdx < recoMetaDoc.getNumberOfPages(); pageIdx++) {
                // Get all text from the page given by index and from the given document and replace groups of 2+ spaces with single space
                String docText = PdfUtils.getText(recoFile, pageIdx).replaceAll(" +", " ");

                // Check all RecoForms on per-page basis of the target document
                for (RecoForm recoForm : recoFormList) {

                    RecoDebugger recoDebugger = recoMetaDoc.getRecoDebugger().forForm(recoForm.getId());
                    recoDebugger.setPageIdx(pageIdx);
                    List<String> mismatchedKeywords = new ArrayList<>();

                    // 1) RecoForm keywords are not present - such RecoForms are always to be skipped
                    if (recoForm.getKeywords().isEmpty()) {
                        recoDebugger.log(RecoDebugger.ENTRY_TYPE_RECOSERVICE, "RecoForm excluded due to no keywords defined");
                        continue; // No need to continue - no keywords defined
                    }

                    // 2) RecoForm keywords all match with the target document
                    boolean hasKeywordMatch = true;
                    for (String keyword : recoForm.getKeywordsInclusive()) {
                        if (keyword.startsWith(PREFIX_REGEX)) {
                            String regex = keyword.substring(PREFIX_REGEX.length());
                            Pattern pattern = Pattern.compile(regex, CASE_INSENSITIVE);
                            Matcher matcher = pattern.matcher(docText);
                            if (!matcher.find()) {
                                hasKeywordMatch = false;
                                mismatchedKeywords.add(keyword);
                            }
                        } else {
                            if (!containsIgnoreCase(docText, keyword)) {
                                hasKeywordMatch = false;
                                mismatchedKeywords.add(keyword);
                            }
                        }
                    }
                    if (!hasKeywordMatch) {
                        recoDebugger.log(RecoDebugger.ENTRY_TYPE_RECOSERVICE, "RecoForm excluded from matching due to missing keywords:", mismatchedKeywords.stream().collect(Collectors.joining("; ")));
                        mismatchedKeywords.clear();
                    }

                    // 3) RecoForm minus keywords have no matches (in case at least one minus keyword matches - such form is to be excluded)
                    boolean hasMinusKeywordMatch = false;
                    for (String minusKeyword : recoForm.getKeywordsMinus()) {
                        if (minusKeyword.startsWith(PREFIX_REGEX)) {
                            String regex = minusKeyword.substring(PREFIX_REGEX.length());
                            Pattern pattern = Pattern.compile(regex, CASE_INSENSITIVE);
                            Matcher matcher = pattern.matcher(docText);
                            if (matcher.find()) {
                                hasMinusKeywordMatch = true;
                                mismatchedKeywords.add("-".concat(minusKeyword));
                            }
                        } else {
                            if (containsIgnoreCase(docText, minusKeyword)) {
                                hasMinusKeywordMatch = true;
                                mismatchedKeywords.add("-".concat(minusKeyword));
                            }
                        }
                    }
                    if (hasMinusKeywordMatch) {
                        recoDebugger.log(RecoDebugger.ENTRY_TYPE_RECOSERVICE, "RecoForm excluded from matching due to presense of minus keywords:", mismatchedKeywords.stream().collect(Collectors.joining("; ")));
                    }

                    if (!hasKeywordMatch || hasMinusKeywordMatch) {
                        continue;
                    }

                    RecognitionMetaFragment docFragment = new RecognitionMetaFragment();
                    docFragment.setPageStart(pageIdx);
                    docFragment.setRecoForm(recoForm);
                    recoMetaDoc.getRecoMetaFragmentList().add(docFragment);
                }
            }
        } catch (IOException ex) {
            throw new RecoWorkflowRecognitionException("Error reading pre-processed file content", ex);
        }

        updateFragmentsByKeywordsRange(recoMetaDoc);
    }


    /**
     * Calculates end of the each fragment of the provided document as the first page after this fragment start, that also starts some fragment
     *
     * @param recoMetaDoc
     */
    void updateFragmentsByKeywordsRange(RecognitionMetaDoc recoMetaDoc) {
        recoMetaDoc.getRecoMetaFragmentList().forEach(fragment -> fragment.setPageEnd(recoMetaDoc.getNextDocumentStartPageIdx(fragment.getPageStart()) - 1));
    }


    /**
     * Adds formatted message for each located document fragment
     *
     * @param recoWF
     * @param recoMetaDoc
     */
    private void logDocumentFragments(Workflow recoWF, RecognitionMetaDoc recoMetaDoc) {
        List<RecognitionMetaFragment> recoMetaFragmentsList = recoMetaDoc.getRecoMetaFragmentList();
        recoMetaFragmentsList.sort(Comparator.comparingInt(RecognitionMetaFragment::getPageStart).thenComparingInt(RecognitionMetaFragment::getPageEnd));
        for (int i = 0; i < recoMetaFragmentsList.size(); i++) {
            RecognitionMetaFragment fragment = recoMetaFragmentsList.get(i);
            List<RecognitionMetaFragment> fragmentsToLog = new ArrayList<>();
            fragmentsToLog.add(fragment);

            // Check next fragment - if they start and end on the same page, then they can be logged in same message
            int j = i + 1;
            for (; j < recoMetaFragmentsList.size(); j++) {
                RecognitionMetaFragment otherFragment = recoMetaFragmentsList.get(j);
                if (otherFragment.getPageStart() != fragment.getPageStart() || otherFragment.getPageEnd() != fragment.getPageEnd()) {
                    // Stop iterating - as fragments are sorted, if at least one page idx is wrong - then we already found all required fragments
                    break;
                }
                fragmentsToLog.add(otherFragment);
            }
            i = j - 1; // Jump to last fragment, that was selected for current log entry

            // Prepare String with list of forms to be logged
            String formsList = fragmentsToLog.stream()
                    .map(RecognitionMetaFragment::getRecoForm)
                    .map(this::formatFormNameForLog)
                    .collect(Collectors.joining(", "));

            // Prepare formatted message. We use (i + 1) to show user-friendly page numbers (1-based instead of 0-based)
            String msg;
            if (fragment.getPageStart() == fragment.getPageEnd()) {
                msg = format("Page[{0}] Classification result: ", new Object[]{(fragment.getPageStart() + 1)});
            } else {
                msg = format("Page[{0}-{1}] Classification result: ", new Object[]{(fragment.getPageStart() + 1), (fragment.getPageEnd() + 1)});
            }

            msg = msg.concat(formsList);

            LogHelper.setEvent("id00109", null, msg, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
        }
    }


    private String formatFormNameForLog(RecoForm recoForm) {
        String result = "";
        if (recoForm.isTypeAuto()) {
            // Adds "automatic" prefix to form type name, in case form is auto-generated during matching
            result = result.concat(recoForm.getName());
            result = result.concat(" (")
                    .concat(RecoForm.AUTOMATIC_FORM_ID.equals(recoForm.getId()) ? "automatic " : "")
                    .concat(RecoForm.TYPE_NAME_SUFFIX.get(recoForm.getType()))
                    .concat(")");
        } else {
            result = result.concat(recoForm.getName())
                    .concat(" (")
                    .concat(RecoForm.TYPE_NAME_SUFFIX.get(recoForm.getType()))
                    .concat(")");
        }
        return result;
    }


    /**
     * Checks whether RecoFormResult contains a custom preprocessed file and if yes - attaches it to the RecoData
     *
     * @param recoData
     * @param wfTask
     * @param recoFormResult
     */
    private DbFileRef attachPreprocessedFinalFile(RecoData recoData, WorkflowTask wfTask, RecoFormResult recoFormResult) {
        DbFileRef dbFileRef = null;
        if (recoFormResult.getPreprocessedFile() != null) {
            try (FileInputStream fis = new FileInputStream(recoFormResult.getPreprocessedFile())) {
                RecoFileMeta fileMeta = recoFormResult.getPreprocessedFileMeta();
                List<String> fileTags = List.of(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name());
                fileMeta.setTags(fileTags);
                dbFileRef = _recoDataDao.getFilesDao().uploadFromStream(
                        Paths.get(recoFormResult.getPreprocessedFile()).getFileName().toString(),
                        fis,
                        FileUtils.getContentType(recoFormResult.getPreprocessedFile()),
                        fileMeta.toMetadata()
                );
                dbFileRef.setTags(fileTags);
                recoData.getFiles().add(dbFileRef);
                _recoDataDao.updateOrCreate(null, recoData); // Intermediate save in DB after adding a file
            } catch (IOException ioex) {
                String msg = format("Error saving file preprocessed by RecoForm[{0}]: {1}", recoFormResult.getRecoForm().getName(), ioex);
                LogHelper.setEvent("id00118", ioex, msg, new LogExt(wfTask).setLogAccount(ELDOC_RECO_BOT));
            } finally {
                _filesCleanupService.addQueueItem(Paths.get(recoFormResult.getPreprocessedFile()));
            }
        }
        return dbFileRef;
    }


    private List<DbFileRef> attachExtraFiles(WorkflowTask wfTask, RecoFormResult recoFormResult, DbFileRef preprocessedFinalFileRef) {
        List<DbFileRef> attachedFiles = new ArrayList<>();
        if (preprocessedFinalFileRef == null) {
            // Should never happen - currently form should not create extra files, if not performing custom preprocessing
            for (Triple<String, Integer, FileMeta> fileDef : recoFormResult.getExtraFiles()) {
                LOG.warn("RecoForm[{}] Extra file created without custom preprocessing: path: '{}', type: '{}', metadata: '{}'", recoFormResult.getRecoForm().getId(), fileDef.getLeft(), fileDef.getMiddle(), fileDef.getRight().toJson());
                _filesCleanupService.addQueueItem(Paths.get(fileDef.getLeft()));
            }
            return attachedFiles;
        }

        DbFile preprocessedFinalFile = _recoDataDao.getFilesDao().getFile(null, preprocessedFinalFileRef);
        for (Triple<String, Integer, FileMeta> fileDef : recoFormResult.getExtraFiles()) {
            if (TextIndexFileMeta.fromJson(fileDef.getRight().toJson()).getType() == TextIndexFileMeta.TYPE_TEXT_INDEX) {
                try {
                    preprocessedFinalFile.getMetadata().put(WorkflowFileMeta._TEXT_INDEX_STATE, WorkflowFileMeta.TEXT_INDEX_STATE_READY);
                    preprocessedFinalFile.getMetadata().put(WorkflowFileMeta._TEXT_INDEX_TYPE, fileDef.getMiddle());
                    String fileContent = Files.readString(Paths.get(fileDef.getLeft()), StandardCharsets.UTF_8);
                    _textIndexingService.uploadTextIndexFile(preprocessedFinalFile, fileContent, (TextIndexFileMeta) fileDef.getRight(), fileDef.getMiddle(), false);
                    _recoDataDao.getFilesDao().updateFileMeta(null, preprocessedFinalFileRef, preprocessedFinalFile.getMetadata());
                } catch (IOException ioex) {
                    String msg = format("Error saving extra file, created by RecoForm[{0}]: {1}", recoFormResult.getRecoForm().getName(), ioex);
                    LogHelper.setEvent("id00416", ioex, msg, new LogExt(wfTask).setLogAccount(ELDOC_RECO_BOT));
                } finally {
                    _filesCleanupService.addQueueItem(Paths.get(recoFormResult.getPreprocessedFile()));
                }
            } else {
                LOG.error("RecoForm[{}] Unknown extra file created: path: '{}', type: '{}', metadata: '{}'", recoFormResult.getRecoForm().getId(), fileDef.getLeft(), fileDef.getMiddle(), fileDef.getRight().toJson());
            }
        }

        return attachedFiles;
    }


    /**
     * Saves raw recoResults data within RecoData (RecoResult)
     *
     * @param recoData {@link RecoData}, that owns provided RecoResults
     * @param recoFormResult
     */
    void attachRecoResultFile(RecoData recoData, RecoFormResult recoFormResult) {
        List<String> fileTags = List.of(RecoData.FILE_TAG.IDP_RECORESULT.name());
        Map<String, Object> metadata = CollectionUtils.isEmpty(fileTags) ? null : Map.of(RecoFileMeta._TAGS, fileTags);
        DbFileRef dbFileRef = _recoDataDao.getFilesDao().uploadFromByteArray(RecoData.ATTACHMENT_RECORESULT,
                recoFormResult.toJson().getBytes(StandardCharsets.UTF_8),
                "application/json",
                metadata);
        dbFileRef.setTags(fileTags);
        recoData.getFiles().add(dbFileRef);
        _recoDataDao.updateOrCreate(null, recoData); // Intermediate save in DB after adding a file
    }


    /**
     * Checks whether resolution threshold is passed as per RecoForm settings: in case source image is below resolution threshold, document will be sent for manual validation
     *
     * @param recoWF
     * @param recoFormResult
     * @return
     */
    boolean isValidationRequiredOnResolutionThreshold(Workflow recoWF, RecoFormResult recoFormResult, RecognitionMetaDoc recoMetaDoc) {
        boolean requiresValidation = false;
        String resolutionThreshold = recoFormResult.getRecoForm().getProperty(PropertyConfig.IDP_RESOLUTION_THRESHOLD, "");

        if (isBlank(resolutionThreshold)) {
            // nothing to do, property not presents
            return requiresValidation;
        }

        LOG.trace("RecoForm[{}]({}) advanced property '{}'={} ", recoFormResult.getRecoForm().getId(), recoFormResult.getRecoForm().getName(), PropertyConfig.IDP_RESOLUTION_THRESHOLD, resolutionThreshold);

        PropertyConfig resolutionProp = PropertyConfig.get(PropertyConfig.IDP_RESOLUTION_THRESHOLD);
        if (resolutionProp == null) {
            LOG.warn("RecoForm[{}]({}) Property config is not defined for name: {}", recoFormResult.getRecoForm().getId(), recoFormResult.getRecoForm().getName(), PropertyConfig.IDP_RESOLUTION_THRESHOLD);
            return requiresValidation;
        } else if (!resolutionThreshold.matches(resolutionProp.getValidator())) {
            LOG.warn("RecoForm[{}]({}) Property value [{}] is not valid (matching pattern: {})", recoFormResult.getRecoForm().getId(), recoFormResult.getRecoForm().getName(), resolutionThreshold, resolutionProp.getValidator());
            return requiresValidation;
        }

        Rectangle sizeThreshold = ImageProcessor.getDimensionsFromString(resolutionThreshold);

        // Check whether source image is in place
        DbFile sourceFile = _workflowDao.getFilesDao().getFile(null, recoWF.getFileByTag(Workflow.FILE_TAG.IDP_SOURCE.name()));
        if (sourceFile == null) {
            LogHelper.setEvent("id00115", null, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
            return requiresValidation;
        }

        RecoFileMeta sourceMeta = RecoFileMeta.fromJson(sourceFile.getMetadataAsJson());
        List<String> pageDimensions = sourceMeta.getPagesDimensions();

        // Iterate over all Page Results and check for Field Results
        if (!pageDimensions.isEmpty()) {
            for (int pageResultIdx = 0; pageResultIdx < recoFormResult.getPages().size(); pageResultIdx++) {
                RecoPageResult recoPageResult = recoFormResult.getPages().get(pageResultIdx);

                // Check whether page contains any non-optional field (threshold > 0)
                if (recoPageResult.getFields().stream().anyMatch(recoFieldResult -> recoFormResult.getRecoFieldByResult(recoFieldResult).getConfidenceThreshold() > 0)) {
                    // Check whether we have page dimension for this page
                    if (pageDimensions.size() > pageResultIdx && pageDimensions.get(pageResultIdx).matches(resolutionProp.getValidator())) {
                        Rectangle pageSizes = ImageProcessor.getDimensionsFromString(pageDimensions.get(pageResultIdx));
                        double pageW = Math.min(pageSizes.getWidth(), pageSizes.getHeight());
                        double pageH = Math.max(pageSizes.getWidth(), pageSizes.getHeight());
                        LOG.trace("RecoWorkflow[{}] source file page[{}] resolution: {}x{}", recoWF.getId(), pageResultIdx, pageW, pageH);

                        if (pageW < sizeThreshold.getWidth() || pageH < sizeThreshold.getHeight()) {
                            String msg = format("Page[{0}] resolution (W={1,number,#}; H={2,number,#}) below defined threshold (W={3,number,#}; H={4,number,#})",
                                    pageResultIdx + 1,
                                    pageW, pageH,
                                    sizeThreshold.getWidth(), sizeThreshold.getHeight());

                            RecoDebugger recoDebugger = recoMetaDoc.getRecoDebugger().forForm(recoFormResult.getRecoForm().getId());
                            recoDebugger.setPageIdx(recoFormResult.getFirstPageIdx() + pageResultIdx);
                            recoDebugger.log(RecoDebugger.ENTRY_TYPE_PAGE, msg);

                            LogHelper.setEvent("id00116", null, msg, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
                            requiresValidation = true;
                            break;
                        }
                    } else {
                        LOG.warn("RecoWorkflow[{}] source RecoFileMeta doesn't contain pages dimensions data or it is not valid for page index: {}", recoWF.getId(), pageResultIdx);
                    }
                }
            }
        }

        return requiresValidation;
    }


    /**
     * Checks, if provided RecoMetaDoc contains at least one pair of RecoFormResult, that has overlapping pages
     *
     * @param recoMetaDoc
     * @return
     */
    boolean isValidationRequiredOnOverlappingResults(RecognitionMetaDoc recoMetaDoc) {
        if (recoMetaDoc.getRecoResultList().size() <= 1) {
            return false;
        }

        for (int i = 0; i < recoMetaDoc.getRecoResultList().size() - 1; i++) {
            RecoFormResult firstResult = recoMetaDoc.getRecoResultList().get(i);
            Range<Integer> firstResultRange = Range.of(firstResult.getFirstPageIdx(), firstResult.getLastPageIdx());
            for (int j = i + 1; j < recoMetaDoc.getRecoResultList().size(); j++) {
                RecoFormResult secondResult = recoMetaDoc.getRecoResultList().get(j);
                Range<Integer> secondResultRange = Range.of(secondResult.getFirstPageIdx(), secondResult.getLastPageIdx());
                if (firstResultRange.isOverlappedBy(secondResultRange)) {
                    return true;
                }
            }
        }

        return false;
    }


    private void processDocument(Workflow recoWF, DbFile recoFile, final RecognitionMetaDoc recoMetaDoc, AtomicBoolean requiresValidation, List<RecoForm> recoFormList, AiRequestContext billingRequestContext) throws IOException {
        recoMetaDoc.setCurrentPageIdx(0);

        // Remove forms, that are not supported, from matching (i.e. if form for AI/LLM is enabled, but AI services are not available)
        Iterator<RecoForm> recoFormIT = recoFormList.iterator();
        while (recoFormIT.hasNext()) {
            RecoForm recoForm = recoFormIT.next();
            if (!isFormProcessable(recoForm)) {
                LOG.debug("RecoWorkflow[{}]|RecoForm[{}]: Ignoring form as not currently supported for matching, form type: {}", recoWF.getId(), recoForm.getId(), recoForm.getType());
                recoFormIT.remove();
            }
        }

        // Get RecoForms list selected by tags for document
        List<RecoForm> recoFormListByTags = updateRecoFormsSelectedByTags(recoWF, recoFormList, recoMetaDoc);

        if (isUseAiClassification(RecoFileMeta.fromJson(recoFile.getMetadataAsJson()), recoFormList)) {
            updateRecoFormsSelectedByAi(recoMetaDoc, recoFormListByTags, recoFile, billingRequestContext);
        } else {
            // Update RecoForms selected by keywords for document pages which have match
            updateRecoFormsSelectedByKeywords(recoMetaDoc, recoFormListByTags, recoFile);
        }

        // Log classification results
        logDocumentFragments(recoWF, recoMetaDoc);

        // Iterate over all document pages and perform matching
        while (!recoMetaDoc.isDocumentEndReached()) {

            // Match RecoFrom template: perform actual matching with pre-selected by keywords recoForms
            List<RecoFormResult> recoFormResultList = processDocumentFragment(
                    TenantContext.getCurrentTenantId(),
                    recoWF,
                    recoMetaDoc,
                    recoFile,
                    billingRequestContext
            );

            doFragmentsClassificationIfNeeded(recoWF, recoMetaDoc, recoFormResultList.size(), requiresValidation);

            // Move cursor to the next page for matching in the source document
            int nextFragmentToBeMatchedStartPageIdx = getNextDocumentFragmetToBeMatched(recoMetaDoc, recoFormResultList);
            recoMetaDoc.setCurrentPageIdx(nextFragmentToBeMatchedStartPageIdx);
        }
    }


    private boolean isFormProcessable(RecoForm form) {
        return switch (form.getType()) {
            // Anchor-based and Regex-based forms are always processable
            case RecoForm.TYPE_AIBASED -> _aiService.isVisionAvailable();
            default -> false;
        };
    }


    private void updateRecoFormsSelectedByAi(RecognitionMetaDoc recoMetaDoc, List<RecoForm> recoFormList, DbFile recoFileRef, AiRequestContext billingRequestContext) {
        RecoFileMeta recoFileMeta = RecoFileMeta.fromJson(recoFileRef.getMetadataAsJson());

        Map<String, String> formsDescription = new HashMap<>();
        for (RecoForm recoForm : recoFormList) {
            StringBuilder formDescBuilder = new StringBuilder();
            formDescBuilder.append("- Form name: ").append(recoForm.getName()).append(System.lineSeparator());
            if (isNotBlank(recoForm.getDescription())) {
                formDescBuilder.append("    Description: ").append(recoForm.getDescription()).append(System.lineSeparator());
            }
            formsDescription.put(recoForm.getName(), formDescBuilder.toString().trim());
        }

        List<String> fileContent = getRecoFileContent(recoFileRef);

        // Request classification by AI, if enabled, and update recoForms for document pages
        AiRequestContext aiContext;
        if (billingRequestContext instanceof AiRequestContext aiRequestContext) {
            aiContext = aiRequestContext;
        } else {
            aiContext = AiRequestContext.forSrcIDP(I18n.LANG_SYSTEM_CODE_EN, TenantContext.getCurrentTenantId(), billingRequestContext.getAclContext());
        }
        List<DocumentFragmentAiDTO> detectedFragmentsDTOList = _aiService.classifyDocument(recoFileRef.getId(), fileContent, formsDescription, true, aiContext, 3);
        if (detectedFragmentsDTOList == null) {
            return;
        }

        if (detectedFragmentsDTOList.isEmpty()) {
            // AI was not able to detect documents in provided file content, but it still may be able to extract data from it - add auto form
            assignAutoForm(recoMetaDoc, 0, recoMetaDoc.getNumberOfPages() - 1);
            return;
        }

        // Assign RecognitionMetaFragments to the RecoWorkflow according to the received classification
        for (DocumentFragmentAiDTO fragmentDTO : detectedFragmentsDTOList) {
            List<RecoForm> fragmentFormsList = recoFormList.stream()
                    .filter(form -> fragmentDTO.getCandidates().stream().anyMatch(candidate -> Strings.CI.equals(form.getName(), candidate)))
                    .distinct()
                    .toList();
            List<String> unknownFormsList = fragmentDTO.getCandidates().stream()
                    .filter(candidate -> fragmentFormsList.stream().noneMatch(form -> Strings.CI.equals(form.getName(), candidate)))
                    .toList();
            if (fragmentFormsList.isEmpty() && !unknownFormsList.isEmpty()) {
                // LLM found some document, but was not able to identify it as corresponding to any provided RecoForm
                if (recoFileMeta.getProperty(RecoFileMeta.IDP_AUTO_CLASSIFICATION, false)) {
                    // Auto-classification is enabled - we should create empty 'AutoForm' so that AI will automatically pick fields set and match form
                    assignAutoForm(recoMetaDoc, fragmentDTO.getPageStart() - 1, fragmentDTO.getPageEnd() - 1); // LLM responds with 1-based page indexes
                }
                continue;
            }

            for (RecoForm recoForm : fragmentFormsList) {
                RecognitionMetaFragment docFragment = new RecognitionMetaFragment();
                docFragment.setPageStart(fragmentDTO.getPageStart() - 1); // LLM responds with 1-based page indexes
                docFragment.setPageEnd(fragmentDTO.getPageEnd() - 1); // LLM responds with 1-based page indexes
                docFragment.setRecoForm(recoForm);
                recoMetaDoc.getRecoMetaFragmentList().add(docFragment);
            }
        }
    }


    private void assignAutoForm(RecognitionMetaDoc recoMetaDoc, int pageStart, int pageEnd) {
        // Auto-classification is enabled - we should create empty 'AutoForm' so that AI will automatically pick fields set and match form
        RecoForm autoForm = RecoFormService.createRecoForm(null);
        autoForm.setName(RecoForm.AUTOMATIC_FORM_NAME);
        autoForm.setId(RecoForm.AUTOMATIC_FORM_ID);
        autoForm.setType(RecoForm.TYPE_AIBASED);

        RecognitionMetaFragment docFragment = new RecognitionMetaFragment();
        docFragment.setPageStart(pageStart);
        docFragment.setPageEnd(pageEnd);
        docFragment.setRecoForm(autoForm);
        recoMetaDoc.getRecoMetaFragmentList().add(docFragment);
    }


    private boolean isUseAiClassification(RecoFileMeta recoFileMeta, List<RecoForm> recoFormsList) {
        // Currently we perform AI classification only in case if VL model available to avoid incorrect results from OCR
        return _aiService.isClassificationAvailable()
                && (recoFileMeta.getProperty(RecoFileMeta.IDP_AUTO_CLASSIFICATION, false) || recoFormsList.stream().anyMatch(form -> form.getType() == RecoForm.TYPE_AIBASED));
    }


    private List<String> getRecoFileContent(DbFile recoFileRef) {
        try (InputStream is = _workflowDao.getFilesDao().getFileDownloadStream(recoFileRef.getDbFileRef()); PDDocument preprocessedDoc = PdfUtils.loadPDF(is)) {
            List<String> docText = new ArrayList<>();
            for (int pageIdx = 0; pageIdx < preprocessedDoc.getNumberOfPages(); pageIdx++) {
                docText.add(PdfUtils.getText(preprocessedDoc, pageIdx));
            }
            return docText;
        } catch (IOException ex) {
            throw new RecoWorkflowRecognitionException("Error reading pre-processed file content", ex);
        }
    }


    /**
     * Calculates the start of the next document fragment, that should be matched, based on next rules:<br>
     * <ul>
     * <li>If no RecoForm matched for current fragment or RecoForm is assigned based on keywords classification - proceed with next fragment;</li>
     * <li>If there is RecoForms (possibly multiple) matched for current fragment and all fits into it, without overlapping next fragment - proceed with next fragment;</li>
     * <li>If there is only one RecoForm matched, all required fields are matched (confidence level = 100), and it overlaps next fragment(s) - proceed with matching for
     * next fragment after the end of current RecoFormResult;</li>
     * <li>If there is only one RecoForm matched, and it misses some required fields (confidence level &lt; 100), and it overlaps next fragment(s) - proceed with next
     * fragment anyway;</li>
     * <li>If there is multiple recoforms matched - proceed with next fragment, even if one of matched forms has confidence level 100 and overlaps next fragment;</li>
     * </ul>
     *
     * @return page index of the beginning of the document fragment, that should be matched next
     */
    int getNextDocumentFragmetToBeMatched(RecognitionMetaDoc recoMetaDoc, List<RecoFormResult> recoFormResultList) {
        if (recoFormResultList.isEmpty()) {
            // No RecoForm matched - proceed with next fragment, as usual
            return recoMetaDoc.getNextDocumentStartPageIdx();
        }

        if (recoFormResultList.size() > 1) {
            // Multiple forms matched - proceed with next fragment, as usual, regardless of their page range
            return recoMetaDoc.getNextDocumentStartPageIdx();
        }

        RecoFormResult recoResult = recoFormResultList.get(0);
        if (recoResult.getRecoFormConfidence() < 100) {
            // RecoFormResult has fields, that are required, but was not matched - proceedwith next fragment, as usual
            return recoMetaDoc.getNextDocumentStartPageIdx();
        }

        // At this point we have only one RecoFormResult with confidence level 100 (all required fields matched) - get first document fragment, that starts
        // after the last page of the RecoFormResult
        int recoFormResultLastPageIdx = recoResult.getPages().get(recoResult.getPages().size() - 1).getPageIdx();
        int nextFragmentStart = recoMetaDoc.getNextDocumentStartPageIdx();
        while (nextFragmentStart <= recoFormResultLastPageIdx && nextFragmentStart < recoMetaDoc.getNumberOfPages()) {
            recoMetaDoc.setCurrentPageIdx(nextFragmentStart);
            nextFragmentStart = recoMetaDoc.getNextDocumentStartPageIdx();
        }
        return nextFragmentStart;
    }


    /**
     * Performs classification of currently processed fragments, if required according to matching results (No RecoForm matched on current step/batch)
     *
     * @param recoWF {@link Workflow} document being matched
     * @param recoMetaDoc {@link RecognitionMetaDoc} matching metadata
     * @param recoResultsCount number of RecoForms successfully matched on current step/batch
     * @param requiresValidation shows, if document requires validation (global flag)
     */
    void doFragmentsClassificationIfNeeded(Workflow recoWF, RecognitionMetaDoc recoMetaDoc, int recoResultsCount, AtomicBoolean requiresValidation) {

        if (recoResultsCount > 0) {
            // There is already matched form for current fragment - no classification required
            return;
        }

        // Find forms, that can be used for classification by keywords
        List<RecognitionMetaFragment> recoFormsKeywordsClassification = recoMetaDoc.getCurrentPageFragments().stream()
                .filter(fragment -> !fragment.getRecoForm().getKeywordsInclusive().isEmpty())
                .filter(fragment -> fragment.getRecoForm().getProperty(IDP_RECOFORM_KEYWORDS_CLASSIFICATION, false))
                .collect(Collectors.toList());

        if (recoFormsKeywordsClassification.isEmpty()) {
            // No forms can be used for classification - nothing to do
            return;
        }

        double classificationConfidence = 100.0 / recoFormsKeywordsClassification.size();

        for (RecognitionMetaFragment fragment : recoFormsKeywordsClassification) {
            RecoFormResult rfr = createClassificationResultForFragment(fragment);
            rfr.setRecoFormConfidence(classificationConfidence);
            if (fragment.getRecoForm().hasFields()) {
                requiresValidation.set(true);
            }

            // Update page index with current page offset
            for (int i = 0; i < rfr.getPages().size(); i++) {
                rfr.getPages().get(i).setPageIdx(fragment.getPageStart() + i);
            }

            // Log classification to RecoDebugger
            RecoDebugger recoDebugger = recoMetaDoc.getRecoDebugger().forForm(rfr.getRecoForm().getId());
            recoDebugger.setPageIdx(fragment.getPageStart());
            recoDebugger.log(RecoDebugger.ENTRY_TYPE_FORM,
                    format("RecoForm assigned based on the keywords classification for page {0}.", fragment.getPageStart()));

            // Log classification to document
            String msg2 = format(
                    "RecoForm[{0}]({1}) assigned based on the keywords classification for page {2}",
                    rfr.getRecoForm().getId(),
                    rfr.getRecoForm().getName(),
                    fragment.getPageStart() + 1);
            LogHelper.setEvent("id00113", null, msg2, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));

            recoMetaDoc.getRecoResultList().add(rfr);
        }
    }


    /**
     * Creates new RecoFormResult, that will represent classification results for fragment.<br>
     * In general, classification results represent 'matching' result for the RecoForm, assigned for fragment, but with no
     * data extracted - i.e. 'we are sure, that RecoForm X is present here, but can't extract data'.<br>
     * In case created RecoFormResult has more pages, than provided fragment - excessive pages are removed and all fields
     * from them are moved to the last 'valid' page.
     *
     * @param fragment {@link RecognitionMetaFragment} document fragment, for which classification results should be created
     * @return {@link RecoFormResult} with classification results
     */
    RecoFormResult createClassificationResultForFragment(RecognitionMetaFragment fragment) {
        RecoFormResult recoFormResult = RecoFormResultUtils.initWithRecoForm(fragment.getRecoForm());

        // Reset value region coordinates for all fields if form is assigned by keywords, so fields won't be shown on document preview
        // See: http://jira.dms-solutions.co/browse/ELDOC-492
        recoFormResult.getFields().forEach(recoFieldResult -> {
            recoFieldResult.setRegion(new Rectangle());
            recoFieldResult.setMatchConfidence(RecoFieldResult.FIELD_NOT_FOUND_CONFIDENCE);
        });

        // If created RecoFormResult has more pages, than available fragment - excessive pages should be removed, moving fields from them
        // to last 'valid' RecoPageResult
        int fragmentPagesCount = fragment.getPageEnd() - fragment.getPageStart() + 1;
        if (recoFormResult.getPages().size() > fragmentPagesCount) {
            // Get last page of the RecoFormResult, that fits fragment
            RecoPageResult lastPage = recoFormResult.getPages().get(fragmentPagesCount - 1);

            // Move all RecoFieldResults from extra pages to last page
            for (int i = fragmentPagesCount; i < recoFormResult.getPages().size(); i++) {
                lastPage.getFields().addAll(recoFormResult.getPages().get(i).getFields());
            }

            // Remove extra pages from RecoFormResult
            recoFormResult.getPages().retainAll(recoFormResult.getPages().subList(0, fragmentPagesCount));
        }

        // Set pageIdx of the source document to the current RecoFormResult
        return recoFormResult;
    }


    /**
     * Processes document by matching RecoForms
     *
     * @param recoWF
     * @param recoFile
     * @param recoMetaDoc
     * @param tenantId
     * @return List of RecoFormResults for matched RecoForms. Can be empty, if no RecoForm matched, or contain multiple RecoFormResults, if multiple RecoForms matched with same confidence levels
     * @throws RecoWorkflowRecognitionException
     */
    private List<RecoFormResult> processDocumentFragment(final String tenantId, final Workflow recoWF, RecognitionMetaDoc recoMetaDoc, DbFile recoFile, AiRequestContext billingRequestContext) throws RecoWorkflowRecognitionException {
        List<RecoFormResult> recoFormResultList = new ArrayList<>();
        try {
            // Create list of tasks, one task per each recoForm
            Map<Future<RecoFormResult>, RecoProcessorTask> processingFutures = new HashMap<>();
            recoMetaDoc.getCurrentPageFragments().forEach(fragment -> {
                RecoProcessorTask recoProcessorTask = new RecoProcessorTask(tenantId, recoWF, recoMetaDoc, fragment, recoFile, fragment.getRecoForm(), billingRequestContext);
                processingFutures.put(_executor.submit(recoProcessorTask), recoProcessorTask);
            });

            // Retrieve all matching results. Waits for computations to finish, if necessary
            recoFormResultList = monitorAndRetrieveResults(recoWF, recoMetaDoc, processingFutures);

            if (recoFormResultList.isEmpty()) {
                return new ArrayList<>();
            }

            RecoFormResult bestResult = recoFormResultList.stream().max(Comparator.comparingDouble(RecoFormResult::getRecoFormConfidence)).get();
            if (bestResult.getRecoFormConfidence() >= FORM_CONFIDENCE_THRESHOLD_INT) {

                // Add all RecoFormResults, that has same confidence level, as best RecoFormResult to RecoMetaDoc.
                // Such RecoFormResults will have overlapping page range and need to be handled separatelly by user
                List<RecoFormResult> bestRecoResultsList = recoFormResultList.stream()
                        .filter(recoResult -> recoResult.getRecoFormConfidence() == bestResult.getRecoFormConfidence())
                        .collect(Collectors.toList());

                recoMetaDoc.getRecoResultList().addAll(bestRecoResultsList);

                // Return number of recoForms, that was matched with same confidence level, as best result
                return bestRecoResultsList;
            } else {
                LOG.trace("RecoFormResult[{}] with best confidence [{}] is below defined threshold: [{}]", bestResult.getRecoForm().getName(), bestResult.getRecoFormConfidence(), FORM_CONFIDENCE_THRESHOLD_INT);
                return new ArrayList<>();
            }
        } finally {
            // Add Preprocessed Final files of recoFormResults, that wasn't selected, to files removal service
            // Best RecoFormResult is to be added on the later stage (once its preprocessed final file is saved to DB)
            recoFormResultList.stream()
                    .filter(not(recoMetaDoc.getRecoResultList()::contains))
                    .map(RecoFormResult::getPreprocessedFile)
                    .filter(Objects::nonNull)
                    .map(Paths::get)
                    .forEach(_filesCleanupService::addQueueItem);

            // Add extra files, that may be generated during matching of recoFormResults, that wasn't selected, to files removal service
            recoFormResultList.stream()
                    .filter(not(recoMetaDoc.getRecoResultList()::contains))
                    .map(RecoFormResult::getExtraFiles)
                    .flatMap(List::stream)
                    .map(Triple::getLeft)
                    .filter(Objects::nonNull)
                    .map(Paths::get)
                    .forEach(_filesCleanupService::addQueueItem);

            // Cleanup file attachments for Image-type fields of RecoFormResults, that wasn't selected
            recoFormResultList.parallelStream()
                    .filter(not(recoMetaDoc.getRecoResultList()::contains))
                    .map(RecoFormResult::getFields)
                    .flatMap(List::parallelStream)
                    .flatMap(field -> field.isTypeTable() ? field.getTableData().parallelStream().flatMap(List::parallelStream) : Stream.of(field).parallel())
                    .filter(RecoFieldResult::isTypeImage)
                    .map(RecoFieldResult::getValue)
                    .filter(StringUtils::isNotBlank)
                    .sequential()
                    .map(id -> _recoDataDao.getFilesDao().createDbFileRef(id, Arrays.asList(RecoData.FILE_TAG.IDP_FIELD_ATTACHMENT.name())))
                    .forEach(dbFileRef -> _recoDataDao.getFilesDao().deleteFile(null, dbFileRef));

        }
    }


    /**
     * Checks all queued Recognition tasks, retrieves their results, once task was finished, and if debugging is off - interrupts obviously loosing matching tasks for optimization purposes.
     *
     * @param recoWF
     * @param recoMetaDoc
     * @param matchingTasks
     * @return
     */
    List<RecoFormResult> monitorAndRetrieveResults(Workflow recoWF, RecognitionMetaDoc recoMetaDoc, Map<Future<RecoFormResult>, RecoProcessorTask> matchingTasks) {
        List<RecoFormResult> recoFormResultList = new ArrayList<>();

        boolean matchingFinished = false;

        while (!matchingFinished) {

            matchingFinished = matchingTasks.keySet().stream().allMatch(Future::isDone);

            processCompletedTasks(matchingTasks, recoFormResultList, recoWF);

            if (!recoMetaDoc.getRecoDebugger().isEnabled()) {
                // Debug disabled - we need to control/stop matching, as we do not need to obtain as full matching results, as possible
                interruptObviouslyLoosingTasks(matchingTasks, recoFormResultList, recoWF);
            }

            // Wait for 1 second before next iteration
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                LOG.warn("Document fragment matching thread was interrupted", ex);
                Thread.currentThread().interrupt();
            }
        }

        return recoFormResultList;
    }


    /**
     * Checks all queued tasks, and if they were successfully completed (not canceled) - retrieves their results.
     * Tasks, that was canceled, should not be added to the list of RecoFormResults, as they can't win
     *
     * @param matchingTasks
     * @param recoFormResultList
     * @param recoWF
     */
    void processCompletedTasks(Map<Future<RecoFormResult>, RecoProcessorTask> matchingTasks, List<RecoFormResult> recoFormResultList, Workflow recoWF) {
        List<Future> canceledFuturesToRemove = new ArrayList<>();

        matchingTasks.keySet().stream()
                .filter(Future::isDone) // Process only results from RecoProcessors, that already finished processing
                .map(future -> {
                    try {
                        if (future.isCancelled()) {
                            return matchingTasks.get(future).getRecoFormResult();
                        } else {
                            return future.get();
                        }
                    } catch (InterruptedException | ExecutionException ex) {
                        canceledFuturesToRemove.add(future);
                        if (ex.getCause() instanceof RecoWorkflowRecognitionException dfre) {
                            LOG.error("RecoWorkflow[{}] Error during form classification (matching) process (for one of the RecoForms)", recoWF.getId(), dfre);
                            LogHelper.setEvent("id00150", ex, ex.getMessage(), new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
                        } else {
                            LOG.error("RecoWorkflow[{}] Form classification (matching) process was interrupted by server environment (for one of the RecoForms)", recoWF.getId(), ex);
                            LogHelper.setEvent("id00022", ex, null, new LogExt(recoWF).setLogAccount(ELDOC_RECO_BOT));
                        }
                        return null;
                    }
                }) // Obtain result
                .filter(Objects::nonNull) // Ignore null results - they was already logged and processed
                .filter(not(recoFormResultList::contains)) // Ignore RecoFormResult's for processors, which results may be obtained previously
                .forEach(recoFormResultList::add);

        canceledFuturesToRemove.forEach(matchingTasks::remove);
    }


    /**
     * Checks already started, but not finished matching tasks, if they can produce meaningful results (they can reach confidence level that can be higher than both
     * threshold and all already matched RecoFormResults)
     *
     * @param matchingTasks
     * @param recoFormResultList
     * @param recoWF
     */
    void interruptObviouslyLoosingTasks(Map<Future<RecoFormResult>, RecoProcessorTask> matchingTasks, List<RecoFormResult> recoFormResultList, Workflow recoWF) {
        // Calculate current max confidence
        double maxConf = recoFormResultList.stream()
                .mapToDouble(RecoFormResult::getRecoFormConfidence)
                .max().orElse(-1);

        // Stop all matching processes, that definitelly can't win. It is safe to check even if no form was matched, as maxConf will be -1 and no form can have negative confidence level
        matchingTasks.entrySet().stream()
                .filter(futureEntry -> !futureEntry.getKey().isDone()) // Ignore already finished tasks
                .filter(futureEntry -> futureEntry.getValue().getRecoFormResult() != null) // Ignore tasks, that still don't have RecoFormResult initiated (not started matching)
                .filter(futureEntry -> isFormProcessingShouldBeInterrupted(futureEntry.getValue(), maxConf)) // Find futures, that can't win regardless of further matching process
                .peek(futureEntry -> LOG.debug("RecoWorkflow[{}]|RecoForm[{}] Canceling matching as form can't win due to confidence level", recoWF.getId(), futureEntry.getValue().getRecoFormResult().getRecoForm().getId()))
                .forEach(futureEntry -> futureEntry.getKey().cancel(true)); // Cancel all such obviously-lost RecoForm matching tasks
    }


    /**
     * Checks, if provided matching task should be interrupted due to low confidence level.<br>
     * Matching should be interrupted, if:<br>
     * <ul>
     * <li>Current form can't pass confidence level threshold;</li>
     * <li>Current form can't get confidence level higher, than one of already matched forms;</li>
     * </ul>
     *
     * @param task {@link RecoProcessorTask} matching task to be checked
     * @param maxConf Maximum confidence level of already finished matching tasks
     * @return
     */
    boolean isFormProcessingShouldBeInterrupted(RecoProcessorTask task, double maxConf) {
        double maxPossibleConfidence = RecoFormResultUtils.calculateMatchConfidenceMax(task.getRecoFormResult());
        return maxPossibleConfidence < maxConf || maxPossibleConfidence < FORM_CONFIDENCE_THRESHOLD_INT;
    }
}
