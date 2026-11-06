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
package ai.eldoc.cluster.model;

import com.hazelcast.scheduledexecutor.NamedTask;
import java.io.Serializable;
import java.util.Objects;


/**
 * Multi-tenant Task Model
 */
public abstract class MultiTenantTask implements NamedTask, Serializable {

    private static final long serialVersionUID = 9142070726011014558L;

    private final String _tenantId;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    /**
     * Constructor
     *
     * @param tenantId Tenant ID
     */
    public MultiTenantTask(String tenantId) {
        if (tenantId == null) {
            throw new NullPointerException("tenantId must not be null");
        }
        _tenantId = tenantId;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getTenantId() {
        return _tenantId;
    }


    @Override
    public abstract String getName();


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(_tenantId);
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
        final MultiTenantTask other = (MultiTenantTask) obj;
        if (!Objects.equals(_tenantId, other._tenantId)) {
            return false;
        }
        return true;
    }

}
