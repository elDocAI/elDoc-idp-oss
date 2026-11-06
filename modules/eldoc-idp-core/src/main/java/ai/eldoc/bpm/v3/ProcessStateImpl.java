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

import ai.eldoc.bpm.*;
import ai.eldoc.model.AttachmentManager;
import com.google.gson.annotations.SerializedName;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import static ai.eldoc.bpm.BpmConstants.*;
import static ai.eldoc.constants.DirectoryConstants.BPM_PERS_INITIATOR;


/**
 * BPM Process Phase Model (Implementation v3)
 */
public class ProcessStateImpl implements ProcessState, Serializable {

    @Serial
    private static final long serialVersionUID = 4428334851209378521L;

    private static final String _STATE_ID = "stateId";
    private static final String _STATE_NAME = "stateNm";
    private static final String _STEPS = "steps";
    private static final String _STEPS_RULE = "stepsRule";
    private static final String _AUTO_NEXT_STATE = "autoNextState";
    private static final String _AUTO_NEXT_STATE_SKIP_EMPTY = "nxtSttSkpEmpt";
    private static final String _OPTIMIZE_ROUTE = "optimizeRoute";
    private static final String _LOCK_STEPS = "lockSteps";
    private static final String _ATTACHMENT_MANAGER = "attachManager";

    @SerializedName(_STATE_ID)
    private int _stateId;

    @SerializedName(_STATE_NAME)
    private String _stateName;

    @SerializedName(_STEPS)
    private final ArrayList<ProcessStep> _steps;

    @SerializedName(_STEPS_RULE)
    private String _stepsRule;

    @SerializedName(_AUTO_NEXT_STATE)
    private boolean _autoNextState;

    @SerializedName(_AUTO_NEXT_STATE_SKIP_EMPTY)
    private boolean _autoNextStateSkipEmpty;

    @SerializedName(_OPTIMIZE_ROUTE)
    private boolean _optimizeRoute;

    @SerializedName(_LOCK_STEPS)
    private boolean _lockSteps;

    @SerializedName(_ATTACHMENT_MANAGER)
    private AttachmentManager _attachmentManager;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public ProcessStateImpl() {
        _stateId = 0;
        _steps = new ArrayList<>();
        _stepsRule = RULE_ANYACTION;
        _attachmentManager = new AttachmentManager();
    }


