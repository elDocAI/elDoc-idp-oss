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

import java.util.Objects;
import ai.eldoc.model.MultiTenantEvent;


/**
 * Process Step Event
 */
public class ProcessStepEvent extends MultiTenantEvent {

    private static final long serialVersionUID = 1863322593354312563L;

    private final String _systemId;
    private final String _workflowId;
    private final int _state;
    private final int _step;
    private final int _iteration;
    private final int _taskStatus;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ProcessStepEvent(String systemId, String workflowId, int state, int step, int iteration, int taskStatus, String tenantId) {
        super(tenantId);
        _systemId = systemId;
        _workflowId = workflowId;
        _state = state;
        _step = step;
        _iteration = iteration;
        _taskStatus = taskStatus;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getSystemId() {
        return _systemId;
    }


    public String getWorkflowId() {
        return _workflowId;
    }


    public int getState() {
        return _state;
    }


    public int getStep() {
        return _step;
    }


    public int getIteration() {
        return _iteration;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 53 * hash + Objects.hashCode(_systemId);
        hash = 53 * hash + Objects.hashCode(_workflowId);
        hash = 53 * hash + _state;
        hash = 53 * hash + _step;
        hash = 53 * hash + _iteration;
        hash = 53 * hash + _taskStatus;
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
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ProcessStepEvent other = (ProcessStepEvent) obj;
        if (_state != other._state) {
            return false;
        }
        if (_step != other._step) {
            return false;
        }
        if (_iteration != other._iteration) {
            return false;
        }
        if (_taskStatus != other._taskStatus) {
            return false;
        }
        if (!Objects.equals(_systemId, other._systemId)) {
            return false;
        }
        return Objects.equals(_workflowId, other._workflowId);
    }

}
