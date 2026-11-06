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

import ai.eldoc.context.ConfigContext;
import ai.eldoc.util.JsonbUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;


/**
 * Status API
 */
@Path("/status")
public class StatusApi {

    @Inject
    private ConfigContext _configContext;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatus() {
        Map<String, Object> result = Map.of(
                "elDoc", Map.of("ver", _configContext.getVersion()),
                "API", Map.of(
                        "status", "ok",
                        "ts", JsonbUtils.instantToIsoString(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                )
        );
        return Response.ok(result).build();
    }
}
