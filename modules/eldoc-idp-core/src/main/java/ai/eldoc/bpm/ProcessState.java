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

package ai.eldoc.bpm;

import ai.eldoc.model.AttachmentManager;
import java.util.List;
import java.util.Map;


/**
 * BPM Process State Interface<br>
 * <p>
 * Represents single Process State (or Phase) like Approval, and contains Process Steps with assigned Users and Groups/Units.<br>
 * Process Step with index 0 - is always Initiator Process Step.<br>
 * Participants assigned on the same Process Step receive workflow in parallel for processing, and those assigned on the
 * next Process Steps - receive workflow for after processing is done by the previous Process Step.<br>
 * Rules on how to proceed with next Process Steps based on processing on previous Process Steps is defined on the Process State.
 */
public interface ProcessState {

    /**
     * Resets steps by removing all steps except an Initiator step but replacing Initiator systemId with the respective bpm-var
     *
     * @return
     */
    public ProcessState resetSteps();


    /**
     * Returns all names present on the process
     *
     * @param inclAuthor - include Initiator in the resulting list
     * @param onlyGroupMembers - true - get only group members, false - get only group names
     * @return
     */
    public List<String> getNamesAll(boolean inclAuthor, boolean onlyGroupMembers);


    /**
     * Returns users on the process with a list of group members, WITHOUT an initiator and WITHOUT group names
     * Main purpose of this method is usage on UI in EL
     *
     * @return List
     */
    public List<String> getNamesAllwGroupMembers();


    /**
     * Returns Process Subject from the current Process State<br>
     * Checks Users first and then Groups by the given id<br>
     * Checks all steps starting from the index 0 and returns subject on the first match
     *
     * @param id
     * @return
     */
    public ProcessSubject getProcessSubject(String id);


    /**
     * Returns requested Step
     *
     * @param stepNum
     * @return
     */
    public ProcessStep getStep(int stepNum);


    /**
     * Returns last available process state after the Initiator step.
     * Creates new step in case only initiator step is present on the state.
     *
     * @return
     */
    public ProcessStep getStepLast();


    /**
     * Removes empty steps (where no assigned names left due to clean-up, etc.) from the ProcessState
     *
     * @return List(Integer) with indexes of removed steps (based on the original indexes of steps on the ProcessState before clean-up)
     * <pre>
     * Example:
     *   Original process phase (e.g. EXEC) contained steps: 0, 1, 2, 3, 4, 5
     *   Steps indexes which are blank and to be deleted: 1, 3, 4
     *   Resulting returned value is: [1, 3, 4]
     * </pre>
     */
    public List<Integer> removeEmptySteps();


    /**
     * Returns List of TaskStatuses not allowed for State steps rule
     *
     * @return
     */
    public List<Integer> getStepsRuleNotAllowedTaskStatuses();


    /**
     * Removes names duplication on this Process State instance which could occur due to automatic process calculation
     *
     * @param skipInitiatorCheck excludes Initiator from checks with the rest of the process participants. In other words
     * in case Initiator is present on process steps - he will not be removed from those steps.
     * @param checkPerStepOnly when set to TRUE performs removes duplicates on step level only, otherwise removes duplicates for the whole process (default)
     * @return Map(Integer, List(names)) of names removed on the specific step
     */
    public Map<Integer, List<String>> removeNameDuplication(boolean skipInitiatorCheck, boolean checkPerStepOnly);


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * Returns Process State stateId
     *
     * @return
     */
    public int getStateId();


    /**
     * Returns Process State name (user-friendly name)
     *
     * @return
     * @since v5.10.0
     */
    public String getStateName();


    public List<ProcessStep> getSteps();


    public String getStepsRule();


    public void setStepsRule(String stepsRule);


    /**
     * Returns rule if to allow automatically moving process to the next Process State
     *
     * @return
     */
    public boolean isAutoNextState();


    /**
     * Sets rule to allow automatically moving process to the next Process State
     *
     * @param autoNextState
     */
    public void setAutoNextState(boolean autoNextState);


    /**
     * Returns rule if to allow skip next Process State in case no users assigned to it
     *
     * @return
     * @since v5.10.0
     */
    public boolean isAutoNextStateSkipEmpty();


    /**
     * Sets rule to allow skip next Process State in case no users assigned to it
     *
     * @param nextStateSkipEmpty
     * @since v5.10.0
     */
    public void setAutoNextStateSkipEmpty(boolean nextStateSkipEmpty);


    public boolean isOptimizeRoute();


    public void setOptimizeRoute(boolean optimizeRoute);


    public AttachmentManager getAttachmentManager();


    /**
     * Defines whether Steps are locked for changes or can be adjusted
     *
     * @return
     */
    public boolean isLockSteps();


    public void setLockSteps(boolean lockSteps);


    /**
     * Defines whether Process State has Fixed or Flexible route.
     * Implemented according to the following rule: in case any steps exit - Process State has Fixed Route,
     * in case no steps defined - Process State has flexible route
     *
     * @return <code>true</code> in case process has Fixed Route (Process Steps were predefined), or <code>false</code> in case of Flexible Route
     */
    public boolean isFixedRoute();
}
