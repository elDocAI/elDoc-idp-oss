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

package ai.eldoc.model;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.bpm.*;
import ai.eldoc.bpm.Process;
import ai.eldoc.dao.DaoUtils;
import ai.eldoc.service.LogHelper;
import ai.eldoc.workflow.WorkflowUtils;
import org.apache.commons.lang3.Strings;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import static ai.eldoc.bpm.BpmConstants.*;


/**
 * Model for handling workflow monitoring related data
 */
public class ProcessMonitoringData implements Serializable {

    @Serial
    private static final long serialVersionUID = 2767713025758291185L;

    private final Processable _workflow;
    private String _wfId;
    private Process _wfProcessBackUp;
    private int _state;
    private boolean _docStateUIchanged;
    private int _iteration;
    private boolean _docIterationUIchanged;
    private LogExt _logExt;

    List<WorkflowTask> _wfTaskList;

    private final TreeSet<Integer> _docStatesSet = new TreeSet<>();
    private final HashMap<Integer, TreeSet<Integer>> _docIterationsOnStatesMap = new HashMap<>();

    private final ArrayList<ArrayList<ProcessMonitoringDataItem>> _monitoringDS = new ArrayList<>();


    // Cunstructors -------------------------------------------------------------------------------


    /**
     * Constructor inits object based
     *
     * @param workflow
     */
    public ProcessMonitoringData(final Workflow workflow) {
        _workflow = workflow;
        _state = _workflow.getState();
        _logExt = new LogExt(workflow);
        if (STATE_ID_DRAFT != _state) {
            // Init the rest only after document not draft anymore
            _wfId = workflow.getId();
            _iteration = _workflow.getIteration();
            init();
        }
    }


    // Class methods ------------------------------------------------------------------------------


    /**
     * Inits monitoring data
     */
    private void init() {
        try {
            _wfTaskList = DaoUtils.getWorkflowTaskDaoTenant().findAll(_wfId, null, null, null, null, null);
            initStatesAndIterations();
            initMonitoringDS();
        } catch (ElDocRuntimeException elx) {
            LogHelper.setEvent("id00359", null, elx.getMessage(), _logExt);
        } catch (Exception e) {
            LogHelper.setEvent("id00329", e, "Process monitoring data initialization error", _logExt);
        }
    }


    /**
     * Collects all unique workflow states and iterations on states based on existing wfTasks in the view
     */
    private void initStatesAndIterations() {
        for (WorkflowTask wfTask : _wfTaskList) {
            // include only information for states which already were passed according to assigned to document processes
            // this is based on current approach, when assigned processes to document have natural order based on their IDs
            if (wfTask.getState() <= _state) {
                // collect the statistics for states and integrations based on already existing data in DB
                _docStatesSet.add(wfTask.getState());

                if (!_docIterationsOnStatesMap.containsKey(wfTask.getState())) {
                    _docIterationsOnStatesMap.put(wfTask.getState(), new TreeSet<>());
                }

                _docIterationsOnStatesMap.get(wfTask.getState()).add(wfTask.getIteration());

                // find process backup for selected state & iteration (procBackup present only on step 0 - action of author)
                if (_state == wfTask.getState() && _iteration == wfTask.getIteration() && wfTask.getStep() == 0) {
                    if (wfTask.getProcess() != null) {
                        // in case process is not null - we remember it
                        // use case for this check is block/unblock of document in repo or arch for example
                        _wfProcessBackUp = wfTask.getProcess();
                    }
                }
            }
        }
        // in case at this moment process was not initialized based on process backup in workflowTask - alert this
        if (_wfProcessBackUp == null) {
            LogHelper.setEvent("id00328", null, "Process monitoring data initialization error: cannot locate initial process image for the current process phase", _logExt);
        } else if (Process.CURRENT_VERSION != _wfProcessBackUp.getVer()) {
            throw new ElDocRuntimeException("Process monitoring data initialization error: process version " + _wfProcessBackUp.getVer() + " not supported");
        }
    }


