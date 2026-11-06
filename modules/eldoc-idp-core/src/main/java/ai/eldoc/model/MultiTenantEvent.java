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

import java.io.Serializable;
import java.util.Objects;


/**
 * Multi Tenant Event
 */
public class MultiTenantEvent implements Serializable {

    private static final long serialVersionUID = 4614948845883762174L;

    private final String _tenantId;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * Constructor
     *
     * @param tenantId Tenant ID
     */
    public MultiTenantEvent(String tenantId) {
        _tenantId = tenantId;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getTenantId() {
        return _tenantId;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 93 * hash + Objects.hashCode(_tenantId);
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
        final MultiTenantEvent other = (MultiTenantEvent) obj;
        if (!Objects.equals(_tenantId, other._tenantId)) {
            return false;
        }
        return true;
    }
}
