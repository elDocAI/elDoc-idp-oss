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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.constants.Pages;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.*;
import ai.eldoc.exceptions.ScriptingException;
import ai.eldoc.model.*;
import ai.eldoc.service.*;
import ai.eldoc.util.*;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.text.Collator;
import java.text.MessageFormat;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static ai.eldoc.bpm.BpmConstants.*;
import static ai.eldoc.workflow.WorkflowUtils.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * FormDefinition View Controller
 */
@Named
@ViewScoped
public class FormDefController extends AbstractUIController implements Serializable {

    @Serial
    private static final long serialVersionUID = 2283814293654302292L;
    private static final Logger LOG = LoggerFactory.getLogger(FormDefController.class);

    @Inject
    private FormDefinitionService _formDefService;

    @Inject
    private FormDefinitionDao _formDefDao;

    @Inject
    private WorkflowService _workflowService;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private NumSequenceDao _numSequenceDao;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private CodeSnippetService _codeSnippetService;

    private static final Integer STATE_ID_REPO_ARCH = 95100; // 95100 is workaround for two values: Repo and Arch
    private FormDefinition _currentFD;
    private Workflow _previewDoc;
    private boolean _showAllForms = false;
    private boolean _showPreview = false;
    private String _viewParamId; // url param: id

    private String _retentionPeriod = "5";
    private String _retentionPeriodType = "Y";

    public static final String FORM_ID_REGEX = "^(f|fd|frd|fsd|fi)\\d{1,10}$";


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Opens and Inits FormDefinition on page open
     *
     * @return navigation case
     */
    public String openFormDef() {
        try {
            if (_viewParamId != null) {
                if (_viewParamId.matches(FORM_ID_REGEX)) {
                    _currentFD = _formDefDao.findByWebId(_viewParamId, true, null);
                } else if (BasicModel.ID_WEB_PATTERN.matcher(_viewParamId).matches()) {
                    // in case id represents ObjectId string value
                    _currentFD = _formDefDao.find(_viewParamId);
                }
            }

            if (_currentFD == null) {
                JsfUtils.addWarnMessage(LabelUtils.getLabel("msg.doc.notFound", _localeContext.getLocale()));
                // _currentDF = new Workflow(new org.bson.Document()); // we need it here to avoid NullPointerExceptions
            } else {
                // we need to initialize dummy preview document
                initPreviewDoc();
                if (_currentFD.getRetentionPeriod() != null) {
                    String periodString = _currentFD.getRetentionPeriod().toString();
                    _retentionPeriod = periodString.substring(1, periodString.length() - 1);
                    _retentionPeriodType = periodString.substring(periodString.length() - 1);
                }
                setModeRead();
                return null;
            }
        } catch (Exception e) {
            LogHelper.setEvent("id00293", e, null, new LogExt(_currentFD));
            JsfUtils.addErrorMessage(e.toString());
        }
        JsfUtils.keepMessagesOnRedirect();
        return Pages.PAGE_MAIN;
    }


    private void initPreviewDoc() {
        _previewDoc = _workflowService.createWithFormDef(_currentFD, _userContext.getAclContext());
        _previewDoc.setTopic("Preview of " + _currentFD.getNameByLang(_localeContext.getLangSystemCode()));
    }


    /**
     * Creates IDP FormDefinition
     */
    public void doInitIdpForm() {
        try {
            FormDefinition formDef = _formDefService.createStaticFormDefinitionForRecoDoc(_userContext.getId());
            LogHelper.setEvent("id00141", null, null, new LogExt(formDef));
        } catch (Exception me) {
            LogHelper.setEvent("id00155", me);
        }
    }


