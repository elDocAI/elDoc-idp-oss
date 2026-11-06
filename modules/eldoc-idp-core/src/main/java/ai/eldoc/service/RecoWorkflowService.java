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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.bpm.BpmParams;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.dao.RecoDataDao;
import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.*;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.reco.AbstractRecoFieldProcessor;
import ai.eldoc.util.ObjectValueUtils;
import com.mongodb.client.ClientSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.pdfbox.pdmodel.PDDocument;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import static ai.eldoc.bpm.BpmConstants.*;
import static ai.eldoc.bpm.BpmParams.*;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * RecoWorkflow Service provides methods for working with Workflow of the RecoWorkflow type (e.g.: form=frd1).
 * <p>
 * This service automatically differentiates Workflows (RecoDocs) of different versions: legacy (with 1 embedded RecoResult) and actual (with multiple RecoData)
 *
 * <pre>
 * RecoWorkflow BPM Rules
 * RecoWorkflow return to the manual validation step in case of the following scenarious:
 *
 * 1. RecoWorkflow Declined by RecoBot in case no form was matched.
 * Indicator for this case presence of the {@link ai.eldoc.bpm.BpmAclConstants#ACTION_TO_WORK_AGAIN} action in the document ACL
 *
 * 2. RecoWorkflow Executed by RecoBot (RecoForm matched) and here two scenarious are possible:
 *    A) document requires manual validation: Initiator is added to Executors for the next Step of the Execution order
 *       Indicator for this case presence of the {@link ai.eldoc.bpm.BpmAclConstants#ACTION_EXEC_DONE} action in the document ACL
 *
 *    B) RecoWorkflow automatically validated - document is moved to the one of the following stages:
 *       I) ConversionBot is added to the Executors in case conversion to be performed;
 *      II) Next Phase (Repo or Archive) based on the Process settings.
 *
 * </pre>
 */
@ApplicationScoped
public class RecoWorkflowService {

    @Inject
    private RecoDataDao _recoDataDao;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private WorkflowService _workflowService;

    @Inject
    private FilesCleanupService _filesCleanupService;

    @Inject
    private ConfigContext _configContext;


    //
    // BPM related Methods ------------------------------------------------------------------------
    //


    /**
     * Performs RecoData and RecoWorkflow validation logic (after Recognition). In case all RecoData items are validated - whole RecoWorkflow will be validated as well
     *
     * @param recoWF
     * @param wfTask
     * @param aclContext
     * @param recoDataList
     * @param recoData
     * @return Index of the first unvalidated RecoData in the recoDataList or -1 in case all are already validated
     */
    public int actionValidateReco(final Workflow recoWF, final WorkflowTask wfTask, final AclContext aclContext, List<RecoData> recoDataList, final RecoData recoData, UserRequestMeta userRequestMeta) {
        _workflowDao.isValid(recoWF);

        int result = validatedAndUpdateRecoData(recoWF, wfTask, aclContext, recoDataList, recoData);
        if (result != -1) {
            return result;
        }

        boolean requiresConversion = recoDataList.stream().anyMatch(recoDataItem -> isNotBlank(recoDataItem.getRecoForm().getForm()));
        BpmParams bpmParams = new BpmParams(IDP_RECO_REQUIRES_CONVERSION, requiresConversion);

        if (requiresConversion) {
            // Target form is set - need to send document for conversion
            wfTask.setNote("Document validated. Ready for conversion");
        } else {
            // Target form is NOT set - we may just send document further according to the process
            wfTask.setNote("Document validated");
        }

        _workflowService.actionDone(recoWF, wfTask, aclContext, userRequestMeta, bpmParams);

        return -1;
    }


    /**
     * Performs RecoData and RecoWorkflow validation logic (after Conversion). In case all RecoData items are validated - whole RecoWorkflow will be validated as well
     *
     * @param recoWF
     * @param wfTask
     * @param aclContext
     * @param recoDataList
     * @param recoData
     * @param userRequestMeta user request details like userAgent and userIP
     * @return Index of the first unvalidated RecoData in the recoDataList or -1 in case all are already validated
     */
    public int actionValidateConv(final Workflow recoWF, final WorkflowTask wfTask, final AclContext aclContext, List<RecoData> recoDataList, final RecoData recoData, UserRequestMeta userRequestMeta) {
        _workflowDao.isValid(recoWF);

        int result = validatedAndUpdateRecoData(recoWF, wfTask, aclContext, recoDataList, recoData);
        if (result != -1) {
            return result;
        }

        wfTask.setNote("Document validated & sent for conversion again");
        _workflowService.actionDone(recoWF, wfTask, aclContext, userRequestMeta, new BpmParams(BPM_SKIP_PROCESS_UPDATE, true));

        // For the Validation after Conversion we need to perform logyc like "To work again"
        _workflowService.actionToWorkAgain(recoWF, aclContext, userRequestMeta, new BpmParams(BPM_STEP, RECO_PROCESS_STEP_CONVERSIONBOT), null);

        return -1;
    }


