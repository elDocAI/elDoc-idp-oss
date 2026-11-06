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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.bpm.*;
import ai.eldoc.bpm.Process;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import static ai.eldoc.bpm.BpmConstants.*;


/**
 * BPM Process Model (Implementation v3)
 */
public class ProcessImpl implements Process, Serializable {

    @Serial
    private static final long serialVersionUID = 6860931829532301136L;

    public static final String _STATES = "states";
    public static final String _VER = "ver";

    @SerializedName(_STATES)
    private final List<ProcessState> _states;

    @SerializedName(_VER)
    private int _ver = Process.CURRENT_VERSION;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ProcessImpl() {
        _states = new ArrayList<>();
    }


    //
    // ProcessState related methods ---------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessState getState(int stateId) {
        return _states.stream().filter(procState -> procState.getStateId() == stateId).findFirst().orElse(null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public ProcessState getStateFirst() {
        return getState(getStateIdFirst());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public ProcessState getStateLast() {
        return getState(getStateIdLast());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessState createState(int stateId) {
        return new ProcessStateImpl(stateId);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessState addState() {
        synchronized (_states) {  // Synchronizing access to the states collection
            int stateId;
            for (stateId = 10; stateId <= 90; stateId++) {
                if (!hasState(stateId)) {
                    break;
                }
            }

            if (stateId == 90) {
                throw new ElDocRuntimeException("No slots for dynamic ProcessState left, checked stateIds from 10 to 89");
            }

            return addState(stateId);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessState addState(int stateId) {
        synchronized (_states) {  // Synchronizing access to the states collection
            ProcessState state = null;
            if (!hasState(stateId)) {
                state = createState(stateId);
                _states.add(state);
                // Order is defined based on the sorting of Process States by stateId
                _states.sort(Comparator.comparingInt(ProcessState::getStateId));
            }
            return state;
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void removeState(int stateId) {
        synchronized (_states) {  // Synchronizing access to the states collection
            _states.removeIf(procState -> procState.getStateId() == stateId);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasState(int stateId) {
        return _states.stream().anyMatch(procState -> procState.getStateId() == stateId);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public Map<Integer, ProcessState> getStateMap() {
        return _states.stream().collect(Collectors.toMap(
                ProcessState::getStateId, // Key mapper
                state -> state, // Value mapper
                (existing, replacement) -> existing, // In case of conflict, keep the existing
                () -> new HashMap<>(_states.size()) // Use HashMap as the Map implementation specifying the initial capacity
        ));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public List<Integer> getStateIdOrder() {
        return _states.stream().map(ProcessState::getStateId).toList();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public List<Integer> getStateIdOrderExceptDraftRepoArch() {
        List<Integer> allProcessStatesList = new ArrayList<>(getStateIdOrder());
        allProcessStatesList.removeIf(stateId -> List.of(STATE_ID_DRAFT, STATE_ID_RPSTR, STATE_ID_ARCH).contains(stateId));
        return allProcessStatesList;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public int getStateIdFirst() {
        try {
            return getStateIdOrderExceptDraftRepoArch().get(0);
        } catch (IndexOutOfBoundsException iobe) {
            return -1;
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public int getStateIdLast() {
        try {
            return getStateIdOrderExceptDraftRepoArch().get(getStateIdOrderExceptDraftRepoArch().size() - 1);
        } catch (IndexOutOfBoundsException iobe) {
            return -1;
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void removeEmptySteps() {
        _states.forEach(processState -> processState.removeEmptySteps());
    }


    //
    // ProcessStep related methods ----------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessStep createStep() {
        return new ProcessStepImpl();
    }


    //
    // ProcessSubject related methods -------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessSubject createSubject() {
        return new ProcessSubjectImpl();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getNamesAll(boolean inclAuthor, boolean inclGroupMemebers) {
        List<String> allNames = new ArrayList<>();

        if (hasState(STATE_ID_APPROVE)) {
            allNames.addAll(this.getState(STATE_ID_APPROVE).getNamesAll(inclAuthor, inclGroupMemebers));
        }

        if (hasState(STATE_ID_SIGN)) {
            allNames.addAll(this.getState(STATE_ID_SIGN).getNamesAll(inclAuthor, inclGroupMemebers));
        }

        return allNames;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getNamesAllUnique(boolean inclAuthor, boolean inclGroupMemebers) {
        List<String> namesAll = getNamesAll(inclAuthor, inclGroupMemebers);

        java.util.Set<String> namesAlluniqueSet = new HashSet<>();
        namesAlluniqueSet.addAll(namesAll);

        List<String> namesAllunique = new ArrayList<>();
        namesAllunique.addAll(namesAlluniqueSet);

        return namesAllunique;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toJson() {
        Gson gson = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
        return gson.toJson(this);
    }


    public static Process fromJson(String processJsonStr) {
        Gson gson = new GsonBuilder().registerTypeAdapter(ProcessState.class, new ProcessStateDeserialiser()).disableHtmlEscaping().serializeNulls().create();
        return (Process) gson.fromJson(processJsonStr, ProcessImpl.class);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_STATES)
    @JsonbProperty(_STATES)
    public List<ProcessState> getStates() {
        return _states;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_VER)
    @JsonbProperty(_VER)
    public int getVer() {
        return _ver;
    }


    @BsonProperty(_VER)
    @JsonbProperty(_VER)
    public void setVer(int ver) {
        _ver = ver;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProcessImpl process = (ProcessImpl) o;
        return _ver == process._ver && Objects.equals(_states, process._states);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_states, _ver);
    }
}
