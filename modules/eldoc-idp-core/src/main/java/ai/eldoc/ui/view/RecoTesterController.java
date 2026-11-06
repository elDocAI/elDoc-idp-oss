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
package ai.eldoc.ui.view;

import ai.eldoc.TenantContext;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.context.*;
import ai.eldoc.dao.RecoFormDao;
import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.*;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.service.*;
import ai.eldoc.service.RecoPostProcessorService.PostProcessorCustomField;
import ai.eldoc.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.bind.JsonbException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.graalvm.polyglot.PolyglotException;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Recognition Tester Tool Class
 */
@Named
@ViewScoped
public class RecoTesterController extends AbstractUIController {

    @Serial
    private static final long serialVersionUID = -4921219052233439366L;

    private static final Logger LOG = LoggerFactory.getLogger(RecoTesterController.class);

    private static final int REGEX_TESTER_PAGES_COUNT = 3;

    @Inject
    private RecoFormDao _recoFormDao;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private RecognitionService _recoService;

    @Inject
    private FormDefinitionService _formDefService;

    @Inject
    private WorkflowService _workflowService;

    @Inject
    private RecoWorkflowService _recoWorkflowService;

    @Inject
    private RecoPostProcessorService _recoPostProcessorService;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;

    private String _recoFormId;
    private String _postProcFunc;

    // RegEx Tester fields
    private String _regexTesterHaystack;
    private String _regexTesterPattern;
    private String _regexTesterResult;

    // Field-level tester fields
    private String _postProcFieldInput;
    private String _postProcFieldResult;
    private String _postProcFieldConsole;
    private RecoFieldDataType _fieldDataType;

    // Form-level tester fields
    private String _postProcRecoDataJson;
    private String _postProcRecoDataResultJson;
    private String _postProcFormConsole;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        // Re-init regex value sample and reset regex result
        _regexTesterPattern = "(?smi)(?<value>.{1,3})";
        _regexTesterResult = "";
        _fieldDataType = RecoFieldDataType.TEXT;

