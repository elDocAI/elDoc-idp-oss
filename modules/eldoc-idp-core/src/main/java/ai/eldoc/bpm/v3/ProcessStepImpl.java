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
package ai.eldoc.bpm.v3;

import ai.eldoc.bpm.ProcessStep;
import ai.eldoc.bpm.ProcessSubject;
import com.google.gson.annotations.SerializedName;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.lang3.StringUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Workflow Process Step Model (Implementation v3)
 */
public class ProcessStepImpl implements ProcessStep, Serializable {

    @Serial
    private static final long serialVersionUID = -5596893806486977735L;

    private static final String _USERS = "users";
    private static final String _GROUPS = "groups";

    @SerializedName(_USERS)
    private final List<ProcessSubject> _users;

    @SerializedName(_GROUPS)
    private final List<ProcessSubject> _groups;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ProcessStepImpl() {
        _users = new ArrayList<>();
        _groups = new ArrayList<>();
    }


    //
    // Methods  -----------------------------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessStep addUser(String userId) {
        _users.add(new ProcessSubjectImpl(userId));
        return this;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessStep addUserExternal(String emailOrId, String name) {
        _users.add(new ProcessSubjectImpl(emailOrId, name));
        return this;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessStep addGroup(String groupId) {
        _groups.add(new ProcessSubjectImpl(groupId));
        return this;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void removeName(String systemId) {
        _users.removeIf(userItem -> userItem.getId().equals(systemId));
        _groups.removeIf(groupItem -> groupItem.getId().equals(systemId));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAllGroups() {
        _groups.clear();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAllUsers() {
        _users.clear();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void replaceName(final String oldName, final String newName) {
        for (ProcessSubject userSubj : _users) {
            if (StringUtils.equals(userSubj.getId(), oldName)) {
                userSubj.setId(newName);
            }
        }
        for (ProcessSubject groupSubj : _groups) {
            if (StringUtils.equals(groupSubj.getId(), oldName)) {
                groupSubj.setId(newName);
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasGroup(String groupId) {
        return _groups.stream().anyMatch(groupSubject -> StringUtils.equals(groupSubject.getId(), groupId));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasUser(String userId) {
        return _users.stream().anyMatch(userSubject -> StringUtils.equals(userSubject.getId(), userId));
    }


    //
    // Getters (virtual) --------------------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public List<String> getUsersAll() {
        return _users.stream().map(userSubject -> userSubject.getId()).collect(Collectors.toList());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getGroupsAll(boolean onlyGroupMembers) {
        return List.of();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getNamesAll(boolean onlyGroupMembers) {
        // On updating this method's logic - update it's query implementation in WorkflowDaoMongoImpl.getInboxByParticipantAggregation()
        List<String> namesAll = new ArrayList<>();
        namesAll.addAll(getUsersAll());
        namesAll.addAll(getGroupsAll(onlyGroupMembers));
        return namesAll;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public List<String> getNamesAll() {
        List<String> names = new ArrayList<>();
        names.addAll(getUsersAll());
        names.addAll(getGroupsAll(false));
        return names;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessSubject getProcessSubject(String id) {
        return _users.stream()
                .filter(usrProcSubj -> Objects.equals(usrProcSubj.getId(), id))
                .findFirst()
                .orElse(_groups.stream()
                        .filter(grpProcSubj -> Objects.equals(grpProcSubj.getId(), id))
                        .findFirst()
                        .orElse(null)
                );
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_USERS)
    @JsonbProperty(_USERS)
    public List<ProcessSubject> getUsers() {
        return _users;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_GROUPS)
    @JsonbProperty(_GROUPS)
    public List<ProcessSubject> getGroups() {
        return _groups;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(_users);
        hash = 97 * hash + Objects.hashCode(_groups);
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
        final ProcessStepImpl other = (ProcessStepImpl) obj;
        if (!Objects.equals(_users, other._users)) {
            return false;
        }
        return Objects.equals(_groups, other._groups);
    }

}
