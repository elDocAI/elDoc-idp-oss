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

import org.apache.shiro.authz.SimpleAuthorizationInfo;
import java.util.*;


/**
 * elDoc Authorization Info
 */
public class ExtendedAuthorizationInfo extends SimpleAuthorizationInfo {

    private static final long serialVersionUID = -513388850166082678L;

    private Set<String> _groups;
    private Set<String> _units;
    private LinkedHashSet<String> _hierarchy;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    //
    // Methods ------------------------------------------------------------------------------------
    //
    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Set<String> getGroups() {
        return _groups;
    }


    public void setGroups(Set<String> groups) {
        _groups = groups;
    }


    public Set<String> getUnits() {
        return _units;
    }


    public void setUnits(Set<String> units) {
        _units = units;
    }


    /**
     * Returns the hierarchy chain assigned to the account
     *
     * @return
     */
    public LinkedHashSet<String> getHierarchy() {
        return _hierarchy;
    }


    /**
     * Sets the hierarchy chain assigned to the account
     *
     * @param hierarchy the hierarchy chain assigned to the account
     */
    public void setHierarchy(LinkedHashSet<String> hierarchy) {
        _hierarchy = hierarchy;
    }


    /**
     * Adds hierarchy nodes to the end of the chain
     *
     * @param hierarchyList
     */
    public void addHierarchy(List<String> hierarchyList) {
        if (_hierarchy == null) {
            _hierarchy = new LinkedHashSet<>();
        }
        _hierarchy.addAll(hierarchyList);
    }


    /**
     * Adds hierarchy node to the end of the chain
     *
     * @param hierarchy
     */
    public void addHierarchy(String hierarchy) {
        if (_hierarchy == null) {
            _hierarchy = new LinkedHashSet<>();
        }
        _hierarchy.add(hierarchy);
    }
}