    /**
     * Inits monitoring data
     */
    private void initMonitoringDS() {

        if (_wfProcessBackUp == null) {
            return;
        }

        List<ProcessStep> processSteps;

        // get Process Steps information from given Process State ID
        if (_wfProcessBackUp.hasState(_state)) {
            processSteps = _wfProcessBackUp.getState(_state).getSteps();
        } else {
            // In case no REPO in Process exist and doc got to Archive - currently it can be send to Repo manually (button present),
            // for such case we need to artificially add steps with initiator to see something in monitoring table,
            // as we assume that initiator moved document from Arch to repo, etc. But it also can be done by docAdminRW, or somebody else
            // TODO: for such cases adjust monitoring data collection in order to build steps for monitoring not only based on document process,
            // but also based on wfTasks & logs records
            processSteps = _wfProcessBackUp.getState(STATE_ID_ARCH).getSteps();
        }

        // go via all Process Steps on given State
        for (int procStepIndex = 0; procStepIndex < processSteps.size(); procStepIndex++) {

            ProcessStep procStep = processSteps.get(procStepIndex);

            // get information about all users on Process Step
            List<String> procStepNames = procStep.getNamesAll(true);
            ArrayList<ProcessMonitoringDataItem> stepItems = new ArrayList<>();

            // go via all users names on Process Step
            for (String name : procStepNames) {

                ProcessMonitoringDataItem dataItem = new ProcessMonitoringDataItem();
                dataItem.setAuthor(name);
                dataItem.setState(_state);
                dataItem.setStep(procStepIndex);
                dataItem.setIteration(_iteration);

                initDataItem(dataItem);

                stepItems.add(dataItem);

                // in case response was delegated - we need to find and process all responses of all delegated users
                // delegation can repeat several times: delegated user can delegate response again to next delegated user and so on
                while (dataItem.isDelegated()) {
                    dataItem = dataItem.createDelegatedDataItem();
                    initDataItem(dataItem);
                    stepItems.add(dataItem);
                }
            }

            _monitoringDS.add(stepItems);
        }
    }


    /**
     * Fills dataItem for log monitoring overview with data based on wfTasks and logs records
     *
     * @param dataItem
     */
    // We have to be super careful with actions which can repeat several times on the same Process Step!
    // Particularly there are:
    // 1) Actions created in with delegation & cancel delegation & repeated delegation functionality
    // 2) Process pauses & resumes (with currently not shown in the process monitoring and could be omitted)
    // 3) Document send to Repo -> Arch -> Repo -> Arch
    private void initDataItem(ProcessMonitoringDataItem dataItem) {

        fillDataItemWithData(dataItem);

        // in case doc response exist - fill monitoring data with times (based on logs)
        if (dataItem.getWfTaskId() != null) {
            fillDataItemWithLogData(dataItem);
        }

        initDataItemStyles(dataItem);
    }


    /**
     * Fills monitoring data item with information about user actions, in case any actions exist at given point of the process</br> Data taken from wfTasks
     *
     * @param dataItem - monitoring data item
     */
    private void fillDataItemWithData(ProcessMonitoringDataItem dataItem) {
        for (WorkflowTask wfTask : _wfTaskList) {
            // Comment re 'dataItem.getDelegatedFrom().equals(delegatedFromC)': on the same point of the process,
            // Workflow action (wfTask) can be delegated to the same user several times, in case:
            // 1) on the Process Step two or more participants exist (according to the original Process configuration,
            // or even one of them was added by delegation);
            // 2) 1st user delegates document to the user X;
            // 3) 1st user cancel delegation to the user X;
            // 4) 2nd user delegates document to the user X;
            // for such scenario we have to compare from whom action was delegated in order to precisely identify valid wfTask
            if (Strings.CI.equals(dataItem.getAuthor(), wfTask.getAuthor())
                    && Objects.equals(dataItem.getState(), wfTask.getState())
                    && Objects.equals(dataItem.getStep(), wfTask.getStep())
                    && Objects.equals(dataItem.getIteration(), wfTask.getIteration())
                    && (TASK_STATUS_LIST_PROCESSED.contains(wfTask.getTaskStatus())
                    || TASK_STATUS_LIST_WORKFLOW_INIT_REINIT.contains(wfTask.getTaskStatus())
                    || TASK_STATUS_INWORK == wfTask.getTaskStatus())
                    && (dataItem.getDelegatedFrom() == null || Strings.CI.equals(dataItem.getDelegatedFrom(), wfTask.getDelegatedFrom()))) {

                dataItem.setTaskStatus(wfTask.getTaskStatus());
                dataItem.setTaskStatusText(WorkflowUtils.getRespStatusName(wfTask.getTaskStatus()));
                dataItem.setWfTaskId(wfTask.getId());

                // In case there is delegated out action - fill necessary data
                if (TASK_STATUS_DELEGATE_OUT == wfTask.getTaskStatus()) {
                    dataItem.setDelegated(true);
                    dataItem.setDelegatedTo(wfTask.getDelegatedTo());
                }
                break;
            }
        }
    }