        // Init sample values for field-post processing
        _postProcFieldInput = "{" + System.lineSeparator()
                + "    \"conf\": 99," + System.lineSeparator()
                + "    \"confThreshold\": 90," + System.lineSeparator()
                + "    \"fields\": null," + System.lineSeparator()
                + "    \"name\": \"Text field\"," + System.lineSeparator()
                + "    \"tags\": [" + System.lineSeparator()
                + "        \"FIELD_1\"" + System.lineSeparator()
                + "    ]," + System.lineSeparator()
                + "    \"type\": \"reco\"," + System.lineSeparator()
                + "    \"validated\": true," + System.lineSeparator()
                + "    \"value\": \"value\"" + System.lineSeparator()
                + "}";
    }


    /**
     * Runs currently used regular expression against haystack, updating corresponding tester results for UI
     */
    public void btnTestRegex() {
        if (isBlank(_regexTesterPattern) || isBlank(_regexTesterHaystack)) {
            _regexTesterResult = "You need to provide both Regex and Text for testing";
            return;
        }
        try {
            Pattern pattern = Pattern.compile(_regexTesterPattern);
            Matcher matcher = pattern.matcher(_regexTesterHaystack);
            if (matcher.find()) {
                _regexTesterResult = matcher.group(RecognitionService.REGEX_VALUE_GROUP_NAME);
            } else {
                _regexTesterResult = "No matches found";
            }
        } catch (PatternSyntaxException ex) {
            _regexTesterResult = "Syntax error: " + ex;
        } catch (Throwable th) {
            LOG.warn("Reco-tester error: regex tester execution", th);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    /**
     * Toggle regex tester area
     */
    public void btnInitRegexTester() {
        // Re-init regex value sample and reset regex result
        _regexTesterPattern = "(?smi)(?<value>.{1,3})";
        _regexTesterResult = "";

        if (_recoFormId == null) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("recoTesterUI.noRecoFormSelected"));
            return;
        }

        // Re-read page contents for the sample text block
        try {
            RecoForm recoForm = _recoFormDao.find(_recoFormId);
            try (InputStream is = _recoFormDao.getFilesDao().getFileDownloadStream(recoForm.getPreprocessedFile()); PDDocument recoFile = PdfUtils.loadPDF(is)) {
                int endPage = Math.min(REGEX_TESTER_PAGES_COUNT, recoFile.getNumberOfPages());
                _regexTesterHaystack = "";
                for (int i = 0; i < endPage; i++) {
                    _regexTesterHaystack = _regexTesterHaystack.concat(PdfUtils.getText(recoFile, i));
                }
            } catch (IOException e3) {
                _regexTesterHaystack = "Error loading document from database";
            }
        } catch (Throwable th) {
            _regexTesterHaystack = "";
            LOG.warn("Reco-tester error: regex tester initialization", th);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnTestFieldPostProc() {
        if (isBlank(_postProcFunc)) {
            _postProcFieldConsole = format("You need to provide input for tester");
            return;
        }

        // Validate post-processing function for field before trying to execute it
        try (OutputStream out = new ByteArrayOutputStream()) {
            Map<String, Object> scriptScope = new HashMap<>();
            _recoPostProcessorService.validateFieldPostProcessor(_postProcFunc, scriptScope, out);
        } catch (TimeoutException | IOException | PolyglotException | ScriptingException ex) {
            String exceptionMessage;
            if (ex instanceof ScriptingException && ex.getCause() instanceof PolyglotException) {
                exceptionMessage = ex.getCause().getMessage();
            } else {
                exceptionMessage = ex.getMessage();
            }
            _postProcFieldResult = "";
            _postProcFieldConsole = format("Post-processing function is not valid: {0} {1}", System.lineSeparator(), fixExceptionMessageLineNumber(exceptionMessage));
            return;
        } catch (Throwable th) {
            _postProcFieldResult = "";
            _postProcFieldConsole = "Post-processing function is not valid.";
            LOG.warn("Reco-tester error: field post-processing validation", th);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
            return;
        }

        try (OutputStream out = new ByteArrayOutputStream()) {
            Map<String, Object> scriptScope = new HashMap<>();

            if (_fieldDataType == RecoFieldDataType.TEXT || _fieldDataType == RecoFieldDataType.TEXT_EXTENDABLE) {
                postProcessTextField(scriptScope, out);
            } else if (_fieldDataType == RecoFieldDataType.TABLE) {
                postProcessTableField(scriptScope, out);
            } else if (_fieldDataType == RecoFieldDataType.OMR) {
                postProcessOmrField(scriptScope, out);
            }

            _postProcFieldConsole = out.toString();
        } catch (TimeoutException | IOException | PolyglotException | ScriptingException ex) {
            String exceptionMessage;
            if (ex instanceof ScriptingException && ex.getCause() instanceof PolyglotException) {
                exceptionMessage = ex.getCause().getMessage();
            } else {
                exceptionMessage = ex.getMessage();
            }
            _postProcFieldResult = "";
            _postProcFieldConsole = format("Post-processing function is not valid: {0} {1}", System.lineSeparator(), fixExceptionMessageLineNumber(exceptionMessage));
        } catch (Throwable th) {
            _postProcFieldResult = "";
            _postProcFieldConsole = "Post-processing function is not valid";
            LOG.warn("Reco-tester error: field post-processing execution", th);
        }
    }


    /**
     * Fixes referenced line number for exeption in referenced JS code during post-processing
     *
     * @param message
     * @return
     */
    private String fixExceptionMessageLineNumber(String message) {
        String prefix = "postProcessor.js:\\d+:";
        String prefixOffset = "postProcessor.js:";

        Pattern pattern = Pattern.compile(prefix);
        Matcher matcher = pattern.matcher(message);

        int prefixStart;

        if (matcher.find()) {
            // Exception message contains reference to source code - remember it's offset and continue
            prefixStart = matcher.start();
        } else {
            // Exception message doesn't contain reference to source code - leave message as is
            return message;
        }

        int numberStart = prefixStart + prefixOffset.length();
        String messageStart = message.substring(0, numberStart);

        int numberEnd = message.indexOf(":", numberStart);
        String messageEnd = message.substring(numberEnd);

        // Reduce line number by 1, as first line of code is internally added by post-processor
        int lineNumber = Integer.parseInt(message.substring(numberStart, numberEnd)) - 1;

        return messageStart.concat(String.valueOf(lineNumber)).concat(messageEnd);
    }


    /**
     * Runs post-processing for TEXT RecoField
     *
     * @param scriptScope
     * @param out
     * @throws ScriptingException
     * @throws TimeoutException
     */
    private void postProcessTextField(Map<String, Object> scriptScope, OutputStream out) throws ScriptingException, TimeoutException {
        // Perform post-processing
        PostProcessorCustomField ppCf = JsonbUtils.getInstance(true).fromJson(_postProcFieldInput, PostProcessorCustomField.class);
        CustomField cf = RecoPostProcessorService.cfFromPostProcCf(ppCf, _configContext.getDefaultLang());
        String result = _recoPostProcessorService.postProcessTextField(cf, _postProcFunc, (String) ppCf.getValue(), _configContext.getDefaultLang(), scriptScope, out);
        ppCf = new PostProcessorCustomField(cf, result, _configContext.getDefaultLang());
        _postProcFieldResult = JsonbUtils.getInstance(true).toJson(ppCf);
    }


    /**
     * Runs post-processing for TABLE RecoField
     *
     * @param scriptScope
     * @param out
     * @throws ScriptingException
     * @throws TimeoutException
     */
    private void postProcessTableField(Map<String, Object> scriptScope, OutputStream out) throws ScriptingException, TimeoutException {
        // Perform post-processing
        PostProcessorCustomField ppCf = JsonbUtils.getInstance(true).fromJson(_postProcFieldInput, PostProcessorCustomField.class);
        CustomField cf = RecoPostProcessorService.cfFromPostProcCf(ppCf, _configContext.getDefaultLang());
        List<List<String>> result = _recoPostProcessorService.postProcessTableField(cf, _postProcFunc, (List<List<String>>) ppCf.getValue(), _configContext.getDefaultLang(), scriptScope, out);
        ppCf = new PostProcessorCustomField(cf, result, _configContext.getDefaultLang());
        _postProcFieldResult = JsonbUtils.getInstance(true).toJson(ppCf);
    }


    /**
     * Runs post-processing for OMR RecoField
     *
     * @param scriptScope
     * @param out
     * @throws ScriptingException
     * @throws TimeoutException
     */
    private void postProcessOmrField(Map<String, Object> scriptScope, OutputStream out) throws ScriptingException, TimeoutException {
        // Perform post-processing
        PostProcessorCustomField ppCf = JsonbUtils.getInstance(true).fromJson(_postProcFieldInput, PostProcessorCustomField.class);
        CustomField cf = RecoPostProcessorService.cfFromPostProcCf(ppCf, _configContext.getDefaultLang());
        List<Boolean> result = _recoPostProcessorService.postProcessOmrField(cf, _postProcFunc, (List<Boolean>) ppCf.getValue(), _configContext.getDefaultLang(), scriptScope, out);
        ppCf = new PostProcessorCustomField(cf, result, _configContext.getDefaultLang());
        _postProcFieldResult = JsonbUtils.getInstance(true).toJson(ppCf);
    }


    public void fieldDataTypeChangeListener() {
        // Set sample value for field post-processing tester input value
        if (_fieldDataType == RecoFieldDataType.TEXT) {
            _postProcFieldInput
                    = "{" + System.lineSeparator()
                    + "    \"conf\": 99," + System.lineSeparator()
                    + "    \"confThreshold\": 90," + System.lineSeparator()
                    + "    \"fields\": null," + System.lineSeparator()
                    + "    \"name\": \"Text field\"," + System.lineSeparator()
                    + "    \"tags\": [" + System.lineSeparator()
                    + "        \"FIELD_1\"" + System.lineSeparator()
                    + "    ]," + System.lineSeparator()
                    + "    \"type\": \"reco\"," + System.lineSeparator()
                    + "    \"validated\": true," + System.lineSeparator()
                    + "    \"value\": \"value\"" + System.lineSeparator()
                    + "}";
        } else if (_fieldDataType == RecoFieldDataType.TABLE) {
            _postProcFieldInput
                    = "{" + System.lineSeparator()
                    + "    \"conf\": [" + System.lineSeparator()
                    + "        [" + System.lineSeparator()
                    + "            99," + System.lineSeparator()
                    + "            99" + System.lineSeparator()
                    + "        ]," + System.lineSeparator()
                    + "        [" + System.lineSeparator()
                    + "            99," + System.lineSeparator()
                    + "            99" + System.lineSeparator()
                    + "        ]" + System.lineSeparator()
                    + "    ]," + System.lineSeparator()
                    + "    \"confThreshold\": null," + System.lineSeparator()
                    + "    \"fields\": [" + System.lineSeparator()
                    + "        {" + System.lineSeparator()
                    + "            \"confThreshold\": 90," + System.lineSeparator()
                    + "            \"fields\": null," + System.lineSeparator()
                    + "            \"name\": \"Col1\"," + System.lineSeparator()
                    + "            \"tags\": [" + System.lineSeparator()
                    + "                \"COL1\"" + System.lineSeparator()
                    + "            ]," + System.lineSeparator()
                    + "            \"type\": \"reco\"," + System.lineSeparator()
                    + "            \"validated\": false," + System.lineSeparator()
                    + "            \"value\": null" + System.lineSeparator()
                    + "        }," + System.lineSeparator()
                    + "        {" + System.lineSeparator()
                    + "            \"confThreshold\": 90," + System.lineSeparator()
                    + "            \"fields\": null," + System.lineSeparator()
                    + "            \"name\": \"Col2\"," + System.lineSeparator()
                    + "            \"tags\": [" + System.lineSeparator()
                    + "                \"COL2\"" + System.lineSeparator()
                    + "            ]," + System.lineSeparator()
                    + "            \"type\": \"reco\"," + System.lineSeparator()
                    + "            \"validated\": false," + System.lineSeparator()
                    + "            \"value\": null" + System.lineSeparator()
                    + "        }" + System.lineSeparator()
                    + "    ]," + System.lineSeparator()
                    + "    \"name\": \"Table field\"," + System.lineSeparator()
                    + "    \"tags\": [" + System.lineSeparator()
                    + "        \"INV_DATA\"" + System.lineSeparator()
                    + "    ]," + System.lineSeparator()
                    + "    \"type\": \"recoTable\"," + System.lineSeparator()
                    + "    \"validated\": [" + System.lineSeparator()
                    + "        [" + System.lineSeparator()
                    + "            true," + System.lineSeparator()
                    + "            true" + System.lineSeparator()
                    + "        ]," + System.lineSeparator()
                    + "        [" + System.lineSeparator()
                    + "            true," + System.lineSeparator()
                    + "            true" + System.lineSeparator()
                    + "        ]" + System.lineSeparator()
                    + "    ]," + System.lineSeparator()
                    + "    \"value\": [" + System.lineSeparator()
                    + "        [" + System.lineSeparator()
                    + "            \"Row1-Col1: value1\"," + System.lineSeparator()
                    + "            \"Row1-Col2: value2\"" + System.lineSeparator()
                    + "        ]," + System.lineSeparator()
                    + "        [" + System.lineSeparator()
                    + "            \"Row2-Col1: value4\"," + System.lineSeparator()
                    + "            \"Roc2-Col2: value5\"" + System.lineSeparator()
                    + "        ]" + System.lineSeparator()
                    + "    ]" + System.lineSeparator()
                    + "}";
        } else if (_fieldDataType == RecoFieldDataType.OMR) {
            _postProcFieldInput
                    = "{" + System.lineSeparator()
                    + "    \"conf\": [" + System.lineSeparator()
                    + "        99," + System.lineSeparator()
                    + "        99" + System.lineSeparator()
                    + "    ]," + System.lineSeparator()
                    + "    \"confThreshold\": 90," + System.lineSeparator()
                    + "    \"fields\": [" + System.lineSeparator()
                    + "        {" + System.lineSeparator()
                    + "            \"confThreshold\": 90," + System.lineSeparator()
                    + "            \"fields\": null," + System.lineSeparator()
                    + "            \"name\": \"Mark1\"," + System.lineSeparator()
                    + "            \"tags\": [" + System.lineSeparator()
                    + "                \"MARK1\"" + System.lineSeparator()
                    + "            ]," + System.lineSeparator()
                    + "            \"type\": \"reco\"," + System.lineSeparator()
                    + "            \"validated\": false," + System.lineSeparator()
                    + "            \"value\": null" + System.lineSeparator()
                    + "        }," + System.lineSeparator()
                    + "        {" + System.lineSeparator()
                    + "            \"confThreshold\": 90," + System.lineSeparator()
                    + "            \"fields\": null," + System.lineSeparator()
                    + "            \"name\": \"Mark2\"," + System.lineSeparator()
                    + "            \"tags\": [" + System.lineSeparator()
                    + "                \"MARK2\"" + System.lineSeparator()
                    + "            ]," + System.lineSeparator()
                    + "            \"type\": \"reco\"," + System.lineSeparator()
                    + "            \"validated\": false," + System.lineSeparator()
                    + "            \"value\": null" + System.lineSeparator()
                    + "        }" + System.lineSeparator()
                    + "    ]," + System.lineSeparator()
                    + "    \"name\": \"OMR field\"," + System.lineSeparator()
                    + "    \"tags\": [" + System.lineSeparator()
                    + "        \"OMR\"" + System.lineSeparator()
                    + "    ]," + System.lineSeparator()
                    + "    \"type\": \"recoOmr\"," + System.lineSeparator()
                    + "    \"validated\": true," + System.lineSeparator()
                    + "    \"value\": [" + System.lineSeparator()
                    + "        false," + System.lineSeparator()
                    + "        true" + System.lineSeparator()
                    + "    ]" + System.lineSeparator()
                    + "}";
        }
    }


    public void initFormPostProc() {
        RecoData postProcRecoData;
        if (_recoFormId == null) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("recoTesterUI.noRecoFormSelected"));
            return;
        }

        try {
            RecoForm recoForm = _recoFormDao.find(_recoFormId);
            FormDefinition formDef = _formDefService.getByWebId(FormDefinition.FORM_ID_RECO, true, null);
            Workflow recoWf = _workflowService.createWithFormDef(formDef, _userContext.getAclContext());
            _workflowService.configureWorkflowProcess(recoWf, formDef);
            recoWf.setTopic("Reco-tester generated: " + recoForm.getName());
            recoWf.setDateCreated(new Date());

            // Copy source file
            DbFileRef sourceFile = recoForm.getSourceFile();
            DbFile dbFile = _recoFormDao.getFilesDao().getFile(null, sourceFile);
            try (DbFileDownloadStream sourceDownloadStream = _recoFormDao.getFilesDao().getFileDownloadStream(sourceFile)) {
                Map<String, Object> originalFileCopyMeta = new HashMap<>(dbFile.getMetadata());
                originalFileCopyMeta.put(RecoFileMeta._TYPE, RecoFileMeta.TYPE_DEFAULT);
                DbFileRef originalFileCopy = _workflowDao.getFilesDao().uploadFromStream(
                        dbFile.getFileName(),
                        sourceDownloadStream,
                        dbFile.getContentType(),
                        originalFileCopyMeta
                );
                originalFileCopy.setTags(List.of(Workflow.FILE_TAG.IDP_SOURCE.name()));
                recoWf.getFiles().add(originalFileCopy);
            }

            // Copy preprocessed file
            DbFileRef preprocessedFile = recoForm.getPreprocessedFile();
            dbFile = _recoFormDao.getFilesDao().getFile(null, preprocessedFile);
            try (DbFileDownloadStream sourceDownloadStream = _recoFormDao.getFilesDao().getFileDownloadStream(preprocessedFile)) {
                Map<String, Object> preprocessedFileCopyMeta = new HashMap<>(dbFile.getMetadata());
                preprocessedFileCopyMeta.put(RecoFileMeta._TYPE, RecoFileMeta.TYPE_DEFAULT);
                DbFileRef preprocessedFileCopy = _workflowDao.getFilesDao().uploadFromStream(
                        dbFile.getFileName(),
                        sourceDownloadStream,
                        dbFile.getContentType(),
                        preprocessedFileCopyMeta
                );
                preprocessedFileCopy.setTags(List.of(Workflow.FILE_TAG.IDP_PREPROCESSED.name()));
                recoWf.getFiles().add(preprocessedFileCopy);
            }

            // Save Workflow (RecoWorkflow)
            _workflowDao.updateOrCreate(null, recoWf);

            // We need to terminate created Workflow, so it won't be taken to processing by robots
            _workflowService.actionTerminate(recoWf, DirectoryConstants.ELDOC_RECO_BOT, null, UserRequestMeta.fromRequest(JsfUtils.getRequest()));

            // Perform test matching
            try {
                AtomicBoolean requiresValidation = new AtomicBoolean();

                AiRequestContext billingRequestContext = AiRequestContext.forSrcIDP(I18n.LANG_SYSTEM_CODE_EN, TenantContext.getCurrentTenantId(), SecurityUtils.getSubjectAclContext());

                // Temporarily disable form-level post-processing to get recoData without it applied
                recoForm.setUsePostProc(false);
                List<RecoForm> selectedForms = List.of(recoForm);

                DbFile fa = _workflowDao.getFilesDao().getFile(null, recoWf.getFileByTag(Workflow.FILE_TAG.IDP_PREPROCESSED.name()));
                WorkflowTask wfTask = WorkflowTask.createFor(recoWf, _userContext.getId());
                _recoService.recognizeAndProcessDiag(recoWf, fa, wfTask, requiresValidation, selectedForms, billingRequestContext);

                // Turn on form post-processing back
                recoForm.setUsePostProc(true);

                List<RecoData> recoDataList = _recoWorkflowService.getRecoDataList(recoWf);

                if (recoDataList.isEmpty()) {
                    JsfUtils.addErrorMessage(_localeContext.getLabel("recoTesterUI.formPostProc.initNoFormMatched"));
                    return;
                }

                postProcRecoData = recoDataList.getFirst();
                _postProcRecoDataJson = RecoPostProcessorService.recoDataToJsonConverter(postProcRecoData, _configContext.getDefaultLang(), true);
            } catch (IOException ex) {
                LOG.warn("Error closing script console output stream", ex);
            } catch (NoRecoResultAvailableException ex) {
                _postProcRecoDataJson = "";
                JsfUtils.addErrorMessage(_localeContext.getLabel("recoTesterUI.formPostProc.initNoFormMatched"));
            }
        } catch (Throwable th) {
            _postProcRecoDataJson = "";
            LOG.warn("Reco-tester error: form post-processing initialization", th);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnTestFormPostProc() {
        RecoData postProcRecoDataResult;
        if (isBlank(_postProcRecoDataJson)) {
            _postProcFieldConsole = format("You need to provide input for tester");
            return;
        }

        // Validate post-processing function for form before trying to execute it
        try (OutputStream out = new ByteArrayOutputStream()) {
            Map<String, Object> scriptScope = new HashMap<>();

            _recoPostProcessorService.validateRecoDataPostProcessor(_postProcFunc, scriptScope, out);

        } catch (TimeoutException | IOException | PolyglotException | ScriptingException ex) {
            String exceptionMessage;
            if (ex instanceof ScriptingException && ex.getCause() instanceof PolyglotException) {
                exceptionMessage = ex.getCause().getMessage();
            } else {
                exceptionMessage = ex.getMessage();
            }
            _postProcRecoDataResultJson = "";
            _postProcFormConsole = format("Post-processing function for form is not valid: {0} {1}", System.lineSeparator(), fixExceptionMessageLineNumber(exceptionMessage));
            return;
        } catch (Throwable th) {
            _postProcRecoDataResultJson = "";
            _postProcFormConsole = format("Post-processing function for form is not valid.");
            LOG.warn("Reco-tester error: form post-processing validation", th);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
            return;
        }

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Map<String, Object> scriptScope = new HashMap<>();
            postProcRecoDataResult = RecoPostProcessorService.jsonToRecoDataConverter(_postProcRecoDataJson, _configContext.getDefaultLang());
            _recoPostProcessorService.postProcessRecoData(_postProcFunc, postProcRecoDataResult, _configContext.getDefaultLang(), scriptScope, os);
            if (postProcRecoDataResult.getRecoForm() == null) {
                // This hook required avoiding NPE on empty RecoData serialization
                RecoFormDto dummyRecoForm = new RecoFormDto(null, null, null);
                postProcRecoDataResult.setRecoForm(dummyRecoForm);
            }
            _postProcRecoDataResultJson = RecoPostProcessorService.recoDataToJsonConverter(postProcRecoDataResult, _configContext.getDefaultLang(), true);
            _postProcFormConsole = os.toString();
        } catch (JsonbException ex) {
            _postProcRecoDataResultJson = "";
            _postProcFormConsole = format("Value is not a valid RecoData: {0} {1}", System.lineSeparator(), ex.getMessage());
        } catch (IOException | TimeoutException | PolyglotException | ScriptingException ex) {
            String exceptionMessage;
            if (ex instanceof ScriptingException && ex.getCause() instanceof PolyglotException) {
                exceptionMessage = ex.getCause().getMessage();
            } else {
                exceptionMessage = ex.getMessage();
            }
            _postProcRecoDataResultJson = "";
            _postProcFormConsole = format("Post-processing function for form is not valid: {0} {1}", System.lineSeparator(), fixExceptionMessageLineNumber(exceptionMessage));
        } catch (Throwable th) {
            _postProcRecoDataResultJson = "";
            _postProcFormConsole = format("Post-processing function for form is not valid");
            LOG.warn("Reco-tester error: form post-processing execution", th);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public List<SelectItem> getFieldTypesSI() {
        List<SelectItem> result = new ArrayList<>();
        List<RecoFieldDataType> enabledTypes = Arrays.asList(RecoFieldDataType.TEXT, RecoFieldDataType.TABLE, RecoFieldDataType.OMR, RecoFieldDataType.TEXT_EXTENDABLE);
        for (RecoFieldDataType dataType : RecoFieldDataType.AVAILABLE) {
            result.add(new SelectItem(dataType, dataType.name(), null, !enabledTypes.contains(dataType)));
        }
        return result;
    }


    public String getPostProcFieldInput() {
        return _postProcFieldInput;
    }


    public void setPostProcFieldInput(String postProcFieldInput) {
        _postProcFieldInput = postProcFieldInput;
    }


    public String getPostProcFieldResult() {
        return _postProcFieldResult;
    }


    public void setPostProcFieldResult(String postProcFieldResult) {
        _postProcFieldResult = postProcFieldResult;
    }


    public String getPostProcFieldConsole() {
        return _postProcFieldConsole;
    }


    public void setPostProcFieldConsole(String postProcFieldConsole) {
        this._postProcFieldConsole = postProcFieldConsole;
    }


    public String getPostProcRecoDataJson() {
        return _postProcRecoDataJson;
    }


    public void setPostProcRecoDataJson(String postProcRecoDataJson) {
        _postProcRecoDataJson = postProcRecoDataJson;
    }


    public String getPostProcRecoDataResultJson() {
        return _postProcRecoDataResultJson;
    }


    public void setPostProcRecoDataResultJson(String postProcRecoDataResultJson) {
        _postProcRecoDataResultJson = postProcRecoDataResultJson;
    }


    public String getPostProcFormConsole() {
        return _postProcFormConsole;
    }


    public void setPostProcFormConsole(String postProcFormConsole) {
        _postProcFormConsole = postProcFormConsole;
    }


    public String getRegexTesterHaystack() {
        return _regexTesterHaystack;
    }


    public void setRegexTesterHaystack(String regexTesterHaystack) {
        _regexTesterHaystack = regexTesterHaystack;
    }


    public String getRegexTesterPattern() {
        return _regexTesterPattern;
    }


    public void setRegexTesterPattern(String regex) {
        _regexTesterPattern = regex;
    }


    public String getRegexTesterResult() {
        return _regexTesterResult;
    }


    public String getPostProcFunc() {
        return _postProcFunc;
    }


    public void setPostProcFunc(String postProcFunc) {
        _postProcFunc = postProcFunc;
    }


    public String getRecoFormId() {
        return _recoFormId;
    }


    public void setRecoFormId(String recoForm) {
        _recoFormId = recoForm;
    }


    public RecoFieldDataType getFieldDataType() {
        return _fieldDataType;
    }


    public void setFieldDataType(RecoFieldDataType fieldDataType) {
        _fieldDataType = fieldDataType;
    }

}
