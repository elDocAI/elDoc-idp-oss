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
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import static ai.eldoc.constants.DirectoryConstants.*;
import static java.text.MessageFormat.format;


/**
 * BPM Name Item Model
 */
public final class BpmNameItem implements DirectoryItem, Serializable {

    @Serial
    private static final long serialVersionUID = -5625041511134959158L;

    public static final String BPM_PERS_INITIATORCHIEF_UI_LABEL = "general.bpm.initiatorChief";
    public static final String BPM_PERS_INITIATOR_UI_LABEL = "general.bpm.initiator";
    public static final String BPM_UNIT_INITIATOR_UI_LABEL = "general.bpm.initiatorUnit";
    public static final String BPM_GRP_TAG_UI_LABEL = "general.bpm.initiatorGroupTag";

    private final String _systemId;
    private final String _itemNameUI; // Unique NameItem's name to display on UI


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public BpmNameItem(String systemId) {
        _systemId = systemId;
        if (BPM_PERS_INITIATOR.equals(getIdSys())) {
            _itemNameUI = LabelUtils.getLabel(BPM_PERS_INITIATOR_UI_LABEL);
        } else if (BPM_PERS_INITIATORCHIEF.equals(getIdSys())) {
            _itemNameUI = LabelUtils.getLabel(BPM_PERS_INITIATORCHIEF_UI_LABEL);
        } else if (BPM_UNIT_INITIATOR.equals(getIdSys())) {
            _itemNameUI = LabelUtils.getLabel(BPM_UNIT_INITIATOR_UI_LABEL);
        } else if (getIdSys() != null && getIdSys().startsWith(BPM_GRP_TAG_PREFIX)) {
            _itemNameUI = format(LabelUtils.getLabel(BPM_GRP_TAG_UI_LABEL), getGroupTagFromSystemId(getIdSys()));
        } else {
            _itemNameUI = systemId;
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Builds BpmNameItem based on group tag
     *
     * @param groupTag
     * @return
     */
    public static BpmNameItem forGroupTag(String groupTag) {
        return new BpmNameItem(BPM_GRP_TAG_PREFIX.concat(groupTag));
    }


    /**
     * Check if the provided name is BPM name
     *
     * @param name Name to be checked
     * @return {@code true} if the provided name is BPM name, otherwise {@code false}
     */
    public static boolean isBpmName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.startsWith(BPM_GRP_TAG_PREFIX)
                || BPM_UNIT_INITIATOR.equals(name)
                || BPM_PERS_INITIATOR.equals(name)
                || BPM_PERS_INITIATORCHIEF.equals(name);
    }


    /**
     * Returns true in case the given BPM Name Item represents Group item (like Initiator's tagged group, etc.), otherwise returns false
     *
     * @param systemId System ID to be checked
     * @return {@code true} if the provided system ID represents Group item, otherwise {@code false}
     */
    public static boolean isGroupItem(String systemId) {
        return isGroupTaggedName(systemId) || BPM_UNIT_INITIATOR.equals(systemId);
    }


    public static boolean isGroupTaggedName(String systemId) {
        return systemId != null && systemId.startsWith(BPM_GRP_TAG_PREFIX);
    }


    public static String getGroupTagFromSystemId(String systemId) {
        if (systemId == null || !systemId.startsWith(BPM_GRP_TAG_PREFIX)) {
            return "";
        }
        String[] groupPrefixAndTag = systemId.split(BPM_GRP_TAG_PREFIX, 2);
        if (groupPrefixAndTag.length != 2) {
            return "";
        }
        return groupPrefixAndTag[1];
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


    /**
     * Returns true in case this BPM Name Item represents Group item (like Initiator's tagged group, etc.), otherwise returns false
     *
     * @return
     */
    @Override
    public boolean isGroupItem() {
        return isGroupItem(getIdSys());
    }


    /**
     * Returns true when this is an BPM Group Tagged DirectoryService Item
     *
     * @return
     */
    public boolean isGroupTaggedName() {
        return isGroupTaggedName(getIdSys());
    }


    /**
     * Returns Group Tag
     *
     * @return
     */
    public String getGroupTag() {
        return getGroupTagFromSystemId(getIdSys());
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
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BpmNameItem that = (BpmNameItem) o;
        return Objects.equals(_systemId, that._systemId);
    }


    @Override
    public int hashCode() {
        return Objects.hashCode(_systemId);
    }
}
