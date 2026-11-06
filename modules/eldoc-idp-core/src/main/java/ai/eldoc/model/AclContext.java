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

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static ai.eldoc.constants.DirectoryConstants.*;
import static java.util.Collections.disjoint;
import static org.apache.commons.collections4.SetUtils.emptyIfNull;


/**
 * ACL Context
 */
public class AclContext implements PropertyAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = -3990749853988432958L;

    private final String _systemId;
    private final Set<String> _systemIdAliases;
    private final String _systemIdOwn;
    private final Set<String> _roles;
    private final Set<String> _groups;
    private final Set<String> _units;
    private final LinkedHashSet<String> _hier;
    private final boolean _fullAccessMode; // Flag marks that current aclContex was switched to the FullAccess mode
    private Boolean _isShare;
    private final Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //


    /**
     * AclContext Constructor
     *
     * @param systemId Subject's systemId
     * @param systemIdOwn Own systemId (systemId of the originally logged-in account before it switched to the runAs context) or {@code NULL} if not runing in runAs mode
     * @param roles Roles
     * @param groups Groups
     * @param units Units
     * @param hier Hierarchy
     */
    public AclContext(String systemId, String systemIdOwn, Set<String> roles, Set<String> groups, Set<String> units, LinkedHashSet<String> hier) {
        this(systemId, null, systemIdOwn, roles, groups, units, hier, false);
    }


    /**
     * AclContext Constructor
     *
     * @param systemId Subject's systemId
     * @param systemIdAliases Subject's systemId aliases
     * @param systemIdOwn Own systemId (systemId of the originally logged-in account before it switched to the runAs context) or {@code NULL} if not runing in runAs mode
     * @param roles Roles
     * @param groups Groups
     * @param units Units
     * @param hier Hierarchy
     */
    public AclContext(String systemId, Set<String> systemIdAliases, String systemIdOwn, Set<String> roles, Set<String> groups, Set<String> units, LinkedHashSet<String> hier) {
        this(systemId, systemIdAliases, systemIdOwn, roles, groups, units, hier, false);
    }


    /**
     * AclContext Constructor
     *
     * @param systemId Subject's systemId
     * @param systemIdAliases Subject's systemId aliases
     * @param systemIdOwn Own systemId (systemId of the originally logged-in account before it switched to the runAs context) or {@code NULL} if not runing in runAs mode
     * @param roles Roles
     * @param groups Groups
     * @param units Units
     * @param hier Hierarchy
     * @param properties
     */
    public AclContext(String systemId, Set<String> systemIdAliases, String systemIdOwn, Set<String> roles, Set<String> groups, Set<String> units, LinkedHashSet<String> hier, Map<String, Object> properties) {
        this(systemId, systemIdAliases, systemIdOwn, roles, groups, units, hier, false);
        if (properties != null && !properties.isEmpty()) {
            _properties.putAll(properties);
        }
    }


    /**
     * Private AclContext Constructor for enabling fullAccess mode
     *
     * @param systemId
     * @param roles
     * @param groups
     * @param units
     * @param hier
     */
    private AclContext(String systemId, Set<String> systemIdAliases, String systemIdOwn, Set<String> roles, Set<String> groups, Set<String> units, LinkedHashSet<String> hier, boolean fullAccess) {
        this(systemId, systemIdAliases, systemIdOwn, roles, groups, units, hier, fullAccess, null);
    }


    /**
     * Private AclContext Constructor for enabling fullAccess mode
     *
     * @param systemId
     * @param roles
     * @param groups
     * @param units
     * @param hier
     */
    private AclContext(String systemId, Set<String> systemIdAliases, String systemIdOwn, Set<String> roles, Set<String> groups, Set<String> units, LinkedHashSet<String> hier, boolean fullAccess, Map<String, Object> properties) {
        if (systemId == null || systemId.isBlank()) {
            throw new IllegalArgumentException("systemId must not be blank or null");
        }
        _systemId = systemId;
        _systemIdOwn = systemIdOwn;
        _roles = emptyIfNull(roles);
        _groups = emptyIfNull(groups);
        _units = emptyIfNull(units);
        _systemIdAliases = emptyIfNull(systemIdAliases);
        _hier = hier != null ? hier : new LinkedHashSet<>();
        _fullAccessMode = fullAccess;
        _properties = new HashMap<>();
        if (properties != null && !properties.isEmpty()) {
            _properties.putAll(properties);
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Creates copy of the current AclContext object with FullAccess mode enabled
     *
     * @return
     */
    public AclContext toFullAccessMode() {
        return new AclContext(_systemId, _systemIdAliases, _systemIdOwn, _roles, _groups, _units, _hier, true, _properties);
    }


    /**
     * Returns combined set of: systemId, groups, orgUnits and roles
     *
     * @return combined set of: systemId, groups, orgUnits and roles
     */
    public Set<String> toSet() {
        return Stream
                .of(
                        Stream.of(getSystemId()),
                        getRoles().stream(),
                        getGroups().stream(),
                        getUnits().stream()
                )
                .flatMap(idSys -> idSys)
                .collect(Collectors.toUnmodifiableSet());
    }


    /**
     * Returns combined set of: systemId, groups and orgUnits
     *
     * @return combined set of: systemId, groups and orgUnits
     */
    public Set<String> toSetSysIdGroupsUnits() {
        return Stream
                .of(
                        Stream.of(getSystemId()),
                        getGroups().stream(),
                        getUnits().stream()
                )
                .flatMap(idSys -> idSys)
                .collect(Collectors.toUnmodifiableSet());
    }


    /**
     * Returns combined set of: groups and orgUnits
     *
     * @return combined set of: groups and orgUnits
     */
    public Set<String> toSetGroupsUnits() {
        return Stream
                .of(
                        getGroups().stream(),
                        getUnits().stream()
                )
                .flatMap(idSys -> idSys)
                .collect(Collectors.toUnmodifiableSet());
    }


    /**
     * Returns set with primary systemId (internal SystemId for Internal Users or email address for External Users) in the element with the index 0
     * and aliases (what usually is a list of confirmed emails), if any present
     *
     * @return combined set of: systemId, groups and orgUnits
     */
    public LinkedHashSet<String> toSetSystemIdWithAliases() {
        return Stream
                .of(
                        Stream.of(getSystemId()),
                        getSystemIdAliases().stream()
                )
                .flatMap(idSys -> idSys)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * Returns effective systemId of the current AclContext<br>
     *
     * @return
     * @see ai.eldoc.security.SecurityUtils#getSubjectSystemId()
     */
    public String getSystemId() {
        return _systemId;
    }


    /**
     * Returns set of SystemId aliases (what usually is a list of confirmed emails), available in current AclContext
     *
     * @return
     */
    public Set<String> getSystemIdAliases() {
        return _systemIdAliases;
    }


    /**
     * Returns own systemId (systemId of the originally logged-in account before it was switched to the runAs mode)
     * or NULL in case not running in runAs mode
     *
     * @return own systemId (systemId of the originally logged-in account before it was switched to the runAs context)
     * or NULL in case not running in runAs mode
     * @see ai.eldoc.security.SecurityUtils#getSubjectSystemIdOwnOrNullIfNotRunAs() - this method used for setting this property
     * @see ai.eldoc.security.SecurityUtils#getSubjectSystemIdOwn() - just for information
     */
    public String getSystemIdOwn() {
        return _systemIdOwn;
    }


    public Set<String> getRoles() {
        return _roles;
    }


    public Set<String> getGroups() {
        return _groups;
    }


    public Set<String> getUnits() {
        return _units;
    }


    public LinkedHashSet<String> getHier() {
        return _hier;
    }


    /**
     * Indicates whether FullAccess Mode is enabled for the current AclContext object
     *
     * @return
     */
    public boolean isFullAccessMode() {
        return _fullAccessMode;
    }


    /**
     * Checks whether user contains any admin role (any role according to the pattern [elAdmin*])
     *
     * @return
     */
    public boolean isAdminAny() {
        return !disjoint(getRoles(), ROLE_ADMINS_All);
    }


    public boolean isAdminDocAny() {
        return !disjoint(getRoles(), ROLE_ADMINDOC_ALL);
    }


    public boolean isAdminDocR() {
        return getRoles().contains(ROLE_ADMINDOCR);
    }


    public boolean isAdminDocRW() {
        return getRoles().contains(ROLE_ADMINDOCRW);
    }


    public boolean isAdminDocD() {
        return getRoles().contains(ROLE_ADMINDOCD);
    }


    public boolean isAdminSysAny() {
        return !disjoint(getRoles(), ROLE_ADMINSYS_ALL);
    }


    public boolean isAdminSysR() {
        return getRoles().contains(ROLE_ADMINSYSR);
    }


    public boolean isAdminSysRW() {
        return getRoles().contains(ROLE_ADMINSYSRW);
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
        return "AclContext{" +
                "_systemId='" + _systemId + '\'' +
                ", _systemIdOwn='" + _systemIdOwn + '\'' +
                ", _fullAccessMode=" + _fullAccessMode +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AclContext that = (AclContext) o;
        return _fullAccessMode == that._fullAccessMode
                && Objects.equals(_systemId, that._systemId)
                && Objects.equals(_systemIdAliases, that._systemIdAliases)
                && Objects.equals(_systemIdOwn, that._systemIdOwn)
                && Objects.equals(_roles, that._roles)
                && Objects.equals(_groups, that._groups)
                && Objects.equals(_units, that._units)
                && Objects.equals(_hier, that._hier)
                && Objects.equals(_isShare, that._isShare)
                && Objects.equals(_properties, that._properties);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_systemId, _systemIdAliases, _systemIdOwn, _roles, _groups, _units, _hier, _fullAccessMode, _isShare, _properties);
    }
}
