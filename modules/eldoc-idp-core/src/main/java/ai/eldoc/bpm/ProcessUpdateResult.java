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

import java.io.Serializable;


/**
 * Process Update Result Model
 */
public class ProcessUpdateResult implements Serializable {

    private static final long serialVersionUID = -2418167091195459953L;

    private boolean _success = false;
    private boolean _returnToInitiator = false;
    private boolean _nextStep = false;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ProcessUpdateResult() {

    }


    public ProcessUpdateResult(boolean success) {
        _success = success;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public boolean isSuccess() {
        return _success;
    }


    public void setSuccess(boolean success) {
        _success = success;
    }


    public boolean isReturnToInitiator() {
        return _returnToInitiator;
    }


    public void setReturnToInitiator(boolean returnToInitiator) {
        _returnToInitiator = returnToInitiator;
    }


    public boolean isNextStep() {
        return _nextStep;
    }


    public void setNextStep(boolean nextStep) {
        _nextStep = nextStep;
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + (_success ? 1 : 0);
        hash = 37 * hash + (_returnToInitiator ? 1 : 0);
        hash = 37 * hash + (_nextStep ? 1 : 0);
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
        final ProcessUpdateResult other = (ProcessUpdateResult) obj;
        if (_success != other._success) {
            return false;
        }
        if (_returnToInitiator != other._returnToInitiator) {
            return false;
        }
        if (_nextStep != other._nextStep) {
            return false;
        }
        return true;
    }

}
