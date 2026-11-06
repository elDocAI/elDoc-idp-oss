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

import ai.eldoc.util.LabelUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.*;


/**
 * Undefined DirectoryItem Model
 *
 * "extends HashMap" is required to avoid exceptions which may appear on UI when some property is not found, e.g.:
 * jakarta.el.PropertyNotFoundException: /group.xhtml @134,118 rendered="#{not empty rowData.unit}":
 * The class 'ai.eldoc.model.directory.UndefinedNameItem' does not have the property 'unit'.
 */
public final class UndefinedNameItem extends HashMap implements DirectoryItem, Serializable {

    private static final long serialVersionUID = 1264871109508760468L;

    private static final Logger LOG = LoggerFactory.getLogger(UndefinedNameItem.class);

    private final String _systemId;
    private final String _itemNameUI;


    //
    // Cunstructors -------------------------------------------------------------------------------
    //
    public UndefinedNameItem() {
        this(""); // we need to have it here to avoid NPE as everyone around expects to have not null in getIdSys()
    }


    public UndefinedNameItem(String systemId) {
        if (systemId != null && !systemId.isEmpty()) {
            _systemId = systemId;
            _itemNameUI = LabelUtils.getLabel("directoryItem.notFound") + ": " + systemId;
        } else {
            _systemId = UUID.randomUUID().toString(); // Just assign some random ID
            _itemNameUI = LabelUtils.getLabel("directoryItem.notFound");
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public Object get(Object key) {
        String keyName = (String) key;
        if (PropertyUtils.isReadable(this, keyName)) {
            try {
                return PropertyUtils.getSimpleProperty(this, keyName);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                LOG.error(String.valueOf(key), e);
            }
            return null;
        }
        return super.get(key);
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
        return "userLink userLinkCard blocked";
    }


    @Override
    public boolean isActive() {
        return false;
    }


    @Override
    public boolean isDeleted() {
        return true;
    }


    @Override
    public int getStatus() {
        return STATUS_DELETED;
    }


    @Override
    public int getType() {
        return TYPE_LOCAL;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return String.valueOf(getIdSys());
    }


    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(_systemId);
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
        final UndefinedNameItem other = (UndefinedNameItem) obj;
        if (!Objects.equals(_systemId, other._systemId)) {
            return false;
        }
        return true;
    }

}
