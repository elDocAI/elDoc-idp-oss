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

import ai.eldoc.constants.Pages;
import ai.eldoc.constants.Preview;
import ai.eldoc.context.*;
import ai.eldoc.dao.*;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.*;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.reco.ImageProcessor;
import ai.eldoc.service.*;
import ai.eldoc.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.faces.annotation.RequestMap;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIInput;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseId;
import jakarta.faces.model.SelectItem;
import jakarta.faces.validator.ValidatorException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.tuple.Triple;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.PrimeFaces;
import org.primefaces.event.data.PageEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static ai.eldoc.bpm.BpmAclConstants.ACTION_DECLINE;
import static ai.eldoc.bpm.BpmAclConstants.ACTION_DONE;
import static ai.eldoc.bpm.BpmConstants.*;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * RecoWorkflow Bean - is based on Workflow Bean and optimizes handling RecoDocs on UI (Workflows of the RecoWorkflow type)
 */
@Named
@ViewScoped
public class RecoDocController implements Serializable {

    @Serial
    private static final long serialVersionUID = 8365702005076036015L;

    private static final Logger LOG = LoggerFactory.getLogger(RecoDocController.class);

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private RecoDataDao _recoDataDao;

    @Inject
    @RequestMap
    private Map<String, Object> _requestMap;

    @Inject
    private RecoFormService _recoFormService;

    @Inject
    private RecoWorkflowService _recoWorkflowService;

    @Inject
    private WorkflowAccessController _workflowAccessController;

    @Inject
    private WorkflowController _workflowController;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private PageController _pageController;

    @Inject
    private TaskService _taskService;

    @Inject
    private ConfigContext _configContext;

    private RecoFormResult _recoFormResult;
    private List<DbFile> _previewFileList;
    private DbFile _previewFile;
    private final List<Integer> _pageIndexList;
    private int _pageIndex = 0; // preview page index, we always start from page with index 0
    private boolean _previewPreprocessed;
    private boolean _previewFileIsPdf;
    private List<RecoData> _recoDataList;
    private int _recoDataIdx;
    private int _sourceFilePageCount = -1;
    private boolean _showAllFields;

    private CustomField _selectedCF;
    private int _selectedCFRow = -1;
    private int _selectedCFCol = -1;
    private Rectangle _selectedCFArea;
    private List<Triple<Integer, Integer, Integer>> _customFieldsNotValid;

    private String _docIdOnLoad;
    private String _userIdOnLoad;
    private final String _viewUnid;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public RecoDocController() {
        _previewPreprocessed = true; // By default we show PREPROCESSED(_FINAL) document
        _viewUnid = UUID.randomUUID().toString();
        _pageIndexList = new ArrayList<>();
        _customFieldsNotValid = new ArrayList<>();
    }


    //
    // Initialization Methods ---------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        // First trying to lock document
        _docIdOnLoad = getRecoDoc().getId();
        _userIdOnLoad = _userContext.getId();
        _workflowAccessController.putLock(_docIdOnLoad, _userIdOnLoad.concat("|").concat(_viewUnid));

