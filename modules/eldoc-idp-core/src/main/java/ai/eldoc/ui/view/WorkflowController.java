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
import ai.eldoc.bpm.*;
import ai.eldoc.bpm.Process;
import ai.eldoc.constants.Pages;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.dao.WorkflowTaskDao;
import ai.eldoc.data.WorkflowLazyQuery;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.service.LogHelper;
import ai.eldoc.service.WorkflowService;
import ai.eldoc.ui.ResolutionManager;
import ai.eldoc.ui.component.ProcBuilder;
import ai.eldoc.util.JsfUtils;
import ai.eldoc.util.LabelUtils;
import ai.eldoc.workflow.WorkflowUtils;
import jakarta.faces.annotation.RequestMap;
import jakarta.faces.annotation.RequestParameterMap;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.validator.ValidatorException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.Strings;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import static ai.eldoc.bpm.BpmAclConstants.*;
import static ai.eldoc.bpm.BpmConstants.*;
import static ai.eldoc.workflow.WorkflowUtils.getProcessNameLabelId;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Workflow UI Controller
 */
@Named
@ViewScoped
public class WorkflowController extends AbstractUIController implements Serializable, ResolutionManager {

    @Serial
    private static final long serialVersionUID = 7560338857984449521L;
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowController.class);

    private static final String CACHE_KEY_WF_REFBY = "__wf_referencedBy";

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private WorkflowTaskDao _workflowTaskDao;

    @Inject
    private WorkflowService _workflowService;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private PageController _pageController;

    @Inject
    private UserContext _userContext;

    @Inject
    private WorkflowFilesController _workflowFilesController;

    @Inject
    @RequestParameterMap
    private Map<String, String> _requestParamMap;

    @Inject
    @RequestMap
    private Map<String, Object> _requestMap;

    private Workflow _currentWF;
    private WorkflowTask _currentWFTask;
    private WorkflowTask _currentWFTaskAnnex;

    private Set<String> _docACL;

    private Integer _iterationSelector = null;
    private final ArrayList<SelectItem> _iterationSelectorSI = new ArrayList<>();

    private final ArrayList<Integer> _tabsVisible = new ArrayList<>();
    private Integer _tabStateId = null;
    private ProcessMonitoringData _monitoringData;
    private int _uiActiveTabIndex = 0;
    private String _viewParamId; // url param: id - stands for document id
    private String _viewParamV; // url param v - stands for view from which document was opened

    private static final Integer TAB_MONITORING = -10;
    private static final String URL_PARAM_STATE = "state";
    private static final String URL_PARAM_ITER = "iter";
    private static final String URL_PARAM_TAB_MONITORING = "mon";

    private Boolean _isSignStateUsersEmptyInitially = null;


    //
    // Methods -----------------------------------------------------------------------------------
    //


    /**
     * Opens and Inits Workflow on document open
     *
     * @return
     */
    public String load() {
        try {
            if (_viewParamId != null && BasicModel.ID_WEB_PATTERN.matcher(_viewParamId).matches()) {
                _currentWF = _workflowDao.findByWebId(_viewParamId, SecurityUtils.getSubjectAclContext());
                if (_currentWF == null && BasicModel.ID_PATTERN.matcher(_viewParamId).matches()) {
                    // in case id represents ObjectId string value
                    _currentWF = _workflowDao.find(_viewParamId, SecurityUtils.getSubjectAclContext());
                }
            }

            if (_currentWF == null) {
                JsfUtils.addWarnMessage(_localeContext.getLabel("msg.doc.notFoundOrNoAccess"));
                JsfUtils.keepMessagesOnRedirect();
                return JsfUtils.getNavigationTarget(Pages.PAGE_MAIN);
            }

            if (isOpenInEditMode(_viewParamId)) {
                setModeEdit();
            }

            _currentWFTask = _workflowService.workflowTaskCreateAndInitForParticipant(_currentWF, null, _userContext.getAclContext());
            if (_currentWFTask == null) {
                // just create an empty docResponse to be here "on-duty", can happen, for example, when Workflow in DRAFT, or process participant opens document after processing it
                _currentWFTask = WorkflowTask.createFor(_currentWF, _userContext.getId());
            }

            refreshDocACL(); // refreshes docACL
            initDocResponseAnnexDS(); //inits / creates new DS for WorkflowTask with Annex
            refreshTabsVisible();
            initActiveTabIndex();
            _pageController.setPageTitlePrefix((isBlank(getWorkflowName()) ? _localeContext.getLabel("general.workflow") : getWorkflowName()) + ' ' + _currentWF.getIdWeb());
            return null;
        } catch (Exception e) {
            LOG.error("Workflow[{}] loading error", _viewParamId, e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
            JsfUtils.keepMessagesOnRedirect();
            return JsfUtils.getNavigationTarget(Pages.PAGE_MAIN);
        }
    }


    private void initActiveTabIndex() {
        String reqStateParam = _requestParamMap.get(URL_PARAM_STATE);
        Boolean tabMonitoring = Boolean.valueOf(_requestParamMap.get(URL_PARAM_TAB_MONITORING));
        if (tabMonitoring) {
            _uiActiveTabIndex = _tabsVisible.indexOf(TAB_MONITORING);
        } else {
            if (reqStateParam != null && reqStateParam.matches("\\d")) {
                _uiActiveTabIndex = _tabsVisible.indexOf(Integer.valueOf(reqStateParam));
            } else {
                _uiActiveTabIndex = _tabsVisible.indexOf(getState());
            }
        }
        _uiActiveTabIndex = (_uiActiveTabIndex == -1 ? 0 : _uiActiveTabIndex);
    }


    public Process getProcess() {
        return _currentWF.getProcess();
    }


    public ProcessState getProcessState(Integer stateNum) {
        return _currentWF.getProcessState(stateNum);
    }


    public Map<Integer, ProcessState> getProcessStates() {
        return _currentWF.getProcess().getStateMap();
    }


    /**
     * @inheritDoc
     */
    @Override
    public boolean resolutionAdd(WorkflowTask wfTaskFollowup) {
        return _workflowService.workflowTaskFolloupAdd(_currentWF, wfTaskFollowup, _userContext.getId());
    }


    /**
     * @inheritDoc
     */
    @Override
    public boolean resolutionRemove(WorkflowTask wfTaskFollowup) {
        return _workflowService.workflowTaskFollowupRemove(_currentWF, wfTaskFollowup, _userContext.getId());
    }


    /**
     * @inheritDoc
     */
    @Override
    public boolean getResolutionAcl(RESOL_ACTION action, WorkflowTask wfTaskFollowup) {
        boolean isRender = false;
        if (_currentWF.isRecoWF()) {
            // for RECODOC this is always disabled
            return false;
        }
        // Get the state configuration from the process
        boolean isLockSteps = getProcessState(STATE_ID_EXEC).isLockSteps();
        try {
            switch (action) {
                case RESOL_ADD:
                case RESOL_DELETE:
                    isRender = !isLockSteps && (isModeEdit()
                            || getDocACL().contains(ACTION_TO_SIGN)
                            || getDocACL().contains(ACTION_TO_EXEC)
                            || (getState().equals(STATE_ID_SIGN)
                            && WorkflowService.isNameExistOnStateStep(_currentWF, STATE_ID_SIGN, getStep(), _userContext.getAclContext().toSetSysIdGroupsUnits())));
                    break;
                case RESOL_EDIT:
                    if (wfTaskFollowup != null && Strings.CI.equals(wfTaskFollowup.getAuthor(), _userContext.getId())) {
                        isRender = !isLockSteps && (isModeEdit()
                                || getDocACL().contains(ACTION_TO_SIGN)
                                || getDocACL().contains(ACTION_TO_EXEC)
                                || (getState().equals(STATE_ID_SIGN)
                                && WorkflowService.isNameExistOnStateStep(_currentWF, STATE_ID_SIGN, getStep(), _userContext.getAclContext().toSetSysIdGroupsUnits())));
                    }
                    break;
            }
        } catch (Exception e) {
            LOG.error("Error computing WorkflowTask ACL on Workflow[{}] for action: {}", _currentWF.getId(), action, e);
        }
        return isRender;
    }


    /**
     * @inheritDoc
     */
    @Override
    public List<WorkflowTask> getResolutionList() {
        return _workflowTaskDao.getAssignedTasksList(getId(), STATE_ID_EXEC).stream().sorted(Comparator.comparingInt(WorkflowTask::getStep)).collect(Collectors.toList());
    }


    public List<String> getProcessStateNamesAll(Integer stateNum, boolean inclAuthor, boolean inclGroupMembers) {
        return _currentWF.getProcessStateNamesAll(stateNum, inclAuthor, inclGroupMembers);
    }


    public Integer getNextState() {
        return _currentWF.getNextState();
    }


    public boolean hasState(Integer stateId) {
        return _currentWF.getProcess().hasState(stateId);
    }


    /**
     * Approve State Validation hook
     *
     * @param fc
     * @param component
     * @param data
     */
    public void validatorStateApprove(FacesContext fc, UIComponent component, Object data) throws ValidatorException {
        if (getStep() != 0) {
            // No need to validate steps other than 0 (Initiator step)
            return;
        }
        if (isAction_btnToWorkAgain() && STATE_ID_APPROVE != getState()) {
            // no need to validate APPROVE state when ToWorkAgain is pressed on other states
            return;
        }
        if (!isAction_btnToWorkAgain() && STATE_ID_APPROVE != getNextState()) {
            // no need to validate any other actions in case nextState is not APPROVE
            return;
        }
        UIComponent parentDiv = component.getParent();
        if (getProcessStateNamesAll(STATE_ID_APPROVE, false, false).isEmpty()) {
            parentDiv.getAttributes().put("style", "background: #fbe3e4;"); // adds red background color instead of error style class
            JsfUtils.throwValidationException(LabelUtils.getLabel("docLayout1.stateApprove.validation", _localeContext.getLocale()));
        } else {
            parentDiv.getAttributes().put("style", "");
        }
    }


    /**
     * Sign State Validator
     *
     * @param fc
     * @param component
     * @param data
     */
    public void validatorStateSign(FacesContext fc, UIComponent component, Object data) throws ValidatorException {
        if (getStep() != 0) {
            // No need to validate steps other than 0 (Initiator step)
            return;
        }
        if (isAction_btnToWorkAgain() && STATE_ID_SIGN != getState()) {
            // no need to validate SIGN state when ToWorkAgain is pressed on other states
            return;
        }
        if (!isAction_btnToWorkAgain() && STATE_ID_SIGN != getNextState()) {
            // no need to validate any other actions in case nextState is not SIGN
            return;
        }
        UIComponent parentDiv = component.getParent();
        if (getProcessStateNamesAll(STATE_ID_SIGN, false, false).isEmpty()) {
            parentDiv.getAttributes().put("style", "background: #fbe3e4;"); // adds red background color instead of error style class
            JsfUtils.throwValidationException(LabelUtils.getLabel("docLayout1.stateSign.validation", _localeContext.getLocale()));
        } else {
            parentDiv.getAttributes().put("style", "");
        }
    }


    /**
     * Execution State Validator
     *
     * @param fc
     * @param component
     * @param data
     */
    public void validatorStateExec(FacesContext fc, UIComponent component, Object data) throws ValidatorException {
        if (getStep() != 0) {
            // No need to validate steps other than 0 (Initiator step)
            return;
        }
        if (isAction_btnToWorkAgain() && STATE_ID_EXEC != getState()) {
            // no need to validate EXEC state when ToWorkAgain is pressed on other states
            return;
        }
        if (!isAction_btnToWorkAgain() && STATE_ID_EXEC != getNextState()) {
            // no need to validate any other actions in case nextState is not EXEC
            return;
        }
        UIComponent parentDiv = component.getParent();
        if (getProcessStateNamesAll(STATE_ID_EXEC, false, false).isEmpty()) {
            parentDiv.getAttributes().put("style", "background: #fbe3e4;"); // adds red background color instead of error style class
            JsfUtils.throwValidationException(LabelUtils.getLabel("docLayout1.stateExec.validation", _localeContext.getLocale()));
        } else {
            parentDiv.getAttributes().put("style", "");
        }
    }


    /**
     * perform check whether the current event is {@link WorkflowController#btnToWorkAgain()}
     *
     * @return {@code true} or {@code false}
     */
    private boolean isAction_btnToWorkAgain() {
        return Boolean.valueOf(JsfUtils.getRequest().getParameter("action_btnToWorkAgain"));
    }


    public boolean isRenderNamePickerSignStateBuilder() {
        return (isModeEdit() || getDocACL().contains(ACTION_TO_SIGN));
    }


    public boolean isAllowApproveStateUsersAdjustment() {
        if (isModeEdit()) {
            // standard case
            return !_currentWF.getProcessState(STATE_ID_APPROVE).isLockSteps();
        } else {
            return false;
        }
    }


    public boolean isAllowSignStateUsersAdjustment() {
        if (isModeEdit()) {
            // standard case
            return !_currentWF.getProcessState(STATE_ID_SIGN).isLockSteps();
        } else if (isModeRead() && _docACL.contains(ACTION_TO_SIGN) && _currentWF.getStep() == 0 && _currentWF.isAuthor(_userContext.getId())) {
            // case when document was Approved on Approval state and about to be sent to Sign state (by Initiator) but no users were selected for the Sign state
            if (_isSignStateUsersEmptyInitially == null) {
                // we need to remember whether Sign State was empty initially or not
                _isSignStateUsersEmptyInitially = _currentWF.getProcessState(STATE_ID_SIGN).getNamesAll(false, true).isEmpty();
            }
            return _isSignStateUsersEmptyInitially;
        } else {
            // we have to init the _btnOkRendered to avoid NullPointerException on the UI
            return false;
        }
    }


    /**
     * Returns a path to the Workflow page component to be included based on the form type (dynamic or static)
     *
     * @return path to the component to be included
     */
    public String getFormTypeIncludePath() {
        return "/WEB-INF/includes/workflow/" + _currentWF.getForm() + ".xhtml";
    }


    /**
     * @return Workflow process state name
     */
    public String getDocProcStateName() {
        return LabelUtils.getLabel(getProcessNameLabelId(_currentWF.getState()), _localeContext.getLocale());
    }


    /**
     * Refreshes / updates and caches local workflow ACL<br>
     * Must be used to init workflow ACL
     */
    public void refreshDocACL() {
        if (_currentWF != null) {
            _docACL = _workflowService.getWorkflowACL(_currentWF, _userContext.getAclContext());
        }
    }


    /**
     * Return settings for Preview manager
     *
     * @param dbFile
     * @return
     */
    public boolean fileManagerAllowDelete(DbFile dbFile) {
        return isModeEdit() && _workflowService.isFilesAllowDelete(_currentWF, dbFile.getDbFileRef());
    }


    /**
     * Returns settings for the Attachment Manager UI Component: defines whether reset action as available
     *
     * @param dbFile
     * @return
     */
    public boolean fileManagerAllowReset(DbFile dbFile) {
        // The first default check: editable and on author step and has any templates
        return isModeEdit() && _workflowService.isFilesAllowReset(_currentWF, dbFile.getDbFileRef());
    }


    public boolean fileManagerAllowDownload() {
        return _workflowService.isFilesAllowDownload(_currentWF, _docACL, _userContext.getId());
    }


    public boolean refreshIterationSelector(Integer forState) {
        boolean needRefresh = false;

        if (_tabStateId == null) {
            needRefresh = true;
        } else {
            if (!_tabStateId.equals(forState)) {
                needRefresh = true;
            }
        }

        if (needRefresh) {
            _tabStateId = forState;
            _iterationSelectorSI.clear();
            ArrayList<Integer> iterations = _workflowTaskDao.getIretationsOnState(getId(), _tabStateId, null, null, TASK_STATUS_LIST_RESPONSES_TO_SHOW, null);

            // in case specific iteration requested via url
            if (_requestParamMap.containsKey(URL_PARAM_ITER)) {
                String reqIterParam = _requestParamMap.get(URL_PARAM_ITER);
                Integer reqIter = 1;
                if (reqIterParam != null && reqIterParam.matches("\\d")) {
                    reqIter = Integer.valueOf(reqIterParam);
                }
                if (reqIter == 1 || reqIter < iterations.size()) {
                    // use requested iteration only in case it is in range
                    _iterationSelector = reqIter;
                    // and we also need to init _iterationSelectorSI
                    if (iterations.size() > 1) {
                        iterations.forEach(iteration -> _iterationSelectorSI.add(new SelectItem(iteration, iteration.toString())));
                    } else {
                        _iterationSelectorSI.add(new SelectItem(_iterationSelector, _iterationSelector.toString()));
                    }
                    // we can stop and exit here now...
                    return true;
                }
            }

            if (iterations.size() > 1) {
                // in case of two and more iterations - use the latest one
                _iterationSelector = iterations.getLast();
                iterations.forEach(iteration -> _iterationSelectorSI.add(new SelectItem(iteration, iteration.toString())));
            } else {
                _iterationSelector = 1;
                _iterationSelectorSI.add(new SelectItem(_iterationSelector, _iterationSelector.toString()));
            }
        }

        return true;
    }


    /**
     * Returns documents for showing as docResponses to the current workflow
     *
     * @return
     */
    public List<WorkflowTask> getResponsesForStateAndIteration() {
        return _workflowTaskDao.findAll(getId(), _tabStateId, null, _iterationSelector, TASK_STATUS_LIST_RESPONSES_TO_SHOW, null, true);
    }


    public Date getDocResponseDateUI(WorkflowTask wfTask) {
        return wfTask.getMeta().getLastModifiedOrCreated();
    }


    /**
     * Determines whether to show author attachments in the response list to the document
     *
     * @return
     */
    public boolean isShowAuthorAttachments() {
        // the logic: in case there are any responses on the next iteration (selectedIteration + 1) means that we are currently looking at not latest iteration,
        // thus we can show author response attachments
        return _workflowTaskDao.count(getId(), _tabStateId, null, _iterationSelector + 1, TASK_STATUS_LIST_RESPONSES_TO_SHOW, null) > 0;
    }


    public void refreshTabsVisible() {
        try {
            // On Draft state return only draft tab and exit
            if (STATE_ID_DRAFT == getState()) {
                _tabsVisible.add(STATE_ID_DRAFT);
                return;
            }

            _tabsVisible.addAll(getProcess().getStateIdOrder());

            // remove states that do not have tabs
            _tabsVisible.removeIf(state -> STATE_ID_DRAFT == state);
            _tabsVisible.removeIf(state -> STATE_ID_RPSTR == state);
            _tabsVisible.removeIf(state -> STATE_ID_ARCH == state);

            // it is important to add this in the end
            _tabsVisible.add(TAB_MONITORING);
        } catch (Exception e) {
            LogHelper.setEvent("", e, null, new LogExt(_currentWF));
        }
    }


    //
    // Workflow Actions ----------------------------------------------------------------------------
    //


    /**
     * Cancels any edits & etc. and reloads current page
     *
     * @return
     */
    public String btnCancel() {
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    /**
     * Saves an edited document as is
     *
     * @return
     */
    public String btnSaveEdit() {
        try {
            if (_workflowDao.updateOrCreate(null, _currentWF)) {
                setModeRead();
                LogHelper.setEvent("id00055", null, null, new LogExt(_currentWF).apply(getUserRequestMeta()));
            } else {
                LogHelper.setEvent("id00056", null, null, new LogExt(_currentWF).apply(getUserRequestMeta()));
            }
        } catch (ConcurrentWriteException cwe1) {
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
        } catch (Exception e1) {
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"), e1.toString());
        }
        return null;
    }


    /**
     * Saves document as draft
     *
     * @return navigation target
     */
    public String btnSaveDraft() {
        try {
            _currentWF.setState(STATE_ID_DRAFT);
            _currentWF.setStep(0);
            _currentWF.setIteration(0);
            _currentWF.setStatus(PROCESS_STATUS_NORMAL);

            if (_workflowDao.updateOrCreate(null, _currentWF)) {
                setModeRead();
                LogHelper.setEvent("id00007", null, null, new LogExt(_currentWF).apply(getUserRequestMeta()));
                return null;
            } else {
                LogHelper.setEvent("id00008", null, null, new LogExt(_currentWF).apply(getUserRequestMeta()));
            }
        } catch (Exception e) {
            LogHelper.setEvent("id00009", e, null, new LogExt(_currentWF).apply(getUserRequestMeta()));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    /**
     * Button action: Sends a document to Approve Phase
     *
     * @return
     */
    public String btnToApprove() {
        try {
            _workflowService.actionToApproveState(_currentWF, _userContext.getAclContext(), getUserRequestMeta(), null);
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_STATUS);
        } catch (BpmEmptyStateException e1) {
            // Do nothing - this is UI-related issue
            return null;
        } catch (Exception e) {
            LogHelper.setEvent("id00012", e, null, new LogExt(_currentWF).apply(getUserRequestMeta()));
            return null;
        }
    }


    /**
     * Button action: sends a document to Sign Phase
     *
     * @return
     */
    public String btnToSign() {
        try {
            _workflowService.actionToSignState(_currentWF, _userContext.getAclContext(), getUserRequestMeta(), null);
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_STATUS);
        } catch (BpmEmptyStateException e1) {
            // Do nothing - this is UI-related issue
            return null;
        } catch (Exception e) {
            LogHelper.setEvent("id00031", e, null, new LogExt(_currentWF).apply(getUserRequestMeta()));
            return null;
        }
    }


    /**
     * Button action: sends a document to Exec Phase
     *
     * @return navigation case
     */
    public String btnToExecute() {
        try {
            _workflowService.actionToExecState(_currentWF, _userContext.getAclContext(), getUserRequestMeta(), null);
            return JsfUtils.getNavigationTarget(Pages.PAGE_MAIN);
        } catch (BpmEmptyStateException e1) {
            // Do nothing - this is UI-related issue
            return null;
        } catch (Exception e) {
            LogHelper.setEvent("id00063", e, null, new LogExt(_currentWF));
            return null;
        }
    }


    /**
     * Button action: sends a document to the Repository Phase
     *
     * @return
     */
    public String btnToRepo() {
        try {
            _workflowService.actionToRepoState(_currentWF, _userContext.getAclContext(), getUserRequestMeta(), null, null);
            return JsfUtils.getNavigationTarget(Pages.PAGE_MAIN);
        } catch (Exception e) {
            LogHelper.setEvent("id00227", e, null, new LogExt(_currentWF));
            return null;
        }
    }


    /**
     * Button action: sends a document to the Archive Phase
     *
     * @return navigation case
     */
    public String btnToArchive() {
        try {
            _workflowService.actionToArchiveState(_currentWF, _userContext.getAclContext(), getUserRequestMeta(), null, null);
            return JsfUtils.getNavigationTarget(Pages.PAGE_MAIN);
        } catch (Exception e) {
            LogHelper.setEvent("id00026", e, null, new LogExt(_currentWF));
            return null;
        }
    }


    /**
     * Button action: sends a document to processing repeatedly
     *
     * @return navigation case
     */
    public String btnToWorkAgain() {
        try {
            _workflowService.actionToWorkAgain(_currentWF, _userContext.getAclContext(), getUserRequestMeta(), null, null);
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_STATUS);
        } catch (BpmEmptyStateException bpme1) {
            // This means that no persons left after process optimization; thus we should stop processing this action
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.optimizeProcess.emptyStateException"));
            return null;
        } catch (Exception e) {
            LogHelper.setEvent("id00021", e, null, new LogExt(_currentWF));
            return null;
        }
    }


    public String btnDeleteDoc() {
        try {
            _workflowService.actionDelete(_currentWF, _userContext.getId(), _userContext.getDoneByActualName(), getUserRequestMeta());
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.doc.deleted"));
            JsfUtils.keepMessagesOnRedirect();
            return JsfUtils.getNavigationTarget(Pages.PAGE_MAIN);
        } catch (Exception e) {
            LogHelper.setEvent("id00182", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnDeleteBatch(List<Workflow> wfList, boolean applyToAll, WorkflowLazyQuery lazyQuery) {
        if (applyToAll) {
            UserRequestMeta userRequestMeta = getUserRequestMeta();
            _requestMap.put("applyToAllPages", false); // We need to reset applyToAll after each request

            int processedCount = 0;
            int totalCount = 0;
            lazyQuery.setPageSize(100);
            lazyQuery.setFirst(0);

            // Get the first page
            List<Workflow> workflowList = _workflowDao.findAllLazy(lazyQuery, SecurityUtils.getSubjectAclContext()).getDatasource();

            // Process first page and get the remaining later pages in the loop
            while (!workflowList.isEmpty()) {
                int currentCount = workflowList.size();
                int currentProcessedCount = btnDeleteBatchInner(workflowList, userRequestMeta);
                totalCount += currentCount;
                processedCount += currentProcessedCount;
                // Get Workflows for the next page. IMPORTANT: because we are deleting documents - there is a need to paginate only with the step
                // of unprocessed docs, as the rest of the docs were already removed
                lazyQuery.setFirst(lazyQuery.getFirst() + (currentCount - currentProcessedCount));
                workflowList = _workflowDao.findAllLazy(lazyQuery, SecurityUtils.getSubjectAclContext()).getDatasource();
            }

            if (processedCount == totalCount) {
                // All documents were processed
                JsfUtils.addInfoMessage(format(_localeContext.getLabel("msg.doc.batchDeletedAll"), processedCount));
            } else {
                // Some documents were skipped
                JsfUtils.addWarnMessage(format(_localeContext.getLabel("msg.doc.batchDeletedWithSkipped"), processedCount, totalCount));
            }
            return null;
        } else {
            return btnDeleteBatch(wfList);
        }
    }


    public String btnDeleteBatch(List<Workflow> wfList) {
        int totalCount = wfList.size();
        int processedCount = btnDeleteBatchInner(wfList, getUserRequestMeta());
        if (processedCount == totalCount) {
            // All documents were processed
            JsfUtils.addInfoMessage(format(_localeContext.getLabel("msg.doc.batchDeletedAll"), processedCount));
        } else {
            // Some documents were skipped
            JsfUtils.addWarnMessage(format(_localeContext.getLabel("msg.doc.batchDeletedWithSkipped"), processedCount, totalCount));
        }
        return null;
    }


    private int btnDeleteBatchInner(List<Workflow> wfList, UserRequestMeta userRequestMeta) {
        int requestedBatchSize = wfList.size();
        Iterator<Workflow> workflowIterator = wfList.iterator();

        while (workflowIterator.hasNext()) {
            Workflow workflow = workflowIterator.next();
            try {
                // Get ACL for the Workflow
                Set<String> docACL = _workflowService.getWorkflowACL(workflow, _userContext.getAclContext());

                // Step 1: in case the workflow is not allowed for deletion (e.g., Draft) and not blocked - we need to block it first
                if (!docACL.contains(ACTION_DELETE) && PROCESS_STATUS_BLOCKED != workflow.getStatus()) {
                    if (docACL.contains(ACTION_BLOCK)) {
                        _workflowService.actionBlock(workflow, _userContext.getId(), _userContext.getDoneByActualName(), userRequestMeta);
                        // We need to refresh ACL after blocking the document
                        docACL = _workflowService.getWorkflowACL(workflow, _userContext.getAclContext());
                    } else {
                        continue;
                    }
                }

                // Step 2: once document is blocked or allows deletion - proceed with deletion
                if (docACL.contains(ACTION_DELETE)) {
                    _workflowService.actionDelete(workflow, _userContext.getId(), _userContext.getDoneByActualName(), userRequestMeta);
                    workflowIterator.remove();
                }
            } catch (Exception e) {
                // Some unexpected exception - to be logged (but not shown, as skipped docs are to be reflected in the resulting counters)
                LogHelper.setEvent("id00004", e, null, new LogExt(workflow));
            }
        }
        return requestedBatchSize - wfList.size();
    }


    public String btnSuspend() {
        try {
            _workflowService.actionSuspend(_currentWF, _userContext.getId(), _userContext.getDoneByActualName(), getUserRequestMeta());
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.doc.suspended"));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00064", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnSuspendBatch(List<Workflow> wfList) {
        int requestedBatchSize = wfList.size();
        UserRequestMeta userRequestMeta = getUserRequestMeta();
        Iterator<Workflow> workflowIterator = wfList.iterator();
        while (workflowIterator.hasNext()) {
            Workflow workflow = workflowIterator.next();
            if (workflow.getStatus() != PROCESS_STATUS_NORMAL) {
                continue;
            }
            try {
                // Get ACL for the Workflow
                Set<String> docACL = _workflowService.getWorkflowACL(workflow, _userContext.getAclContext());

                // In case ACL allows this action - then proceed with suspend
                if (docACL.contains(ACTION_SUSPEND)) {
                    _workflowService.actionSuspend(workflow, _userContext.getId(), _userContext.getDoneByActualName(), userRequestMeta);
                    workflowIterator.remove();
                }
            } catch (Exception e) {
                // Some unexpected exception - to be logged (but not shown, as skipped docs are to be reflected in the resulting counters)
                LogHelper.setEvent("id00290", e, null, new LogExt(workflow));
            }
        }
        if (wfList.isEmpty()) {
            // All documents were processed
            JsfUtils.addInfoMessage(format(_localeContext.getLabel("msg.doc.batchSuspendedAll"), requestedBatchSize));
        } else {
            // Some documents were skipped
            JsfUtils.addWarnMessage(format(_localeContext.getLabel("msg.doc.batchSuspendedWithSkipped"), requestedBatchSize - wfList.size(), requestedBatchSize));
        }
        return null;
    }


    public String btnConfirm() {
        try {
            _workflowService.actionByTaskStatus(_currentWF, _currentWFTask, TASK_STATUS_DONE, _userContext.getAclContext(), getUserRequestMeta());
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_INBOX);
        } catch (ConcurrentWriteException ec1) {
            LogHelper.setEvent("id00203", null, "For information only: " + ec1.getMessage(), new LogExt(_currentWF));
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00037", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnConfirmEdit() {
        try {
            _workflowService.actionByTaskStatus(_currentWF, _currentWFTask, TASK_STATUS_DONE_W_COMMENTS, _userContext.getAclContext(), getUserRequestMeta());
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_INBOX);
        } catch (ConcurrentWriteException ec1) {
            LogHelper.setEvent("id00189", null, "For information only: " + ec1.getMessage(), new LogExt(_currentWF));
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00040", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnSign() {
        try {
            _workflowService.actionByTaskStatus(_currentWF, _currentWFTask, TASK_STATUS_DONE, _userContext.getAclContext(), getUserRequestMeta());
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_INBOX);
        } catch (ConcurrentWriteException ec1) {
            LogHelper.setEvent("id00188", null, "For information only: " + ec1.getMessage(), new LogExt(_currentWF));
            JsfUtils.addWarnMessage(LabelUtils.getLabel("msg.dao.concurrentWriteException", _localeContext.getLocale()));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00201", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnDecline() {
        try {
            _workflowService.actionByTaskStatus(_currentWF, _currentWFTask, TASK_STATUS_DECLINE, _userContext.getAclContext(), getUserRequestMeta());
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_INBOX);
        } catch (ConcurrentWriteException ec1) {
            LogHelper.setEvent("id00187", null, "For information only: " + ec1.getMessage(), new LogExt(_currentWF));
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00043", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnExecuteDone() {
        try {
            _workflowService.actionByTaskStatus(_currentWF, _currentWFTask, TASK_STATUS_DONE, _userContext.getAclContext(), getUserRequestMeta());
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW_INBOX);
        } catch (ConcurrentWriteException ec1) {
            LogHelper.setEvent("id00186", null, "For information only: " + ec1.getMessage(), new LogExt(_currentWFTask));
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00046", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnResume() {
        try {
            _workflowService.actionResume(_currentWF, _userContext.getId(), _userContext.getDoneByActualName(), getUserRequestMeta());
            LogHelper.setFlashByEventId("id00069", _localeContext.getLangSystemCode());
            JsfUtils.keepMessagesOnRedirect();
        } catch (ConcurrentWriteException ec1) {
            LogHelper.setEvent("id00124", null, "For information only: " + ec1.getMessage(), new LogExt(_currentWF));
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00068", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnResumeBatch(List<Workflow> wfList) {
        int requestedBatchSize = wfList.size();
        UserRequestMeta userRequestMeta = getUserRequestMeta();
        Iterator<Workflow> workflowIterator = wfList.iterator();
        while (workflowIterator.hasNext()) {
            Workflow workflow = workflowIterator.next();
            if (workflow.getStatus() != PROCESS_STATUS_SUSPENDED) {
                continue;
            }
            try {
                // Get ACL for the Workflow
                Set<String> docACL = _workflowService.getWorkflowACL(workflow, _userContext.getAclContext());

                // In case ACL allows this action - then proceed with resume
                if (docACL.contains(ACTION_RESUME)) {
                    _workflowService.actionResume(workflow, _userContext.getId(), _userContext.getDoneByActualName(), userRequestMeta);
                    workflowIterator.remove();
                }
            } catch (Exception e1) {
                // Some unexpected exception - to be logged (but not shown, as skipped docs are to be reflected in the resulting counters)
                LogHelper.setEvent("id00291", e1, null, new LogExt(workflow));
            }
        }
        if (wfList.isEmpty()) {
            // All documents were processed
            JsfUtils.addInfoMessage(format(_localeContext.getLabel("msg.doc.batchResumeStatusAll"), requestedBatchSize));
        } else {
            // Some documents were skipped
            JsfUtils.addWarnMessage(format(_localeContext.getLabel("msg.doc.batchResumeStatusWithSkipped"), requestedBatchSize - wfList.size(), requestedBatchSize));
        }
        return null;
    }


    public String btnTerminate() {
        try {
            _workflowService.actionTerminate(_currentWF, _userContext.getId(), _userContext.getDoneByActualName(), getUserRequestMeta());
            LogHelper.setFlashByEventId("id00073", _localeContext.getLangSystemCode());
            JsfUtils.keepMessagesOnRedirect();
        } catch (ConcurrentWriteException ec1) {
            LogHelper.setEvent("id00119", null, "For information only: " + ec1.getMessage(), new LogExt(_currentWF));
            JsfUtils.addWarnMessage(_localeContext.getLabel("msg.dao.concurrentWriteException"));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00072", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnBlock() {
        try {
            _workflowService.actionBlock(_currentWF, _userContext.getId(), _userContext.getDoneByActualName(), getUserRequestMeta());
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.doc.blocked"));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00172", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnBlockBatch(List<Workflow> wfList) {
        int requestedBatchSize = wfList.size();
        UserRequestMeta userRequestMeta = getUserRequestMeta();
        Iterator<Workflow> workflowIterator = wfList.iterator();
        while (workflowIterator.hasNext()) {
            Workflow workflow = workflowIterator.next();
            if (workflow.getStatus() == PROCESS_STATUS_BLOCKED) {
                continue;
            }
            try {
                // Get ACL for the Workflow
                Set<String> docACL = _workflowService.getWorkflowACL(workflow, _userContext.getAclContext());

                // In case ACL allows this action - then proceed with resume
                if (docACL.contains(ACTION_BLOCK)) {
                    _workflowService.actionBlock(workflow, _userContext.getId(), _userContext.getDoneByActualName(), userRequestMeta);
                    workflowIterator.remove();
                }
            } catch (Exception e) {
                // Some unexpected exception - to be logged (but not shown, as skipped docs are to be reflected in the resulting counters)
                LogHelper.setEvent("id00212", e, null, new LogExt(workflow));
            }
        }
        if (wfList.isEmpty()) {
            // All documents were processed
            JsfUtils.addInfoMessage(format(_localeContext.getLabel("msg.doc.batchBlockedAll"), requestedBatchSize));
        } else {
            // Some documents were skipped
            JsfUtils.addWarnMessage(format(_localeContext.getLabel("msg.doc.batchBlockedWithSkipped"), requestedBatchSize - wfList.size(), requestedBatchSize));
        }
        return null;
    }


    public String btnUnBlock() {
        try {
            _workflowService.actionUnBlock(_currentWF, _userContext.getId(), _userContext.getDoneByActualName(), getUserRequestMeta());
            JsfUtils.addInfoMessage(_localeContext.getLabel("msg.doc.unblocked"));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00176", e, null, new LogExt(_currentWF));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnUnBlockBatch(List<Workflow> wfList) {
        int requestedBatchSize = wfList.size();
        UserRequestMeta userRequestMeta = getUserRequestMeta();
        Iterator<Workflow> workflowIterator = wfList.iterator();
        while (workflowIterator.hasNext()) {
            Workflow workflow = workflowIterator.next();
            if (workflow.getStatus() != PROCESS_STATUS_BLOCKED) {
                continue;
            }
            try {
                // Get ACL for the Workflow
                Set<String> docACL = _workflowService.getWorkflowACL(workflow, _userContext.getAclContext());

                // In case ACL allows this action - then proceed with resume
                if (docACL.contains(ACTION_UNBLOCK)) {
                    _workflowService.actionUnBlock(workflow, _userContext.getId(), _userContext.getDoneByActualName(), userRequestMeta);
                    workflowIterator.remove();
                }
            } catch (Exception e) {
                // Some unexpected exception - to be logged (but not shown, as skipped docs are to be reflected in the resulting counters)
                LogHelper.setEvent("id00211", e, null, new LogExt(workflow));
            }
        }
        if (wfList.isEmpty()) {
            // All documents were processed
            JsfUtils.addInfoMessage(format(_localeContext.getLabel("msg.doc.batchUnBlockAll"), requestedBatchSize));
        } else {
            // Some documents were skipped
            JsfUtils.addWarnMessage(format(_localeContext.getLabel("msg.doc.batchUnBlockWithSkipped"), requestedBatchSize - wfList.size(), requestedBatchSize));
        }
        return null;
    }


    /**
     * Cancels delegation (including all delegation chains)<br>
     * IMPORTANT: removal of a delegated person from the document is not implemented
     *
     * @return
     */
    public String btnDelegateCancel() {
        try {
            if (_workflowDao.isValid(_currentWF) && _workflowTaskDao.delegationChainCancel(
                    null,
                    getId(),
                    _currentWFTask.getState(),
                    _currentWFTask.getStep(),
                    _currentWFTask.getIteration(),
                    _currentWFTask.getDelegatedTo(),
                    _currentWFTask.getAuthor()
            )) {
                // if all delegations resps cancelled OK
                _currentWFTask.setDelegatedTo(null);
                _currentWFTask.setTaskStatus(TASK_STATUS_INWORK);

                if (_workflowTaskDao.updateOrCreate(null, _currentWFTask)) {
                    LogHelper.setEvent("id00013", null, null, new LogExt(_currentWFTask).apply(getUserRequestMeta()));
                    // notifyDelegated(user, workDoc.getUniversalID());
                } else {
                    LogHelper.setEvent("id00014", null, null, new LogExt(_currentWFTask).apply(getUserRequestMeta()));
                }
            } else {
                // if something wrong with delegations resps cancellation
                LogHelper.setEvent("id00015", null, null, new LogExt(_currentWFTask).apply(getUserRequestMeta()));
            }
        } catch (DBEntryNotValidException | ConcurrentWriteException ec1) {
            LogHelper.setEvent("id00185", null, ec1.getMessage(), new LogExt(_currentWF).apply(getUserRequestMeta()));
            JsfUtils.addWarnMessage(LabelUtils.getLabel("msg.dao.concurrentWriteException", _localeContext.getLocale()));
            JsfUtils.keepMessagesOnRedirect();
        } catch (Exception e) {
            LogHelper.setEvent("id00016", e, null, new LogExt(_currentWF).apply(getUserRequestMeta()));
        }
        return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", getWebId());
    }


    public String btnPrevDoc() {
        WorkflowLazyQuery lazyQuery = new WorkflowLazyQuery();
        lazyQuery.setView(WorkflowListController.getViewIdByUrlParam(getViewParamV()));
        lazyQuery.setPageSize(-1);
        lazyQuery.getFilter().put(Workflow._FORM, FormDefinition.FORM_ID_RECO);
        lazyQuery.getFilter().put(Workflow._ID, new Document("$lt", new ObjectId(_currentWF.getId())));
        addDefaultViewFilters(lazyQuery);
        List<Workflow> workflowList = _workflowDao.findAllLazy(lazyQuery, SecurityUtils.getSubjectAclContext()).getDatasource();
        if (!workflowList.isEmpty()) {
            Workflow prevWorkflow = workflowList.getLast();
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", prevWorkflow.getIdWeb()).concat("&v=").concat(getViewParamV());
        }
        JsfUtils.addInfoMessage(_localeContext.getLabel("msg.doc.alreadyFirstDocument"));
        return null;
    }


    public String btnNextDoc() {
        WorkflowLazyQuery lazyQuery = new WorkflowLazyQuery();
        lazyQuery.setView(WorkflowListController.getViewIdByUrlParam(getViewParamV()));
        lazyQuery.setPageSize(1);
        lazyQuery.getFilter().put(Workflow._FORM, FormDefinition.FORM_ID_RECO);
        lazyQuery.getFilter().put(Workflow._ID, new Document("$gt", new ObjectId(_currentWF.getId())));
        addDefaultViewFilters(lazyQuery);
        List<Workflow> workflowList = _workflowDao.findAllLazy(lazyQuery, SecurityUtils.getSubjectAclContext()).getDatasource();
        if (!workflowList.isEmpty()) {
            Workflow nextWorkflow = workflowList.getFirst();
            return JsfUtils.getNavigationTarget(Pages.PAGE_WORKFLOW, "id", nextWorkflow.getIdWeb()).concat("&v=").concat(getViewParamV());
        }
        JsfUtils.addInfoMessage(_localeContext.getLabel("msg.doc.alreadyLastDocument"));
        return null;
    }


    private void addDefaultViewFilters(WorkflowLazyQuery lazyQuery) {
        if (WorkflowLazyQuery.VIEW.RECOQUEUE.equals(WorkflowListController.getViewIdByUrlParam(getViewParamV()))) {
            // For RECOQUEUE we show only active documents by default,
            // By default, we only show Workflows with Normal status (active Workflows)
            lazyQuery.getFilter().put(Workflow._STATE, STATE_ID_EXEC);
            lazyQuery.getFilter().put(Workflow._STATUS, PROCESS_STATUS_NORMAL);
        }
    }


    public void handleFileUploadForWFTask(FileUploadEvent event) {
        try {
            UploadedFile uploadedFile = event.getFile();
            _currentWFTask.getFiles().add(_workflowTaskDao.getFilesDao().uploadFromStream(uploadedFile.getFileName(), uploadedFile.getInputStream(), uploadedFile.getContentType(), null));
            _workflowTaskDao.updateOrCreate(null, _currentWFTask);
            _workflowFilesController.resetCache();
        } catch (IOException ex) {
            LOG.error("Error uploading file for WorkflowTask[{}]", _currentWFTask.getId(), ex);
            throw new ElDocRuntimeException("Error occurred during file upload processing");
        }
    }


    public void initDocResponseAnnexDS() {
        _currentWFTaskAnnex = WorkflowTask.createAnnexFor(_currentWF, _userContext.getId());
    }


    public void initProcBuilderDialog(ProcessableDocument procDS, int stateId, boolean editable) {
        ProcBuilder.createInstance(procDS, stateId, editable);
    }


    public List<String> completeTags(String query) {
        if (isBlank(query)) {
            return _workflowDao.getTagsDistinct().stream().sorted().collect(Collectors.toList());
        } else {
            List<String> result = _workflowDao.getTagsDistinct()
                    .stream()
                    .filter(tag -> Strings.CI.contains(tag, query))
                    .sorted()
                    .collect(Collectors.toList());
            result.remove(query); // remove query value (in case present) as we are going to add it to the first place
            result.addFirst(query);
            return result;
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getWorkflowName() {
        return _currentWF.getNameByLang(_localeContext.getLangSystemCode());
    }


    public boolean isBtnToApproveRendered() {
        if (STATE_ID_DRAFT == getState()) {
            return isModeEdit() && getDocACL().contains(ACTION_TO_APPROVAL);
        } else {
            return getDocACL().contains(ACTION_TO_APPROVAL);
        }
    }


    public boolean isBtnToSignRendered() {
        if (STATE_ID_DRAFT == getState()) {
            return isModeEdit() && getDocACL().contains(ACTION_TO_SIGN);
        } else {
            return getDocACL().contains(ACTION_TO_SIGN);
        }
    }


    public boolean isBtnToExecuteRendered() {
        if (STATE_ID_DRAFT == getState()) {
            return isModeEdit() && getDocACL().contains(ACTION_TO_EXEC);
        } else {
            return getDocACL().contains(ACTION_TO_EXEC);
        }
    }


    public boolean isBtnToRepoRendered() {
        if (STATE_ID_DRAFT == getState()) {
            return (isModeEdit() && getDocACL().contains(ACTION_TO_REPO));
        } else {
            return getDocACL().contains(ACTION_TO_REPO);
        }
    }


    public boolean isBtnToArchiveRendered() {
        if (STATE_ID_DRAFT == getState()) {
            return (isModeEdit() && getDocACL().contains(ACTION_TO_ARCH));
        } else {
            return getDocACL().contains(ACTION_TO_ARCH);
        }
    }


    public boolean isBtnSaveEditRendered() {
        return isModeEdit() && getDocACL().contains(ACTION_SAVE_EDIT);
    }


    public boolean isBtnBlockRendered() {
        return getState() > STATE_ID_DRAFT && getDocACL().contains(ACTION_BLOCK);
    }


    public boolean isBtnUnBlockRendered() {
        return getDocACL().contains(ACTION_UNBLOCK);
    }


    public List<SelectItem> getMonitoringStatesSI() {
        List<SelectItem> result = new ArrayList<>();
        try {
            TreeSet<Integer> uniqueStates = new TreeSet<>();
            uniqueStates.addAll(getMonitoringData().getDocStatesSet());
            uniqueStates.forEach(docStateId -> result.add(new SelectItem(docStateId, LabelUtils.getLabel(getProcessNameLabelId(docStateId), _localeContext.getLocale()))));
        } catch (Exception e) {
            LogHelper.setEvent("", e, null, new LogExt(_currentWF));
        }
        return result;
    }


    public List<SelectItem> getMonitoringIterationSI() {
        List<SelectItem> result = new ArrayList<>();
        try {
            Integer forState = getMonitoringData().getDocState();
            TreeSet<Integer> uniqueIterations = new TreeSet<>();
            uniqueIterations.addAll(getMonitoringData().getDocIterationsOnStatesMap().get(forState));
            uniqueIterations.forEach(i -> result.add(new SelectItem(i, i.toString())));
        } catch (Exception e) {
            LogHelper.setEvent("", e, null, new LogExt(_currentWF));
        }
        return result;
    }


    public Workflow getCurrentWF() {
        return _currentWF;
    }


    public WorkflowTask getCurrentWFTask() {
        return _currentWFTask;
    }


    public String getId() {
        return _currentWF.getId();
    }


    public String getWebId() {
        return _currentWF.getIdWeb();
    }


    public Integer getIteration() {
        return _currentWF.getIteration();
    }


    public Integer getState() {
        return _currentWF.getState();
    }


    public String getAuthor() {
        return _currentWF.getAuthor();
    }


    public Integer getStep() {
        return _currentWF.getStep();
    }


    public Integer getStatus() {
        return _currentWF.getStatus();
    }


    public Set<String> getDocACL() {
        if (_docACL == null) {
            _docACL = new HashSet<>();
        }
        return _docACL;
    }


    public List<RefItem> getRefDocsUI(Object dataSource) {
        List<RefItem> result = new ArrayList<>();
        if (dataSource instanceof Workflow workflow) {
            workflow.getRefItems().forEach(id -> result.add(new RefItem(RefItem.TYPE.WORKFLOW, id, _workflowDao.find(id, _userContext.getAclContext()), _localeContext.getLocale())));
        } else if (dataSource instanceof WorkflowTask wfTask) {
            wfTask.getRefItems().forEach(id -> result.add(new RefItem(RefItem.TYPE.WORKFLOW, id, _workflowDao.find(id, _userContext.getAclContext()), _localeContext.getLocale())));
        }
        return result;
    }


    /**
     * Returns cached for request scope list of workflows which reference to this workflow
     *
     * @param workflow
     * @return
     */
    public List<RefItem> getReferencedByUI(Workflow workflow) {
        if (_requestMap.get(CACHE_KEY_WF_REFBY) == null) {
            List<RefItem> result = new ArrayList<>();
            _workflowDao.findAllReferencedBy(workflow.getId()).forEach(id -> result.add(new RefItem(RefItem.TYPE.WORKFLOW, id, _workflowDao.find(id, _userContext.getAclContext()), _localeContext.getLocale())));
            _requestMap.put(CACHE_KEY_WF_REFBY, result);
        }
        return (List<RefItem>) _requestMap.get(CACHE_KEY_WF_REFBY);
    }


    public RefItem getRefDocById(String refDocId) {
        return new RefItem(RefItem.TYPE.WORKFLOW, refDocId, _workflowDao.find(refDocId, _userContext.getAclContext()), _localeContext.getLocale());
    }


    public void setIterationSelector(Integer iterationSelector) {
        _iterationSelector = iterationSelector;
    }


    public Integer getIterationSelector() {
        return _iterationSelector;
    }


    public boolean getIterationSelectorDisabled() {
        return (_iterationSelectorSI.size() == 1);
    }


    public List<SelectItem> getIterationSelectorSI() {
        return _iterationSelectorSI;
    }


    public Integer getTabMonitoring() {
        return TAB_MONITORING;
    }


    public List<Integer> getTabsVisible() {
        return _tabsVisible;
    }


    /**
     * Returns label id from the label resource file
     *
     * @return
     */
    public String getRespStatusName(WorkflowTask wfTask) {
        return WorkflowUtils.getRespStatusName(wfTask.getTaskStatus());
    }


    public ProcessMonitoringData getMonitoringData() {
        if (_monitoringData == null) {
            // Inits data for Monitoring tab - do it on first access
            try {
                _monitoringData = new ProcessMonitoringData(_currentWF);

                // check whether it is requested to set specific state and iter via url string
                String reqStateParam = _requestParamMap.get(URL_PARAM_STATE);
                String reqIterParam = _requestParamMap.get(URL_PARAM_ITER);
                if (reqStateParam != null && reqStateParam.matches("\\d")) {
                    // in case state requested via url string
                    if (_tabsVisible.contains(Integer.valueOf(reqStateParam))) {
                        // and only in case the state is present in visible tabs
                        _monitoringData.setDocState(Integer.valueOf(reqStateParam));
                        if (reqIterParam != null && reqIterParam.matches("\\d")) {
                            _monitoringData.setIteration(Integer.valueOf(reqIterParam));
                        } else {
                            // by default, we set iteration to 1st in case someone tries to cheat the system
                            _monitoringData.setIteration(1);
                        }
                    }
                }
            } catch (Exception e) {
                LogHelper.setEvent("id00358", e, "Process monitoring data initialization error", new LogExt(_currentWF));
            }
        }
        return _monitoringData;
    }


    /**
     * Determines active tab on page load
     *
     * @return
     */
    public int getActiveTabIndex() {
        return _uiActiveTabIndex;
    }


    public void setActiveTabIndex(int index) {
        _uiActiveTabIndex = index;
    }


    public String getViewParamId() {
        return _viewParamId;
    }


    public void setViewParamId(String viewParamId) {
        _viewParamId = viewParamId;
    }


    public String getViewParamV() {
        return _viewParamV;
    }


    public void setViewParamV(String viewParamV) {
        this._viewParamV = viewParamV;
    }


    public String getStateLabelByStateId(Integer stateId) {
        return getProcessNameLabelId(stateId);
    }


    private UserRequestMeta getUserRequestMeta() {
        return UserRequestMeta.fromRequest(JsfUtils.getRequest());
    }

}
