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
package ai.eldoc.dao;

import ai.eldoc.model.Workflow;
import ai.eldoc.model.WorkflowTask;
import com.mongodb.client.ClientSession;
import java.util.ArrayList;
import java.util.List;


/**
 * Workflow Task DAO Interface
 */
public interface WorkflowTaskDao extends BasicLazyDao<WorkflowTask>, FileAwareDao {

    public WorkflowTask find(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author, String delegatedFrom, String delegatedTo);


    public List<WorkflowTask> findAll(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author);


    public List<WorkflowTask> findAll(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author, boolean sorted);


    public List<WorkflowTask> findAll(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author, String delegatedFrom, String delegatedTo, String taskTo);


    /**
     * Deletes all WorflowTasks (including attachments) to the given Workflow ID
     *
     * @param dbClientSession
     * @param wfId of Workflow document which responses to be deleted
     * @return
     *
     */
    public boolean delete(ClientSession dbClientSession, String wfId);


    public boolean delete(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author, String delegatedFrom, String delegatedTo, String taskTo);


    public WorkflowTask findProcBackup(String wfId, Integer state, Integer iteration);


    /**
     * Counts the number of WorkflowTasks by the given parameters
     *
     * @param wfId
     * @param state
     * @param step
     * @param iteration
     * @param taskStatusArray
     * @param author
     * @return
     */
    public long count(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author);


    public long count(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author, String delegatedFrom, String delegatedTo, String resolTo);


    /**
     * Changes taskStatus for all WorkflowTasks that are in delegation chain for author on TASK_STATUS_DELEGATE_CANCEL
     *
     * @param dbClientSession
     * @param wfId UNID of parent document of responce
     * @param state state of workflow process
     * @param step step of workflow process
     * @param iteration iteration number
     * @param author author of responce
     * @param delegatedFrom user that was delegated this responce
     * @return
     */
    public boolean delegationChainCancel(ClientSession dbClientSession, String wfId, int state, int step, int iteration, String author, String delegatedFrom);


    /**
     * Returns WorkflowTasks taskStatus with meaning "Document Processed", includes delegation chain for checking
     *
     * @param wfId
     * @param state
     * @param stepNumber
     * @param iteration
     * @param delegatedFromName
     * @param authorSystemId
     * @return
     */
    public Integer getTaskStatusIfProcessed(String wfId, int state, Integer stepNumber, int iteration, String authorSystemId, String delegatedFromName);


    public ArrayList<Integer> getIretationsOnState(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author);


    /**
     * Checks are there any WorflowTasks with the given arguments
     *
     * @param wfId ID of parent document of response
     * @param state
     * @param taskStatusArray task status
     * @param step step of the process
     * @param iteration iteration number
     * @param author author of WorkflowTask
     * @return
     *
     */
    public boolean isPresent(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author);


    /**
     * Returns Assigned Workflow Task List for the given document Workflow ID and StateId
     *
     * @param wfId
     * @param stateId
     * @return
     */
    public List<WorkflowTask> getAssignedTasksList(String wfId, int stateId);


    /**
     * Returns Assigned Workflow Task by the given arguments
     *
     * @param wfId
     * @param stateId
     * @return
     */
    public WorkflowTask getAssignedTask(String wfId, int stateId, int step, int iter, String taskTo);


    /**
     * Returns Assigned Workflow Task List for the current Process Step of the given Workflow
     *
     * @param workflow
     * @return
     */
    public List<WorkflowTask> getAssignedTasksListForCurrentProcessStep(Workflow workflow);


    /**
     * Returns Assigned Workflow Task for the given User systemId on the current Process Step of the given Workflow
     *
     * @param workflow
     * @return
     */
    public WorkflowTask getAssignedTaskForUserOnCurrentProcessStep(Workflow workflow, String systemId);


    /**
     * Shifts Assigned Workflow Task step what can be used during Workflow clean-up of Process Steps on Process State
     *
     * @param wfId
     * @param stateId
     * @param oldStage
     * @param newStage
     * @return
     */
    public boolean shiftAssignedTaskStep(String wfId, int stateId, int oldStage, int newStage);

}