    public String doSave() {
        try {
            if (_currentFD.getProcessIds().isEmpty()) {
                JsfUtils.addErrorMessage(_localeContext.getLabel("pAdminForm.msg.noProcessesSelectedError"));
                return null;
            }

            if (isNotBlank(_currentFD.getPostCreateHook())) {
                _codeSnippetService.validateSnippet(_currentFD.getPostCreateHookDecoded());
            }

            if (!_currentFD.isActive()) {
                // Activate form in case it was not activated
                _currentFD.setStatus(FormDefinition.STATUS_ACTIVE);
                JsfUtils.addInfoMessage(MessageFormat.format(_localeContext.getLabel("pAdminForm.msg.statusChangeTo"), _localeContext.getLabel("formDef.status.active")));
            }

            // Update Retention configuration
            if (_currentFD.getRetentionAction() != FormDefinition.RETENTION_ACTION_KEEP) {
                if (isBlank(_retentionPeriod)) {
                    _retentionPeriod = "1";
                }
                Period retentionPeriod = Period.parse("P" + _retentionPeriod + _retentionPeriodType);
                _currentFD.setRetentionPeriod(retentionPeriod);
            }

            // numSequence enabling | disabling for the form
            NumSequence numSequence = _numSequenceDao.findByName(_currentFD.getFormId());
            boolean isCounterExist = (numSequence != null);
            if (_currentFD.getSfDef().getNum().isAutoReg() && !isCounterExist) {
                numSequence = new NumSequence();
                numSequence.setName(_currentFD.getFormId());
                if (_numSequenceDao.updateOrCreate(null, numSequence)) {
                    _currentFD.getSfDef().getNum().setEnabled(true);
                    _currentFD.getSfDef().getNum().setRequired(true);
                    LogHelper.setEvent("id00217", null, null, new LogExt(_currentFD));
                } else {
                    _currentFD.getSfDef().getNum().setAutoReg(false);
                    LogHelper.setEvent("id00218", null, null, new LogExt(_currentFD));
                }
            } else if (!_currentFD.getSfDef().getNum().isAutoReg() && isCounterExist) {
                _numSequenceDao.delete(null, numSequence);
                LogHelper.setEvent("id00219", null, null, new LogExt(_currentFD));
            }

            if (_formDefDao.updateOrCreate(null, _currentFD)) {
                LogHelper.setEvent("id00163", null, null, new LogExt(_currentFD));
                setModeRead();
            } else {
                LogHelper.setEvent("id00164", null, null, new LogExt(_currentFD));
                return null;
            }
        } catch (ScriptingException ex) {
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"), ex.getMessage());
            return null;
        } catch (Exception e) {
            LogHelper.setEvent("id00165", e, null, new LogExt(_currentFD));
        }
        JsfUtils.keepMessagesOnRedirect();
        return JsfUtils.getNavigationTarget(Pages.PAGE_ADMIN_FORM, "id", _currentFD.getFormId());
    }


    public String doCancel() {
        return JsfUtils.getNavigationTarget(Pages.PAGE_ADMIN_FORM, "id", _currentFD.getFormId());
    }


    /**
     * Gets Selection labels for Search ComboBox in the document search area (ccSearchArea)
     *
     * @return
     */
    public List<SelectItem> getSearchDocTitleLabelsSI() {
        List<SelectItem> result = new ArrayList<>();
        List<String> notFoundFormDefsIds = new ArrayList<>();
        List<FormDefinition> allFormDefs = _formDefDao.findAllByStatus(List.of(FormDefinition.STATUS_ACTIVE), true, null);

        // also we need to add formDefs names that could be in use in the past but now blocked or suspended
        Set<String> formDefIdsFromDB = _workflowDao.getDistinctValuesByField(Workflow._FORM, String.class);
        // cleanup by removing duplicates
        allFormDefs.forEach(formDef -> formDefIdsFromDB.removeIf(formDefWebId -> formDef.getFormId().equals(formDefWebId)));
        formDefIdsFromDB.forEach(formName -> {
            FormDefinition formDef = _formDefDao.findByWebId(formName, true, null);
            if (formDef != null) {
                allFormDefs.add(formDef);
            } else {
                // just in case we somehow get one of the formDefs lost - add its id to the not found list
                notFoundFormDefsIds.add(formName);
            }
        });

        Collator collator = Collator.getInstance(_localeContext.getLocale());
        collator.setStrength(Collator.PRIMARY); // ignores case and accents in many locales
        allFormDefs.sort(Comparator.comparing(formDef -> formDef.getNameByLang(_localeContext.getLangSystemCode()), Comparator.nullsLast(collator)));

        allFormDefs.forEach(formDef -> result.add(new SelectItem(formDef.getFormId(), formDef.getNameByLang(_localeContext.getLangSystemCode()))));

        // we never should get here, but just in case we lost some FormDef documents...
        notFoundFormDefsIds.forEach(formName -> result.add(new SelectItem(formName, formName)));
        return result;
    }