    /**
     * Performs RecoData validation and save
     *
     * @param recoWF
     * @param wfTask
     * @param aclContext
     * @param recoDataList
     * @param recoData
     * @return Index of the first unvalidated RecoData in the recoDataList or -1 in case all are already validated
     */
    private int validatedAndUpdateRecoData(final Workflow recoWF, final WorkflowTask wfTask, final AclContext aclContext, List<RecoData> recoDataList, final RecoData recoData) {
        if (recoDataList.isEmpty() || recoData == null) {
            throw new RecoDataNotAvailableException().setWorkflow(recoWF);
        }

        if (!Objects.equals(recoWF.getId(), recoData.getRefId())) {
            throw new IllegalArgumentException("Workflow(RecoWorkflow) and RecoData relation mismatch");
        }

        if (!recoData.isValidated()) {
            recoData.setValidated(true); // mark that we have validated it (probably manually on the UI)
            markFilledFieldsValidated(recoData);
            if (!_recoDataDao.updateOrCreate(null, recoData)) {
                throw new RecoDataSaveException().setWorkflow(recoWF).setRecoData(recoData);
            }
        }

        // Next we need to check whether any other RecoData left unvalidated left for the current RecoWorkflow
        for (RecoData recoDataItem : recoDataList) {
            if (!recoDataItem.isValidated()) {
                // Return index of the first unvalidated RecoData and exit
                return recoDataList.indexOf(recoDataItem);
            }
        }

        // Check whether any recoData pairs have page overlap. This may heppen when document got ambigious RecoFormResults. In such
        // case those RecoData will could be auto-validated, but will have page range overlap
        if (recoDataList.size() > 1) {
            for (int i = 0; i < recoDataList.size() - 1; i++) { // .size() - 1 - in order to optimize loop and also because we have (i + 1) below
                for (int y = (i + 1); y < recoDataList.size(); y++) { // y = i + 1 - in order to optimize loop
                    // Check whether any page range overlapping is present
                    if (Range.of(recoDataList.get(i).getPageIdxStart(), recoDataList.get(i).getPageIdxEnd())
                            .isOverlappedBy(Range.of(recoDataList.get(y).getPageIdxStart(), recoDataList.get(y).getPageIdxEnd()))) {
                        throw new RecoDataPagesOverlapException().setWorkflow(recoWF).setRecoData1(recoDataList.get(i)).setRecoData2(recoDataList.get(y));
                    }
                }
            }
        }

        return -1;
    }


    /**
     * Checks RecoFields in provided RecoData and for each non-empty RecoField, that has empty (or zero-ed) confidence
     * levels - sets them to 100.<br>
     * <br>
     * Implementation notes:<br>
     * <ul>
     * <li>Only non-empty fields are processed;</li>
     * <li>For text fields, only those without confidence levels are updated - field may have value matched, but with confidence level set to 0, i.e. from OCR;</li>
     * <li>For OMR and Image fields, both fields without confidence levels and with all-zero confidence levels are updated;</li>
     * </ul>
     *
     * @param recoData
     */
    private void markFilledFieldsValidated(RecoData recoData) {
        for (CustomField cf : recoData.getCfDef()) {
            Object value = recoData.getCfData().get(recoData.getCfDsFieldName(cf));
            if (value == null) {
                continue;
            }
            if (cf.isTypeReco() && cf.getRecoProp().getConfidenceText().isEmpty() && value instanceof String valueStr && isNotBlank(valueStr)) {
                cf.getRecoProp().setConfidenceText(Collections.nCopies(valueStr.length(), 100));
            } else if (cf.isTypeRecoImage() && isZeroOrEmptyConfidence(cf.getRecoProp().getConfidenceImage())) {
                cf.getRecoProp().setConfidenceImage(List.of(100));
                cf.getRecoProp().setConfidenceOmr(Collections.nCopies(((List) value).size(), 100));
            } else if (cf.isTypeRecoOmr() && isZeroOrEmptyConfidence(cf.getRecoProp().getConfidenceOmr())) {
                cf.getRecoProp().setConfidenceOmr(Collections.nCopies(((List) value).size(), 100));
            } else if (cf.isTypeRecoTable()) {
                // For table - we need to update ConfidenceLevels for each cell separately
                List<List<String>> tableData = ObjectValueUtils.getValueAsListOfStringList(value);
                List<List<List<Integer>>> tableConf = cf.getRecoProp().getConfidenceTable();
                for (int rowIdx = 0; rowIdx < tableData.size(); rowIdx++) {
                    List<String> tableRow = tableData.get(rowIdx);
                    List<List<Integer>> tableRowConf;
                    if (rowIdx > tableConf.size() - 1) {
                        tableConf.add(new ArrayList<>());
                    }
                    tableRowConf = tableConf.get(rowIdx);
                    for (int cellIdx = 0; cellIdx < tableRow.size(); cellIdx++) {
                        if (cellIdx > tableRowConf.size() - 1) {
                            tableRowConf.add(new ArrayList<>());
                        }
                        if (cf.getRecoProp().getConfidenceImage().isEmpty() && isNotBlank(tableRow.get(cellIdx))) {
                            tableRowConf.set(cellIdx, Collections.nCopies(tableRow.get(cellIdx).length(), 100));
                        }
                    }
                }
                cf.getRecoProp().setConfidenceTable(tableConf);
            }
        }
    }


