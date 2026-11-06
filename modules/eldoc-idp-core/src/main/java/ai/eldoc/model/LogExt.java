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

import ai.eldoc.TenantContext;
import ai.eldoc.service.LogHelper;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Log Extension Model
 */
public final class LogExt implements Serializable {

    @Serial
    private static final long serialVersionUID = 4791950636561703359L;
    private static final Logger LOG = Logger.getLogger(LogExt.class.getName());

    private String _entId;
    private int _pState;
    private int _pStep;
    private int _pIter;
    private int _entStatus;
    private int _ptStatus;
    private String _logAccount;
    private String _entRelatedId;
    private String _entType;
    private boolean _entGlobal;
    private final Map<String, Object> _properties;

    private String _duplicateTenantId;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public LogExt() {
        _properties = new HashMap<>();
    }


    /**
     * Constructor
     *
     * @param <M>
     * @param mainDoc main document with which the log record should be associated
     */
    public <M extends BasicModel> LogExt(M mainDoc) {
        this();
        initWith(mainDoc);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Transfers data from given document fields to extended log data
     *
     * @param <M>
     * @param entity - main document for logging
     */
    public <M extends BasicModel> void initWith(M entity) {
        if (entity == null) {
            return;
        }

        try {
            setEntityId(entity.getId());

            if (entity instanceof Workflow workflow) {

                setEntityType(Log.ENTITY_TYPE_WORKFLOW + "|" + workflow.getForm());
                setEntityGlobal(isNotBlank(workflow.getTenantId()));
                setEntityRelatedId(entity.getId());
                setEntityStatus(workflow.getStatus());

                setProcessState(workflow.getState());
                setProcessStep(workflow.getStep());
                setProcessIter(workflow.getIteration());

                if (!Objects.equals(TenantContext.getCurrentTenantId(), workflow.getTenantId())) {
                    setDuplicateTenantId(workflow.getTenantId());
                }

            } else if (entity instanceof WorkflowTask wfTask) {

                setEntityType(Log.ENTITY_TYPE_WORKFLOW_TASK);
                setEntityGlobal(isNotBlank(wfTask.getTenantId()));
                setEntityRelatedId(wfTask.getParentId());
                setEntityStatus(wfTask.getStatus());

                setProcessState(wfTask.getState());
                setProcessStep(wfTask.getStep());
                setProcessIter(wfTask.getIteration());

                setProcessTaskStatus(wfTask.getTaskStatus());
                setProcessTaskDelegatedTo(wfTask.getDelegatedTo());

                setUserIP(wfTask.getProperty(WorkflowTask.PROP_USER_IP, null, String.class));
                setUserAgent(wfTask.getProperty(WorkflowTask.PROP_USER_AGENT, null, String.class));

                if (!Objects.equals(TenantContext.getCurrentTenantId(), wfTask.getTenantId())) {
                    setDuplicateTenantId(wfTask.getTenantId());
                }

            } else if (entity instanceof BpmProcessDefinition) {
                setEntityType(Log.ENTITY_TYPE_BPMPROCDEF);
            } else if (entity instanceof FormDefinition) {
                setEntityType(Log.ENTITY_TYPE_FORMDEF);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "id00255: LogExt error for _id='" + entity.getId() + "' (" + entity.getClass().getName() + ")", e);
            LogHelper.setEvent("id00255");
        }
    }


    /**
     * Applies user request meta to this record
     *
     * @param userRequestMeta contains user request info like userAgent and userIP
     * @return
     */
    public LogExt apply(UserRequestMeta userRequestMeta) {
        if (userRequestMeta != null) {
            setUserIP(userRequestMeta.getUserIP());
            setUserAgent(userRequestMeta.getUserAgent());
        }
        return this;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getEntityId() {
        return _entId;
    }


    public void setEntityId(String entId) {
        _entId = entId;
    }


    public int getProcessState() {
        return _pState;
    }


    public void setProcessState(int pState) {
        _pState = pState;
    }


    public int getProcessStep() {
        return _pStep;
    }


    public LogExt setProcessStep(int dfStep) {
        _pStep = dfStep;
        return this;
    }


    public int getProcessIter() {
        return _pIter;
    }


    public void setProcessIter(int dfIter) {
        _pIter = dfIter;
    }


    public void setEntityType(String entType) {
        _entType = entType;
    }


    public String getEntityType() {
        return _entType;
    }


    public boolean isEntityGlobal() {
        return _entGlobal;
    }


    public void setEntityGlobal(boolean entGlobal) {
        _entGlobal = entGlobal;
    }


    public int getEntityStatus() {
        return _entStatus;
    }


    public void setEntityStatus(int dfStatus) {
        _entStatus = dfStatus;
    }


    public int getProcessTaskStatus() {
        return _ptStatus;
    }


    public LogExt setProcessTaskStatus(int drTS) {
        _ptStatus = drTS;
        return this;
    }


    public String getLogAccount() {
        return _logAccount;
    }


    public LogExt setLogAccount(String logAccount) {
        _logAccount = logAccount;
        return this;
    }


    public String getEntityRelatedId() {
        return _entRelatedId;
    }


    public void setEntityRelatedId(String entRelId) {
        _entRelatedId = entRelId;
    }


    //
    // Properties Map - related methods
    //
    public Map<String, Object> getProperties() {
        return _properties;
    }


    public void setProcessTaskDelegatedTo(String drDelegatedTo) {
        _properties.put(Log.PROP_PROCESS_TASK_DELEGATE_TO, drDelegatedTo);
    }


    public void setUserIP(String userIP) {
        _properties.put(Log.PROP_USER_IP, userIP);
    }


    public void setUserAgent(String userAgent) {
        _properties.put(Log.PROP_USER_AGENT, userAgent);
    }


    public LogExt setLogAccountBy(String logAccountBy) {
        _properties.put(Log.PROP_SYSTEM_ID_BY, logAccountBy);
        return this;
    }


    public LogExt setCount(int pageCount) {
        _properties.put(Log.PROP_COUNT, pageCount);
        return this;
    }


    public String getDuplicateTenantId() {
        return _duplicateTenantId;
    }


    public void setDuplicateTenantId(String duplicateTenantId) {
        _duplicateTenantId = duplicateTenantId;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return "LogExt{entityId=" + _entId + "; account=" + _logAccount + "; entityRelatedId=" + _entRelatedId + "; entityType=" + _entType + '}';
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + Objects.hashCode(_entId);
        hash = 47 * hash + Objects.hashCode(_pState);
        hash = 47 * hash + Objects.hashCode(_pStep);
        hash = 47 * hash + Objects.hashCode(_pIter);
        hash = 47 * hash + Objects.hashCode(_entStatus);
        hash = 47 * hash + Objects.hashCode(_ptStatus);
        hash = 47 * hash + Objects.hashCode(_logAccount);
        hash = 47 * hash + Objects.hashCode(_entRelatedId);
        hash = 47 * hash + Objects.hashCode(_entType);
        hash = 47 * hash + (_entGlobal ? 1 : 0);
        hash = 47 * hash + Objects.hashCode(_properties);
        hash = 47 * hash + Objects.hashCode(_duplicateTenantId);
        return hash;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LogExt other = (LogExt) obj;
        if (!Objects.equals(_entId, other._entId)) {
            return false;
        }
        if (!Objects.equals(_logAccount, other._logAccount)) {
            return false;
        }
        if (!Objects.equals(_entRelatedId, other._entRelatedId)) {
            return false;
        }
        if (!Objects.equals(_entType, other._entType)) {
            return false;
        }
        if (_entGlobal != other._entGlobal) {
            return false;
        }
        if (!Objects.equals(_pState, other._pState)) {
            return false;
        }
        if (!Objects.equals(_pStep, other._pStep)) {
            return false;
        }
        if (!Objects.equals(_pIter, other._pIter)) {
            return false;
        }
        if (!Objects.equals(_entStatus, other._entStatus)) {
            return false;
        }
        if (!Objects.equals(_ptStatus, other._ptStatus)) {
            return false;
        }
        if (!Objects.equals(_properties, other._properties)) {
            return false;
        }
        if (!Objects.equals(_duplicateTenantId, other._duplicateTenantId)) {
            return false;
        }
        return true;
    }
}
