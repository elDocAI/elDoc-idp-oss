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

import ai.eldoc.context.*;
import ai.eldoc.security.SecurityUtils;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;


/**
 * User Context API
 */
@Path("/uctx")
@RequestScoped
public class UserContextApi {

    @Inject
    private UserContext _userContext;

    @Inject
    private UserUiContext _userUiContext;

    @Inject
    private LocaleContext _localeContext;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserSettings() {
        Map<String, Object> settings = Map.of(
                "ustp", _userContext.getUserType(),
                "timeZone", _userContext.getTimezone(),
                "timeFormat", _userContext.getTimeFormat(),
                "dateFormat", _userContext.getDateFormat(),
                "dateTimeFormat", _userContext.getDateTimeFormat(),
                "dateTimeFormatShort", _userContext.getDateTimeShortFormat(),
                "dateTimeFormatLong", _userContext.getDateTimeLongFormat(),
                "roles", _userContext.getAclContext().getRoles()
        );
        return RestApiUtils.buildOkResponse(settings);
    }


    @POST
    @Path("/cmd")
    public Response postCommand(JsonObject data) {
        boolean commandSuccess = false;

        if (isValuePresentAndValidString(data, "mainMenu")) {
            if ("toggle".equals(data.getString("mainMenu"))) {
                _userUiContext.mainMenuToggle();
                commandSuccess = true;
            } else if ("show".equals(data.getString("mainMenu"))) {
                _userUiContext.mainMenuShow();
                commandSuccess = true;
            } else if ("hide".equals(data.getString("mainMenu"))) {
                _userUiContext.mainMenuHide();
                commandSuccess = true;
            }
        }

        if (isValuePresentAndValidString(data, "lang")) {
            _localeContext.setLocale(data.getString("lang"));
            commandSuccess = true;
        }

        if (isValuePresentAndValidBoolean(data, "logout")) {
            // On Logout we need to redirect to the contextPath for better UX in Cloud
            SecurityUtils.getSubject().logout();
            commandSuccess = true;
        }

        if (commandSuccess) {
            return RestApiUtils.buildOkNoContentResponse();
        } else {
            return RestApiUtils.buildErrorResponse(Response.Status.BAD_REQUEST, "CMD param not known...");
        }
    }


    private boolean isValuePresentAndValidString(JsonObject data, String cmd) {
        return data.containsKey(cmd) && !data.isNull(cmd) && JsonValue.ValueType.STRING.equals(data.get(cmd).getValueType());
    }


    private boolean isValuePresentAndValidBoolean(JsonObject data, String cmd) {
        return data.containsKey(cmd) && !data.isNull(cmd) && (JsonValue.ValueType.FALSE.equals(data.get(cmd).getValueType()) || JsonValue.ValueType.TRUE.equals(data.get(cmd).getValueType()));
    }
}
