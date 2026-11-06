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

import ai.eldoc.model.BasicModel;
import org.bson.codecs.pojo.annotations.BsonIgnore;


/**
 * * Interface declares Directory Item model
 */
public interface DirectoryItem extends BasicModel {

    //
    // Common properties --------------------------------------------------------------------------
    //
    /**
     * Property stores DirectoryItem's systemId (aka: system ID), which is a model system wide id (primary key)
     */
    public static final String _ID_SYSTEM = "_id_sys";

    /**
     * ID used in external system / directory (mostly used when type = 2)
     */
    //public static final String _ID_EXT = BasicModel._ID_EXT;
    /**
     * Property indicates the DirectoryItem status:<br>
     * 0 - normal. Deprecated value: optional, can be absent(null)<br>
     * 5 - locked<br>
     * 10 - deleted
     */
    public static final String _STATUS = "status";

    /**
     * Property indicates the DirectoryItem origin:<br>
     * 1 (TYPE_LOCAL) - internal directory<br>
     * 2 (TYPE_EXTERNAL) - external directory
     */
    public static final String _TYPE = "type";

    //
    // Static values ------------------------------------------------------------------------------
    //
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_BLOCKED = 5;
    public static final int STATUS_DELETED = 10;

    /**
     * Indicates this record uses Local Directory as the primary store.<br>
     * Default value (can be absent in documents)
     */
    public static final int TYPE_LOCAL = 1;
    /**
     * Indicates this record was synced from the External Directory
     */
    public static final int TYPE_EXTERNAL = 2;


    /**
     * Return systemId of the Directory Item
     *
     * @return systemId of the Directory Item
     */
    public String getIdSys();


    public int getStatus();


    /**
     * Indicates the DirectoryItem origin: local or external directory
     *
     * @return
     */
    public int getType();


    public String getItemNameUI();


    //
    // Default implementations --------------------------------------------------------------------
    //
    /**
     * Returns TRUE if DirectoryItem in active status (not blocked or deleted)
     *
     * @return
     */
    @BsonIgnore
    default public boolean isActive() {
        return (!isDeleted() && !isLocked());
    }


    /**
     * Returns TRUE if DirectoryItem is deleted
     *
     * @return
     */
    @BsonIgnore
    default public boolean isDeleted() {
        return getStatus() == STATUS_DELETED;
    }


    /**
     * Returns TRUE if DirectoryItem is blocked
     *
     * @return
     */
    @BsonIgnore
    default public boolean isLocked() {
        return getStatus() == STATUS_BLOCKED;
    }


    /**
     * Indicates this record is uses Local Directory as primary store
     *
     * @return
     */
    @BsonIgnore
    default public boolean isTypeLocal() {
        return TYPE_EXTERNAL != getType();
    }


    /**
     * Indicates this record is synced from the External Directory
     *
     * @return
     */
    @BsonIgnore
    default public boolean isTypeExternal() {
        return TYPE_EXTERNAL == getType();
    }


    @BsonIgnore
    default public String getItemStyleClassUI() {
        if (isActive()) {
            return "userLink userLinkCard";
        } else {
            return "userLink userLinkCard blocked";
        }
    }


    @BsonIgnore
    default public String getItemIconClassUI() {
        if (isInit()) {
            if (isGroup() || isRole() || isBpmName()) {
                return "fa-solid fa-users";
            } else if (isSystemNameItem()) {
                return "fa-regular fa-user";
            }
        }
        // for other cases
        return "fa-solid fa-ban";
    }


    @BsonIgnore
    default public boolean isGroup() {
        return false;
    }


    @BsonIgnore
    default public boolean isSystemNameItem() {
        return (this instanceof SystemNameItem);
    }


    @BsonIgnore
    default public boolean isBpmName() {
        return (this instanceof BpmNameItem);
    }


    @BsonIgnore
    default public boolean isRole() {
        return (this instanceof RoleNameItem);
    }


    @BsonIgnore
    default public boolean isGroupItem() {
        if (isGroup() || (isBpmName() && ((BpmNameItem) this).isGroupItem())) {
            // Items which represent group item
            return true;
        } else {
            // All other items to be treated as items which represent single systemId
            return false;
        }
    }


    /**
     * Flag indicating that DirectoryItem was successfully initialized, what means document was found for the given key<br>
     * If this flag equals false means that no record in the Directory was found for the given system name
     *
     * @return true if record was found for the given key<br>
     * false if no record available in the Directory for the given key
     */
    @BsonIgnore
    default public boolean isInit() {
        return !(this instanceof UndefinedNameItem);
    }
}
