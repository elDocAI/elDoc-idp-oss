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

import java.util.List;
import java.util.Map;


/**
 * BPM Process Interface<br><br>
 *
 * Represents whole workflow process and contains Process States representing workflow phases like: Approval, Sign-off, Follow-up,
 * and other, including custom-defined phases.<br>
 * All Process States are differentiated by stateId.<br>
 * Process States: Draft and Archive - both must be always present on the Process.
 */
public interface Process {

    /**
     * Stores current process version
     */
    public static final int CURRENT_VERSION = 3;


    //
    // ProcessState related methods ---------------------------------------------------------------
    //
    /**
     * Returns all PorcessStates
     *
     * @return
     */
    public List<ProcessState> getStates();


    /**
     * Returns ProcessState by the given stateId
     *
     * @param stateId state Id
     * @return ProcessState in case it was found in the Process, otherwise NULL
     */
    public ProcessState getState(int stateId);


    /**
     * Returns first Process State of the working Process States (except Draft, Repo and Arch)
     *
     * @return ProcessState in case it was found in the Process, otherwise NULL
     */
    public ProcessState getStateFirst();


    /**
     * Returns last Process State of the working Process States (except Draft, Repo and Arch)
     *
     * @return ProcessState in case it was found in the Process, otherwise NULL
     */
    public ProcessState getStateLast();


    /**
     * Creates ProcessState object
     *
     * @param stateId
     * @return
     */
    public ProcessState createState(int stateId);


    /**
     * Creates and adds dynamic ProcessState to this Process with the next available stateId between 10-89<br>
     *
     * @return newly created and added dynamic ProcessState
     */
    public ProcessState addState();


    /**
     * Creates and adds ProcessState to this Process with the given stateId<br>
     * In case ProcessState already present in the Process with the given stateId - then it will be returned, otherwise new ProcessState is created and the given stateId is assigned to it
     *
     * @param stateId state Id to be added
     * @return
     */
    public ProcessState addState(int stateId);


    /**
     * Removes ProcessState with the give stateId
     *
     * @param stateId state Id to be removed
     */
    public void removeState(int stateId);


    /**
     * Checks if the given stateId is present in the Process
     *
     * @param stateId ID of the state
     */
    public boolean hasState(int stateId);


    /**
     * Returns ProcessStates map where stateId is a key and ProcessState is a value
     */
    public Map<Integer, ProcessState> getStateMap();


    /**
     * Returns List with stateId order
     *
     * @return
     */
    public List<Integer> getStateIdOrder();


    /**
     * Returns List with stateId order except Draft, Repo and Arch states
     *
     * @return
     */
    public List<Integer> getStateIdOrderExceptDraftRepoArch();


    /**
     * Returns first stateId of the working Process States (except Draft, Repo and Arch)
     *
     * @return stateId or -1 in case no working states are present (fixed or dynamic)
     */
    public int getStateIdFirst();


    /**
     * Returns last stateId of the working Process States (except Draft, Repo and Arch)
     *
     * @return stateId or -1 in case no working states are present (fixed or dynamic)
     */
    public int getStateIdLast();


    /**
     * Removes empty ProcessSteps (empty - with no assigned users) on all ProccessStates
     */
    public void removeEmptySteps();


    //
    // ProcessStep related methods ----------------------------------------------------------------
    //
    /**
     * Creates ProcessStep object
     *
     * @return
     */
    public ProcessStep createStep();


    //
    // ProcessSubject related methods -------------------------------------------------------------
    //
    /**
     * Creates ProcessSubject object
     *
     * @return
     */
    public ProcessSubject createSubject();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns all userIds present on the Process on the state with stateId: 1 (STATE_ID_APPROVE) and 2 (STATE_ID_SIGN)
     *
     * @param inclAuthor include initiator in the returned list
     * @param inclGroupMemebers {@code true} resolves group members and includes resolved userIds instead of groupIds, otherwise if {@code false} - includes groupIds only
     */
    public List<String> getNamesAll(boolean inclAuthor, boolean inclGroupMemebers);


    /**
     * Returns all (unique only) userIds present on the Process on the state with stateId: 1 (STATE_ID_APPROVE) and 2 (STATE_ID_SIGN)
     *
     * @param inclAuthor include initiator in the returned list
     * @param inclGroupMemebers {@code true} resolves group members and includes resolved userIds instead of groupIds, otherwise if {@code false} - includes groupIds only
     */
    public List<String> getNamesAllUnique(boolean inclAuthor, boolean inclGroupMemebers);


    /**
     * Returns JSON-representation of this process
     *
     * @return
     */
    public String toJson();


    public int getVer();

}