    public List<SelectItem> getSysFromActiveSI() {
        List<SelectItem> result = new ArrayList<>();
        result.add(new SelectItem(null, ""));
        List<FormDefinition> allFormDefs = _formDefDao.findAllByStatus(List.of(FormDefinition.STATUS_ACTIVE), true, null);

        Collator collator = Collator.getInstance(_localeContext.getLocale());
        collator.setStrength(Collator.PRIMARY); // ignores case and accents in many locales
        allFormDefs.sort(Comparator.comparing(formDef -> formDef.getNameByLang(_localeContext.getLangSystemCode()), Comparator.nullsLast(collator)));

        result.addAll(allFormDefs.stream()
                .map(formDef -> new SelectItem(formDef.getFormId(), formDef.getNameByLang(_localeContext.getLangSystemCode())))
                .toList());
        return result;
    }


    /**
     * Method to be used for retrieving a list of the standard Workflow statuses and their labels
     *
     * @return list of SelectItems with the standard Workflow statuses and their labels
     */
    public List<SelectItem> getWorkflowStatusSI() {
        List<SelectItem> result = new ArrayList<>();
        PROCESS_STATUS_LIST.forEach(statusId -> result.add(new SelectItem(statusId, _localeContext.getLabel(getProcessStatusLabelId(statusId)))));
        return result;
    }


    /**
     * Returns Workflow State SelectItems List
     *
     * @param recoDocStates If {@code true}, the result will contain RecoWorkflow-specific states
     * @return List of SelectItems with the standard Workflow states and their labels
     */
    public List<SelectItem> getWorkflowStateSI(boolean recoDocStates) {
        List<SelectItem> result = new ArrayList<>();
        result.add(new SelectItem(STATE_ID_DRAFT, _localeContext.getLabel(getProcessNameLabelId(STATE_ID_DRAFT))));

        if (recoDocStates) {
            result.add(new SelectItem(STATE_ID_EXEC_RECOBOT, _localeContext.getLabel(getProcessNameLabelIdReco(STATE_ID_EXEC, RECO_PROCESS_STEP_RECOBOT))));
            result.add(new SelectItem(STATE_ID_EXEC_RECO_VALIDATION, _localeContext.getLabel(getProcessNameLabelIdReco(STATE_ID_EXEC, RECO_PROCESS_STEP_RECO_VALIDATOR))));
            result.add(new SelectItem(STATE_ID_EXEC_CONVERSIONBOT, _localeContext.getLabel(getProcessNameLabelIdReco(STATE_ID_EXEC, RECO_PROCESS_STEP_CONVERSIONBOT))));
            result.add(new SelectItem(STATE_ID_EXEC_CONV_VALIDATION, _localeContext.getLabel(getProcessNameLabelIdReco(STATE_ID_EXEC, RECO_PROCESS_STEP_CONV_VALIDATOR))));
        } else {
            result.add(new SelectItem(STATE_ID_APPROVE, _localeContext.getLabel(getProcessNameLabelId(STATE_ID_APPROVE))));
            result.add(new SelectItem(STATE_ID_SIGN, _localeContext.getLabel(getProcessNameLabelId(STATE_ID_SIGN))));
            result.add(new SelectItem(STATE_ID_EXEC, _localeContext.getLabel(getProcessNameLabelId(STATE_ID_EXEC))));
        }

        result.add(new SelectItem(STATE_ID_RPSTR, _localeContext.getLabel(getProcessNameLabelId(STATE_ID_RPSTR))));
        result.add(new SelectItem(STATE_ID_ARCH, _localeContext.getLabel(getProcessNameLabelId(STATE_ID_ARCH))));
        return result;
    }


    /**
     * @return docEdit states SI for ComboBox
     */
    public List<SelectItem> getDocEditStagesSI() {
        List<SelectItem> result = new ArrayList<>();
        String labelDraft = _localeContext.getLabel(getProcessNameLabelId(STATE_ID_DRAFT));
        String labelRepo = _localeContext.getLabel(getProcessNameLabelId(STATE_ID_RPSTR));
        String labelArch = _localeContext.getLabel(getProcessNameLabelId(STATE_ID_ARCH));
        String labelRepoArch = labelRepo + " & " + labelArch;
        result.add(new SelectItem(STATE_ID_DRAFT, labelDraft));
        result.add(new SelectItem(STATE_ID_RPSTR, labelRepo));
        result.add(new SelectItem(STATE_ID_ARCH, labelArch));
        result.add(new SelectItem(STATE_ID_REPO_ARCH, labelRepoArch));
        return result;
    }


