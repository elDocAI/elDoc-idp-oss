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

import java.lang.reflect.Type;
import ai.eldoc.bpm.ProcessState;
import ai.eldoc.bpm.ProcessStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;


public class ProcessStateDeserialiser implements JsonDeserializer<ProcessState> {

    @Override
    public ProcessState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            Gson gson = new GsonBuilder().registerTypeAdapter(ProcessStep.class, new ProcessStepDeserialiser()).disableHtmlEscaping().serializeNulls().create();
            return (ProcessState) gson.fromJson(json, ProcessStateImpl.class);
        } catch (Exception e) {
            throw new JsonParseException(e.getMessage());
        }
    }

}
