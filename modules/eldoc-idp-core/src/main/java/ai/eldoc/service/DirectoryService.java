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
package ai.eldoc.service;

import ai.eldoc.comparator.ItemDI;
import ai.eldoc.exceptions.BpmNameGroupByMemberAndTagNotFoundException;
import ai.eldoc.model.AclContext;
import ai.eldoc.model.directory.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import static ai.eldoc.constants.DirectoryConstants.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Directory Service.
 * Provides management services for managing directories with Users, Groups, Units, Roles
 */
@ApplicationScoped
public class DirectoryService {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryService.class);


    //
    // General Methods ----------------------------------------------------------------------------
    //


    /**
     * Returns model based according to the requested system ID
     *
     * @param systemId - system ID of the Person, Group, OrgUnit, or BPM Name
     * @return
     */
    public DirectoryItem getDirectoryItem(final String systemId) {
        try {
            return getDirectoryItemInner(systemId);
        } catch (Exception e) {
            LOG.error("Error locating Directory Item in database by systemId: {}", systemId, e);
            LogHelper.setEvent("id00129", e, "systemId: " + systemId);
        }
        return new UndefinedNameItem(systemId);
    }


    private DirectoryItem getDirectoryItemInner(final String systemId) {
        if (isBlank(systemId)) {
            return new UndefinedNameItem();
        }

        if (SystemNameItem.isSystemName(systemId)) {
            // Init as SystemName account
            return new SystemNameItem(systemId);
        }

        if (ROLE_All.contains(systemId)) {
            // Init as Role
            return new RoleNameItem(systemId);
        }

        if (BpmNameItem.isBpmName(systemId)) {
            // Init as BPM name
            return new BpmNameItem(systemId);
        }

        LOG.warn("No record found in Directory for systemId: {}", systemId);
        return new UndefinedNameItem(systemId);
    }


    /**
     * Provides a convenient method for locating DirectoryService Item in Database by its id in the database
     *
     * @param id
     * @return
     */
    public DirectoryItem getDirectoryItemByDbId(final String id) {
        // Currently, this method was designed only for one single usage in the DirectoryLazyDataModel
        // In case it will be used in other classes - it should be combined with the getDirectoryItem(String) method
        try {
            if (isBlank(id)) {
                // Placeholder
                return new UndefinedNameItem();
            }

            if (SystemNameItem.isSystemName(id)) {
                // Init as SystemName account
                return new SystemNameItem(id);
            }

            if (ROLE_ALLUSERS.equals(id)) {
                // Init as Role
                return new RoleNameItem(id);
            }

            if (BpmNameItem.isBpmName(id)) {
                // Init as BPM name
                return new BpmNameItem(id);
            }

            LOG.warn("No record found in Directory for _id: {}", id);

        } catch (Exception e) {
            LOG.error("Error locating Directory Item in database by _id: {}", id, e);
            LogHelper.setEvent("id00128", e, "_id: " + id);
        }
        return new UndefinedNameItem(id);
    }


    /**
     * Resolves PBM name
     *
     * @param nameToBeResolved
     * @param initiatorId
     * @return
     * @throws BpmNameGroupByMemberAndTagNotFoundException
     */
    public String resolveBpmName(String nameToBeResolved, final String initiatorId) throws BpmNameGroupByMemberAndTagNotFoundException {
        String result = null;
        if (BPM_PERS_INITIATOR.equals(nameToBeResolved)) {
            result = initiatorId;
        }
        return result;
    }


    /**
     * Returns relative URL path to the given DirectoryItem
     *
     * @param di
     * @return
     */
    public String getItemUrl(DirectoryItem di) {
        return null;
    }


    /**
     * Returns relative URL path to the avatar image of the given DirectoryItem
     *
     * @param di
     * @return the image URL, or {@code null} when no avatar image is available
     */
    public String getItemPicUrl(DirectoryItem di) {
        return null;
    }


    /**
     * Returns all admin system roles
     *
     * @return
     */
    public static List<RoleNameItem> getRolesAdmins() {
        List<RoleNameItem> result = new ArrayList<>();
        for (String role : ROLE_ADMINS_All) {
            result.add(new RoleNameItem(role));
        }
        result.sort(new ItemDI());
        return result;
    }


    /**
     * Generic method for comparing two DirectoryItems in situations when a mix of DAO is being used with the single (combined) list
     * of all selected DIs (Persons, Groups, etc.) we need to have a generic method for checking whether the two DirectoryItems are equal or not
     * Method is Null safe
     *
     * @param di1
     * @param di2
     * @return
     */
    public static boolean isDirItemEqual(DirectoryItem di1, DirectoryItem di2) {
        if (di1 == null || di2 == null) {
            return false;
        }
        return Strings.CS.equals(di1.getIdSys(), di2.getIdSys());
    }


    /**
     * Returns User Roles set by User systemId
     *
     * @param systemId of the user
     * @return
     */
    public Set<String> getUserRoles(final String systemId) {
        if (isBlank(systemId)) {
            return new HashSet<>();
        }
        Set<String> localUsersRolesSet = new HashSet<>();
        // for local users (ONLY FOR LOCAL) we need to add default elDoc role [elUser] explicitly,
        // as a lot of checks rely on this default role
        localUsersRolesSet.add(ROLE_ALLUSERS);
        if (MAINTENANCE_USER_SYSTEMID.equals(systemId)) {
            // We have to assign some roles for elDoc Maintenance user as well
            localUsersRolesSet.add(ROLE_ADMINSYSRW);
        }
        return localUsersRolesSet;
    }


    /**
     * Returns Groups for the given systemId
     *
     * @param systemId of the user
     * @return
     */
    public Set<String> getUserGroups(final String systemId) {
        return new HashSet<>();
    }


    /**
     * Returns OrgUnits for the given systemId
     *
     * @param systemId of the user
     * @return
     */
    public Set<String> getUserUnits(final String systemId) {
        return new HashSet<>();
    }


    /**
     * Returns hierarchy for the given user
     *
     * @param systemId
     * @return
     */
    public LinkedHashSet<String> getUserHierarchy(final String systemId) {
        return new LinkedHashSet<>();
    }


    /**
     * Returns array which contains a given user systemId and systemIds of his groups and units
     *
     * @param systemId of the user
     * @return
     */
    public Set<String> getUserGroupsAndSysId(final String systemId) {
        if (isBlank(systemId)) {
            return new HashSet<>();
        }
        Set<String> userIdGroupsUnitsSet = getUserGroups(systemId);
        userIdGroupsUnitsSet.add(systemId);
        return userIdGroupsUnitsSet;
    }


    /**
     * Returns array which contains current user systemId, the systemIds of groups he belongs to,
     * and also systemIds of the Roles user has
     *
     * @param systemId of the user
     * @return
     */
    public Set<String> getUserGroupsAndNameAndRoles(final String systemId) {
        if (isBlank(systemId)) {
            return new HashSet<>();
        } else if (ELDOC_ROBOT.equals(systemId) || ELDOC_RECO_BOT.equals(systemId) || ELDOC_CONVERTER_BOT.equals(systemId) || ELDOC_ESIGN_BOT.equals(systemId)) {
            // For internal bots we are giving full access to the document
            return Set.of(ROLE_ADMINDOCR, ROLE_ADMINDOCRW, ROLE_ADMINDOCD);
        }
        Set<String> result = getUserGroupsAndSysId(systemId);
        result.addAll(getUserRoles(systemId));
        return result;
    }


    /**
     * Returns AclContext for the given systemId (supports user accounts and API serviceAccounts)<br><br>
     * <p>
     * IMPORTANT: For systemIds of system accounts (ELDOC_ROBOT, ELDOC_RECO_BOT, ELDOC_CONVERTER_BOT) this method returns following AclContexts:<br>
     * <pre>
     * 1. ELDOC_ROBOT         - AclContext with the ELDOC_ROBOT systemId and with all system roles
     * 2. ELDOC_RECO_BOT      - AclContext with the ELDOC_RECO_BOT systemId
     * 3. ELDOC_CONVERTER_BOT - AclContext with the ELDOC_CONVERTER_BOT systemId
     * </pre>
     *
     * @param systemId
     * @return
     */
    public AclContext getAclContext(final String systemId) {
        if (ELDOC_ROBOT.equals(systemId)) {
            // For internal ELDOC_ROBOT user we are giving full access to the document and system
            return new AclContext(systemId,
                    null,
                    ROLE_ADMINS_All,
                    null,
                    null,
                    null);
        }

        if (ELDOC_RECO_BOT.equals(systemId) || ELDOC_CONVERTER_BOT.equals(systemId) || ELDOC_ESIGN_BOT.equals(systemId)) {
            return new AclContext(systemId,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        // Otherwise it should be a user account
        return new AclContext(systemId,
                null,
                getUserRoles(systemId),
                getUserGroups(systemId),
                getUserUnits(systemId),
                getUserHierarchy(systemId));
    }


    //
    // Query Methods ------------------------------------------------------------------------------
    //


    public List<RoleNameItem> queryRole(String query) {
        return queryRole(query, ROLE_All);
    }


    /**
     * Queries Roles from the given set of roles
     *
     * @param query
     * @param rolesToQueryFrom
     * @return
     */
    public List<RoleNameItem> queryRole(String query, Set<String> rolesToQueryFrom) {
        List<RoleNameItem> result = new ArrayList<>();

        for (String roleSystemId : rolesToQueryFrom) {
            RoleNameItem role = new RoleNameItem(roleSystemId);
            if (isNotBlank(query)) {
                if (Strings.CI.contains(roleSystemId, query.trim()) || Strings.CI.contains(role.getItemNameUI(), query.trim())) {
                    result.add(role);
                }
            } else {
                result.add(role);
            }
        }

        return result;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    public static DirectoryService getInstance() {
        return CDI.current().select(DirectoryService.class).get();
    }
}
