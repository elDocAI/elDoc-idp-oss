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
package ai.eldoc.security;

import ai.eldoc.model.PropertyAwareModel;
import java.io.Serializable;
import java.util.*;


/**
 * Identity Model
 */
public class Identity implements PropertyAwareModel, Serializable {

    private static final long serialVersionUID = -7064162171622578854L;

    private final String _systemId;
    private final Set<String> _aliases;
    private final List<String> _tenants;
    private final Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private Identity(String systemId) {
        _systemId = systemId;
        _aliases = new HashSet<>();
        _tenants = new ArrayList<>();
        _properties = new HashMap<>();
    }


    public Identity(String systemId, String tenantId) {
        this(systemId);
        if (tenantId != null) {
            _tenants.add(tenantId);
        }
    }


    public Identity(String systemId, Set<String> aliases, String tenantId) {
        this(systemId, tenantId);
        if (aliases != null) {
            _aliases.addAll(aliases);
        }
    }


    public Identity(String systemId, List<String> tenantIdList) {
        this(systemId);
        _tenants.addAll(tenantIdList);
    }


    public Identity(String systemId, Set<String> aliases, List<String> tenantIdList) {
        this(systemId, tenantIdList);
        if (aliases != null) {
            _aliases.addAll(aliases);
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getSystemId() {
        return _systemId;
    }


    public List<String> getTenants() {
        return _tenants;
    }


    /**
     * Returns primary tenantId (in case any available) or null
     *
     * @return
     */
    public String getTenantId() {
        if (!_tenants.isEmpty()) {
            return _tenants.get(0);
        }
        return null;
    }


    /**
     * Returns list of aliases, that can be used to refer to this Identity
     *
     * @return
     */
    public Set<String> getAliases() {
        return _aliases;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getProperties() {
        return _properties;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return _systemId + "|" + _tenants + "|" + _aliases + "|" + _properties;
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 61 * hash + Objects.hashCode(_systemId);
        hash = 61 * hash + Objects.hashCode(_tenants);
        hash = 61 * hash + Objects.hashCode(_aliases);
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
        final Identity other = (Identity) obj;
        if (!Objects.equals(_systemId, other._systemId)) {
            return false;
        }
        if (!Objects.equals(_tenants, other._tenants)) {
            return false;
        }
        if (!Objects.equals(_aliases, other._aliases)) {
            return false;
        }
        if (!Objects.equals(_properties, other._properties)) {
            return false;
        }
        return true;
    }

}
