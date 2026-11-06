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
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import static ai.eldoc.constants.DirectoryConstants.SYSTEM_USERS_ID_LIST;


/**
 * SystemNameItem Model
 */
public final class SystemNameItem implements DirectoryItem, Serializable {

    @Serial
    private static final long serialVersionUID = -4732903302383138367L;

    private static final Logger LOG = LoggerFactory.getLogger(SystemNameItem.class);

    public static final String NAME_ELDOC_ROBOT = "elDocSystem";
    public static final String NAME_ELDOC_RECO_BOT = "elDocRecoBot";
    public static final String NAME_ELDOC_CONVERTER_BOT = "elDocConverterBot";
    public static final String NAME_ELDOC_ESIGN_BOT = "elDocESignBot";
    public static final String NAME_MAINTENANCE_USER_SYSTEMID = "elDocAdmin";
    public static final String NAME_ANONYMOUS = "Anonymous";

    private final String _systemId;
    private final String _itemNameUI; // Unique name of NameItem for UI display


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public SystemNameItem(String systemId) {
        _systemId = systemId;
        switch (systemId) {
            case DirectoryConstants.ELDOC_ROBOT -> _itemNameUI = NAME_ELDOC_ROBOT;
            case DirectoryConstants.ELDOC_RECO_BOT -> _itemNameUI = NAME_ELDOC_RECO_BOT;
            case DirectoryConstants.ELDOC_CONVERTER_BOT -> _itemNameUI = NAME_ELDOC_CONVERTER_BOT;
            case DirectoryConstants.ELDOC_ESIGN_BOT -> _itemNameUI = NAME_ELDOC_ESIGN_BOT;
            case DirectoryConstants.MAINTENANCE_USER_SYSTEMID -> _itemNameUI = NAME_MAINTENANCE_USER_SYSTEMID;
            case DirectoryConstants.ANONYMOUS -> _itemNameUI = NAME_ANONYMOUS;
            case null, default -> {
                _itemNameUI = systemId;
                LOG.warn("Unknown system name was provided: {}. This should never happen, please contact support in case you see this message", systemId);
            }
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Checks if the given name is a SystemUser account
     *
     * @param systemId
     * @return
     */
    public static boolean isSystemName(String systemId) {
        return SYSTEM_USERS_ID_LIST.contains(systemId);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
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
        return "userLink userLinkCard inDoc";
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
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SystemNameItem that = (SystemNameItem) o;
        return Objects.equals(_systemId, that._systemId);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_systemId);
    }
}