    private boolean isZeroOrEmptyConfidence(List<Integer> conf) {
        return conf == null || conf.isEmpty() || List.of(0).containsAll(conf);
    }


    /**
     * Validates RecoData fields and returns list of fields, that failed validation<br>
     * For table-type fields, also includes row and column index of cells, that failed validation or
     * -1 for both, if failed check references field as a whole
     *
     * @param recoData
     * @return
     */
    public static List<Triple<Integer, Integer, Integer>> validateRecoDataFields(RecoData recoData) {
        List<Triple<Integer, Integer, Integer>> validationResult = new ArrayList<>();
        for (CustomField cf : recoData.getCfDef()) {
            List<Pair<Integer, Integer>> fieldValidationResult = validateRecoDataFieldValue(recoData, cf);
            if (fieldValidationResult.isEmpty()) {
                continue;
            }
            fieldValidationResult.stream()
                    .map(result -> Triple.of(recoData.getCfDef().indexOf(cf), result.getLeft(), result.getRight()))
                    .forEach(validationResult::add);
        }
        return validationResult;
    }


    /**
     * Validates CustomField result and returns list of failures with optional row and column index for tables
     * (set to -1 for other field types)
     *
     * @param recoData
     * @param cf
     * @return
     */
    private static List<Pair<Integer, Integer>> validateRecoDataFieldValue(RecoData recoData, CustomField cf) {
        List<Pair<Integer, Integer>> validationResult = new ArrayList<>();
        Object cfValue = recoData.getCfData().get(recoData.getCfDsFieldName(cf));
        if (cf.isTypeReco()) {
            if (cf.isRequired() && isBlank((String) cfValue)) {
                validationResult.add(Pair.of(-1, -1));
            }
        } else if (cf.isTypeRecoTable()) {
            List<CustomField> columns = cf.getFields();
            List<List<String>> tableData = ObjectValueUtils.getValueAsListOfStringList(cfValue);
            for (int rowIdx = 0; rowIdx < tableData.size(); rowIdx++) {
                for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
                    if (!columns.get(colIdx).isRequired()) {
                        continue;
                    }
                    if (isBlank(tableData.get(rowIdx).get(colIdx))) {
                        validationResult.add(Pair.of(rowIdx, colIdx));
                    }
                }
            }
        }

