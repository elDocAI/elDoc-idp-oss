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
package ai.eldoc.model.directory;

import ai.eldoc.constants.DirectoryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.util.Objects;


/**
 * RoleNameItem Model
 */
public final class RoleNameItem implements DirectoryItem, Serializable {

    private static final long serialVersionUID = 7408077326889677411L;

    private static final Logger LOG = LoggerFactory.getLogger(RoleNameItem.class);

    private final String _systemId;
    private final String _itemNameUI;


    //
    // Cunstructors -------------------------------------------------------------------------------
    //
    public RoleNameItem(String systemId) {
        _systemId = systemId;
        _itemNameUI = DirectoryConstants.ROLE_ALLUSERS.equals(systemId) ? String.format("All users (%s)", systemId) : systemId; // For admin roles currently no labels
        if (!DirectoryConstants.ROLE_All.contains(systemId)) {
            LOG.warn("Unknown system role name was provided: {}. This should never happen, please contact support in case you see this message", systemId);
        }
    }


    //
    // Getters & Setters (DB persistance model) ---------------------------------------------------
    //
    @Override
    public String getId() {
        return getIdSys();
    }


    @Override
    public String getIdSys() {
        return _systemId;
    }


    @Override
    public String getItemNameUI() {
        return _itemNameUI;
    }


    @Override
    public String getItemStyleClassUI() {
        return "userLink userLinkCard";
    }


    @Override
    public boolean isActive() {
        return true;
    }


    @Override
    public boolean isDeleted() {
        return false;
    }


    @Override
    public int getStatus() {
        return STATUS_NORMAL;
    }


    @Override
    public int getType() {
        return TYPE_LOCAL;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    /**
     * toString() method override<br>
     * IMPORTANT: can be used in some logic, to be double-checked before changing the method behaviour
     *
     * @return
     */
    @Override
    public String toString() {
        return String.valueOf(getIdSys());
    }


    @Override
    public int hashCode() {
        int hash = 5;
        hash = 13 * hash + Objects.hashCode(_systemId);
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
        final RoleNameItem other = (RoleNameItem) obj;
        return Objects.equals(_systemId, other._systemId);
    }

}
