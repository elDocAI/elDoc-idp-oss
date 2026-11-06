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

import ai.eldoc.ai.config.LLMModelConfig;
import ai.eldoc.ai.config.LLMProviderConfig;
import ai.eldoc.ai.service.AiService;
import ai.eldoc.ai.service.LLMProviderAndModelService;
import ai.eldoc.comparator.ItemSI;
import ai.eldoc.constants.Pages;
import ai.eldoc.constants.Preview;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.RecoFormDao;
import ai.eldoc.data.MongoLazyDataModel;
import ai.eldoc.exceptions.RecoFormValidationException;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.*;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.service.LogHelper;
import ai.eldoc.service.RecoFormService;
import ai.eldoc.ui.converter.RecoFormStatusConverter;
import ai.eldoc.util.JsfUtils;
import ai.eldoc.util.LabelUtils;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.event.data.PageEvent;
import org.primefaces.model.file.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static ai.eldoc.service.RecoFormService.createRecoForm;
import static java.text.MessageFormat.format;
import static java.util.function.Predicate.isEqual;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Backing bean for RecoForm pages
 */
@Named
@ViewScoped
public class RecoFormController extends AbstractUIController implements Serializable {

    @Serial
    private static final long serialVersionUID = -2009220046861896884L;

    private static final Logger LOG = LoggerFactory.getLogger(RecoFormController.class);

    @Inject
    private RecoFormDao _recoFormDao;

    @Inject
    private RecoFormService _recoFormService;

    @Inject
    private AiService _aiService;

    @Inject
    private LLMProviderAndModelService _llmProviderAndModelService;

    @Inject
    private FormDefController _formDefController;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private UserContext _userContext;

