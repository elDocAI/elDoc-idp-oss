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

import ai.eldoc.annotation.RestApiVisible;
import ai.eldoc.api.RestApiVisibilityStrategy;
import ai.eldoc.bpm.Process;
import ai.eldoc.bpm.ProcessableResponse;
import jakarta.json.bind.annotation.*;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import static ai.eldoc.bpm.BpmConstants.*;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Workflow Task Model
 */
@JsonbVisibility(RestApiVisibilityStrategy.class) // when private property and public getter with the same name require special handling
public class WorkflowTask implements ProcessableResponse, BasicFileAwareModel, BasicMetaAwareModel, BasicAuthorAwareModel, PropertyAwareModel, BasicTenantIdAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = -6882471801978433795L;

    public static final String _AUTHOR_BY = "authorBy"; // Author system id who actually created the workflow
    public static final String _AUTHOR_BY_JSON = "authorBy";
    public static final String _NOTE = "note";
    public static final String _NOTE_JSON = "note";
    public static final String _DATE_DUE = "dtDue";
    public static final String _DATE_DUE_JSON = "dateDue";
    public static final String _DELEGATED_FROM = "dlgFrm"; // Delegated From systemId
    public static final String _DELEGATED_FROM_JSON = "delegatedFrom"; // Delegated From systemId
    public static final String _DELEGATED_TO = "dlgTo"; // Delegated To systemId
    public static final String _DELEGATED_TO_JSON = "delegatedTo"; // Delegated To systemId

    /**
     * Workflow Task description Foreign Key ID, currently stores KF of a ListValue._id (default).
     * In case required to store KF of other models - add another field tskKFTp (TASK_KF_TYPE) for storing name of the foreign model,
     * or as another option - use a prefix: ModelType:ObjectId and keep the same field
     */
    public static final String _TASK_KF_ID = "tskKFId";
    public static final String _TASK_KF_ID_JSON = "taskKfId";
    public static final String _TASK_ID = "tskId"; // taskId for internal use
    public static final String _TASK_ID_JSON = "taskId"; // taskId for internal use
    public static final String _TASK_TO = "tskTo"; // workflow task to systemId
    public static final String _TASK_TO_JSON = "taskTo"; // workflow task to systemId
    public static final String _TASK_STATUS = "tskStts"; // WorkflowTask Task Status
    public static final String _TASK_STATUS_JSON = "taskStatus"; // WorkflowTask Task Status
    public static final String _PROCESS = "prcs";
    public static final String _PROCESS_JSON = "process";
    public static final String _REF_ITEMS = "ref";
    public static final String _REF_ITEMS_JSON = "references";

    // Workflow related variables
    public static final String _PARENT_ID = "pId";
    public static final String _PARENT_ID_JSON = "parentId";
    public static final String _PARENT_STATUS = "pStts";
    public static final String _PARENT_STATUS_JSON = "parentStatus";
    public static final String _PROCESS_STATE = "pStt";
    public static final String _PROCESS_STATE_JSON = "processState";
    public static final String _PROCESS_STEP = "pStp";
    public static final String _PROCESS_STEP_JSON = "processStep";
    public static final String _PROCESS_ITERATION = "pItr";
    public static final String _PROCESS_ITERATION_JSON = "processIteration";

    // Properties
    public static final String PROP_USER_IP = "usrIP";
    public static final String PROP_USER_AGENT = "usrAgnt";

    private String _id;
    private String _author;
    private String _authorBy;
    private String _delegatedFrom;
    private String _delegatedTo;
    private String _taskKFId;
    private int _taskId;
    private String _taskTo;
    private int _status;
    private Process _process;
    private String _note;
    private Date _dateDue;
    private String _parentId;
    private int _wfState;
    private int _wfStep;
    private int _wfIteration;
    private int _wfStatus;
    private List<String> _wfRef;
    private Meta _meta;
    private List<DbFileRef> _files;
    private Map<String, Object> _properties;
    private String _tid;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    protected WorkflowTask() {
        this(null, null);
    }


    /**
     * WorkflowTask Constructor
     *
     * @param parentId id of the parent model (which can be Workflow or FormDefinition)
     * @param authorSystemId systemId of the author
     */
    public WorkflowTask(String parentId, String authorSystemId) {
        // Init own properties
        _meta = new Meta();
        _files = new ArrayList<>();
        _properties = new TreeMap<>();
        _wfRef = new ArrayList<>();

        // Set values
        _parentId = parentId;
        _author = authorSystemId;
        _tid = null;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Creates WorkflowTask for the current state, step, iteration, status of the given Workflow
     *
     * @param workflow
     * @param authorSystemId
     * @return
     */
    public static WorkflowTask createFor(final Workflow workflow, final String authorSystemId) {
        WorkflowTask wfTask = new WorkflowTask(workflow.getId(), authorSystemId);
        wfTask.setState(workflow.getState());
        wfTask.setStatus(workflow.getStatus());
        wfTask.setStep(workflow.getStep());
        wfTask.setIteration(workflow.getIteration());
        return wfTask;
    }


    /**
     * Creates WorkflowTask for the given BpmProcessDefinition
     *
     * @param bpmProcDef
     * @param authorSystemId
     * @return
     */
    public static WorkflowTask createFor(BpmProcessDefinition bpmProcDef, final String authorSystemId) {
        WorkflowTask wfTask = new WorkflowTask(bpmProcDef.getId(), authorSystemId);
        wfTask.setState(STATE_ID_DRAFT);
        wfTask.setStatus(PROCESS_STATUS_NORMAL);
        wfTask.setStep(0);
        wfTask.setIteration(0);
        return wfTask;
    }


    /**
     * Creates WorkflowTask as Annex for the given Workflow
     *
     * @param workflow Workflow, for which task is created
     * @param authorSystemId Task author SystemID
     * @return Created WorkflowTask
     */
    public static WorkflowTask createAnnexFor(Workflow workflow, final String authorSystemId) {
        WorkflowTask wfTaskAnnex = new WorkflowTask(workflow.getId(), authorSystemId);
        wfTaskAnnex.setState(STATE_ID_ANNEX);
        wfTaskAnnex.setStatus(PROCESS_STATUS_NORMAL);
        wfTaskAnnex.setStep(0);
        wfTaskAnnex.setIteration(0);
        return wfTaskAnnex;
    }


    /**
     * Creates WorkflowTask as FollowUp (ex. resolution)
     *
     * @param wfId ID of the Workflow, for which task is created
     * @param authorSystemId Task author SystemID
     * @param followupToSystemId SystemID of the user, to whom task is assigned
     * @return Created WorkflowTask
     */
    public static WorkflowTask createFollowupFromTo(String wfId, String authorSystemId, String followupToSystemId) {
        WorkflowTask followUpWFTask = new WorkflowTask(wfId, authorSystemId);
        followUpWFTask.setState(STATE_ID_EXEC);
        followUpWFTask.setStatus(PROCESS_STATUS_NORMAL);
        followUpWFTask.setStep(1);
        followUpWFTask.setIteration(1);
        followUpWFTask.setTaskStatus(TASK_STATUS_ASSIGNMENT);
        followUpWFTask.setTaskTo(followupToSystemId);
        return followUpWFTask;
    }


    /**
     * Creates WorkflowTask Assignment from the given authorSystemId to the given taskToSystemId.<br>
     * IMPORTANT: caller is responsible for setting proper stateId, step and iteration
     *
     * @param workflow Workflow, for which task is created
     * @param authorSystemId Task author SystemID
     * @param taskToSystemId SystemID of the user, to whom task is assigned
     * @return Created WorkflowTask
     */
    public static WorkflowTask createTaskAssignmentFromTo(Workflow workflow, String authorSystemId, String taskToSystemId) {
        WorkflowTask taskAssignWFTask = new WorkflowTask(workflow.getId(), authorSystemId);
        taskAssignWFTask.setTaskStatus(TASK_STATUS_ASSIGNMENT);
        taskAssignWFTask.setStatus(PROCESS_STATUS_NORMAL);
        taskAssignWFTask.setTaskTo(taskToSystemId);
        return taskAssignWFTask;
    }


    public void apply(UserRequestMeta userRequestMeta) {
        if (userRequestMeta == null) {
            return;
        }
        setUserAgent(userRequestMeta.getUserAgent());
        setUserIP(userRequestMeta.getUserIP());
    }


    //
    // Getters (virtual) --------------------------------------------------------------------------
    //
    @Override
    @BsonIgnore
    @JsonbTransient
    public String getResolutionTo() {
        return getTaskTo();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    @RestApiVisible
    @BsonProperty(_ID)
    @JsonbProperty(_ID)
    public String getId() {
        return _id;
    }


    @RestApiVisible
    @BsonProperty(_ID)
    @JsonbProperty(_ID)
    public void setId(String id) {
        _id = id;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_AUTHOR)
    @JsonbProperty(_AUTHOR_JSON)
    public String getAuthor() {
        return _author;
    }


    @RestApiVisible
    @BsonProperty(_AUTHOR)
    @JsonbProperty(_AUTHOR_JSON)
    public void setAuthor(String author) {
        _author = author;
    }


    @RestApiVisible
    @BsonProperty(_AUTHOR_BY)
    @JsonbProperty(_AUTHOR_BY_JSON)
    public String getAuthorBy() {
        return _authorBy;
    }


    @RestApiVisible
    @BsonProperty(_AUTHOR_BY)
    @JsonbProperty(_AUTHOR_BY_JSON)
    public void setAuthorBy(String authorBy) {
        _authorBy = authorBy;
    }


    @Override
    @BsonProperty(_DELEGATED_FROM)
    @JsonbProperty(_DELEGATED_FROM_JSON)
    public String getDelegatedFrom() {
        return _delegatedFrom;
    }


    @BsonProperty(_DELEGATED_FROM)
    @JsonbProperty(_DELEGATED_FROM_JSON)
    public void setDelegatedFrom(String delegatedFrom) {
        _delegatedFrom = delegatedFrom;
    }


    @Override
    @BsonProperty(_DELEGATED_TO)
    @JsonbProperty(_DELEGATED_TO_JSON)
    public String getDelegatedTo() {
        return _delegatedTo;
    }


    @BsonProperty(_DELEGATED_TO)
    @JsonbProperty(_DELEGATED_TO_JSON)
    public void setDelegatedTo(String delegatedTo) {
        _delegatedTo = delegatedTo;
    }


    /**
     * @return assigned taskKFId (currently ListValue._id from the ListValuesDao) to this WorkflowTask
     */
    @RestApiVisible
    @BsonProperty(_TASK_KF_ID)
    @JsonbProperty(_TASK_KF_ID_JSON)
    public String getTaskKFId() {
        return _taskKFId;
    }


    /**
     * Sets taskKFId (currently ListValue._id from the ListValuesDao) on this WorkflowTask
     *
     * @param taskId Task KF ID to be assigned
     */
    @RestApiVisible
    @BsonProperty(_TASK_KF_ID)
    @JsonbProperty(_TASK_KF_ID_JSON)
    public void setTaskKFId(String taskId) {
        _taskKFId = taskId;
    }


    /**
     * @return assigned taskId to this WorkflowTask
     */
    @RestApiVisible
    @BsonProperty(_TASK_ID)
    @JsonbProperty(_TASK_ID_JSON)
    public int getTaskId() {
        return _taskId;
    }


    /**
     * @param taskId Task ID to be assigned
     */
    @RestApiVisible
    @BsonProperty(_TASK_ID)
    @JsonbProperty(_TASK_ID_JSON)
    public void setTaskId(int taskId) {
        _taskId = taskId;
    }


    @RestApiVisible
    @BsonProperty(_TASK_TO)
    @JsonbProperty(_TASK_TO_JSON)
    public String getTaskTo() {
        return _taskTo;
    }


    @RestApiVisible
    @BsonProperty(_TASK_TO)
    @JsonbProperty(_TASK_TO_JSON)
    public void setTaskTo(String taskTo) {
        _taskTo = taskTo;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_TASK_STATUS)
    @JsonbProperty(_TASK_STATUS_JSON)
    public int getTaskStatus() {
        return _status;
    }


    @RestApiVisible(writable = true)
    @BsonProperty(_TASK_STATUS)
    @JsonbProperty(_TASK_STATUS_JSON)
    public void setTaskStatus(int status) {
        _status = status;
    }


    @BsonProperty(_PROCESS)
    @JsonbProperty(_PROCESS_JSON)
    public Process getProcess() {
        return _process;
    }


    @BsonProperty(_PROCESS)
    @JsonbProperty(_PROCESS_JSON)
    public void setProcess(Process process) {
        _process = process;
    }


    @RestApiVisible
    @BsonProperty(_NOTE)
    @JsonbProperty(_NOTE_JSON)
    public String getNote() {
        return _note;
    }


    @RestApiVisible(writable = true)
    @BsonProperty(_NOTE)
    @JsonbProperty(_NOTE_JSON)
    public void setNote(String note) {
        _note = note;
    }


    @BsonProperty(_DATE_DUE)
    @JsonbProperty(_DATE_DUE_JSON)
    public Date getDateDue() {
        return _dateDue;
    }


    @BsonProperty(_DATE_DUE)
    @JsonbProperty(_DATE_DUE_JSON)
    public void setDateDue(Date dateDue) {
        _dateDue = dateDue;
    }


    @RestApiVisible
    @BsonProperty(_PARENT_ID)
    @JsonbProperty(_PARENT_ID_JSON)
    public String getParentId() {
        return _parentId;
    }


    @RestApiVisible
    @BsonProperty(_PARENT_ID)
    @JsonbProperty(_PARENT_ID_JSON)
    public void setParentId(String parentId) {
        _parentId = parentId;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_PROCESS_STATE)
    @JsonbProperty(_PROCESS_STATE_JSON)
    public int getState() {
        return _wfState;
    }


    @RestApiVisible
    @BsonProperty(_PROCESS_STATE)
    @JsonbProperty(_PROCESS_STATE_JSON)
    public void setState(int state) {
        _wfState = state;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_PROCESS_STEP)
    @JsonbProperty(_PROCESS_STEP_JSON)
    public int getStep() {
        return _wfStep;
    }


    @RestApiVisible
    @BsonProperty(_PROCESS_STEP)
    @JsonbProperty(_PROCESS_STEP_JSON)
    public void setStep(int step) {
        _wfStep = step;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_PROCESS_ITERATION)
    @JsonbProperty(_PROCESS_ITERATION_JSON)
    public int getIteration() {
        return _wfIteration;
    }


    @RestApiVisible
    @BsonProperty(_PROCESS_ITERATION)
    @JsonbProperty(_PROCESS_ITERATION_JSON)
    public void setIteration(int iteration) {
        _wfIteration = iteration;
    }


    @Override
    @RestApiVisible
    @BsonProperty(_PARENT_STATUS)
    @JsonbProperty(_PARENT_STATUS_JSON)
    public int getStatus() {
        return _wfStatus;
    }


    @BsonProperty(_PARENT_STATUS)
    @JsonbProperty(_PARENT_STATUS_JSON)
    @RestApiVisible
    public void setStatus(int status) {
        _wfStatus = status;
    }


    @RestApiVisible
    @BsonProperty(_REF_ITEMS)
    @JsonbProperty(_REF_ITEMS_JSON)
    public List<String> getRefItems() {
        return _wfRef;
    }


    @RestApiVisible
    @BsonProperty(_REF_ITEMS)
    @JsonbProperty(_REF_ITEMS_JSON)
    public void setRefItems(List<String> ref) {
        _wfRef.clear();
        if (ref != null && !ref.isEmpty()) {
            _wfRef.addAll(ref.stream().distinct().toList()); // We need to leave only unique entries
        }
    }


    @Override
    @RestApiVisible
    @BsonProperty(_META)
    @JsonbProperty(_META)
    public Meta getMeta() {
        return _meta;
    }


    @RestApiVisible
    @BsonProperty(_META)
    @JsonbProperty(_META)
    public void setMeta(Meta meta) {
        _meta = meta;
    }


    @Override
    @BsonProperty(_FILES)
    @JsonbProperty(_FILES)
    public List<DbFileRef> getFiles() {
        return _files;
    }


    @BsonProperty(_FILES)
    @JsonbProperty(_FILES)
    public void setFiles(List<DbFileRef> files) {
        _files.clear();
        if (files != null && !files.isEmpty()) {
            _files.addAll(files);
        }
    }


    @Override
    @BsonProperty(_PROPERTIES)
    @JsonbProperty(_PROPERTIES)
    public Map<String, Object> getProperties() {
        return _properties;
    }


    @BsonProperty(_PROPERTIES)
    @JsonbProperty(_PROPERTIES)
    public void setProperties(Map<String, Object> properties) {
        _properties.clear();
        if (properties != null && !properties.isEmpty()) {
            _properties.putAll(properties);
        }
    }


    /**
     * {@inheritDoc}
     */
    @BsonProperty(_TID)
    @JsonbProperty(_TID)
    @Override
    public String getTenantId() {
        return _tid;
    }


    @BsonProperty(_TID)
    @JsonbProperty(_TID)
    public void setTenantId(String tid) {
        _tid = tid;
    }


    //
    // Getters & Setters for properties -----------------------------------------------------------
    //
    @BsonIgnore
    @JsonbTransient
    public String getUserAgent() {
        return getProperty(PROP_USER_AGENT, null, String.class);
    }


    @BsonIgnore
    @JsonbTransient
    public void setUserAgent(String userAgent) {
        if (isBlank(userAgent)) {
            // Do not store default values
            removeProperty(PROP_USER_AGENT);
        }
        setProperty(PROP_USER_AGENT, userAgent);
    }


    @BsonIgnore
    @JsonbTransient
    public String getUserIP() {
        return getProperty(PROP_USER_IP, null, String.class);
    }


    @BsonIgnore
    @JsonbTransient
    public void setUserIP(String userIp) {
        if (isBlank(userIp)) {
            // Do not store default values
            removeProperty(PROP_USER_IP);
        }
        setProperty(PROP_USER_IP, userIp);
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTask that = (WorkflowTask) o;
        return _taskId == that._taskId && _status == that._status && _wfState == that._wfState && _wfStep == that._wfStep && _wfIteration == that._wfIteration && _wfStatus == that._wfStatus
                && Objects.equals(_id, that._id) && Objects.equals(_author, that._author) && Objects.equals(_authorBy, that._authorBy) && Objects.equals(_delegatedFrom, that._delegatedFrom)
                && Objects.equals(_delegatedTo, that._delegatedTo) && Objects.equals(_taskKFId, that._taskKFId) && Objects.equals(_taskTo, that._taskTo) && Objects.equals(_process, that._process)
                && Objects.equals(_note, that._note) && Objects.equals(_dateDue, that._dateDue)
                && Objects.equals(_parentId, that._parentId) && Objects.equals(_wfRef, that._wfRef) && Objects.equals(_meta, that._meta) && Objects.equals(_files, that._files) && Objects.equals(_properties, that._properties)
                && Objects.equals(_tid, that._tid);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _author, _authorBy, _delegatedFrom, _delegatedTo, _taskKFId, _taskId, _taskTo, _status, _process, _note,
                _dateDue, _parentId, _wfState, _wfStep, _wfIteration, _wfStatus, _wfRef, _meta, _files, _properties, _tid);
    }
}
