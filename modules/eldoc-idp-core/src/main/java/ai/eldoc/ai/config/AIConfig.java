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

package ai.eldoc.ai.config;

import ai.eldoc.model.config.Config;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.util.Objects;

public class AIConfig extends Config {

    @Serial
    private static final long serialVersionUID = -4578036354449999302L;

    public static final String _DEFAULT_MODEL_CHAT = "dmCht";
    public static final String _DEFAULT_MODEL_AGENT = "dmAgnt";
    public static final String _DEFAULT_MODEL_VL = "dmVl";

    private String _defaultModelChat;
    private String _defaultModelAgent;
    private String _defaultModelVision;

    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_DEFAULT_MODEL_CHAT)
    public String getDefaultModelChat() {
        return _defaultModelChat;
    }


    @BsonProperty(_DEFAULT_MODEL_CHAT)
    public void setDefaultModelChat(String defaultModelChat) {
        _defaultModelChat = defaultModelChat;
    }


    @BsonProperty(_DEFAULT_MODEL_AGENT)
    public String getDefaultModelAgent() {
        return _defaultModelAgent;
    }


    @BsonProperty(_DEFAULT_MODEL_AGENT)
    public void setDefaultModelAgent(String defaultModelAgent) {
        _defaultModelAgent = defaultModelAgent;
    }


    @BsonProperty(_DEFAULT_MODEL_VL)
    public String getDefaultModelVision() {
        return _defaultModelVision;
    }


    @BsonProperty(_DEFAULT_MODEL_VL)
    public void setDefaultModelVision(String defaultModelVision) {
        _defaultModelVision = defaultModelVision;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        AIConfig aiConfig = (AIConfig) o;
        return Objects.equals(_defaultModelChat, aiConfig._defaultModelChat)
                && Objects.equals(_defaultModelAgent, aiConfig._defaultModelAgent)
                && Objects.equals(_defaultModelVision, aiConfig._defaultModelVision);
    }


    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(_defaultModelChat);
        result = 31 * result + Objects.hashCode(_defaultModelAgent);
        result = 31 * result + Objects.hashCode(_defaultModelVision);
        return result;
    }
}
