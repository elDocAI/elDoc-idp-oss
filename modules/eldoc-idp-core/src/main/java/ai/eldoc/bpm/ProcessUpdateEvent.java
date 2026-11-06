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
import ai.eldoc.model.Workflow;


/**
 * Workflow Process Update Event
 */
public class ProcessUpdateEvent extends MultiTenantEvent {

    private static final long serialVersionUID = -7764584550072563040L;


    public static enum TYPE {
        NEXT_STEP,
        NEXT_STATE,
        RETURN_TO_INITIATOR
    }

    private final Workflow _workflow;
    private final TYPE _type;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ProcessUpdateEvent(Workflow workflow, TYPE type, String tenantId) {
        super(tenantId);
        _workflow = workflow;
        _type = type;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Workflow getWorkflow() {
        return _workflow;
    }


    public boolean isNextState() {
        return TYPE.NEXT_STATE.equals(_type);
    }


    public boolean isNextStep() {
        return TYPE.NEXT_STEP.equals(_type);
    }


    public boolean isReturnToInitiator() {
        return TYPE.RETURN_TO_INITIATOR.equals(_type);
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + Objects.hashCode(_workflow);
        hash = 47 * hash + Objects.hashCode(_type);
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
        final ProcessUpdateEvent other = (ProcessUpdateEvent) obj;
        if (!Objects.equals(_type, other._type)) {
            return false;
        }
        return Objects.equals(_workflow, other._workflow);
    }
}