    private String _id;
    private RecoForm _recoForm;
    private RecoField _recoField;
    private List<RecoForm> _selectedForms = new ArrayList<>();
    private MongoLazyDataModel<RecoForm> _dataProvider;
    private int _currentPageIndex = 0;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Opens and Inits RecoForm
     *
     * @return navigation case
     */
    public String load() {
        try {
            if (isBlank(_id)) {
                _recoForm = createRecoForm(_userContext.getId());
                if (_aiService.isVisionAvailable()) {
                    _recoForm.setType(RecoForm.TYPE_AIBASED);
                }
                setModeEdit();
            } else {
                _recoForm = _recoFormService.findById(_id);
                if (_recoForm == null) {
                    // RecoForm not found, most likedly was already deleted
                    JsfUtils.addWarnMessage(_localeContext.getLabel("msg.recoForm.notFound"));
                    JsfUtils.keepMessagesOnRedirect();
                    return Pages.PAGE_ADMIN_RECOFORMS;
                }
                if (isOpenInEditMode(_recoForm.getId())) {
                    setModeEdit();
                }
            }
            return null;
        } catch (Exception e) {
            LOG.error(null, e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
        JsfUtils.keepMessagesOnRedirect();
        return Pages.PAGE_ADMIN_RECOFORMS;
    }


    public void handleFileUpload(FileUploadEvent event) {
        try {
            UploadedFile file = event.getFile();
            _recoFormService.attachSourceFile(_recoForm, file.getInputStream(), file.getFileName(), file.getContentType());
            JsfUtils.addInfoMessage(_localeContext.getLabel("recoFormUI.msg.fileUploaded"));
        } catch (Exception e) {
            LOG.error(null, e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }

        JsfUtils.keepMessagesOnRedirect();
        setOpenInEditMode(_recoForm.getId()); // remember that we need to open this document in edit mode after redirect
        JsfUtils.redirect(Pages.PAGE_ADMIN_RECOFORM, "id", _recoForm.getId());
    }


    public String btnRemoveAttachments() {
        try {
            _recoFormService.removeFormAttachments(_recoForm);
            JsfUtils.addInfoMessage(_localeContext.getLabel("recoFormUI.msg.fileRemoved"));
            JsfUtils.keepMessagesOnRedirect();
            setOpenInEditMode(_recoForm.getId()); // remember that we need to open this document in edit mode after redirect
            return JsfUtils.getNavigationTarget(Pages.PAGE_ADMIN_RECOFORM, "id", _recoForm.getId());
        } catch (Exception e) {
            LOG.error("RecoForm _id={}", _recoForm.getId(), e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
        return null;
    }


    public void createField() {
        _recoField = RecoField.createDefault();
        _recoForm.getPages().get(_currentPageIndex).getFields().add(_recoField);
    }


    public void initField() {
        try {
            _recoFormService.initField(_recoForm, getPageIdxByField(_recoField), _recoField);
        } catch (Exception ex) {
            LOG.error("RecoForm[{}]: Field initialization error", _recoForm.getId(), ex);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnEditField(RecoField field) {
        _recoField = field;
    }


    public void btnRemoveField(RecoField field) {
        _recoForm.getPages().forEach(page -> page.getFields().remove(field));
        if (Objects.equals(field, _recoField)) {
            _recoField = null;
        }
    }


    public void btnSuspend() {
        btnSuspend(_recoForm);
    }


    public void btnSuspend(RecoForm recoForm) {
        try {
            _recoFormService.suspendForm(recoForm);
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.recoForm.suspendSuccess"));
        } catch (Exception ex) {
            LOG.error("Error saving RecoForm to database, _id={}", recoForm.getId(), ex);
            JsfUtils.addErrorMessage(LabelUtils.getLabel("msg.error.general", _localeContext.getLocale()));
        }
    }


    public void btnSuspendBatch(List<RecoForm> recoFormList) {
        int formsSuspendedCount = _recoFormService.suspendFormBatch(recoFormList);

        if (formsSuspendedCount != recoFormList.size()) {
            // Some documents were skipped
            String details = format(_localeContext.getLabel("msg.recoForm.batchSuspendStatusDetail"),
                    formsSuspendedCount,
                    recoFormList.size(),
                    recoFormList.size() - formsSuspendedCount
            );
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.recoForm.batchSuspendStatus"), details);
        } else {
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.recoForm.batchSuspendStatus"));
        }
    }


    public void btnActivate() {
        btnActivate(_recoForm);
    }


    public void btnActivate(RecoForm recoForm) {
        try {
            _recoFormService.activateForm(recoForm);
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.recoForm.activationSuccess"));
        } catch (RecoFormValidationException ex) {
            JsfUtils.addErrorMessage(format(getLabelForValidationException(ex.getErrorCode()), ex.getMessageParams()));
        } catch (Throwable ex) {
            LOG.error("Error saving RecoForm to database, _id={}", recoForm.getId(), ex);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnActivateBatch(List<RecoForm> recoFormList) {
        int formActivationCount = _recoFormService.activateFormBatch(recoFormList);

        if (formActivationCount != recoFormList.size()) {
            // Some documents were skipped
            String details = format(_localeContext.getLabel("msg.recoForm.batchActivationStatusDetail"),
                    formActivationCount,
                    recoFormList.size(),
                    recoFormList.size() - formActivationCount
            );
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.recoForm.batchActivationStatus"), details);
        } else {
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.recoForm.batchActivationStatus"));
        }
    }


    public void btnExportBatch(List<RecoForm> recoFormList) throws IOException {
        if (recoFormList.isEmpty()) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.export.nothingSelected"));
            return;
        }

        try {
            String exportTaskId = _recoFormService.exportRecoForms(recoFormList);
            PrimeFaces.current().ajax().addCallbackParam("exportUrl", "/api/export/download?id=".concat(exportTaskId));
        } catch (Exception e) {
            String msg = format("Number of records: {0} | {1}", recoFormList.size(), e.getMessage());
            LOG.error(msg, e);
            LogHelper.setEvent(e, msg);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnDelete(RecoForm recoForm) {
        try {
            _recoFormService.deleteForm(recoForm);
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.recoForm.deleteSuccess"));
        } catch (Exception ex) {
            LOG.error("Error removing RecoForm from database, _id={}", recoForm.getId(), ex);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnDeleteBatch(List<RecoForm> recoFormList) {
        int formsDeletedCount = _recoFormService.deleteFormBatch(recoFormList);

        if (formsDeletedCount != recoFormList.size()) {
            // Some documents were skipped
            String details = format(_localeContext.getLabel("msg.recoForm.batchDeleteStatusDetail"),
                    formsDeletedCount,
                    recoFormList.size(),
                    recoFormList.size() - formsDeletedCount
            );
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.recoForm.batchDeleteStatus"), details);
        } else {
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.recoForm.batchDeleteStatus"));
        }
    }


    public void btnDuplicate(RecoForm recoFormSource) {
        try {
            _recoFormService.duplicateForm(recoFormSource);
            JsfUtils.addInfoMessage(_localeContext.getLabel("pAdminForm.msg.duplicateDone"));
        } catch (Exception e) {
            LogHelper.setEvent(e, format("Error duplicating RecoForm[{0}]({1})", recoFormSource.getId(), recoFormSource.getName()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnAddColumn() {
        RecoField newCol = RecoField.createDefault();
        newCol.setName("Column" + _recoField.getColumns().size());
        newCol.setRequired(false);
        _recoField.getColumns().add(newCol);
    }


    public void btnRemoveColumn(RecoField column) {
        boolean success = _recoField.getColumns().remove(column);
        if (!success) {
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnRemoveOpticalMark(RecoField opMark) {
        boolean success = _recoField.getOpMarks().remove(opMark);
        if (!success) {
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public String btnSave() {
        boolean isNewDoc;
        try {
            isNewDoc = _recoForm.isNew();

            // Check, if RecoForm name is unique
            if (!_recoFormService.isRecoFormNameUnique(_recoForm)) {
                JsfUtils.addErrorMessage(_localeContext.getLabel("recoFormUI.validation.formNameNotUnique"));
                return null;
            }

            // Perform RecoForm update/fix
            _recoFormService.fixForm(_recoForm);

            // Validate form before saving
            try {
                if (_recoForm.isStatusActive()) {
                    _recoFormService.validateFormForActivation(_recoForm);
                }
            } catch (RecoFormValidationException ex) {
                JsfUtils.addErrorMessage(format(getLabelForValidationException(ex.getErrorCode()), ex.getMessageParams()));
                // Form was active, but has errors - suspend form
                _recoForm.setStatus(RecoForm.SUSPENDED);
            }

            _recoFormService.updateOrCreate(_recoForm);
            _recoField = null;
            super.setModeRead();

            if (isNewDoc) {
                JsfUtils.keepMessagesOnRedirect();
                return JsfUtils.getNavigationTarget(Pages.PAGE_ADMIN_RECOFORM, "id", _recoForm.getId());
            } else {
                return null;
            }

        } catch (Throwable e) {
            LOG.error(null, e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }

        return null;
    }


    public String btnSaveAndActivate() {
        boolean wasActive = _recoForm.isStatusActive();
        String target = btnSave();

        if (!wasActive) {
            // If RecoForm was originally SUSPENDED or NEW - try activating it. This also validates form.
            // If RecoForm was originally active, but became suspended - this happened during saving due to validation errors, so no need to try to activate it again
            btnActivate(_recoForm);
        }
        return target;
    }


    public String btnCancel() {
        if (_recoForm.isNew()) {
            return JsfUtils.getNavigationTarget(Pages.PAGE_ADMIN_RECOFORMS);
        } else {
            super.setModeRead();
            return JsfUtils.getNavigationTarget(Pages.PAGE_ADMIN_RECOFORM, "id", _recoForm.getId());
        }
    }


    public void btnInitFields() {
        try {
            _recoFormService.initRecoFormResultWithAi(_recoForm);
            setOpenInEditMode(_recoForm.getId()); // remember that we need to open this document in edit mode after redirect
            JsfUtils.redirect(Pages.PAGE_ADMIN_RECOFORM, "id", _recoForm.getId());
        } catch (Throwable th) {
            LOG.error(null, th);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void moveAnchorUnderTableToCurrentPage() {
        if (_recoField == null) {
            return;
        }
        if (!_recoField.isTypeTable()) {
            return;
        }

        int fieldPageIdx = getPageIdxByField(_recoField);

        if (_currentPageIndex < fieldPageIdx) {
            // Do not allow to set field's AnchorUnderTable page before field's page
            JsfUtils.addErrorMessage(format(_localeContext.getLabel("recoFormUI.validation.fieldAnchorUnderTableWrongPage"), _recoField.getName()));
            return;
        }

        if (fieldPageIdx != _currentPageIndex) {
            // If field is set not on the current page - save AnchorUnderTable page index
            _recoField.getTableProp().setAnchorUnderTablePageIndex(_currentPageIndex);
        } else {
            // If field is set on the current page - reset AnchorUnderTable page index to default, that will mean AnchorUnderTable located on the same page, as the field
            _recoField.getTableProp().setAnchorUnderTablePageIndex(0);
        }
    }


    public List<String> completeTags(String query) {
        if (isBlank(query)) {
            return new ArrayList<>(_recoFormService.getTagsDistinct());
        } else {
            List<String> result = _recoFormService.getTagsDistinct()
                    .stream()
                    .filter(tag -> Strings.CI.contains(tag, query))
                    .collect(Collectors.toList());
            result.addFirst(query);
            return result;
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    private String getLabelForValidationException(int errorCode) {
        String labelId = switch (errorCode) {
            case RecoFormValidationException.FORM_KEYWORD_REQUIRED -> "recoFormUI.validation.keywordsRequired";
            case RecoFormValidationException.FORM_KEYWORD_REGEX_INVALID -> "recoFormUI.validation.keywordRegexNotValid";
            case RecoFormValidationException.FORM_KEYWORD_REGEX_BLANK -> "recoFormUI.validation.keywordRegexBlank";
            case RecoFormValidationException.FORM_SOURCE_FILE_REQUIRED -> "recoFormUI.validation.sourceFileRequired";
            case RecoFormValidationException.FORM_POSTPROC_INVALID -> "recoFormUI.validation.formPostProcessNotValid";
            case RecoFormValidationException.FORM_NAME_REQUIRED -> "recoFormUI.validation.formNameRequired";
            case RecoFormValidationException.FORM_NAME_NOT_UNIQUE -> "recoFormUI.validation.formNameNotUnique";
            case RecoFormValidationException.FORM_DESCRIPTION_TOO_LONG -> "recoFormUI.validation.descriptionTooLong";
            case RecoFormValidationException.FORM_EXTRACTION_CONTEXT_TOO_LONG -> "recoFormUI.validation.extractionContextTooLong";
            case RecoFormValidationException.FORM_LANGS_REQUIRED -> "recoFormUI.validation.formLangsRequired";
            case RecoFormValidationException.FIELD_NAME_REQUIRED -> "recoFormUI.validation.fieldNameRequired";
            case RecoFormValidationException.FIELD_NAME_NOT_UNIQUE -> "recoFormUI.validation.fieldNameNotUnique";
            case RecoFormValidationException.FIELD_POSTPROC_INVALID -> "recoFormUI.validation.fieldPostProcessNotValid";
            case RecoFormValidationException.FIELD_REGEX_REQUIRED -> "recoFormUI.validation.fieldRegexRequired";
            case RecoFormValidationException.FIELD_DATETIME_FORMAT_INVALID -> "recoFormUI.validation.fieldDateTimeFormatNotValid";
            case RecoFormValidationException.FIELD_ANCHOR_TEXT_REQUIRED -> "recoFormUI.validation.anchorTextRequired";
            case RecoFormValidationException.FIELD_ANCHOR_REGEX_INVALID -> "recoFormUI.validation.fieldAnchorRegexNotValid";
            case RecoFormValidationException.FIELD_ANCHOR_UNDTBL_TEXT_REQUIRED -> "recoFormUI.validation.anchorUndTblTextRequired";
            case RecoFormValidationException.FIELD_ANCHOR_UNDTBL_REGEX_INVALID -> "recoFormUI.validation.fieldAnchorUndTblRegexNotValid";
            case RecoFormValidationException.FIELD_ANCHOR_UNDFLD_TEXT_REQUIRED -> "recoFormUI.validation.anchorUndFldTextRequired";
            case RecoFormValidationException.FIELD_ANCHOR_UNDFLD_REGEX_INVALID -> "recoFormUI.validation.fieldAnchorUndFldRegexNotValid";
            case RecoFormValidationException.FIELD_REGEX_BLANK -> "recoFormUI.validation.fieldRegexBlank";
            case RecoFormValidationException.FIELD_REGEX_INVALID -> "recoFormUI.validation.fieldRegexNotValid";
            case RecoFormValidationException.FIELD_REGEX_MISSING_GROUP -> "recoFormUI.validation.fieldRegexMissingGroup";
            case RecoFormValidationException.FIELD_TAG_NOT_UNIQUE -> "recoFormUI.validation.fieldTagNotUnique";
            case RecoFormValidationException.FIELD_CONF_THRESHOLD_INVALID -> "recoFormUI.validation.fieldConfThresholdInvalid";
            default -> "msg.error.general";
        };
        return _localeContext.getLabel(labelId);
    }


    public MongoLazyDataModel<RecoForm> getDataProvider() {
        if (_dataProvider == null) {
            _dataProvider = new MongoLazyDataModel<>(_recoFormDao);
            _dataProvider.getFilters().put(RecoForm._STATUS, List.of(RecoForm.NEW, RecoForm.ACTIVE));
        }
        return _dataProvider;
    }


    public List<RecoField> getAllFieldsList() {
        return _recoForm == null ? List.of() : _recoForm.getFields();
    }


    public boolean isTableCollsAllowedForEdit(RecoField recoField) {
        return _recoForm.getPages().get(_currentPageIndex).getFields().contains(recoField);
    }


    public boolean isFieldAllowedForEdit(RecoField recoField) {
        return (_recoForm.getPages().get(_currentPageIndex).getFields().contains(recoField)
                || (recoField.isTypeTable()
                && (recoField.getTableProp().getAnchorUnderTablePageIndex() != 0
                && recoField.getTableProp().getAnchorUnderTablePageIndex() == _currentPageIndex)));
    }


    public boolean isClassificationAvailable() {
        return _aiService.isClassificationAvailable();
    }


    public boolean isFieldInitAvailable() {
        // Initializing RecoForm fields currently uses Agentic model (initialization from text)
        return getRecoForm().isTypeAuto() && _aiService.isAiAgentsAvailable();
    }


    public RecoPage getPageByField(RecoField recoField) {
        return _recoForm.getPageByField(recoField);
    }


    public int getFieldIdxOnPage(RecoField recoField) {
        return getPageByField(recoField).getFields().indexOf(recoField);
    }


    public int getPageIdxByField(RecoField recoField) {
        return _recoForm.getPages().indexOf(getPageByField(recoField));
    }


    public List<SelectItem> getFieldTypesSI() {
        List<SelectItem> result = new ArrayList<>();
        List<RecoFieldDataType> enabledTypes;
        if (getRecoForm().getType() == RecoForm.TYPE_AIBASED) {
            enabledTypes = List.of(RecoFieldDataType.TEXT, RecoFieldDataType.TABLE);
        } else {
            enabledTypes = List.of(RecoFieldDataType.TEXT, RecoFieldDataType.TEXT_EXTENDABLE, RecoFieldDataType.IMAGE, RecoFieldDataType.TABLE, RecoFieldDataType.OMR);
        }
        for (RecoFieldDataType dataType : RecoFieldDataType.AVAILABLE) {
            result.add(new SelectItem(dataType, dataType.name(), null, !enabledTypes.contains(dataType)));
        }
        return result;
    }


    public List<SelectItem> getCellTypesSI() {
        List<SelectItem> result = new ArrayList<>();
        List<RecoFieldDataType> enabledTypes = List.of(RecoFieldDataType.TEXT, RecoFieldDataType.IMAGE);
        for (RecoFieldDataType dataType : RecoFieldDataType.values()) {
            result.add(new SelectItem(dataType, dataType.name(), null, !enabledTypes.contains(dataType)));
        }
        return result;
    }


    public List<SelectItem> getAiExtractionModelSI() {
        List<SelectItem> extractionModels = new ArrayList<>();
        Pair<LLMProviderConfig, LLMModelConfig> defaultModel = _llmProviderAndModelService.getIdpExtractionModelConfig();
        if (defaultModel != null) {
            String defaultLabelSI = _localeContext.getLabel("pRecoForm.extractionModelDefault");
            String modelLabel = MessageFormat.format(defaultLabelSI, defaultModel.getLeft().getName(), defaultModel.getRight().getNameEffective());
            // To keep System Default value - we store just null instead of the specific providerAndModelPairId
            extractionModels.add(new SelectItem(null, modelLabel));
        }
        extractionModels.addAll(ConfigController.aiModelsToSelectItems(_llmProviderAndModelService.getVisionModels(true)));
        return extractionModels;
    }


    /**
     * @return RecoForms with status RecoForm.ACTIVE as List of SelectItems
     */
    public List<SelectItem> getRecoFormSI() {
        return _recoFormService.findAllByStatus(List.of(RecoForm.ACTIVE)).stream()
                .map(recoForm -> new SelectItem(recoForm.getId(), recoForm.getName()))
                .sorted(new ItemSI())
                .collect(Collectors.toList());
    }


    public List<SelectItem> getRecoFormSI(String rfId, String rfName) {
        List<SelectItem> result = new ArrayList<>();
        if (isBlank(rfId)) {
            result.add(new SelectItem(null, "Select recognition form..."));
        }
        result.addAll(getRecoFormSI());
        if (isNotBlank(rfId) && result.stream().map(SelectItem::getValue).noneMatch(isEqual(rfId))) {
            // Note 1: blank rfId comes when RecoForm added manually during validation by using Add button
            // Note 2: No RecoForm present (was replaced during Import/Export most common case)
            // We do not add it as disabled as it will cause poor UX on validation
            result.add(new SelectItem(rfId, rfName));
        }
        return result;
    }


    public List<SelectItem> getRecoFormTypeSI() {
        List<SelectItem> si = new ArrayList<>();
        if (_aiService.isVisionAvailable()) {
            si.add(new SelectItem(RecoForm.TYPE_AIBASED, RecoForm.getFormTypeName(RecoForm.TYPE_AIBASED)));
        }
        return si;
    }


    public List<SelectItem> getRegexTypeSI() {
        List<SelectItem> si = new ArrayList<>();
        si.add(new SelectItem(TextProperties.TYPE_STANDARD, _localeContext.getLabel("recoField.textProp.regex.typeStandard")));
        si.add(new SelectItem(TextProperties.TYPE_BITAP, _localeContext.getLabel("recoField.textProp.regex.typeBitap")));
        return si;
    }


    public List<SelectItem> getDateFormatSI() {
        List<SelectItem> si = new ArrayList<>();
        si.add(new SelectItem("", "Not set"));
        si.add(new SelectItem("dd.MM.yyyy", "dd.MM.yyyyy"));
        si.add(new SelectItem("mm.dd.yyyy", "mm.dd.yyyyy"));
        si.add(new SelectItem("dd.MM.yyyy HH:mm", "dd.MM.yyyyy HH:mm"));
        si.add(new SelectItem("mm.dd.yyyy hh:mm a", "mm.dd.yyyyy hh:mm a"));
        return si;
    }


    public List<SelectItem> getRecoFormStatusSI() {
        return Stream.of(RecoForm.NEW, RecoForm.ACTIVE, RecoForm.SUSPENDED)
                .map(recoFormStatus -> new SelectItem(recoFormStatus, RecoFormStatusConverter.getLabelByStatus(recoFormStatus, _localeContext.getLocale())))
                .collect(Collectors.toList());
    }


    public RecoForm getRecoForm() {
        return _recoForm;
    }


    public RecoField getRecoField() {
        return _recoField;
    }


    public void setRecoField(RecoField field) {
        _recoField = field;
    }


    public String getId() {
        return _id;
    }


    public void setId(String id) {
        _id = id;
    }


    public List<SelectItem> getRecoFormListSI() {
        return _recoFormService.findAll().stream()
                .map(recoForm -> new SelectItem(recoForm.getId(), recoForm.getName()))
                .sorted(new ItemSI())
                .collect(Collectors.toList());
    }


    public List<RecoForm> getSelectedForms() {
        return _selectedForms;
    }


    public void setSelectedForms(List<RecoForm> selectedForms) {
        _selectedForms = selectedForms;
    }


    public Integer getCurrentPageNumber() {
        return _currentPageIndex;
    }


    public void setCurrentPageNumber(PageEvent event) {
        _currentPageIndex = event.getPage();
        if (_recoField != null && !_recoField.isTypeTable()) {
            // Reset selected field only if it is not table
            _recoField = null;
        }
    }


    public List<SelectItem> getTargetFormsSI() {
        return _formDefController.getSysFromActiveSI().stream().filter(si -> !FormDefinition.isRecoWFForm(String.valueOf(si.getValue()))).collect(Collectors.toList());
    }


    public FormDefinition getTargetFormDefinition(String formId) {
        return _formDefController.getAllActive().stream().filter(form -> form.getFormId().equals(formId)).findFirst().orElse(null);
    }


    public boolean isPageHeaderFooterPropInherited() {
        return getRecoForm().getHeaderFooterSettingsHolderForPage(getCurrentRecoPage()).getPageIndex() != getCurrentRecoPage().getPageIndex();
    }


    public String getPageImageUrlPreviewId() {
        DbFileRef previewFile = _recoForm.getPreprocessedFile();
        if (previewFile == null) {
            return "#";
        }
        PreviewItem previewItem = new PreviewItem(_recoFormDao.getFilesDao().getFile(null, previewFile), _recoForm.getAuthor(), Preview.TYPE_DOC_AS_IMG, false, PdfUtils.IMAGE_RENDER_WEB_DPI, null);
        return _userContext.putPreviewItem(previewItem);
    }


    private PageProperties getPageHeaderFooterProp() {
        return getRecoForm().getHeaderFooterSettingsForPage(getCurrentRecoPage());
    }


    private RecoPage getCurrentRecoPage() {
        return getRecoForm().getPages().get(_currentPageIndex);
    }


    public boolean isHeaderEnabled() {
        return getPageHeaderFooterProp().isHeaderEnabled();
    }


    public boolean isApplyHeaderFooterRemainingPages() {
        return getPageHeaderFooterProp().isApplyHeaderFooterRemainingPages();
    }


    public void setApplyHeaderFooterRemainingPages(boolean apply) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && apply == isApplyHeaderFooterRemainingPages()) {
            return;
        }
        getCurrentRecoPage().getProp().setApplyHeaderFooterRemainingPages(apply);
    }


    public void setHeaderEnabled(boolean enabled) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && enabled == isHeaderEnabled()) {
            return;
        }
        getCurrentRecoPage().getProp().setHeaderEnabled(enabled);
    }


    public int getHeaderHeight() {
        return getPageHeaderFooterProp().getHeaderHeight();
    }


    public void setHeaderHeight(int height) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && height == getHeaderHeight()) {
            return;
        }
        getCurrentRecoPage().getProp().setHeaderHeight(height);
    }


    public List<String> getHeaderKeywords() {
        return getPageHeaderFooterProp().getHeaderKeywords();
    }


    public void setHeaderKeywords(List<String> keywords) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && Objects.equals(keywords, getHeaderKeywords())) {
            return;
        }
        getCurrentRecoPage().getProp().setHeaderKeywords(keywords);
    }


    public boolean isHeaderUseKeywordsAsBorder() {
        return getPageHeaderFooterProp().isHeaderUseKeywordsAsBorder();
    }


    public void setHeaderUseKeywordsAsBorder(boolean useAsBorder) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && useAsBorder == isHeaderUseKeywordsAsBorder()) {
            return;
        }
        getCurrentRecoPage().getProp().setHeaderUseKeywordsAsBorder(useAsBorder);
    }


    public boolean isHeaderAuto() {
        return getPageHeaderFooterProp().isHeaderAuto();
    }


    public void setHeaderAuto(boolean auto) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && auto == isHeaderAuto()) {
            return;
        }
        getCurrentRecoPage().getProp().setHeaderAuto(auto);
    }


    public boolean isFooterEnabled() {
        return getPageHeaderFooterProp().isFooterEnabled();
    }


    public void setFooterEnabled(boolean enabled) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && enabled == isFooterEnabled()) {
            return;
        }
        getCurrentRecoPage().getProp().setFooterEnabled(enabled);
    }


    public int getFooterHeight() {
        return getPageHeaderFooterProp().getFooterHeight();
    }


    public void setFooterHeight(int height) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && height == getFooterHeight()) {
            return;
        }
        getCurrentRecoPage().getProp().setFooterHeight(height);
    }


    public List<String> getFooterKeywords() {
        return getPageHeaderFooterProp().getFooterKeywords();
    }


    public void setFooterKeywords(List<String> keywords) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && Objects.equals(keywords, getFooterKeywords())) {
            return;
        }
        getCurrentRecoPage().getProp().setFooterKeywords(keywords);
    }


    public boolean isFooterUseKeywordsAsBorder() {
        return getPageHeaderFooterProp().isFooterUseKeywordsAsBorder();
    }


    public void setFooterUseKeywordsAsBorder(boolean useAsBorder) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && useAsBorder == isFooterUseKeywordsAsBorder()) {
            return;
        }
        getCurrentRecoPage().getProp().setFooterUseKeywordsAsBorder(useAsBorder);
    }


    public boolean isFooterAuto() {
        return getPageHeaderFooterProp().isFooterAuto();
    }


    public void setFooterAuto(boolean auto) {
        if (getCurrentRecoPage().getProp() != getPageHeaderFooterProp() && auto == isFooterAuto()) {
            return;
        }
        getCurrentRecoPage().getProp().setFooterAuto(auto);
    }


    private double getImageDpiScale() {
        return ((double) PdfUtils.IMAGE_RENDER_WEB_DPI) / ((double) PdfUtils.IMAGE_RENDER_DEFAULT_DPI);
    }


    public String getFrdData() {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("imageDpiScale", getImageDpiScale())
                .add("modeEdit", isModeEdit())
                .add("pageIdx", _currentPageIndex);
        if (_recoForm != null) {
            builder = builder.add("bottomAnchorUseBottom", _recoForm.getProperty(PropertyConfig.IDP_RECOFORM_TABLE_ANCUNDTBL_USEBOTTOM, false));
        }
        return builder.build().toString();
    }

}