    public void btnTogglePreview() {
        _showPreview = !_showPreview;
        if (_showPreview) {
            initPreviewDoc();
        }
    }


    public void closePreview() {
        _showPreview = false;
    }


    public List<SelectItem> getCustomFieldTypesSI() {
        List<SelectItem> result = new ArrayList<>();
        result.add(new SelectItem(CustomField.TYPE_STRING, _localeContext.getLabel("ccFormBuilder_field.string")));
        result.add(new SelectItem(CustomField.TYPE_NUMBER_INTEGER, _localeContext.getLabel("ccFormBuilder_field.numberInteger")));
        result.add(new SelectItem(CustomField.TYPE_NUMBER_FRACTIONAL, _localeContext.getLabel("ccFormBuilder_field.numberFractional")));
        result.add(new SelectItem(CustomField.TYPE_DATE, _localeContext.getLabel("ccFormBuilder_field.date")));
        result.add(new SelectItem(CustomField.TYPE_DATETIME, _localeContext.getLabel("ccFormBuilder_field.dateTime")));
        result.add(new SelectItem(CustomField.TYPE_CHECKBOX, _localeContext.getLabel("ccFormBuilder_field.checkbox")));
        result.add(new SelectItem(CustomField.TYPE_DOCREF, _localeContext.getLabel("ccFormBuilder_field.docRef")));
        result.add(new SelectItem(CustomField.TYPE_SIGN, _localeContext.getLabel("ccFormBuilder_field.sign")));
        result.add(new SelectItem(CustomField.TYPE_TABLE, _localeContext.getLabel("ccFormBuilder_field.table")));
        return result;
    }


    public void handleFileUpload(FileUploadEvent event) {
        try {
            UploadedFile uploadedFile = event.getFile();
            DbFileRef tempDbRef = _formDefDao.getFilesDao().uploadFromStream(uploadedFile.getFileName(), uploadedFile.getInputStream(), uploadedFile.getContentType(), null);
            _currentFD.getTemplates().add(new TemplateRef(tempDbRef));
            _formDefDao.updateOrCreate(null, _currentFD);
        } catch (IOException ex) {
            LOG.error("FormDefinition[{}] File upload error", _currentFD.getFormId(), ex);
            throw new ElDocRuntimeException("Error uploading file");
        }
    }


    /**
     * Deletes file template from the document and updates the document in the DB
     *
     * @param templateIndex Index of the template to delete
     */
    public void deleteTemplate(int templateIndex) {
        // 1: Delete reference to attachment from the document
        TemplateRef removedTemplate = _currentFD.getTemplates().remove(templateIndex);
        // 2: Delete attachment from DB
        _formDefDao.getFilesDao().deleteFile(null, removedTemplate.getDbFileRef());
        _formDefDao.updateOrCreate(null, _currentFD);
    }


