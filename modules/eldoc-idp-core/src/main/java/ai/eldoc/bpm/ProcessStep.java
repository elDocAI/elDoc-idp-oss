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
package ai.eldoc.bpm;

import java.util.List;


/**
 * BPM Process Step Interface<br>
 *
 * Represents workflow step with assigned Users or Groups/Units
 */
public interface ProcessStep {

    /**
     * Adds individual participant to the current ProcessStep
     *
     * @param systemId individual's systemId
     */
    public ProcessStep addUser(String systemId);


    /**
     * Adds external individual participant (not from the local directory) to the current ProcessStep
     *
     * @param emailOrId external user email or id from the external system or integration (e.g.: vchasno, etc.)
     * @param name
     * @return
     */
    public ProcessStep addUserExternal(String emailOrId, String name);


    /**
     * Adds group of individual participant to the current ProcessStep
     *
     * @param systemId groups's systemId
     */
    public ProcessStep addGroup(String systemId);


    /**
     * Returns all individual users explicitly specified on the ProcessStep
     *
     * @return List
     */
    public List<String> getUsersAll();


    /**
     * Returns all groups explicitly specified on the ProcessStep, or only group members
     *
     * @param onlyGroupMembers true - get only group members, false - get only group names
     * @return List
     */
    public List<String> getGroupsAll(boolean onlyGroupMembers);


    /**
     * Returns all names present on the ProcessStep (both Users and Groups)
     * Depending on the parameter, it will return either group names or group members
     *
     * @param onlyGroupMembers true - get only group members, false - get only group names
     * @return List
     */
    public List<String> getNamesAll(boolean onlyGroupMembers);


    /**
     * Returns all names present on the ProcessStep (only Users and Groups (without users, who are members of groups))
     *
     * @return List containing names of users and group names
     */
    public List<String> getNamesAll();


    /**
     * Returns Process Subject from the current Process Step<br>
     * Checks Users first and then Groups by the given id
     *
     * @param id
     * @return
     */
    public ProcessSubject getProcessSubject(String id);


    /**
     * Removes name from the ProcessStep
     *
     * @param name name of user or group
     */
    public void removeName(String name);


    public void removeAllUsers();


    public void removeAllGroups();


    public boolean hasUser(String userId);


    public boolean hasGroup(String groupId);


    /**
     * Replaces one process name (systemId) with another one (systemId)
     *
     * @param oldName systemId to be replaced
     * @param newName systemId replacement
     */
    public void replaceName(final String oldName, final String newName);


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns users defined on the Process Step
     *
     * @return
     */
    public List<ProcessSubject> getUsers();


    /**
     * Returns groups defined on the Process Step
     *
     * @return
     */
    public List<ProcessSubject> getGroups();
}