        // Load all available reco results (also handles legacy RecoDocs by preparing RecoData for them)
        _recoDataList = _recoWorkflowService.getRecoDataList(getRecoDoc());
    }


    @PreDestroy
    protected void destroy() {
        // On leaving the page - try to remove lock
        _workflowAccessController.removeLock(_docIdOnLoad, _userIdOnLoad.concat("|").concat(_viewUnid));
    }


    //
    // RecoWorkflow Actions Methods --------------------------------------------------------------------
    //


    /**
     * Saves current RecoData (and RecoWorkflow in case any fields were updated)
     */
    public void btnSaveEdit() {
        try {
            if (getRecoData() == null || getRecoData().isValidated()) {
                // Nothing to save
                return;
            }
            saveFieldEdit(); // Save IMAGE-type field edits, if needed
            _recoWorkflowService.actionSaveRecoData(getRecoDoc(), getRecoData());
            _workflowController.btnSaveEdit();
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.doc.recoDataSaved"));
        } catch (Exception e) {
            LogHelper.setEvent("id00233", e, e.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    /**
     * Performs RecoWorkflow validation
     *
     * @return
     */
    public String btnValidateReco() {
        try {
            saveFieldEdit(); // Save IMAGE-type field edits, if needed
            if (isRecoDataRequiresValidationCheckAndNotify()) {
                return null;
            }
            int unValidatedRecoDataIdx = _recoWorkflowService.actionValidateReco(
                    getRecoDoc(),
                    _workflowController.getCurrentWFTask(),
                    _userContext.getAclContext(),
                    _recoDataList, getRecoData(),
                    UserRequestMeta.fromRequest(JsfUtils.getRequest()));
            if (unValidatedRecoDataIdx >= 0) {
                // In case more RecoData left unvalidated - its index will be returned. We need to swith to it
                JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.unvalidatedRecoDataRemains"));
                _recoDataIdx = unValidatedRecoDataIdx;
                resetOnRecoDataChange();
                return null;
            }
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_INBOX);
        } catch (RecoDataNotAvailableException naex1) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.recoDataNotAvailable"));
        } catch (RecoDataPagesOverlapException poe1) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.recoDataPageOverlapDetected"));
        } catch (DBEntryNotValidException dbex1) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.notValid"));
        } catch (ConcurrentWriteException cwe1) {
            _workflowController.refreshDocACL(); // refreshes docACL
            LogHelper.setEvent("id00184", null, "For information only: " + cwe1.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
        } catch (Exception e) {
            LogHelper.setEvent("id00230", e, e.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
        return null;
    }


    /**
     * Performs Validation after Conversion Action
     *
     * @return
     */
    public String btnValidateConv() {
        try {
            saveFieldEdit(); // Save IMAGE-type field edits, if needed
            if (isRecoDataRequiresValidationCheckAndNotify()) {
                return null;
            }
            int unValidatedRecoDataIdx = _recoWorkflowService.actionValidateConv(
                    getRecoDoc(),
                    _workflowController.getCurrentWFTask(),
                    _userContext.getAclContext(),
                    _recoDataList,
                    getRecoData(),
                    UserRequestMeta.fromRequest(JsfUtils.getRequest()));
            if (unValidatedRecoDataIdx >= 0) {
                // In case more RecoData left unvalidated - its index will be returned. We need to swith to it
                JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.unvalidatedRecoDataRemains"));
                _recoDataIdx = unValidatedRecoDataIdx;
                resetOnRecoDataChange();
                return null;
            }
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_INBOX);
        } catch (RecoDataNotAvailableException naex1) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.recoDataNotAvailable"));
        } catch (RecoDataPagesOverlapException poe1) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.recoDataPageOverlapDetected"));
        } catch (DBEntryNotValidException dbex1) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.notValid"));
        } catch (ConcurrentWriteException cwe1) {
            _workflowController.refreshDocACL(); // refreshes docACL
            LogHelper.setEvent("id00240", null, "For information only: " + cwe1.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
        } catch (Exception e) {
            LogHelper.setEvent("id00241", e, e.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
        return null;
    }


    private boolean isRecoDataRequiresValidationCheckAndNotify() {
        _customFieldsNotValid = RecoWorkflowService.validateRecoDataFields(getRecoData());
        if (!_customFieldsNotValid.isEmpty()) {
            _customFieldsNotValid.forEach(fieldValidationResult -> {
                CustomField cf = getRecoData().getCfDef().get(fieldValidationResult.getLeft());
                if (fieldValidationResult.getMiddle() == -1 && fieldValidationResult.getRight() == -1) {
                    JsfUtils.postValidationError(cf.getName().get(_localeContext.getLangSystemCode()) + ": value required");
                } else {
                    JsfUtils.postValidationError(cf.getName().get(_localeContext.getLangSystemCode()) + "[" + cf.getFields().get(fieldValidationResult.getRight()).getName().get(_localeContext.getLangSystemCode()) + "][" + (fieldValidationResult.getMiddle() + 1) + "]: value required");
                }
            });
            return true;
        }
        return false;
    }


    /**
     * Sends RecoWorkflow to re-work
     *
     * @return
     */
    public String btnToWorkAgain() {
        try {
            _recoWorkflowService.actionToWorkAgain(getRecoDoc(), _workflowController.getCurrentWFTask(), _userContext.getAclContext(), UserRequestMeta.fromRequest(JsfUtils.getRequest()));
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_INBOX);
        } catch (DBEntryNotValidException dbex1) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.notValid"));
        } catch (ConcurrentWriteException cwe1) {
            _workflowController.refreshDocACL(); // refreshes docACL
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
        } catch (Exception e) {
            LogHelper.setEvent("id00239", e, e.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
        return null;
    }


    /**
     * Cancels RecoWorkflow
     *
     * @return
     */
    public String btnCancel() {
        try {
            btnCancelFieldEdit(); // Reset field edit, if present (IMAGE-type fields mainly)
            _recoWorkflowService.actionCancel(getRecoDoc(), _workflowController.getCurrentWFTask(), _userContext.getAclContext(), UserRequestMeta.fromRequest(JsfUtils.getRequest()));
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_INBOX);
        } catch (DBEntryNotValidException dbex1) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.notValid"));
        } catch (ConcurrentWriteException cwe1) {
            _workflowController.refreshDocACL(); // refreshes docACL
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
        } catch (Exception e) {
            LogHelper.setEvent("id00238", e, e.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
        return null;
    }


    public void btnRecoDataEdit() {
        try {
            if (getRecoData() == null || !getRecoData().isValidated()) {
                // Nothing to do when there is no RecoData or it is still not yet validated
                // This should be handled on UI, but having it here for additional stability
                return;
            }
            getRecoData().setValidated(false);
            _recoWorkflowService.actionSaveRecoData(getRecoDoc(), getRecoData());
        } catch (ConcurrentWriteException cwe1) {
            _workflowController.refreshDocACL(); // refreshes docACL
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
        } catch (Exception e) {
            LogHelper.setEvent("id00236", e, e.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnRecoDataAdd() {
        try {
            RecoData recoData = new RecoData(getRecoDoc());
            recoData.setRecoForm(new RecoFormDto(null, "New recognition data", null));
            _recoWorkflowService.actionSaveRecoData(getRecoDoc(), recoData);
            _recoDataList.add(recoData);
            _recoDataIdx = _recoDataList.size() - 1; // Switch UI to the newly created RecoData
            resetOnRecoDataChange();
        } catch (ConcurrentWriteException cwe1) {
            _workflowController.refreshDocACL(); // refreshes docACL
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
        } catch (Exception e) {
            LogHelper.setEvent("id00237", e, e.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    /**
     * Deletes currently selected RecoData
     */
    public void btnRecoDataDelete() {
        try {
            _recoWorkflowService.actionDeleteRecoData(getRecoDoc(), getRecoData());
            _recoDataList.remove(_recoDataIdx); // After removing recoData from List _recoDataIdx will point to the next recoData, what is desired behaviour
            if (_recoDataIdx == _recoDataList.size()) {
                // In case no elements left - adjust index to 0, otherwise - point to the last element
                _recoDataIdx = _recoDataList.isEmpty() ? 0 : _recoDataList.size() - 1;
            }
            resetOnRecoDataChange();
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.doc.recoDataDeleted"));
        } catch (ConcurrentWriteException cwe1) {
            _workflowController.refreshDocACL(); // refreshes docACL
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
        } catch (Exception e) {
            LogHelper.setEvent("id00235", e, e.getMessage(), new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public void btnAddFieldImage(CustomField cf) {
        if (cf == null) {
            return;
        }
        resetImageFieldEdit();
        _selectedCF = cf;
        RecoFileMeta fileMeta = RecoFileMeta.fromJson(_previewFile.getMetadataAsJson());
        _selectedCFArea = Rectangle.fromString(Rectangle.RECT_DEFAULT_STR).createIntersection(ImageProcessor.getDimensionsFromString(fileMeta.getPagesDimensions().get(_pageIndex)));
    }


    public void btnAddFieldCellImage(CustomField cf, int row, int col) {
        if (cf == null) {
            return;
        }
        resetImageFieldEdit();
        _selectedCF = cf;
        _selectedCFRow = row;
        _selectedCFCol = col;
        RecoFileMeta fileMeta = RecoFileMeta.fromJson(_previewFile.getMetadataAsJson());
        _selectedCFArea = Rectangle.fromString(Rectangle.RECT_DEFAULT_STR).createIntersection(ImageProcessor.getDimensionsFromString(fileMeta.getPagesDimensions().get(_pageIndex)));
    }


    public void btnEditField(CustomField cf) {
        if (cf == null) {
            return;
        }
        resetImageFieldEdit();
        _selectedCF = cf;
        _selectedCFArea = RecoWorkflowService.getRecoFieldResultByCustomField(getRecoFormResult(), cf, _configContext.getDefaultLang()).getRegion();
        if (_selectedCFArea.getArea() == 0) {
            RecoFileMeta fileMeta = RecoFileMeta.fromJson(_previewFile.getMetadataAsJson());
            _selectedCFArea = Rectangle.fromString(Rectangle.RECT_DEFAULT_STR).createIntersection(ImageProcessor.getDimensionsFromString(fileMeta.getPagesDimensions().get(_pageIndex)));
        }
    }


    public void btnEditFieldCell(CustomField cf, int row, int col) {
        if (cf == null) {
            return;
        }
        resetImageFieldEdit();
        _selectedCF = cf;
        _selectedCFRow = row;
        _selectedCFCol = col;
        _selectedCFArea = RecoWorkflowService.getRecoFieldImageCellResultByCustomField(getRecoData(), getRecoFormResult(), cf, _configContext.getDefaultLang(), row, col).getRegion();
    }


    public void btnSaveFieldEdit() {
        try (InputStream is = DaoUtils.getFileAwareDao(_previewFile.getDbFileRef()).getFilesDao().getFileDownloadStream(_previewFile.getDbFileRef())) {
            BufferedImage currentPageBufImg = PdfUtils.renderPageToImage(is, _pageIndex, PdfUtils.IMAGE_RENDER_BEST_DPI);
            RecoFileMeta fileMeta = RecoFileMeta.fromJson(_previewFile.getMetadataAsJson());
            Rectangle pageRect = ImageProcessor.getDimensionsFromString(fileMeta.getPagesDimensions().get(_pageIndex));// Original page rect
            Rectangle scaledPageRect = pageRect.getScaledClone(getFieldImageRenderScale()); // Page rect with applied DPI
            currentPageBufImg = ImageProcessor.getSubimage(currentPageBufImg, _selectedCFArea.getScaledClone(getFieldImageRenderScale()).createIntersection(scaledPageRect));
            String fieldValue;
            if (_selectedCF.isTypeRecoTable()) {
                fieldValue = ObjectValueUtils.getValueAsListOfStringList(getRecoData().getCfData().get(getRecoData().getCfDsFieldName(_selectedCF))).get(_selectedCFRow).get(_selectedCFCol);
            } else {
                fieldValue = (String) getRecoData().getCfData().getOrDefault(getRecoData().getCfDsFieldName(_selectedCF), "");
            }
            if (isBlank(fieldValue)) {
                _recoWorkflowService.uploadRecoFieldImage(getRecoData(), _selectedCF, _selectedCFRow, _selectedCFCol, _configContext.getDefaultLang(), currentPageBufImg);
            } else {
                _recoWorkflowService.replaceRecoFieldImage(getRecoData(), _selectedCF, _selectedCFRow, _selectedCFCol, currentPageBufImg);
            }
        } catch (Throwable th) {
            LOG.error("Error reading page fragment, docId={}, recoData={}, page={}, area={}", getRecoDoc().getId(), getRecoData().getId(), getPageIndex(), _selectedCFArea, th);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
            return;
        }
        resetImageFieldEdit();
    }


    private void saveFieldEdit() {
        if (_selectedCF != null) {
            // There is active field edit - we need to save possible changes as well
            btnSaveFieldEdit();
        }
        resetImageFieldEdit();
    }


    public void btnCancelFieldEdit() {
        resetImageFieldEdit();
    }


    private void resetImageFieldEdit() {
        _selectedCF = null;
        _selectedCFArea = null;
        _selectedCFRow = -1;
        _selectedCFCol = -1;
    }


    public void btnRemoveImage(CustomField cf, int row, int col) {
        try {
            _recoWorkflowService.removeRecoFieldImage(getRecoData(), cf, row, col);
        } catch (Exception e) {
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    //
    // UI Controls ACL Getters --------------------------------------------------------------------
    //
    public String getLockedBy() {
        return _workflowAccessController.getLockedBy(_docIdOnLoad);
    }


    public String getLockedBySystemId() {
        return (getLockedBy() == null ? null : getLockedBy().split("\\|")[0]);
    }


    public String getLockedByViewId() {
        return (getLockedBy() == null ? null : getLockedBy().split("\\|")[1]);
    }


    public boolean isLockedByCurrentUser() {
        return Objects.equals(getLockedBySystemId(), _userContext.getId());
    }


    public boolean isModeEdit() {
        return _workflowController.getDocACL().contains(ACTION_DONE);
    }


    public boolean isBtnSaveRendered() {
        return isModeEdit();
    }


    /**
     * Defines whether button ToWorkAgain is rendered on the UI
     *
     * @return
     */
    public boolean isBtnToWrkAgainRecoValidationRendered() {
        return RECO_PROCESS_STEP_RECO_VALIDATOR == getRecoDoc().getStep()
                && _workflowController.getDocACL().contains(ACTION_DECLINE);
    }


    /**
     * Defines whether button ToWorkAgain is rendered on the UI
     *
     * @return
     */
    public boolean isBtnValidateConvRendered() {
        return RECO_PROCESS_STEP_CONV_VALIDATOR == getRecoDoc().getStep()
                && _workflowController.getDocACL().contains(ACTION_DECLINE);
    }


    public boolean isBtnCancelRendered() {
        return _workflowController.getDocACL().contains(ACTION_DECLINE);
    }


    public boolean isBtnValidateRecoRendered() {
        return RECO_PROCESS_STEP_RECO_VALIDATOR == getRecoDoc().getStep() && isModeEdit();
    }


    public boolean isBtnRecoDataEdtDisabled() {
        return !isLockedByCurrentUser() || !isModeEdit() || getRecoDoc().getStep() != RECO_PROCESS_STEP_RECO_VALIDATOR || getRecoData() == null || !getRecoData().isValidated();
    }


    public boolean isBtnRecoDataAddDisabled() {
        return !isLockedByCurrentUser() || !isModeEdit() || getRecoDoc().getStep() != RECO_PROCESS_STEP_RECO_VALIDATOR;
    }


    public boolean isBtnRecoDataDelDisabled() {
        return !isLockedByCurrentUser() || !isModeEdit() || getRecoDoc().getStep() != RECO_PROCESS_STEP_RECO_VALIDATOR || _recoDataList.isEmpty();
    }


    public boolean isRecoDataFormSIDisabled() {
        // We allow to alter RecoData RecoForm only at Validation after Recognition step
        return !isRecoDataEditable() || getRecoDoc().getStep() != RECO_PROCESS_STEP_RECO_VALIDATOR;
    }


    public boolean isRecoDataPageSIDisabled() {
        // We allow to alter RecoData pages only at Validation after Recognition step
        return isRecoDataFormSIDisabled();
    }


    /**
     * Defines whether RecoData is editable, the following rules apply:<br>
     * 1) Whole document should be in the edit mode<br>
     * 2) RecoData should not be already validated<br>
     *
     * @return
     */
    public boolean isRecoDataEditable() {
        return isModeEdit() && getRecoData() != null && !getRecoData().isValidated();
    }


    /**
     * Returns TRUE in case the current RecoForm is the automatic form used for the document parsing on-the-fly (demo mode)
     *
     * @return
     */
    public boolean isAutoGeneratedForm() {
        return RecoForm.AUTOMATIC_FORM_ID.equals(getRecoForm().getId());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    private Workflow getRecoDoc() {
        return _workflowController.getCurrentWF();
    }


    /**
     * Returns currently selected RecoData (if any available). In case no RecoData is available or this is a legacy document -
     * returns an empty RecoData
     *
     * @return
     */
    public RecoData getRecoData() {
        if (_recoDataList.isEmpty()) {
            return null;
        } else {
            return _recoDataList.get(_recoDataIdx);
        }
    }


    public boolean isRecoFormAltered() {
        return getRecoData().isRecoFormAltered(getRecoFormResult());
    }


    /**
     * Returns RecoForm DTO for the current RecoData
     *
     * @return
     */
    public RecoFormDto getRecoForm() {
        if (getRecoData() == null) {
            // No RecoData available - means no RecoForms were matched
            return null;
        }
        return getRecoData().getRecoForm();
    }


    public String getRecoFormName() {
        if (RecoForm.AUTOMATIC_FORM_ID.equals(getRecoForm().getId())) {
            return _localeContext.getLabel("pDocument.reco.autoGeneratedFormName");
        }
        return getRecoForm().getName();
    }


    /**
     * Returns RecoFormResult for the current RecoWorkflow or RecoData or null (also handles legacy RecoDocs)
     *
     * @return
     */
    public RecoFormResult getRecoFormResult() {
        if (_recoFormResult == null) {
            _recoFormResult = _recoWorkflowService.getRecoFormResult(getRecoData());
        }
        return _recoFormResult;
    }


    public RecoPageResult getRecoPageResult() {
        if (getRecoFormResult() == null) {
            // This document was not processed by RecoService yet
            return null;
        }

        int pageOffset = 0;
        if (_previewFile.getDbFileRef().getTags().contains(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name())) {
            // If recoData contains Preprocessed Final file - it may have additional offset
            RecoFileMeta preprocessedFinalMeta = RecoFileMeta.fromJson(_previewFile.getMetadataAsJson());
            pageOffset = preprocessedFinalMeta.getPageIdxOffset();
        }
        int requestedPage = getPageIndex() + pageOffset;
        return getRecoFormResult().getPages().stream().filter(recoPageResult -> recoPageResult.getPageIdx() == requestedPage).findFirst().orElse(null);
    }


    /**
     * Returns index of the provided CustomField on the page, where it is located in
     * current RecoFormResult
     *
     * @param cf
     * @return
     */
    public int getFieldOnPageIdx(CustomField cf) {
        if (cf == null) {
            return -1;
        }

        RecoFormResult recoFormResult = getRecoFormResult();
        if (recoFormResult == null) {
            return -1;
        }
        RecoPageResult pageResult = recoFormResult.getPages().stream().filter(page -> page.getPageIdx() == cf.getRecoProp().getPageIdx()).findFirst().orElse(null);
        if (pageResult == null) {
            return -1;
        }

        RecoFieldResult recoFieldResult = RecoWorkflowService.getRecoFieldResultByCustomField(recoFormResult, cf, _configContext.getDefaultLang());
        if (recoFieldResult == null) {
            return -1;
        }

        return pageResult.getFields().indexOf(recoFieldResult);
    }


    /**
     * Returns index of the provided CustomField on the page, where it is located in
     * current RecoFormResult
     *
     * @param cf
     * @param rowIdx
     * @param cellIdx
     * @return
     */
    public int getFieldOnPageIdx(CustomField cf, int rowIdx, int cellIdx) {
        if (cf == null) {
            return -1;
        }
        return RecoWorkflowService.getRecoFieldTableIdxOnPageByImageCustomField(getRecoData(), getRecoFormResult(), cf, _configContext.getDefaultLang(), rowIdx, cellIdx);
    }


    /**
     * Required for UI. Checks, if fields focus enabled by checking, if it was matched on the page
     *
     * @param cf
     * @return
     */
    public boolean isFieldFocusEnabled(CustomField cf) {
        if (cf == null) {
            return false;
        }

        RecoFormResult recoFormResult = getRecoFormResult();
        if (recoFormResult == null) {
            return false;
        }
        RecoPageResult pageResult = recoFormResult.getPages().stream().filter(page -> page.getPageIdx() == cf.getRecoProp().getPageIdx()).findFirst().orElse(null);
        if (pageResult == null) {
            return false;
        }

        RecoFieldResult recoFieldResult = RecoWorkflowService.getRecoFieldResultByCustomField(recoFormResult, cf, _configContext.getDefaultLang());
        if (recoFieldResult == null) {
            return false;
        }

        return recoFieldResult.getRegion().getWidth() != 0 && recoFieldResult.getRegion().getHeight() != 0;
    }


    /**
     * Returns RecoData selection list for the Workflow
     *
     * @return
     */
    public List<SelectItem> getRecoDataListSI() {
        List<SelectItem> result = new ArrayList<>();
        for (int i = 0; i < _recoDataList.size(); i++) {
            // Value is adjusted by converter, label is to be adjusted manually
            result.add(new SelectItem(i, format("{0,number,#}: {1}", i + 1, _recoDataList.get(i).getRecoDataTitle())));
        }
        if (_recoDataList.isEmpty()) {
            result.add(new SelectItem(0, _localeContext.getLabel("recoData.noRecordsAvailable"), null, true));
        }
        return result;
    }


    /**
     * Prepares list of Select Items for page Start/End selection
     *
     * @return
     */
    public List<SelectItem> getRecoDataStartEndPageIdxSI() {
        List<SelectItem> result = new ArrayList<>();
        for (int pageIdx = 0; pageIdx < getSourceFilePageCount(); pageIdx++) {
            SelectItem si = new SelectItem(pageIdx, Integer.toString(pageIdx + 1)); // Label on UI pageIdx is 1-based (handled by converter), whereas in the code pageIdx is 0-based
            for (int i = 0; i < _recoDataList.size(); i++) {
                if (_recoDataList.get(i).isValidated() && pageIdx >= _recoDataList.get(i).getPageIdxStart() && pageIdx <= _recoDataList.get(i).getPageIdxEnd()) {
                    si.setDisabled(true);
                }
            }
            result.add(si);
        }
        return result;
    }


    /**
     * Performs validation of the submitted pages for the RecoData
     *
     * @param fc
     * @param startPgComponent
     * @param startPgSubmValue
     */
    public void recoDataPagesIdxValidator(FacesContext fc, UIComponent startPgComponent, Object startPgSubmValue) {
        if (JsfUtils.getRequestParamMap() != null && Boolean.parseBoolean(JsfUtils.getRequestParamMap().get("skipValidation"))) {
            // Skip validation action was requested - nothing to validate
            return;
        }

        UIInput startPgInput = null;
        UIInput endPgInput = null;
        try {
            // Get components
            startPgInput = (UIInput) startPgComponent;
            endPgInput = (UIInput) startPgComponent.getAttributes().get("input_endPage");

            String endPgSubmValue = null;
            if (isNotBlank((String) endPgInput.getSubmittedValue())) {
                // Read end page submitted value which sometimes may come as blank string ("")
                endPgSubmValue = (String) endPgInput.getSubmittedValue();
            }

            // Check if any pages left for selection
            if (startPgSubmValue == null && endPgSubmValue == null) {
                // This can happen when on previous validation whole pages range was chosen
                endPgInput.setValid(false);
                JsfUtils.throwValidationException(_localeContext.getLabel("msg.doc.recoDataStartEndPageNotSelectable"));
            } else if (startPgSubmValue == null) {
                endPgInput.setValid(false);
                JsfUtils.throwValidationException(_localeContext.getLabel("msg.doc.recoDataStartPageNotSelectable"));
            } else if (endPgSubmValue == null) {
                endPgInput.setValid(false);
                JsfUtils.throwValidationException(_localeContext.getLabel("msg.doc.recoDataEndPageNotSelectable"));
            }

            // Get Start Page Idx
            int startPgIdxSubm = (Integer) startPgSubmValue; // The value comes already converted by defined Converter

            // Get End Page Idx
            int endPgIdxSubm = (Integer) endPgInput.getConverter().getAsObject(fc, endPgInput, endPgSubmValue); // We need to manually get Converted value

            if (endPgIdxSubm < startPgIdxSubm) {
                endPgInput.setValid(false);
                JsfUtils.throwValidationException(_localeContext.getLabel("msg.doc.recoDataStartLessThanEnd"));
            }

            for (RecoData recoDataItem : _recoDataList) {
                if (getRecoData().equals(recoDataItem)) {
                    continue; // Skip self
                }

                if (!recoDataItem.isValidated()) {
                    continue; // Skip unvalidated items
                }

                if (Range.of(startPgIdxSubm, endPgIdxSubm).isOverlappedBy(Range.of(recoDataItem.getPageIdxStart(), recoDataItem.getPageIdxEnd()))) {
                    endPgInput.setValid(false);
                    JsfUtils.throwValidationException(_localeContext.getLabel("msg.doc.recoDataPageRangeOverlapsWith") + ": " + recoDataItem.getRecoDataTitle());
                }
            }
        } catch (ValidatorException ve) {
            throw ve;
        } catch (Exception e) {
            LOG.warn("RecoData validation error", e);
            LogHelper.setEvent("id00234", e, null, new LogExt(getRecoDoc()));
            if (endPgInput != null) {
                endPgInput.setValid(false);
            }
            JsfUtils.throwValidationException(_localeContext.getLabel("msg.error.validation") + ": " + e.toString());
        }
    }


    /**
     * Returns total page count in the source document
     *
     * @return
     */
    private int getSourceFilePageCount() {
        if (_sourceFilePageCount == -1) {
            DbFile sourceFile = _recoWorkflowService.getPreviewFiles(getRecoDoc(), null)
                    .stream()
                    .filter(fileItem -> fileItem.getDbFileRef().getTags().contains(Workflow.FILE_TAG.IDP_SOURCE.name()))
                    .findFirst()
                    .orElse(null);
            if (sourceFile == null) {
                _sourceFilePageCount = 0;
            } else {
                _sourceFilePageCount = RecoFileMeta.fromJson(sourceFile.getMetadataAsJson()).getPageCount();
            }
        }
        return _sourceFilePageCount;
    }


    /**
     * Resets bean on RecoData change
     */
    private void resetOnRecoDataChange() {
        _recoFormResult = null; // We need to reset RecoFormResult so it will be re-loaded for the new page
        _pageIndexList.clear(); // Clear page indexes list so it will be re-initialized on next request
        if (_previewFile.getDbFileRef().getTags().contains(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name())) {
            // PREPROCESSED_FINAL file is currently opened for preview - switch to page with index 0
            setPageIndex(0);
        } else {
            // PREPROCESSED or SOURCE file opened for preview - switch to page, where RecoData starts
            if (getRecoData() == null) {
                // In case RecoData is missing - show preview from first page
                setPageIndex(0);
            } else {
                // In case IDP_PREPROCESSED is to be used and RecoData present - it contains all pages from the source file, as such - use RecoData start page as page index
                setPageIndex(getRecoData().getPageIdxStart());
            }
        }
    }


    public String getPageImageUrlPreviewId() {
        if (_previewFile == null) {
            return "#";
        }
        // Load image file for preview on UI
        try (DbFileDownloadStream downloadStream = DaoUtils.getFileAwareDao(_previewFile.getDbFileRef()).getFilesDao().getFileDownloadStream(_previewFile.getDbFileRef())) {
            _previewFileIsPdf = PdfUtils.isPdf(downloadStream.getDbFile().getFileName());
            PreviewItem previewItem = new PreviewItem(_previewFile, getRecoDoc().getAuthor(), Preview.TYPE_DOC_AS_IMG, false, PdfUtils.IMAGE_RENDER_WEB_DPI, null);
            return _userContext.putPreviewItem(previewItem);
        } catch (Exception ex) {
            LogHelper.setEvent("id00156", ex, ex.toString(), new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"), ex.toString());
        }
        return "#";
    }


    /**
     * Exports RecoWorkflow recognized file attachment content as PDF document
     */
    public void btnExportAsPdf() {
        try {
            File textDoc = _recoWorkflowService.generateTextPdfFile(getRecoDoc());
            if (textDoc == null) {
                JsfUtils.addWarnMessage(_localeContext.getLabel("pDocument.reco.noOcrDataAvailable"));
                return;
            }
            PrimeFaces.current().ajax().addCallbackParam("exportUrl", "/api/export/download?id=".concat(_taskService.setExportDownloadTask(textDoc.getAbsolutePath())));
        } catch (Exception e) {
            LOG.error("RecoWorkflow[{}]: Error processing OCR data download", getRecoDoc().getId(), e);
            LogHelper.setEvent("id00354", e, null, new LogExt(getRecoDoc()));
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
    }


    public String getFieldImageUrlPreviewId(CustomField cf) {
        // Load image file for preview on UI
        DbFile imageFile = _recoWorkflowService.getRecoFieldImage(getRecoData(), cf);
        if (imageFile == null) {
            return "#";
        }
        try {
            PreviewItem previewItem = new PreviewItem(imageFile, getRecoDoc().getAuthor(), Preview.TYPE_DOC_AS_IMG, false, PdfUtils.IMAGE_RENDER_WEB_DPI, null);
            return _userContext.putPreviewItem(previewItem);
        } catch (Exception ex) {
            LogHelper.setEvent("id00356", ex, ex.toString(), new LogExt(getRecoDoc()));
        }
        return "#";
    }


    public DefaultStreamedContent getFieldImagePreviewOnEdit() {
        if (_selectedCFArea == null || _selectedCFArea.getArea() == 0) {
            return null;
        }
        try (InputStream is = DaoUtils.getFileAwareDao(_previewFile.getDbFileRef()).getFilesDao().getFileDownloadStream(_previewFile.getDbFileRef()); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage currentPageBufImg = PdfUtils.renderPageToImage(is, _pageIndex, PdfUtils.IMAGE_RENDER_BEST_DPI);
            RecoFileMeta fileMeta = RecoFileMeta.fromJson(_previewFile.getMetadataAsJson());
            Rectangle pageRect = ImageProcessor.getDimensionsFromString(fileMeta.getPagesDimensions().get(_pageIndex));// Original page rect
            Rectangle scaledPageRect = pageRect.getScaledClone(getFieldImageRenderScale()); // Page rect with applied DPI
            ImageProcessor.saveToJpg(ImageProcessor.getSubimage(currentPageBufImg, _selectedCFArea.getScaledClone(getFieldImageRenderScale()).createIntersection(scaledPageRect)), baos, 0.9f);
            return DefaultStreamedContent.builder()
                    .contentType(MimeType.IMAGE_JPEG)
                    .contentLength((long) baos.size())
                    .stream(() -> new ByteArrayInputStream(baos.toByteArray())).build();
        } catch (Exception ex) {
            LOG.error("Error reading page fragment, docId={}, recoData={}, page={}, area={}", getRecoDoc().getId(), getRecoData().getId(), getPageIndex(), _selectedCFArea, ex);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }
        return null;
    }


    private float getFieldImageRenderScale() {
        return ((float) PdfUtils.IMAGE_RENDER_BEST_DPI) / ((float) PdfUtils.IMAGE_RENDER_DEFAULT_DPI);
    }


    public String getCellImageUrlPreviewId(CustomField cf, int rowIdx, int cellIdx) {
        // Load image file for preview on UI
        DbFile imageFile = _recoWorkflowService.getRecoFieldCellImage(getRecoData(), cf, rowIdx, cellIdx);
        if (imageFile == null) {
            return "#";
        }
        try {
            PreviewItem previewItem = new PreviewItem(imageFile, getRecoDoc().getAuthor(), Preview.TYPE_DOC_AS_IMG, false, PdfUtils.IMAGE_RENDER_WEB_DPI, null);
            return _userContext.putPreviewItem(previewItem);
        } catch (Exception ex) {
            LogHelper.setEvent("id00357", ex, ex.toString(), new LogExt(getRecoDoc()));
        }
        return "#";
    }


    /**
     * Updates number of pages for preview pagination on UI
     */
    private void updatePageIndexList() {
        if (JsfUtils.getFacesContext().getCurrentPhaseId() != PhaseId.RENDER_RESPONSE) {
            // We perform this logic only during RENDER_RESPONSE
            return;
        }

        if (_requestMap.containsKey("PAGE_INDEX_UPDATE_DONE")) {
            // Nothing to do - already refreshed per request
            return;
        }

        // Proceed with page index list update
        try {
            _pageIndexList.clear();
            initPreviewFiles();

            if (_previewFile == null) {
                // This should never happen - or only for the broken documents
                return;
            }

            // Fill the page index list to be shown on the UI (for showing paginator mainly)
            // PREPROCESSED_FINAL may have reduced number of pages in coparison to SOURCE & PREPROCESSED files
            try (DbFileDownloadStream downloadStream = DaoUtils.getFileAwareDao(_previewFile.getDbFileRef()).getFilesDao().getFileDownloadStream(_previewFile.getDbFileRef())) {

                int pageCount = 1; // default value in case pages count couldn't be retrieved

                // RecoWorkflow on DRAFT State requires special handling as at this state its attachment is still not pre-processed (as such - do not have metadata)
                if (getRecoDoc().getState() == STATE_ID_DRAFT) {
                    if (PdfUtils.isPdf(downloadStream.getDbFile().getFileName())) {
                        pageCount = PdfUtils.getNumberOfPages(downloadStream);
                    } else {
                        pageCount = ImageProcessor.getNumberOfPages(downloadStream);
                    }
                } else {
                    if (downloadStream.getDbFile().getMetadata().get(RecoFileMeta._PAGE_COUNT) instanceof Number) {
                        // Int32 from MongoDB returned as java.math.BigDecimal
                        // displayFile.getRecoFileMeta().getPageCount() can be used here but currently we try to eliminate usage of deprecated classes
                        pageCount = ((Number) downloadStream.getDbFile().getMetadata().get(RecoFileMeta._PAGE_COUNT)).intValue();
                    } else {
                        LOG.warn("Workflow[{}] can not read page count for the {}. Using default page count = 1", getRecoDoc().getId(), downloadStream.getDbFile().getDbFileRef());
                    }
                }

                for (int i = 0; i < pageCount; i++) {
                    _pageIndexList.add(i);
                }
            }

            // Update page index - in case switching from SOURCE to PREPROCESSED file is made, page with requested index may be missing
            if (isPreviewPreprocessed() && !getPageIndexList().contains(getPageIndex())) {
                if (getPageIndex() >= getRecoData().getPageIdxStart() && getPageIndex() <= getRecoData().getPageIdxEnd()) {
                    // PREPROCESSED_FINAL file contains page, that was previously selected on source file - switch to it
                    setPageIndex(getPageIndex() - getRecoData().getPageIdxStart());
                } else {
                    // Requested page is missing from PREPROCESSED_FINAL file - switch to first page of file
                    setPageIndex(0);
                }
            }

        } catch (Exception ex) {
            LOG.error("Workflow[{}] number of pages for preview initialization error", getRecoDoc().getId(), ex);
            LogHelper.setEvent("id00151", ex, ex.toString(), new LogExt(getRecoDoc()));
        } finally {
            _requestMap.put("PAGE_INDEX_UPDATE_DONE", true);
        }
    }


    private void initPreviewFiles() {
        _previewFile = null;

        // Load all available attachments once and cache the list of them in the View Bean
        _previewFileList = _recoWorkflowService.getPreviewFiles(getRecoDoc(), getRecoData());

        // Load image file for preview on UI
        if (_previewPreprocessed) {
            _previewFile = _previewFileList.stream().filter(fileItem -> fileItem.getDbFileRef().getTags().contains(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name())).findFirst().orElse(null);

            if (_previewFile != null) {
                // In case IDP_PREPROCESSED_FINAL is present - it contains extracted pages only for the current RecoData, thus always start from page index 0
            } else {
                // In case Preprocessed Final version is not present - load standard preprocessed file
                _previewFile = _previewFileList.stream().filter(fileItem -> fileItem.getDbFileRef().getTags().contains(Workflow.FILE_TAG.IDP_PREPROCESSED.name())).findFirst().orElse(null);
            }

            if (_previewFile == null) {
                // In case both files (PREPROCESSED and PREPROCESSED_FINAL) are not available - show SOURCE file
                _previewPreprocessed = false;
            }
        }

        if (_previewPreprocessed == false) {
            // In case both files (PREPROCESSED and PREPROCESSED_FINAL) are not available - show SOURCE file
            _previewFile = _previewFileList.stream().filter(fileItem -> fileItem.getDbFileRef().getTags().contains(Workflow.FILE_TAG.IDP_SOURCE.name())).findFirst().orElse(null);
        }
    }


    public CustomField getSelectedCF() {
        return _selectedCF;
    }


    public Rectangle getSelectedCFArea() {
        return _selectedCFArea;
    }


    public void setSelectedCFArea(Rectangle customFieldArea) {
        _selectedCFArea = customFieldArea;
    }


    public boolean isTableCellEditMode(CustomField cf, int row, int col) {
        return Objects.equals(_selectedCF, cf) && _selectedCFRow == row && _selectedCFCol == col;
    }


    public int getTableImageCellPage(CustomField cf, int row, int col) {
        return RecoWorkflowService.getRecoFieldImageCellPageIdByCustomField(getRecoData(), getRecoFormResult(), cf, _configContext.getDefaultLang(), row, col);
    }


    public int getTableImageCellRowIdxOnPage(CustomField cf, int row, int col) {
        return RecoWorkflowService.getRecoFieldImageCellIdxOnPageByCustomField(getRecoData(), getRecoFormResult(), cf, _configContext.getDefaultLang(), row, col);
    }


    public boolean isTableCellEditable(CustomField cf, int row, int col) {
        return getTableImageCellPage(cf, row, col) == _pageIndex;
    }


    public List<RecoData> getRecoDataList() {
        return _recoDataList;
    }


    public int getRecoDataIdx() {
        return _recoDataIdx;
    }


    public void setRecoDataIdx(int recoDataIdx) {
        _recoDataIdx = recoDataIdx;
        resetOnRecoDataChange();
    }


    public void pageIndexEvent(PageEvent pageEvent) {
        saveFieldEdit();
        setPageIndex(pageEvent.getPage());
    }


    public void setPageIndex(int pageIdx) {
        _pageIndex = pageIdx;
    }


    public int getPageIndex() {
        return _pageIndex;
    }


    public List<Integer> getPageIndexList() {
        if (_pageIndexList.isEmpty()) {
            updatePageIndexList();
        }
        return _pageIndexList;
    }


    public boolean isPreviewPreprocessed() {
        return _previewPreprocessed;
    }


    public void setPreviewPreprocessed(boolean previewPreprocessed) {
        if (_previewPreprocessed == previewPreprocessed) {
            return;
        }
        _pageIndexList.clear(); // Clear page indexes list so it will be re-initialized on next request
        RecoFileMeta preprocessedFinalMeta = null;
        if (getRecoData() != null) {
            boolean customPreprocessedFile = !getRecoData().getFilesByTag(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name()).isEmpty();
            if (customPreprocessedFile) {
                preprocessedFinalMeta = RecoFileMeta.fromJson(_recoDataDao.getFilesDao().getFile(null, getRecoData().getFileByTag(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name())).getMetadataAsJson());
            }
        }
        if (!previewPreprocessed) {
            // Switching from previewing PREPROCESSED file to SOURCE file
            if (preprocessedFinalMeta != null) {
                // Previously opened preview file was PREPROCESSED_FINAL - adjust page index so we stay on the same page
                setPageIndex(getPageIndex() + preprocessedFinalMeta.getPageIdxOffset());
            }
        } else {
            // Switching from previewing SOURCE file to PREPROCESSED file
            if (preprocessedFinalMeta != null) {
                // Previously opened preview file was PREPROCESSED_FINAL - adjust page index so we stay on the same page
                setPageIndex(getPageIndex() - preprocessedFinalMeta.getPageIdxOffset());
            }
        }
        _previewPreprocessed = previewPreprocessed;
    }


    public boolean isShowAllFields() {
        return _showAllFields || !isPerPageFieldsViewAvailable();
    }


    public boolean isPerPageFieldsViewAvailable() {
        if (getRecoData() == null) {
            return false;
        }
        return !getRecoData().isRecoFormAltered(getRecoFormResult());
    }


    public boolean isFieldOnPage(CustomField cf) {
        int pageOffset = _previewFile.getDbFileRef().getTags().contains(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name())
                ? (_recoDataList.isEmpty() ? 0 : _recoDataList.get(_recoDataIdx).getPageIdxStart())
                : 0;

        int actualPageIdx = getPageIndex() + pageOffset;

        if (!cf.isTypeRecoTable()) {
            return cf.getRecoProp().getPageIdx() == actualPageIdx;
        }
        RecoField tableField = RecoWorkflowService.getRecoFieldByCustomField(getRecoFormResult().getRecoForm(), cf, _localeContext.getLangSystemCode());
        List<RecoFieldResult> tableFieldResults = getRecoFormResult().getResultByRecoField(tableField);
        int firstTablePage = getRecoFormResult().getPages().stream()
                .filter(page -> !Collections.disjoint(page.getFields(), tableFieldResults))
                .mapToInt(RecoPageResult::getPageIdx)
                .min().orElse(cf.getRecoProp().getPageIdx()); // Fallback to page, set for RecoFieldResult (most likedly 0), when unknown - should happen only after RecoForm change, when we don't know actual field page
        int lastTablePage = getRecoFormResult().getPages().stream()
                .filter(page -> !Collections.disjoint(page.getFields(), tableFieldResults))
                .mapToInt(RecoPageResult::getPageIdx)
                .max().orElse(cf.getRecoProp().getPageIdx()); // Fallback to page, set for RecoFieldResult (most likedly 0), when unknown - should happen only after RecoForm change, when we don't know actual field page
        return firstTablePage <= actualPageIdx && lastTablePage >= actualPageIdx;
    }


    public void setShowAllFields(boolean showAllFields) {
        _showAllFields = showAllFields;
    }


    private double getImageDpiScale() {
        if (_previewFileIsPdf) {
            return ((double) PdfUtils.IMAGE_RENDER_WEB_DPI) / ((double) PdfUtils.IMAGE_RENDER_DEFAULT_DPI);
        } else {
            return 1.0;
        }
    }


    public RecoFileMeta getSourceFileMeta() {
        if (_previewFileList == null) {
            _previewFileList = _recoWorkflowService.getPreviewFiles(getRecoDoc(), getRecoData());
        }
        DbFile sourceFile = _previewFileList.stream().filter(fileItem -> fileItem.getDbFileRef().getTags().contains(Workflow.FILE_TAG.IDP_SOURCE.name())).findFirst().orElse(null);
        if (sourceFile == null) {
            return null;
        }

        return RecoFileMeta.fromJson(sourceFile.getMetadataAsJson());
    }


    public String getFrdData() {
        if (_previewFile == null) {
            // This may happen in case of exception during RecoWorkflow creation, like BpmNameNotResolvedException,
            // what leads to the scenario that frd1 was created but no source file got attached
            return JsonbUtils.createObjectBuilder().build().toString();
        }

        int pageOffset = _previewFile.getDbFileRef().getTags().contains(RecoData.FILE_TAG.IDP_PREPROCESSED_FINAL.name())
                ? ((_recoDataList.isEmpty() ? 0 : _recoDataList.get(_recoDataIdx).getPageIdxStart()))
                : 0;

        return JsonbUtils.createObjectBuilder()
                .add("previewPreprocessed", _previewPreprocessed)
                .add("recoFormAltered", _recoWorkflowService.isRecoFormAltered(getRecoData()))
                .add("imageDpiScale", getImageDpiScale())
                .add("pageIdx", _pageIndex + pageOffset)
                .add("pfPageIdx", _pageIndex) // Page index in preview file
                .add("defaultFieldRect", Rectangle.RECT_DEFAULT_STR)
                .add("modeEdit", isModeEdit())
                .add("hideFields", getRecoFormResult() != null && getRecoFormResult().getRecoForm().getType() == RecoForm.TYPE_AIBASED)
                .add("recoDataFormType", getRecoFormResult() == null ? -1 : getRecoFormResult().getRecoForm().getType())
                .build().toString();
    }


    public boolean isFieldNotValid(int cfIdx, int rowIdx, int colIdx) {
        return _customFieldsNotValid.stream().anyMatch(notMatchedField -> notMatchedField.getLeft() == cfIdx && notMatchedField.getMiddle() == rowIdx && notMatchedField.getRight() == colIdx);
    }


    /**
     * Returns list of attachments for Workflow and selected RecoData. Workflows of RecoWorkflow type should show only 1st file attachment on UI
     *
     * @return
     */
    public List<DbFile> getFiles() {
        List<DbFile> result = new ArrayList<>();
        if (_pageController.isModeDebug()) {
            // In debug mode - show all attachments
            result.addAll(_workflowDao.getFilesDao().getFiles(null, getRecoDoc().getFiles()));
            if (getRecoData() != null) {
                result.addAll(_recoDataDao.getFilesDao().getFiles(null, getRecoData().getFiles()));
            }
        } else if (!getRecoDoc().getFiles().isEmpty()) {
            // Add only first attachment what is the original document
            result.add(_workflowDao.getFilesDao().getFile(null, getRecoDoc().getFiles().get(0)));
        }
        return result;
    }


    //
    // Getters for UI effects & styling & markup --------------------------------------------------
    //
    public String getConfidenceFieldStyle(CustomField cf) {
        if (!cf.isRequired() && cf.getRecoProp().isEmpty()) {
            return "";
        } else if (getRecoDoc().getState() == STATE_ID_EXEC
                && cf.getRecoProp().getConfidenceMin() < cf.getRecoProp().getConfThreshold() || cf.getRecoProp().isEmpty()) {
            return "color: var(--p-message-error-color);";
        }
        return "";
    }


    public String getConfidenceCellStyle(CustomField cf, int rowIndex, int cellIndex) {
        CustomField columnField = cf.getFields().get(cellIndex);
        List<Integer> columnConf = cf.getRecoProp().getConfidenceTable().get(rowIndex).get(cellIndex);
        if (!columnField.isRequired() && columnConf.isEmpty()) {
            return "";
        } else if (getRecoDoc().getState() == STATE_ID_EXEC
                && columnConf.stream().min(Integer::compare).orElse(0) < columnField.getRecoProp().getConfThreshold()) {
            return "color: var(--p-message-error-color);";
        }
        return "";
    }


    public String getConfidenceOpMarkStyle(CustomField cf, int opMarkIdx) {
        if (getRecoDoc().getState() == STATE_ID_EXEC
                && opMarkIdx < cf.getRecoProp().getConfidenceOmr().size()
                && cf.getRecoProp().getConfidenceOmr().get(opMarkIdx) > 0
                && cf.getRecoProp().getConfidenceOmr().get(opMarkIdx) < cf.getRecoProp().getConfThreshold()) {
            return "color: var(--p-message-error-color);";
        }
        return "";
    }


    //
    // RecoForm change logic ----------------------------------------------------------------------
    //
    public void recoFormChangeListener() {
        RecoForm recoForm = _recoFormService.findById(getRecoData().getRecoForm().getId());

        if (recoForm == null) {
            // This actually should never happen
            LOG.warn("RecoForm was not found by _id={}. This should never happen, please contact Support in case you see this log record", getRecoData().getRecoForm().getId());
            return;
        }

        resetImageFieldEdit();

        // Remember updated RecoForm
        getRecoData().setRecoForm(new RecoFormDto(recoForm));

        // Update custom fields
        updateRecoDocCustomFields(recoForm);
    }


    private void updateRecoDocCustomFields(RecoForm recoForm) {
        // Clean existing custom fields from previous recognition step
        getRecoData().getCfDef().clear();
        getRecoData().getCfData().clear();

        // Add empty custom fields for all fields, defined in target recoForm
        recoForm.getFields().forEach(recoField -> {
            // Init Workflow CustomField
            CustomField cf = new CustomField();
            _recoWorkflowService.retrieveAndSetCfDefFromRecoField(recoField, getRecoDoc(), cf);

            // Add CustomFiled to the Workflow CustomFields configuration
            getRecoData().getCfDef().add(cf);

            // Set blank values for newly added field
            Object recoValue = _recoWorkflowService.initAndSetEmptyCfDataFromRecoField(recoField, null, cf);

            // Save Field value within Workflow
            getRecoData().getCfData().put(getRecoData().getCfDsFieldName(cf), recoValue);
        });
    }
}