    /**
     * Fills monitoring data item with timestamps of user actions, in case any actions exist at given point of the process.
     * Data taken from log records
     *
     * @param dataItem - monitoring data item
     */
    private void fillDataItemWithLogData(ProcessMonitoringDataItem dataItem) {

        List<Log> logsList = DaoUtils.getLogDao().findByEntityId(dataItem.getWfTaskId());

        // go through all log documents related to the parent workflow and created for target wfTask (based on _id comparison)
        for (Log log : logsList) {

            // in case 'log time in' OR 'log time out' are still empty - try to set them
            // OR if logTimeOut already set but it is delegated out task status - there is a special case which has to be handled separately
            if (dataItem.getLogTimeIn() == null
                    || dataItem.getLogTimeOut() == null
                    || (dataItem.getLogTimeOut() != null && TASK_STATUS_DELEGATE_OUT == dataItem.getTaskStatus())) {

                int logTaskStatus = log.getProcessTaskStatus();

                // process 'log time in' case
                // taskStatus 'TASK_STATUS_INWORK' can be found only once, as only once document can be taken in work
                if (dataItem.getLogTimeIn() == null && TASK_STATUS_INWORK == logTaskStatus) {
                    dataItem.setLogTimeIn(log.getTimeStamp());
                }

                // logTimeOut already set but it is 'delegated out' task status
                if (dataItem.getLogTimeOut() != null && TASK_STATUS_DELEGATE_OUT == dataItem.getTaskStatus()) {
                    // 1) because at the same point in the process (state & step & iteration) user X can delegate doc response to user Y,
                    // then cancel delegation, then delegate again to the same user Y - the system will have several absolutely similar log documents,
                    // the only difference between them is logTime, and obviously - we have to find the latest time stamp
                    // 2) similar situation with Arch -> Repo -> Arch -> Repo document relocation
                    java.util.Date logTime = log.getTimeStamp();
                    if (logTime != null && logTime.after(dataItem.getLogTimeOut())) {
                        dataItem.setLogTimeOut(logTime);
                    }
                }

                // process 'log time out' case (only in case it is processed by user)
                if (dataItem.getLogTimeOut() == null && dataItem.getTaskStatus() == logTaskStatus) {
                    // check for processed by participants - TASK_STATUS_LIST_PROCESSED
                    // check for processed by initiator (sent to work) - RESP_PROC_INIT
                    if (TASK_STATUS_LIST_PROCESSED.contains(logTaskStatus) || TASK_STATUS_LIST_WORKFLOW_INIT_REINIT.contains(logTaskStatus)) {
                        dataItem.setLogTimeOut(log.getTimeStamp());
                    }
                }
            }
        }
    }


    /**
     * Inits UI Style Classes of the monitoring table rows
     *
     * @param dataItem
     */
    private void initDataItemStyles(ProcessMonitoringDataItem dataItem) {
        if (dataItem.getTaskStatus() == -1) {
            dataItem.setStyleClass("warning");
        } else if (TASK_STATUS_LIST_WORKFLOW_INIT_REINIT.contains(dataItem.getTaskStatus())) {
            // in case it was action with document send to work - it is complete successfully
            dataItem.setStyleClass("success");
        } else if (TASK_STATUS_INWORK == dataItem.getTaskStatus()) {
            dataItem.setStyleClass("info");
        } else {
            if (_wfProcessBackUp.getState(_state).getStepsRuleNotAllowedTaskStatuses().contains(dataItem.getTaskStatus())) {
                dataItem.setStyleClass("error");
            } else {
                dataItem.setStyleClass("success");
            }
        }
    }


    /**
     * Updates monitoring data source
     */
    public void updateMonitoringDS() {
        _monitoringDS.clear();
        init();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * Returns set of all available States based on real-time data from DB
     *
     * @return
     */
    public TreeSet<Integer> getDocStatesSet() {
        return _docStatesSet;
    }


    /**
     * Returns map with all available Iterations Sets for each available State (based on real-time data from DB)
     *
     * @return
     */
    public HashMap<Integer, TreeSet<Integer>> getDocIterationsOnStatesMap() {
        return _docIterationsOnStatesMap;
    }


    public ArrayList<ArrayList<ProcessMonitoringDataItem>> getMonitoringDS() {
        if (_docStateUIchanged || _docIterationUIchanged) {
            updateMonitoringDS();
            _docStateUIchanged = false;
            _docIterationUIchanged = false;
        }
        return _monitoringDS;
    }


    public int getDocState() {
        return _state;
    }


    public void setDocState(int docState) {
        if (_state != docState) {
            _docStateUIchanged = true;
        }
        _state = docState;
    }


    public int getIteration() {
        return _iteration;
    }


    public void setIteration(int iteration) {
        if (_iteration != iteration) {
            _docIterationUIchanged = true;
        }
        _iteration = iteration;
        if (_docStateUIchanged) {
            // due to JSF lifecycle override value set via UI
            _iteration = _docIterationsOnStatesMap.get(_state).last();
        }
    }


    public boolean isOptimizeRoute() {
        if (_workflow instanceof Workflow) {
            return ((Workflow) _workflow).getProcessState(_state).isOptimizeRoute();
        }
        return false;
    }
}