    public void toggleShowAllForms() {
        _showAllForms = !_showAllForms;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    public FormDefinition getCurrentFD() {
        return _currentFD;
    }


    public String getFormTitleUI() {
        return getFormTitleUI(_currentFD);
    }


    public String getFormTitleUI(FormDefinition formDef) {
        String formTitle = formDef.getNameByLang(_localeContext.getLangSystemCode());

        if (isBlank(formTitle)) {
            // There is no form name in current locale - try to find name in other locales
            // If there is no form name in any supported locales - fall back to FormDefinition WebId
            formTitle = I18n.LANG_SYSTEM_CODES_ALL.stream()
                    .map(formDef::getNameByLang)
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .orElse(formDef.getFormId());
        }

        return formTitle;
    }


    public Integer getAccessEditByAuthor() {
        Set<Integer> editAuthorList = _currentFD.getAccessEditByAuthorOnState();
        if (editAuthorList.size() == 1) {
            return editAuthorList.iterator().next();
        } else if (editAuthorList.size() == 2) {
            if (editAuthorList.contains(STATE_ID_RPSTR) && editAuthorList.contains(STATE_ID_ARCH)) {
                return STATE_ID_REPO_ARCH;
            }
        }
        return STATE_ID_DRAFT;
    }


    public void setAccessEditByAuthor(Integer docEditAuthor) {
        Set<Integer> result = new HashSet<>();
        if (STATE_ID_REPO_ARCH.equals(docEditAuthor)) {
            result.add(STATE_ID_RPSTR);
            result.add(STATE_ID_ARCH);
        } else {
            result.add(docEditAuthor);
        }
        _currentFD.setAccessEditByAuthorOnState(result);
    }


    public Integer getAccessEditByAdmin() {
        Set<Integer> editAdminList = _currentFD.getAccessEditByAdminOnState();
        if (editAdminList.size() == 1) {
            return editAdminList.iterator().next();
        } else if (editAdminList.size() == 2) {
            if (editAdminList.contains(STATE_ID_RPSTR) && editAdminList.contains(STATE_ID_ARCH)) {
                return STATE_ID_REPO_ARCH;
            }
        }
        return STATE_ID_DRAFT;
    }


    public void setAccessEditByAdmin(Integer docEditAdmin) {
        Set<Integer> result = new HashSet<>();
        if (STATE_ID_REPO_ARCH.equals(docEditAdmin)) {
            result.add(STATE_ID_RPSTR);
            result.add(STATE_ID_ARCH);
        } else {
            result.add(docEditAdmin);
        }
        _currentFD.setAccessEditByAdminOnState(result);
    }


    public Workflow getPreviewDoc() {
        return _previewDoc;
    }


    /**
     * Return user-friendly formDefinition Status Name
     *
     * @param status FormDefinition status
     * @return User-friendly formDefinition Status Name
     */
    public String getStatusLabel(Integer status) {
        if (status == null) {
            return "n/a";
        }
        return switch (status) {
            case FormDefinition.STATUS_DRAFT -> _localeContext.getLabel("formDef.status.draft");
            case FormDefinition.STATUS_ACTIVE -> _localeContext.getLabel("formDef.status.active");
            case FormDefinition.STATUS_SUSPENDED -> _localeContext.getLabel("formDef.status.suspended");
            default -> "n/a";
        };
    }


    public List<FormDefinition> getAllActive() {
        return _formDefDao.findAllByStatus(Arrays.asList(FormDefinition.STATUS_DRAFT, FormDefinition.STATUS_ACTIVE), true, null);
    }


    public boolean isShowPreview() {
        return _showPreview;
    }


    public void setShowPreview(boolean showPreview) {
        _showPreview = showPreview;
    }


    public boolean isRecoWFForm() {
        return _currentFD.isRecoWFForm();
    }


    public String getViewParamId() {
        return _viewParamId;
    }


    public void setViewParamId(String viewParamId) {
        _viewParamId = viewParamId;
    }


    public boolean isAccessCreateEditable() {
        return true;
    }


    public boolean isAutoRegEditable() {
        return true;
    }


    public boolean isNameEditable() {
        return false;
    }


    public List<SelectItem> getRetentionActionsSI() {
        List<SelectItem> retentionActions = new ArrayList<>();

        retentionActions.add(new SelectItem(FormDefinition.RETENTION_ACTION_KEEP, _localeContext.getLabel("ccFormCommon.retentionAction.keep")));
        retentionActions.add(new SelectItem(FormDefinition.RETENTION_ACTION_BLOCK, _localeContext.getLabel("ccFormCommon.retentionAction.block")));
        retentionActions.add(new SelectItem(FormDefinition.RETENTION_ACTION_DELETE, _localeContext.getLabel("ccFormCommon.retentionAction.delete")));

        return retentionActions;
    }


    public List<SelectItem> getRetentionPeriodTypeSI() {
        List<SelectItem> retentionPeriodTypes = new ArrayList<>();

        retentionPeriodTypes.add(new SelectItem("D", _localeContext.getLabel("ccFormCommon.retentionPeriod.day")));
        retentionPeriodTypes.add(new SelectItem("M", _localeContext.getLabel("ccFormCommon.retentionPeriod.month")));
        retentionPeriodTypes.add(new SelectItem("Y", _localeContext.getLabel("ccFormCommon.retentionPeriod.year")));

        return retentionPeriodTypes;
    }


    public List<SelectItem> getRetentionPeriodSI() {
        return IntStream.rangeClosed(1, 100)
                .mapToObj(v -> new SelectItem(v, String.valueOf(v)))
                .collect(Collectors.toList());
    }


    public String getRetentionPeriod() {
        return _retentionPeriod;
    }


    public void setRetentionPeriod(String retentionPeriod) {
        _retentionPeriod = retentionPeriod;
    }


    public String getRetentionPeriodType() {
        return _retentionPeriodType;
    }


    public void setRetentionPeriodType(String retentionPeriodType) {
        _retentionPeriodType = retentionPeriodType;
    }
}
