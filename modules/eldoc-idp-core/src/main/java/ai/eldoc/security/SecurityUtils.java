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

import ai.eldoc.model.AclContext;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import java.util.*;
import static ai.eldoc.constants.DirectoryConstants.*;
import static java.util.Collections.disjoint;


/**
 * elDoc Security Utils
 */
public final class SecurityUtils {

    //
    // Constructors -------------------------------------------------------------------------------
    //
    private SecurityUtils() {
    }


    //
    // Getters ------------------------------------------------------------------------------------
    //
    /**
     * Returns SecurityManager by calling {@link org.apache.shiro.SecurityUtils#getSecurityManager}
     *
     * @return
     */
    public static MultiTenantWebSecurityManager getSecurityManager() {
        return org.apache.shiro.SecurityUtils.unwrapSecurityManager(org.apache.shiro.SecurityUtils.getSecurityManager(), MultiTenantWebSecurityManager.class);
    }


    /**
     * Returns Subject by calling {@link org.apache.shiro.SecurityUtils#getSubject}
     *
     * @return
     */
    public static Subject getSubject() {
        return org.apache.shiro.SecurityUtils.getSubject();
    }


    /**
     * Returns Subject's systemId<br>
     * IMPORTANT:<br>
     * - In case current user is not logged-in (current session was not authenticated) it returns {@link ai.eldoc.constants.DirectoryConstants#ANONYMOUS};<br>
     * - In case user uses runAs account - it returns systemId of the runAs account to which it was switched, in such case use {@link
     *
     *
     * @return - systemId of the authenticated user (IMPORTANT: returns systemId of the runAs account in case it was switched to the one)<br>
     * - or {@code DirectoryConstants.ANONYMOUS} in case user was not authenticated or remembered
     */
    public static String getSubjectSystemId() {
        return getNormilizedSystemId(getSubjectIdentity());
    }


    public static Set<String> getSubjectSystemIdAliases() {
        return new LinkedHashSet<>(getSubjectIdentity().getAliases());
    }


    /**
     * Returns own systemId (systemId of the originally logged-in account despite it could be switched to the runAs context)
     *
     * @return systemId of the originally logged-in account or {@link ai.eldoc.constants.DirectoryConstants#ANONYMOUS} in case none logged
     */
    public static String getSubjectSystemIdOwn() {
        return getNormilizedSystemId(getSubjectIdentityOwn());
    }


    /**
     * Returns own systemId (systemId of the originally logged-in account despite it was switched to the runAs context)
     *
     * @return systemId of the originally logged-in account or {@code null} in case current session runs on behalf of the original user
     */
    public static String getSubjectSystemIdOwnOrNullIfNotRunAs() {
        // First we need to check whether session acts on behalf of someone else at this momemnt
        if (getSubject().isRunAs()) {
            // In such case return original systemId of the logged-in user
            return getSubjectSystemIdOwn();
        }
        return null;
    }


    /**
     * Returns Roles of the Subject (returned value can be a cached value by the relevant underlying realms)
     *
     * @return
     */
    public static Set<String> getSubjectRoles() {
        Set<String> result = new HashSet<>();
        for (Realm realm : getSecurityManager().getRealms()) {
            if (realm instanceof RolesAwareRealm rolesAwareRealm) {
                result.addAll(rolesAwareRealm.getRoles(getSubject().getPrincipals()));
            }
        }
        return result;
    }


    /**
     * Returns Group of the Subject (returned value can be a cached value by the relevant underlying realms)
     *
     * @return
     */
    public static Set<String> getSubjectGroups() {
        Set<String> result = new HashSet<>();
        for (Realm realm : getSecurityManager().getRealms()) {
            if (realm instanceof GroupsAwareRealm groupsAwareRealm) {
                result.addAll(groupsAwareRealm.getGroups(getSubject().getPrincipals()));
            }
        }
        return result;
    }


    /**
     * Returns Units of the Subject (returned value can be a cached value by the relevant underlying realms)
     *
     * @return
     */
    public static Set<String> getSubjectUnits() {
        return new HashSet<>();
    }


    /**
     * Returns Hierarchy of the Subject (returned value can be a cached value by the relevant underlying realms)
     *
     * @return
     */
    public static LinkedHashSet<String> getSubjectHierarchy() {
        return new LinkedHashSet<>();
    }


    /**
     * Returns AclData for the current Subject
     *
     * @return
     */
    public static AclContext getSubjectAclContext() {
        return new AclContext(
                getSubjectSystemId(),
                getSubjectSystemIdAliases(),
                getSubjectSystemIdOwnOrNullIfNotRunAs(), // NULL in case not in runAs mode, otherwise systemId of the initial user
                getSubjectRoles(),
                getSubjectGroups(),
                getSubjectUnits(),
                getSubjectHierarchy(),
                getSubjectIdentity().getProperties()
        );
    }


    //
    // Roles Getters ------------------------------------------------------------------------------
    //
    /**
     * Checks whether user contains any admin role (any role according to the pattern [elAdmin*])
     *
     * @return
     */
    public static boolean isSubjectAdminAny() {
        return !disjoint(getSubjectRoles(), ROLE_ADMINS_All);
    }


    public static boolean isSubjectAdminDocAny() {
        return !disjoint(getSubjectRoles(), ROLE_ADMINDOC_ALL);
    }


    public static boolean isSubjectAdminDocR() {
        return getSubjectRoles().contains(ROLE_ADMINDOCR);
    }


    public static boolean isSubjectAdminDocRW() {
        return getSubjectRoles().contains(ROLE_ADMINDOCRW);
    }


    public static boolean isSubjectAdminDocD() {
        return getSubjectRoles().contains(ROLE_ADMINDOCD);
    }


    public static boolean isSubjectAdminSysAny() {
        return !disjoint(getSubjectRoles(), ROLE_ADMINSYS_ALL);
    }


    public static boolean isSubjectAdminSysR() {
        return getSubjectRoles().contains(ROLE_ADMINSYSR);
    }


    public static boolean isSubjectAdminSysRW() {
        return getSubjectRoles().contains(ROLE_ADMINSYSRW);
    }


    //
    // Private methods ----------------------------------------------------------------------------
    //
    /**
     * Retrieves Identity from PrincipalCollection
     *
     * @param pc
     * @return Identity or {@code null}
     */
    private static Identity getIdentityFromPrincipalCollection(PrincipalCollection pc) {
        return pc == null ? null : pc.oneByType(Identity.class);
    }


    /**
     * Returns Subject's Identity
     *
     * @return Identity or {@code null}
     */
    private static Identity getSubjectIdentity() {
        return getIdentityFromPrincipalCollection(org.apache.shiro.SecurityUtils.getSubject().getPrincipals());
    }


    /**
     * Returns Subject's Identity used for the initial authentication
     *
     * @return Identity or {@code null}
     */
    private static Identity getSubjectIdentityOwn() {
        return getIdentityFromPrincipalCollection(getSubject().getPreviousPrincipals());
    }


    private static String getNormilizedSystemId(Identity subjectIdentity) {
        return subjectIdentity != null
                ? subjectIdentity.getSystemId()
                : ANONYMOUS;
    }
}
