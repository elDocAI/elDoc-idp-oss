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

package ai.eldoc.api;

import ai.eldoc.util.LabelUtils;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Locale;


@Path("/labels")
public class LabelsApi {

    private static final String PARAM_LABEL_ID = "labelId";
    private static final String PARAM_LANG = "lang";


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLabel(
            @QueryParam(PARAM_LABEL_ID) @Pattern(regexp = "[\\w-.]{2,100}", message = "The id is not valid") String labelId,
            @QueryParam(PARAM_LANG) @DefaultValue("en") @Pattern(regexp = "[a-zA-Z-]{2,5}", message = "The localeLangTag is not valid") String localeLangTag) {
        JsonObjectBuilder jsonResponseBuilder = Json.createObjectBuilder();
        String result = LabelUtils.getLabel(labelId, Locale.forLanguageTag(localeLangTag));
        if (result != null) {
            jsonResponseBuilder.add("label", result);
        } else {
            jsonResponseBuilder.add("label", "???" + labelId + "???");
        }
        return Response.ok(jsonResponseBuilder.build()).build();
    }
}
