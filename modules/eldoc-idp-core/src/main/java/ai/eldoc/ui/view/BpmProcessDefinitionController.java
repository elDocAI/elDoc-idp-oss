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

import ai.eldoc.bpm.*;
import ai.eldoc.bpm.Process;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.BpmProcessDefinitionDao;
import ai.eldoc.dao.WorkflowTaskDao;
import ai.eldoc.data.MongoLazyDataModel;
import ai.eldoc.model.*;
import ai.eldoc.service.LogHelper;
import ai.eldoc.ui.ResolutionManager;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static ai.eldoc.bpm.BpmConstants.*;
import static ai.eldoc.constants.DirectoryConstants.BPM_PERS_INITIATOR;
import static ai.eldoc.workflow.WorkflowUtils.getProcessNameLabelId;


/**
 * Process Bean<br>
 * Helps to manage processes (BpmProcessDefinition model) via UI
 */
@Named
@ViewScoped
public class BpmProcessDefinitionController extends AbstractUIController implements Serializable, ResolutionManager {

    @Serial
    private static final long serialVersionUID = 4247088089749951406L;
    private static final Logger LOG = Logger.getLogger(BpmProcessDefinitionController.class.getName());
    private static final String URL_PARAM_ID = "id";

    @Inject
    private BpmEngine _bpmEngine;

    @Inject
    private BpmProcessDefinitionDao _bpmProcessDefinitionDao;

    @Inject
    private WorkflowTaskDao _workflowTaskDao;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private PageController _pageController;

    private BpmProcessDefinition _bpmProcDef;
    private Map<Integer, StateConfig> _processStatesMap;
    private MongoLazyDataModel<BpmProcessDefinition> _dataProvider;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Performs various process checks and updates before saving the process to the DB
     */
    private void beforeSaveRefresh(boolean onSaveRefresh) {
        // STATE_ID_EXEC requires special handling
        // We need to collect all remaining Resolution WorkflowTasks and add those names back to the process
        if (getProcess().hasState(STATE_ID_EXEC)) {

            // Retrieve sorted by Step Resolutions list
            List<WorkflowTask> resolutionList = getResolutionList();
            LOG.log(Level.FINEST, "Number of Resolution WorkflowTasks found: {0}", resolutionList.size());

            if (resolutionList.isEmpty()) {
                if (onSaveRefresh) {
                    // No resolutions were created but Process marked as Fixed - reset it to Flexible route on save only
                    getProcess().getState(STATE_ID_EXEC).getSteps().clear();
                    getProcess().getState(STATE_ID_EXEC).setLockSteps(false);
                    LOG.log(Level.FINEST, "EXEC State was cleared");
                }
            } else {
                if (getProcess().getState(STATE_ID_EXEC).getSteps().isEmpty()) {
                    // It may happen in case with present Resolutions user switched to Flexible route,
                    // for such scenario we do not not update process but rather keep assigned Resolutions for future
                } else {
                    // Otherwise - refresh EXEC process
                    _bpmEngine.initExecPhaseWithFollowupList(_bpmProcDef.getId(), getProcess(), resolutionList);
                }
            }
        }

        // Saving process:
        // Clearing process from empty Steps (steps without participants assigned)
        // At least one step is added by process route constructor
        _bpmProcDef.getProcess().removeEmptySteps();
    }


    /* (non-Javadoc)
     * @see ai.eldoc.beans.ResolutionManager#resolutionRemove()
     */
    @Override
    public boolean resolutionRemove(WorkflowTask wfTaskResol) {
        try {
            if (_workflowTaskDao.delete(null, wfTaskResol) == false) {
                LogHelper.setEvent("id00208", null, null, new LogExt(_bpmProcDef));
                return false;
            }

            beforeSaveRefresh(false);

            boolean saveResult = _bpmProcessDefinitionDao.updateOrCreate(null, _bpmProcDef);
            if (!saveResult) {
                LogHelper.setEvent("id00286", null, null, new LogExt(_bpmProcDef));
            }
            return saveResult;
        } catch (Exception e) {
            LogHelper.setEvent("id00287", e, null, new LogExt(_bpmProcDef));
            return false;
        }
    }


