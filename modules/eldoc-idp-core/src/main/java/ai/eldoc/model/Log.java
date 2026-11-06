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

import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.service.LogHelper;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 * Log Model
 */
public class Log implements BasicModel, PropertyAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = 1029710059724983057L;

    public static final String ENTITY_TYPE_WORKFLOW = "wf";
    public static final String ENTITY_TYPE_WORKFLOW_IDP = ENTITY_TYPE_WORKFLOW + "|" + FormDefinition.FORM_ID_RECO; // For future use
    public static final String ENTITY_TYPE_WORKFLOW_TASK = "wft";
    public static final String ENTITY_TYPE_BPMPROCDEF = "bpd";
    public static final String ENTITY_TYPE_FORMDEF = "frmd";
    public static final String ENTITY_TYPE_FILE = "fl";

    public static final String _ENTITY_ID = "entId";
    public static final String _ENTITY_TYPE = "entTyp";
    public static final String _ENTITY_GLOBAL = "entGlb"; // Marks entity belongs to the Global DB
    public static final String _ENTITY_STATUS = "entStts";
    public static final String _ENTITY_RELATED_ID = "entRltId";

    public static final String _PROCESS_STATE = "pStt";
    public static final String _PROCESS_STEP = "pStp";
    public static final String _PROCESS_ITER = "pItr";
    public static final String _PROCESS_TASK_STATUS = "ptStts"; // Process Task Status

    public static final String _EVENT_ID = "evtId";
    public static final String _EVENT = "evt";
    public static final String _SYSTEM_ID = "sid";
    public static final String _TIMESTAMP = "ts";
    public static final String _SEVERITY = "sev";
    public static final String _STACKTRACE = "st";

    public static final String PROP_SYSTEM_ID_BY = "sidBy"; // When user acts on bahalf of another user - we put here systemId of the initial user
    public static final String PROP_PROCESS_TASK_DELEGATE_TO = "ptDlgTo";
    public static final String PROP_USER_IP = "usrIP";
    public static final String PROP_USER_AGENT = "usrAgnt";
    public static final String PROP_COUNT = "cnt";

    public static final String _PATH_PROPERTIES_SYSTEM_ID_BY = _PROPERTIES + "." + PROP_SYSTEM_ID_BY;
    public static final String _PATH_PROPERTIES_COUNT = _PROPERTIES + "." + PROP_COUNT;
    public static final String _PATH_PROPERTIES_PROCESS_TASK_DELEGATE_TO = _PROPERTIES + "." + PROP_PROCESS_TASK_DELEGATE_TO;

    private String _id;
    private Date _timeStamp;
    private String _eventId;
    private String _idSys;
    private String _event;
    private int _severity;
    private String _stackTrace;

    private String _entityId;
    private String _entityType;
    private boolean _entityGlobal;
    private String _entityRelatedId;
    private int _entityStatus;

    private int _pState;
    private int _pStep;
    private int _pIter;
    private int _ptStatus;

    private Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //


    /**
     * Log Model internal constructor, mostly used for MongoDB POJO support only
     */
    protected Log() {
    }


    /**
     * Log Model Constructor
     *
     * @param systemId of the related account
     * @param systemIdBy ID of the initial user, if action performed on behalf of other user
     */
    public Log(String systemId, String systemIdBy) {
        _idSys = systemId;
        if (systemIdBy != null && !DirectoryConstants.SYSTEM_USERS_ID_LIST.contains(systemId)) {
            // We log systemIdBy for cases when acting systemId is not a system account
            _properties = new HashMap<>();
            setProperty(PROP_SYSTEM_ID_BY, systemIdBy);
        }
        _timeStamp = new Date();
        _severity = LogHelper.INFO;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Inits log document from log extension object
     *
     * @param logExt
     */
    public void initWithLogExt(LogExt logExt) {
        if (logExt == null) {
            return;
        }

        if (logExt.getLogAccount() != null) {
            setLogAccount(logExt.getLogAccount());
            // Temporary workaround for cases when we manually set logAccount to one of the SystemAccounts
            // We use it in services when actions are triggered by end-user actions, thus performed in context of some specific user
            // Ideally we need to create a thread with context of SystemAccount user so everything will work out-of-the-box without this workaround
            if (DirectoryConstants.SYSTEM_USERS_ID_LIST.contains(logExt.getLogAccount()) && _properties != null) {
                removeProperty(PROP_SYSTEM_ID_BY); // Make sure we do not store systemIdBy as it may be confusing on the logs view page and in general
            }
        }

        setEntityId(logExt.getEntityId());
        setEntityRelatedId(logExt.getEntityRelatedId());
        setEntityType(logExt.getEntityType());
        setEntityGlobal(logExt.isEntityGlobal());
        setEntityStatus(logExt.getEntityStatus());
        setProcessState(logExt.getProcessState());
        setProcessStep(logExt.getProcessStep());
        setProcessIter(logExt.getProcessIter());
        setProcessTaskStatus(logExt.getProcessTaskStatus());

        if (logExt.getProperties() != null && !logExt.getProperties().isEmpty()) {
            if (_properties == null) {
                _properties = new HashMap<>(); // We add properties map only when there are any values in it, otherwise keep it as NULL
            }
            logExt.getProperties().forEach((key, value) -> {
                if (value == null || (value instanceof String valueStr && valueStr.isBlank())) {
                    _properties.remove(key); // Logic for values removal from _properties, in case for example sidBy is required to be cleared
                } else {
                    _properties.put(key, value);
                }
            });
        }
    }


    @BsonIgnore
    public String getSeverityText() {
        return getSeverityText(getSeverity());
    }


    public static String getSeverityText(int severity) {
        return switch (severity) {
            case 1 -> "error";
            case 2 -> "warning";
            case 3 -> "info";
            case 4 -> "success";
            default -> String.valueOf(severity);
        };
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    public String getId() {
        return _id;
    }


    public void setId(String id) {
        _id = id;
    }


    @BsonProperty(_ENTITY_ID)
    public String getEntityId() {
        return _entityId;
    }


    @BsonProperty(_ENTITY_ID)
    public void setEntityId(String entityId) {
        _entityId = entityId;
    }


    @BsonProperty(_ENTITY_TYPE)
    public String getEntityType() {
        return _entityType;
    }


    @BsonProperty(_ENTITY_TYPE)
    public void setEntityType(String entType) {
        _entityType = entType;
    }


    @BsonProperty(_ENTITY_GLOBAL)
    public boolean isEntityGlobal() {
        return _entityGlobal;
    }


    @BsonProperty(_ENTITY_GLOBAL)
    public void setEntityGlobal(boolean entityGlobal) {
        _entityGlobal = entityGlobal;
    }


    @BsonProperty(_ENTITY_RELATED_ID)
    public String getEntityRelatedId() {
        return _entityRelatedId;
    }


    @BsonProperty(_ENTITY_RELATED_ID)
    public void setEntityRelatedId(String entRltId) {
        _entityRelatedId = entRltId;
    }


    @BsonProperty(_PROCESS_STATE)
    public int getProcessState() {
        return _pState;
    }


    @BsonProperty(_PROCESS_STATE)
    public void setProcessState(int pState) {
        _pState = pState;
    }


    @BsonProperty(_PROCESS_STEP)
    public int getProcessStep() {
        return _pStep;
    }


    @BsonProperty(_PROCESS_STEP)
    public void setProcessStep(int pStep) {
        _pStep = pStep;
    }


    @BsonProperty(_PROCESS_ITER)
    public int getProcessIter() {
        return _pIter;
    }


    @BsonProperty(_PROCESS_ITER)
    public void setProcessIter(int pIter) {
        _pIter = pIter;
    }


    @BsonProperty(_ENTITY_STATUS)
    public int getEntityStatus() {
        return _entityStatus;
    }


    @BsonProperty(_ENTITY_STATUS)
    public void setEntityStatus(int entStatus) {
        _entityStatus = entStatus;
    }


    @BsonProperty(_PROCESS_TASK_STATUS)
    public int getProcessTaskStatus() {
        return _ptStatus;
    }


    @BsonProperty(_PROCESS_TASK_STATUS)
    public void setProcessTaskStatus(int ptStatus) {
        _ptStatus = ptStatus;
    }


    @BsonProperty(_EVENT_ID)
    public String getEventId() {
        return _eventId;
    }


    @BsonProperty(_EVENT_ID)
    public void setEventId(String eventId) {
        _eventId = eventId;
    }


    @BsonProperty(_SYSTEM_ID)
    public String getLogAccount() {
        return _idSys;
    }


    @BsonProperty(_SYSTEM_ID)
    public void setLogAccount(String logAccount) {
        _idSys = logAccount;
    }


    @BsonProperty(_EVENT)
    public String getEvent() {
        return _event;
    }


    @BsonProperty(_EVENT)
    public void setEvent(String event) {
        _event = event;
    }


    @BsonProperty(_TIMESTAMP)
    public Date getTimeStamp() {
        return _timeStamp;
    }


    @BsonProperty(_TIMESTAMP)
    public void setTimeStamp(Date logTime) {
        _timeStamp = logTime;
    }


    @BsonProperty(_STACKTRACE)
    public String getStackTrace() {
        return _stackTrace;
    }


    @BsonProperty(_STACKTRACE)
    public void setStackTrace(String stackTrace) {
        _stackTrace = stackTrace;
    }


    @BsonProperty(_SEVERITY)
    public int getSeverity() {
        return _severity;
    }


    @BsonProperty(_SEVERITY)
    public void setSeverity(int severity) {
        _severity = severity;
    }


    /**
     * Returns log Ext data in case any available, otherwise returns null
     *
     * @return
     */
    @Override
    @BsonProperty(_PROPERTIES)
    public Map<String, Object> getProperties() {
        return _properties;
    }


    /**
     * Sets log Ext data
     *
     * @param properties
     */
    @BsonProperty(_PROPERTIES)
    public void setProperties(Map<String, Object> properties) {
        _properties = properties;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return "Log{_id=" + _id + "; eventId=" + _eventId + "; logAction=" + _event + '}';
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Log log = (Log) o;
        return _severity == log._severity
                && _entityGlobal == log._entityGlobal
                && _entityStatus == log._entityStatus
                && _pState == log._pState
                && _pStep == log._pStep
                && _pIter == log._pIter
                && _ptStatus == log._ptStatus
                && Objects.equals(_id, log._id)
                && Objects.equals(_timeStamp, log._timeStamp)
                && Objects.equals(_eventId, log._eventId)
                && Objects.equals(_idSys, log._idSys)
                && Objects.equals(_event, log._event)
                && Objects.equals(_stackTrace, log._stackTrace)
                && Objects.equals(_entityId, log._entityId)
                && Objects.equals(_entityType, log._entityType)
                && Objects.equals(_entityRelatedId, log._entityRelatedId)
                && Objects.equals(_properties, log._properties);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_id, _timeStamp, _eventId, _idSys, _event, _severity, _stackTrace, _entityId, _entityType, _entityGlobal, _entityRelatedId, _entityStatus, _pState, _pStep, _pIter, _ptStatus, _properties);
    }


}