    public ProcessStateImpl(int stateId) {
        this();
        _stateId = stateId;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessStateImpl resetSteps() {
        _steps.clear();
        _steps.add(new ProcessStepImpl().addUser(BPM_PERS_INITIATOR));
        return this;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getNamesAll(boolean inclAuthor, boolean onlyGroupMembers) {
        List<String> namesAll = new ArrayList<>();
        for (int i = (inclAuthor ? 0 : 1); i < _steps.size(); i++) {
            namesAll.addAll(getStep(i).getNamesAll(onlyGroupMembers));
        }
        return namesAll;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public List<String> getNamesAllwGroupMembers() {
        return getNamesAll(false, true);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessSubject getProcessSubject(String id) {
        return _steps.stream()
                .map(step -> step.getProcessSubject(id))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<Integer> removeEmptySteps() {
        List<Integer> stepIndexesRemoved = new ArrayList<>();

        // Detect and remember step indexes to be removed
        for (int i = 0; i < _steps.size(); i++) {
            if (_steps.get(i).getNamesAll().isEmpty()) {
                // This is an empty step which is going to be removed
                stepIndexesRemoved.add(i);
            }
        }

        // Proceed with actual removal
        _steps.removeIf(procStep -> procStep.getNamesAll().isEmpty());
        return stepIndexesRemoved;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public List<Integer> getStepsRuleNotAllowedTaskStatuses() {
        if (RULE_APPROVE.equals(getStepsRule())) {
            return RULE_APPROVE_TSARR;
        } else if (RULE_APPRSTRCT.equals(getStepsRule())) {
            return RULE_APPRSTRCT_TSARR;
        } else {
            return RULE_ANYACTION_TSARR;
        }
    }


    /**
     * @inheritDoc
     */
    @Override
    public Map<Integer, List<String>> removeNameDuplication(boolean skipInitiatorCheck, boolean checkPerStepOnly) {
        Map<Integer, List<String>> removedNamesOnStepMap = new HashMap<>();
        List<String> namesUniqCheck = new ArrayList<>();
        for (int i = 0; i < _steps.size(); i++) {

            // Initialize List for the step
            removedNamesOnStepMap.put(i, new ArrayList<>());

            if (skipInitiatorCheck && i == 0) {
                // skips Initiator check
                continue;
            }

            ProcessStep procStep = _steps.get(i);
            // Clean Users
            Iterator<ProcessSubject> usersIterator = procStep.getUsers().iterator();
            while (usersIterator.hasNext()) {
                ProcessSubject userSubject = usersIterator.next();
                if (namesUniqCheck.contains(userSubject.getId())) {
                    // systemId is already present on the Step, remove new occurence
                    usersIterator.remove();
                    // Remember removed name on the step
                    removedNamesOnStepMap.get(i).add(userSubject.getId());
                } else {
                    namesUniqCheck.add(userSubject.getId());
                }
            }

            // Clean Groups
            Iterator<ProcessSubject> groupsIterator = procStep.getGroups().iterator();
            while (groupsIterator.hasNext()) {
                ProcessSubject groupSubject = groupsIterator.next();
                if (namesUniqCheck.contains(groupSubject.getId())) {
                    // systemId is already present on the Step, remove new occurence
                    groupsIterator.remove();
                    // Remember removed name on the step
                    removedNamesOnStepMap.get(i).add(groupSubject.getId());
                } else {
                    namesUniqCheck.add(groupSubject.getId());
                }
            }

            if (checkPerStepOnly) {
                // on per-step check we clear names array after each step
                namesUniqCheck.clear();
            }
        }

        return removedNamesOnStepMap;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessStep getStep(int stepNum) {
        return _steps.get(stepNum);
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
    public ProcessStep getStepLast() {
        if (_steps.size() == 1) {
            _steps.add(new ProcessStepImpl());
        }
        return getStep(_steps.size() - 1);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonIgnore
    @JsonbTransient
    public boolean isFixedRoute() {
        return !_steps.isEmpty();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_STATE_ID)
    @JsonbProperty(_STATE_ID)
    public int getStateId() {
        return _stateId;
    }


    @BsonProperty(_STATE_ID)
    @JsonbProperty(_STATE_ID)
    public void setStateId(int stateId) {
        _stateId = stateId;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_STATE_NAME)
    @JsonbProperty(_STATE_NAME)
    public String getStateName() {
        return _stateName;
    }


    @BsonProperty(_STATE_NAME)
    @JsonbProperty(_STATE_NAME)
    public void setStateName(String stateName) {
        _stateName = stateName;
    }


    @Override
    @BsonProperty(_STEPS)
    @JsonbProperty(_STEPS)
    public List<ProcessStep> getSteps() {
        return _steps;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_STEPS_RULE)
    @JsonbProperty(_STEPS_RULE)
    public String getStepsRule() {
        return _stepsRule;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_STEPS_RULE)
    @JsonbProperty(_STEPS_RULE)
    public void setStepsRule(String stepsRule) {
        _stepsRule = stepsRule;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_AUTO_NEXT_STATE)
    @JsonbProperty(_AUTO_NEXT_STATE)
    public boolean isAutoNextState() {
        return _autoNextState;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_AUTO_NEXT_STATE)
    @JsonbProperty(_AUTO_NEXT_STATE)
    public void setAutoNextState(boolean autoNextState) {
        _autoNextState = autoNextState;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_AUTO_NEXT_STATE_SKIP_EMPTY)
    @JsonbProperty(_AUTO_NEXT_STATE_SKIP_EMPTY)
    public boolean isAutoNextStateSkipEmpty() {
        return _autoNextStateSkipEmpty;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_AUTO_NEXT_STATE_SKIP_EMPTY)
    @JsonbProperty(_AUTO_NEXT_STATE_SKIP_EMPTY)
    public void setAutoNextStateSkipEmpty(boolean nextStateSkipEmpty) {
        _autoNextStateSkipEmpty = nextStateSkipEmpty;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_OPTIMIZE_ROUTE)
    @JsonbProperty(_OPTIMIZE_ROUTE)
    public boolean isOptimizeRoute() {
        return _optimizeRoute;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_OPTIMIZE_ROUTE)
    @JsonbProperty(_OPTIMIZE_ROUTE)
    public void setOptimizeRoute(boolean optimizeRoute) {
        _optimizeRoute = optimizeRoute;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_ATTACHMENT_MANAGER)
    @JsonbProperty(_ATTACHMENT_MANAGER)
    public AttachmentManager getAttachmentManager() {
        return _attachmentManager;
    }


    @BsonProperty(_ATTACHMENT_MANAGER)
    @JsonbProperty(_ATTACHMENT_MANAGER)
    public void setAttachmentManager(AttachmentManager attachManager) {
        _attachmentManager = attachManager;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_LOCK_STEPS)
    @JsonbProperty(_LOCK_STEPS)
    public boolean isLockSteps() {
        return _lockSteps;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @BsonProperty(_LOCK_STEPS)
    @JsonbProperty(_LOCK_STEPS)
    public void setLockSteps(boolean lockSteps) {
        _lockSteps = lockSteps;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + _stateId;
        hash = 47 * hash + Objects.hashCode(_steps);
        hash = 47 * hash + Objects.hashCode(_stepsRule);
        hash = 47 * hash + (_autoNextState ? 1 : 0);
        hash = 47 * hash + (_autoNextStateSkipEmpty ? 1 : 0);
        hash = 47 * hash + (_optimizeRoute ? 1 : 0);
        hash = 47 * hash + (_lockSteps ? 1 : 0);
        hash = 47 * hash + Objects.hashCode(_attachmentManager);
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
        final ProcessStateImpl other = (ProcessStateImpl) obj;
        if (_stateId != other._stateId) {
            return false;
        }
        if (_autoNextState != other._autoNextState) {
            return false;
        }
        if (_autoNextStateSkipEmpty != other._autoNextStateSkipEmpty) {
            return false;
        }
        if (_optimizeRoute != other._optimizeRoute) {
            return false;
        }
        if (_lockSteps != other._lockSteps) {
            return false;
        }
        if (!Objects.equals(_stepsRule, other._stepsRule)) {
            return false;
        }
        if (!Objects.equals(_steps, other._steps)) {
            return false;
        }
        return Objects.equals(_attachmentManager, other._attachmentManager);
    }

}