    /**
     * @inheritDoc
     */
    @Override
    public boolean resolutionAdd(WorkflowTask docRespResol) {
        try {
            if (!_workflowTaskDao.updateOrCreate(null, docRespResol)) {
                LogHelper.setEvent("id00206", null, null, new LogExt(_bpmProcDef));
                return false;
            }

            beforeSaveRefresh(false);

            boolean saveResult = _bpmProcessDefinitionDao.updateOrCreate(null, _bpmProcDef);
            if (!saveResult) {
                LogHelper.setEvent("id00207", null, null, new LogExt(_bpmProcDef));
                resolutionRemove(docRespResol);
            }

            return saveResult;
        } catch (Exception e) {
            LogHelper.setEvent("id00266", e, null, new LogExt(_bpmProcDef));
            return false;
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    public List<SelectItem> getProcessStatesSI() {
        return STATE_ID_LIST.stream()
                .map(stateId -> new SelectItem(stateId, _localeContext.getLabel(getProcessNameLabelId(stateId))))
                .collect(Collectors.toList());
    }


    public BpmProcessDefinition getSysProcessDS() {
        return _bpmProcDef;
    }


    public Process getProcess() {
        return _bpmProcDef.getProcess();
    }


    /* (non-Javadoc)
     * @see ai.eldoc.beans.ResolutionManager#execMgmtIsRender(String, WorkflowTask)
     */
    @Override
    public boolean getResolutionAcl(RESOL_ACTION action, WorkflowTask respResolItem) {
        if (!isModeEdit()) {
            // We allow editing resolution only in Process Edit Mode
            return false;
        }

        if (_bpmProcDef.isRecoProcess()) {
            // Recognition Document Process has special handling
            if (RESOL_ACTION.RESOL_EDIT.equals(action)) {
                // we may allow only to edit Validation step
                return respResolItem.getStep() == RECO_PROCESS_STEP_RECO_VALIDATOR || respResolItem.getStep() == RECO_PROCESS_STEP_CONV_VALIDATOR;
            }
            // The rest is not to be allowed
            return false;
        }

        // For the rest of Processes we decide based on the role
        return _userContext.isAdminDocRW();
    }


    /**
     * @inheritDoc
     */
    @Override
    public List<WorkflowTask> getResolutionList() {
        return _workflowTaskDao.getAssignedTasksList(_bpmProcDef.getId(), STATE_ID_EXEC).stream().sorted(Comparator.comparingInt(WorkflowTask::getStep)).collect(Collectors.toList());
    }


    //
    // Inner helper class for wotk with the process configuration ---------------------------------
    //


    public final class StateConfig implements Serializable {

        @Serial
        private static final long serialVersionUID = 7355544925931118379L;
        private int _stateId = 0; // Process Phase ID

        private boolean _enabled = false;
        private String _stepsRule = null;
        private boolean _autoNextState = false;
        private boolean _optimizeRoute = false;
        private boolean _fixedRoute = false;
        private boolean _lockSteps = false;


        /**
         * Process Phases configuration constructor
         *
         * @param stateId
         */
        public StateConfig(Integer stateId) {

            _stateId = stateId;

            // Phases Draft and Archive must be always present on the process, as such - we initialize them accordingly
            if ((_stateId == STATE_ID_DRAFT || _stateId == STATE_ID_ARCH) && !BpmProcessDefinitionController.this.getProcess().getStateIdOrder().contains(_stateId)) {
                setEnabled(true); // Enable phase on UI in case it is present in the process (only for STATE_ID_DRAFT and STATE_ID_ARCH)
                enabledCHL(); // Handler for process status change logic
            } else if (BpmProcessDefinitionController.this.getProcess().getStateIdOrder().contains(_stateId)) {
                setEnabled(true); // Enable phase on UI in case it is present in the process
                enabledCHL();
            }

            if (_enabled == false) {
                return; // In case prcoess phase is not enabled - nothing more to do
            }

            _stepsRule = BpmProcessDefinitionController.this.getProcess().getState(_stateId).getStepsRule();
            _autoNextState = BpmProcessDefinitionController.this.getProcess().getState(_stateId).isAutoNextState();
            _optimizeRoute = BpmProcessDefinitionController.this.getProcess().getState(_stateId).isOptimizeRoute();
            _fixedRoute = BpmProcessDefinitionController.this.getProcess().getState(_stateId).isFixedRoute();
            _lockSteps = BpmProcessDefinitionController.this.getProcess().getState(_stateId).isLockSteps();
        }


        //
        // Methods --------------------------------------------------------------------------------
        //


        /**
         * Handler for enabling/disabling process
         */
        public void enabledCHL() {
            if (_enabled) {
                BpmProcessDefinitionController.this.getProcess().addState(_stateId); // Check if process is present in configuration is performed by addState() method
            } else {
                BpmProcessDefinitionController.this.getProcess().removeState(_stateId);
                // reset state settings
                _stepsRule = null;
                _autoNextState = false;
                _fixedRoute = false;
            }

        }


        /**
         * Logic when switching process route mode (from fixed to flexible)
         */
        public void fixedRouteCHL() {
            if (_fixedRoute) {
                ProcessStep procStep = BpmProcessDefinitionController.this.getProcess().createStep();
                procStep.addUser(BPM_PERS_INITIATOR);
                BpmProcessDefinitionController.this.getProcess().getState(_stateId).getSteps().add(procStep);
            } else {
                BpmProcessDefinitionController.this.getProcess().getState(_stateId).getSteps().clear(); // Cleaning up all States when route is switched to flexible
                BpmProcessDefinitionController.this.getProcess().getState(_stateId).setLockSteps(false); // Allow Steps to be adjusted by end-user
            }
        }


        //
        // Getters & Setters ----------------------------------------------------------------------
        //
        public void setEnabled(Boolean enabled) {
            _enabled = enabled;
        }


        public Boolean getEnabled() {
            return _enabled;
        }


        public void setStepsRule(String stepsRule) {
            _stepsRule = stepsRule;
            BpmProcessDefinitionController.this.getProcess().getState(_stateId).setStepsRule(stepsRule); // Writing value directly to process configuration
        }


        public String getStepsRule() {
            return _stepsRule;
        }


        public void setAutoNextState(Boolean autoNextState) {
            _autoNextState = autoNextState;
            BpmProcessDefinitionController.this.getProcess().getState(_stateId).setAutoNextState(autoNextState); // Writing value directly to process configuration
        }


        public Boolean getAutoNextState() {
            return _autoNextState;
        }


        public boolean isOptimizeRoute() {
            return _optimizeRoute;
        }


        public void setOptimizeRoute(boolean optimizeRoute) {
            _optimizeRoute = optimizeRoute;
            BpmProcessDefinitionController.this.getProcess().getState(_stateId).setOptimizeRoute(optimizeRoute); // Writing value directly to process configuration
        }


        public void setFixedRoute(Boolean fixedRoute) {
            _fixedRoute = fixedRoute;
        }


        public Boolean getFixedRoute() {
            return _fixedRoute;
        }


        public AttachmentManager getAttachmentManager() {
            return BpmProcessDefinitionController.this.getProcess().getState(_stateId).getAttachmentManager();
        }


        public boolean isLockSteps() {
            return _lockSteps;
        }


        public void setLockSteps(boolean lockSteps) {
            _lockSteps = lockSteps;
            BpmProcessDefinitionController.this.getProcess().getState(_stateId).setLockSteps(lockSteps);
        }
    }

}
