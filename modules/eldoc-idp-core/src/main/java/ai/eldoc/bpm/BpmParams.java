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

import ai.eldoc.model.PropertyAwareModel;
import ai.eldoc.model.WorkflowTask;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


/**
 * BPM Params Model
 */
public class BpmParams implements PropertyAwareModel, Serializable {

    private static final long serialVersionUID = 558469121081557139L;

    /**
     * systemId of the user who triggered the action
     */
    public static final String USER_SYSTEM_ID = "user.systemId";
    public static final String USER_AGENT = "user.agent";
    public static final String USER_IP = "user.ip";
    public static final String BPM_STATE = "bpm.state";
    public static final String BPM_STEP = "bpm.step";
    public static final String BPM_ITERATION = "bpm.iteration";
    public static final String BPM_STATUS = "bpm.status";
    public static final String BPM_SKIP_PROCESS_UPDATE = "bpm.skipProcessUpdate";
    public static final String IDP_RECO_REQUIRES_VALIDATION = "idp.reco.requiresValidation";
    public static final String IDP_RECO_REQUIRES_CONVERSION = "idp.reco.requiresConversion";
    public static final String IDP_CONV_REQUIRES_VALIDATION = "idp.conv.requiresValidation";

    private final Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public BpmParams() {
        _properties = new HashMap<>();
    }


    public BpmParams(String key, Object value) {
        this();
        setProperty(key, value);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void applyTo(WorkflowTask wfTask) {
        wfTask.setUserIP(getProperty(USER_IP, null, String.class));
        wfTask.setUserAgent(getProperty(USER_AGENT, null, String.class));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public BpmParams addProperty(String propName, Object propValue) {
        setProperty(propName, propValue);
        return this;
    }


    @Override
    public Map<String, Object> getProperties() {
        return _properties;
    }
}