        return validationResult;
    }


    /**
     * Sends RecoWorkflow to re-work
     *
     * @param recoWF
     * @param wfTask
     * @param aclContext
     * @param userRequestMeta user request details like userAgent and userIP
     */
    public void actionToWorkAgain(final Workflow recoWF, final WorkflowTask wfTask, final AclContext aclContext, UserRequestMeta userRequestMeta) {
        _workflowDao.isValid(recoWF);

        wfTask.setNote("Document sent to work again");
        _workflowService.actionDone(recoWF, wfTask, aclContext, userRequestMeta, new BpmParams(BPM_SKIP_PROCESS_UPDATE, true));

        BpmParams bpmParams = new BpmParams();
        if (RECO_PROCESS_STEP_CONV_VALIDATOR == recoWF.getStep()) {
            // In case we do this action after Conversion - we need to send document toWorkAgain to the ConverterBot step directly
            bpmParams.setProperty(BPM_STEP, RECO_PROCESS_STEP_CONVERSIONBOT);
        }

        _workflowService.actionToWorkAgain(recoWF, aclContext, userRequestMeta, bpmParams, null);
    }


    /**
     * Cancels RecoWorkflow
     *
     * @param recoWF
     * @param wfTask
     * @param aclContext
     * @param userRequestMeta user request details like userAgent and userIP
     */
    public void actionCancel(final Workflow recoWF, final WorkflowTask wfTask, final AclContext aclContext, UserRequestMeta userRequestMeta) {
        _workflowDao.isValid(recoWF);

        wfTask.setNote("Document cancelled");
        _workflowService.actionDecline(recoWF, wfTask, aclContext, userRequestMeta, new BpmParams(BPM_SKIP_PROCESS_UPDATE, true));
        _workflowService.actionToArchiveState(recoWF, aclContext, userRequestMeta, new BpmParams(BPM_STATUS, PROCESS_STATUS_TERMINATED), null);
    }


    /**
     * Deletes RecoData for the given RecoWorkflow
     *
     * @param recoWF
     * @param recoData
     */
    public void actionDeleteRecoData(final Workflow recoWF, final RecoData recoData) {
        if (!Objects.equals(recoWF.getId(), recoData.getRefId())) {
            throw new IllegalArgumentException("Workflow(RecoWorkflow) and RecoData relation mismatch");
        }
        _recoDataDao.delete(null, recoData);
    }


    public void actionSaveRecoData(final Workflow recoWF, final RecoData recoData) {
        if (!Objects.equals(recoWF.getId(), recoData.getRefId())) {
            throw new IllegalArgumentException("Workflow(RecoWorkflow) and RecoData relation mismatch");
        }
        _recoDataDao.updateOrCreate(null, recoData);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Retrieves {@link RecoField} configuration (i.e.name, tags, confidence threshold) and
     * transfers it to provided {@link CustomField}
     *
     * @param recoField
     * @param recoWF
     * @param customField
     */
    public void retrieveAndSetCfDefFromRecoField(RecoField recoField, Workflow recoWF, CustomField customField) {
        for (String lang : _configContext.getLangsEnabled()) {
            customField.setName(lang, recoField.getName());
        }
        customField.setEnabled(true);

        customField.getRecoProp().setConfThreshold(recoField.getConfidenceThreshold());

        customField.setRequired(recoField.isRequired());

        customField.getRecoProp().setIgnored(recoField.getProp().isIgnored());

        if (recoField.isTypeTable()) {
            customField.setType(CustomField.TYPE_RECO_TABLE);
            recoField.getColumns()
                    .forEach(columnRecoField -> {
                        CustomField columnCustomField = new CustomField();
                        retrieveAndSetCfDefFromRecoField(columnRecoField, recoWF, columnCustomField);
                        customField.getFields().add(columnCustomField);
                    });
        } else if (recoField.isTypeOmr()) {
            customField.setType(CustomField.TYPE_RECO_OMR);
            recoField.getOpMarks()
                    .forEach(markRecoField -> {
                        CustomField markCustomField = new CustomField();
                        retrieveAndSetCfDefFromRecoField(markRecoField, recoWF, markCustomField);
                        customField.getFields().add(markCustomField);
                    });
        } else if (recoField.isTypeImage()) {
            customField.setType(CustomField.TYPE_RECO_IMAGE);
        } else {
            customField.setType(CustomField.TYPE_RECO);
        }
        customField.getTags().addAll(recoField.getTags());
        customField.getTags().add(CustomField.TAG_RECO_FIELD); // Tag this field as RecoField
    }


    /**
     * Extracts page index and confidence levels from RecoFieldResult and saves them to Custom Field
     *
     * @param recoFormResult
     * @param recoFieldResult
     * @param customField
     */
    public void retrieveAndSetCfDataFromRecoField(RecoFormResult recoFormResult, RecoFieldResult recoFieldResult, CustomField customField) {
        // Retrieve and set page index from RecoFormResult to CustomField, if it wasn't set yet
        // Check for presence of page index on custom field allows to save first page of multi-page table as page index
        if (!customField.containsProperty(CustomFieldRecoProperties.PROP_RECO_PAGE_IDX)) {
            int pageIndex = recoFormResult.getPages().stream()
                    .filter(page -> page.getFields().contains(recoFieldResult))
                    .map(page -> page.getPageIdx())
                    .findFirst()
                    .orElse(-1);
            customField.getRecoProp().setPageIdx(pageIndex);
        }

        // Retrieve and set confidence levels and value regions for values from RecoFormResult to CustomField
        if (recoFieldResult.isTypeText() || recoFieldResult.isTypeTextExtendable()) {
            List<Integer> confidences = recoFieldResult.getValueConf();
            if (customField.getRecoProp().getConfidenceText().isEmpty()) {
                // If there is no confidence levels saved yet - save retrieved from current recoFieldResult
                customField.getRecoProp().setConfidenceText(confidences);
            } else {
                // If custom field already has confidence levels set - this means, that this is multi-page
                // table, and it's values are merged during values retrieving. So we need to simply apply
                // extracted confidence levels from current field to existing ones
                customField.getRecoProp().getConfidenceText().add(100); // Manually added space between values on different pages
                customField.getRecoProp().getConfidenceText().addAll(confidences);
            }
        } else if (recoFieldResult.isTypeImage()) {
            customField.getRecoProp().setConfidenceImage(recoFieldResult.getValueConf());
        } else if (recoFieldResult.isTypeOmr()) {
            List<Integer> confidences = recoFieldResult.getOmrData().stream()
                    .map(mark -> mark.getValueConf().get(0))
                    .collect(Collectors.toList());
            customField.getRecoProp().setConfidenceOmr(confidences);
        } else if (recoFieldResult.isTypeTable()) {
            List<List<List<Integer>>> confidences = recoFieldResult.getTableData().stream()
                    .map(row -> row.stream().map(cell -> cell.getValueConf()).collect(Collectors.toList()))
                    .collect(Collectors.toList());

            if (customField.getRecoProp().getConfidenceTable().isEmpty()) {
                // If there is no confidence levels saved yet - save retrieved from current recoFieldResult
                customField.getRecoProp().setConfidenceTable(confidences);
            } else {
                // If custom field already has confidence levels set - this means, that this is multi-page
                // table, and it's values are merged during values retrieving. So we need to simply apply
                // extracted confidence levels from current field to existing ones
                customField.getRecoProp().getConfidenceTable().addAll(confidences);
            }
        }
    }


    /**
     * Initializes Custom Field as 'empty' field, regarding type of provided RecoField, and sets Confidence level of new value to 100
     *
     * @param recoField
     * @param recoFieldResult
     * @param customField
     * @return
     */
    public Object initAndSetEmptyCfDataFromRecoField(RecoField recoField, RecoFieldResult recoFieldResult, CustomField customField) {
        customField.getRecoProp().setPageIdx(0);
        if (recoField.isTypeText()) {
            List<Integer> confidences = new ArrayList<>();

            if (recoFieldResult == null) {
                // If no RecoFieldResult provided - initialize with single confidence level value
                confidences.add(100);
            } else {
                // If RecoFieldResult provided - initialize confidence level for each symbol
                for (int i = 0; i < recoFieldResult.getValue().length(); i++) {
                    confidences.add(100);
                }
            }

            customField.getRecoProp().setConfidenceText(confidences);

            return "";
        } else if (recoField.isTypeOmr()) {
            List<Integer> confidences = new ArrayList<>();
            recoField.getOpMarks().forEach(mark -> confidences.add(100));
            customField.getRecoProp().setConfidenceOmr(confidences);

            List<Boolean> value = new ArrayList<>();
            recoField.getOpMarks().forEach(mark -> value.add(false));
            return value;
        } else if (recoField.isTypeTable()) {
            List<List<List<Integer>>> confidences = new ArrayList<>();
            if (recoFieldResult == null) {
                // If no RecoFieldResult provided - initialize single row with confidence levels
                List<List<Integer>> rowConfidences = recoField.getColumns().stream()
                        .map(col -> {
                            List<Integer> cellConfidences = new ArrayList<>();
                            cellConfidences.add(100);
                            return cellConfidences;
                        })
                        .collect(Collectors.toList());
                confidences.add(rowConfidences);
            } else {
                // If RecoFieldResult provided - initialize confidence level for each cell
                recoFieldResult.getTableData().forEach(row -> {
                    List<List<Integer>> rowConfidences = recoField.getColumns().stream()
                            .map(col -> {
                                List<Integer> cellConfidences = new ArrayList<>();
                                cellConfidences.add(100);
                                return cellConfidences;
                            })
                            .collect(Collectors.toList());
                    confidences.add(rowConfidences);
                });
            }
            customField.getRecoProp().setConfidenceTable(confidences);

            List<List<String>> value = new ArrayList<>();
            List<String> row = new ArrayList<>();
            recoField.getColumns().forEach(col -> row.add(""));
            value.add(row);
            return value;
        }
        return null;
    }


    /**
     * Deletes all RecoData models assigned to the given Workflow (RecoWorkflow)
     *
     * @param dbClientSession
     * @param recoWF
     */
    public void deleteRecoData(ClientSession dbClientSession, Workflow recoWF) {
        _recoDataDao.deleteAllByRefId(dbClientSession, recoWF.getId());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * Loads all available reco results (also handles legacy RecoDocs by preparing RecoData for them)
     *
     * @param recoWF
     * @return
     */
    public List<RecoData> getRecoDataList(Workflow recoWF) {
        return _recoDataDao.findAllByRefId(recoWF.getId());
    }


    public RecoData find(String id) {
        return _recoDataDao.find(id);
    }


    public List<RecoData> findAllByFileIds(List<String> fileIds) {
        return fileIds.stream()
                .map(this::findByFileId)
                .toList();
    }


    public RecoData findByFileId(String fileId) {
        return _recoDataDao.findByFileId(fileId);
    }


    /**
     * Checks if RecoForm was changed for provided recoData
     *
     * @param recoData
     * @return
     */
    public boolean isRecoFormAltered(RecoData recoData) {
        if (recoData == null) {
            return false;
        } else {
            RecoFormResult recoFormResult = getRecoFormResult(recoData);
            if (recoFormResult == null) {
                return false;
            }
            return recoData.isRecoFormAltered(recoFormResult);
        }
    }


    /**
     * Finds RecoFIeld, related to provided CustomField
     *
     * @param recoForm
     * @param cf
     * @param locale
     * @return
     */
    public static RecoField getRecoFieldByCustomField(RecoForm recoForm, CustomField cf, String locale) {
        return recoForm.getFields().stream()
                .filter(recoField -> Objects.equals(cf.getName().get(locale), recoField.getName()))
                .findFirst()
                .orElse(null);
    }


    public static RecoFieldResult getRecoFieldResultByCustomField(RecoFormResult recoFormResult, CustomField cf, String locale) {
        RecoPageResult pageResult = recoFormResult.getPages().stream().filter(page -> page.getPageIdx() == cf.getRecoProp().getPageIdx()).findFirst().orElse(null);
        if (pageResult == null) {
            return null;
        }
        return pageResult.getFields().stream()
                .filter(recoFieldResult -> Objects.equals(cf.getName().get(locale), recoFieldResult.getName()))
                .findFirst()
                .orElse(null);
    }


    public static RecoFieldResult getRecoFieldImageCellResultByCustomField(RecoData recoData, RecoFormResult recoFormResult, CustomField cf, String locale, int rowIdx, int colIdx) {
        String expectedImageId = ObjectValueUtils.getValueAsListOfStringList(recoData.getCfData().get(recoData.getCfDsFieldName(cf))).get(rowIdx).get(colIdx);

        return recoFormResult.getPages().stream()
                .filter(page -> page.getFields().stream().anyMatch(rfr -> Objects.equals(cf.getName().get(locale), rfr.getName()))) // Find all tables with same name
                .map(RecoPageResult::getFields)
                .flatMap(List::stream)
                .map(RecoFieldResult::getTableData) // Merge all table data
                .flatMap(List::stream)
                .map(row -> row.get(colIdx)) // Get only RecoFieldResult from requested column
                .filter(rfr -> Objects.equals(rfr.getValue(), expectedImageId))
                .findFirst().orElse(null);
    }


    public static int getRecoFieldImageCellPageIdByCustomField(RecoData recoData, RecoFormResult recoFormResult, CustomField cf, String locale, int rowIdx, int colIdx) {
        RecoPageResult pageResult = getRecoPageResultByTableRowImageCell(recoData, recoFormResult, cf, locale, rowIdx, colIdx);

        if (pageResult == null) {
            return -1;
        }

        return pageResult.getPageIdx();
    }


    public static int getRecoFieldTableIdxOnPageByImageCustomField(RecoData recoData, RecoFormResult recoFormResult, CustomField cf, String locale, int rowIdx, int colIdx) {
        RecoPageResult pageResult = getRecoPageResultByTableRowImageCell(recoData, recoFormResult, cf, locale, rowIdx, colIdx);

        if (pageResult == null) {
            return -1;
        }

        RecoFieldResult fieldResult = pageResult.getFields().stream()
                .filter(rfr -> Objects.equals(cf.getName().get(locale), rfr.getName()))
                .findFirst().orElse(null);

        if (fieldResult == null) {
            return -1;
        }

        return pageResult.getFields().indexOf(fieldResult);
    }


    private static RecoPageResult getRecoPageResultByTableRowImageCell(RecoData recoData, RecoFormResult recoFormResult, CustomField cf, String locale, int rowIdx, int colIdx) {
        RecoFieldResult recoCellResult = getRecoFieldImageCellResultByCustomField(recoData, recoFormResult, cf, locale, rowIdx, colIdx);

        if (recoCellResult == null) {
            return null;
        }

        return recoFormResult.getPages().stream()
                .filter(page -> page.getFields().stream().filter(RecoFieldResult::isTypeTable).map(RecoFieldResult::getTableData).flatMap(List::stream).flatMap(List::stream).anyMatch(rfr -> Objects.equals(rfr, recoCellResult)))
                .findFirst().orElse(null);
    }


    public static int getRecoFieldImageCellIdxOnPageByCustomField(RecoData recoData, RecoFormResult recoFormResult, CustomField cf, String locale, int rowIdx, int colIdx) {
        String expectedImageId = ObjectValueUtils.getValueAsListOfStringList(recoData.getCfData().get(recoData.getCfDsFieldName(cf))).get(rowIdx).get(colIdx);

        RecoPageResult pageResult = getRecoPageResultByTableRowImageCell(recoData, recoFormResult, cf, locale, rowIdx, colIdx);

        if (pageResult == null) {
            return -1;
        }

        RecoFieldResult fieldResult = pageResult.getFields().stream()
                .filter(rfr -> Objects.equals(cf.getName().get(locale), rfr.getName()))
                .findFirst().orElse(null);

        if (fieldResult == null) {
            return -1;
        }

        for (int i = 0; i < fieldResult.getTableData().size(); i++) {
            if (Objects.equals(fieldResult.getTableData().get(i).get(colIdx).getValue(), expectedImageId)) {
                return i;
            }
        }

        return -1;
    }


    /**
     * Returns RecoFormResult for the given RecoData. Handles legacy RecoDocs
     *
     * @param recoData null-safe
     * @return
     */
    public RecoFormResult getRecoFormResult(RecoData recoData) {
        return _recoDataDao.getRecoFormResult(recoData);
    }


    /**
     * Returns files suitable for preview on the UI (tagged with: SOURCE, PREPROCESSED, PREPROCESSED_FINAL).
     * Handles legacy documents as well
     *
     * @param recoWF
     * @param recoData null-safe
     * @return
     */
    public List<DbFile> getPreviewFiles(Workflow recoWF, RecoData recoData) {
        List<DbFileRef> filesByTags = recoWF.getFilesByTags(List.of(Workflow.FILE_TAG.IDP_SOURCE.name(), Workflow.FILE_TAG.IDP_PREPROCESSED.name()));
        List<DbFile> result = _workflowDao.getFilesDao().getFiles(null, filesByTags);
        if (recoData != null) {
            DbFileRef preprocessedFinalDbFileRef = recoData.getFileByTag(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name());
            if (preprocessedFinalDbFileRef != null) {
                DbFile preprocessedFinalDbFile = _recoDataDao.getFilesDao().getFile(null, preprocessedFinalDbFileRef);
                if (preprocessedFinalDbFile != null) {
                    result = new ArrayList<>(result); // We need to wrap ImmutableCollection before adding element to it
                    result.add(preprocessedFinalDbFile);
                }
            }
        }

        return result;
    }


    /**
     * Creates PDF document with the text-layer from the recognized file attachment (uses PREPROCESSED file)
     *
     * @param recoWF RecoWorkflow to be converted
     * @return generated document file or null, if no preview file present in RecoWorkflow (i.e. preprocessing was not yet finished)
     */
    public File generateTextPdfFile(Workflow recoWF) {
        File outputFile = null;

        // Retrieve required file from DB
        DbFile previewFile = getPreviewFiles(recoWF, null).stream()
                .filter(fileItem -> fileItem.getDbFileRef().getTags().contains(Workflow.FILE_TAG.IDP_PREPROCESSED.name()))
                .findFirst().orElse(null);

        if (previewFile == null) {
            return null;
        }

        try (DbFileDownloadStream downloadStream = _workflowDao.getFilesDao().getFileDownloadStream(previewFile.getDbFileRef()); PDDocument pdfDoc = PdfUtils.loadPDF(downloadStream)) {
            PdfUtils.removePageProperties(pdfDoc);
            outputFile = PdfUtils.saveTempPDF(pdfDoc, FilenameUtils.getBaseName(previewFile.getFileName()).concat("."));
            _filesCleanupService.addQueueItem(outputFile.toPath(), 4); // Mark file for delayed clean-up
        } catch (Exception ex) {
            throw new ElDocRuntimeException(format("Error generating pdf-document file for RecoWorkflow[{0}]", recoWF.getId()), ex);
        }

        return outputFile;
    }


    public DbFile getRecoFieldImage(RecoData recoData, CustomField cf) {
        String fieldAttachmentId = (String) recoData.getCfData().get(recoData.getCfDsFieldName(cf));
        return getRecoImage(recoData, fieldAttachmentId);
    }


    public DbFile getRecoFieldCellImage(RecoData recoData, CustomField cf, int rowIdx, int cellIdx) {
        List<List<String>> tableData = ObjectValueUtils.getValueAsListOfStringList(recoData.getCfData().get(recoData.getCfDsFieldName(cf)));
        String cellAttachmentId = tableData.get(rowIdx).get(cellIdx);
        return getRecoImage(recoData, cellAttachmentId);
    }


    private DbFile getRecoImage(RecoData recoData, String attachmentId) {
        if (isBlank(attachmentId)) {
            return null;
        }
        DbFileRef attachmentRef = recoData.getFiles().stream().filter(file -> attachmentId.equals(file.getId())).findFirst().orElse(null);
        if (attachmentRef == null) {
            return null;
        }
        return _recoDataDao.getFilesDao().getFile(null, attachmentRef);
    }


    public void replaceRecoFieldImage(RecoData recoData, CustomField cf, int rowIdx, int colIdx, BufferedImage bi) throws IOException {
        DbFileRef fileRef = null;
        try (ClientSession dbClientSession = _recoDataDao.startSession()) {
            try {
                dbClientSession.startTransaction();
                DbFile imageFile;
                if (cf.isTypeRecoTable()) {
                    imageFile = getRecoFieldCellImage(recoData, cf, rowIdx, colIdx);
                } else {
                    imageFile = getRecoFieldImage(recoData, cf);
                }
                removeRecoFieldImage(recoData, cf, rowIdx, colIdx, dbClientSession);
                fileRef = uploadRecoFieldImage(recoData, cf, rowIdx, colIdx, imageFile.getFileName(), bi, imageFile.getContentType(), dbClientSession);
                dbClientSession.commitTransaction();
            } catch (Throwable e) {
                dbClientSession.abortTransaction();
                if (fileRef != null) {
                    // Remove uploaded file, if present - should be done manually, as GridFS does not support multi-document transactions
                    _recoDataDao.getFilesDao().deleteFile(null, fileRef);
                }
                throw e; // Re-throw the original exception
            }
        }
    }


    public void removeRecoFieldImage(RecoData recoData, CustomField cf, int rowIdx, int cellIdx) {
        try (ClientSession dbClientSession = _recoDataDao.startSession()) {
            try {
                dbClientSession.startTransaction();
                removeRecoFieldImage(recoData, cf, rowIdx, cellIdx, dbClientSession);
                dbClientSession.commitTransaction();
            } catch (Throwable e) {
                dbClientSession.abortTransaction();
                throw e; // Re-throw the original exception
            }
        }
    }


    public void uploadRecoFieldImage(RecoData recoData, CustomField cf, int rowIdx, int colIdx, String locale, BufferedImage bi) throws IOException {
        DbFileRef fileRef = null;
        try (ClientSession dbClientSession = _recoDataDao.startSession()) {
            try {
                dbClientSession.startTransaction();
                fileRef = uploadRecoFieldImage(recoData, cf, rowIdx, colIdx, cf.getName().get(locale) + "." + AbstractRecoFieldProcessor.DEFAULT_IMAGE_FIELD_FORMAT, bi, AbstractRecoFieldProcessor.DEFAULT_IMAGE_FIELD_CONTENT_TYPE, dbClientSession);
                dbClientSession.commitTransaction();
            } catch (Throwable e) {
                dbClientSession.abortTransaction();
                if (fileRef != null) {
                    // Remove uploaded file, if present - should be done manually, as GridFS does not support multi-document transactions
                    _recoDataDao.getFilesDao().deleteFile(null, fileRef);
                }
                throw e; // Re-throw the original exception
            }
        }
    }


    private DbFileRef uploadRecoFieldImage(RecoData recoData, CustomField cf, int rowIdx, int cellIdx, String fileName, BufferedImage bi, String contentType, ClientSession dbClientSession) throws IOException {
        try (DbFileUploadStream us = _recoDataDao.getFilesDao().getFileUploadStream(fileName, contentType, List.of())) {
            ImageIO.write(bi, fileName.substring(fileName.lastIndexOf('.') + 1), us);
            recoData.getFiles().add(us.getDbFileRef());
            if (cf.isTypeRecoTable()) {
                ((List<List<String>>) recoData.getCfData().get(recoData.getCfDsFieldName(cf))).get(rowIdx).set(cellIdx, us.getDbFileRef().getId());
            } else {
                recoData.getCfData().put(recoData.getCfDsFieldName(cf), us.getDbFileRef().getId());
            }
            _recoDataDao.updateOrCreate(dbClientSession, recoData);
            return us.getDbFileRef();
        }
    }


    private void removeRecoFieldImage(RecoData recoData, CustomField cf, int rowIdx, int cellIdx, ClientSession dbClientSession) {
        String attachmentId;
        if (cf.isTypeRecoTable()) {
            List<List<String>> tableData = ObjectValueUtils.getValueAsListOfStringList(recoData.getCfData().get(recoData.getCfDsFieldName(cf)));
            attachmentId = tableData.get(rowIdx).get(cellIdx);
        } else {
            attachmentId = (String) recoData.getCfData().get(recoData.getCfDsFieldName(cf));
        }
        if (isBlank(attachmentId)) {
            return;
        }
        DbFileRef attachmentRef = recoData.getFiles().stream().filter(file -> attachmentId.equals(file.getId())).findFirst().orElse(null);
        if (attachmentRef == null) {
            return;
        }

        _recoDataDao.getFilesDao().deleteFile(dbClientSession, attachmentRef);
        recoData.getFiles().remove(attachmentRef);
        if (cf.isTypeRecoTable()) {
            ((List<List<String>>) recoData.getCfData().get(recoData.getCfDsFieldName(cf))).get(rowIdx).set(cellIdx, "");
        } else {
            recoData.getCfData().put(recoData.getCfDsFieldName(cf), "");
        }
        _recoDataDao.updateOrCreate(dbClientSession, recoData);
    }


    //
    // RecoDataDao Proxy methods --------------------------------------------------------------
    //
    public DbFile getFile(DbFileRef fileRef) {
        return _recoDataDao.getFilesDao().getFile(null, fileRef);
    }


    public DbFileDownloadStream getFileDownloadStream(DbFileRef fileRef) {
        return _recoDataDao.getFilesDao().getFileDownloadStream(fileRef);
    }

}
