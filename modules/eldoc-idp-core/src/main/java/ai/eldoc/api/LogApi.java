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

import ai.eldoc.dao.LogDao;
import ai.eldoc.model.Log;
import ai.eldoc.security.SecurityUtils;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import static ai.eldoc.api.RestApiUtils.buildErrorResponse;
import static ai.eldoc.api.RestApiUtils.buildOkResponse;


/**
 * Log REST Service
 */
@Path("/log")
@RequestScoped
public class LogApi {

    private static final Logger LOG = Logger.getLogger(LogApi.class.getName());

    @Inject
    private LogDao _logDao;


    /**
     * Finds Log entry by provided ID and returns it's stacktrace
     *
     * @param id
     * @return
     */
    @GET
    @Path("/{id}/stacktrace")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getLogStacktrace(@PathParam("id") @Pattern(regexp = "^[0-9abcdef]{24}$", message = "Param [id] is not valid") String id) {
        try {
            if (!SecurityUtils.isSubjectAdminSysAny()) {
                return buildErrorResponse(Response.Status.FORBIDDEN, "Authentication required");
            }

            Log logEntry = _logDao.find(id);
            if (logEntry == null) {
                return buildErrorResponse(Response.Status.NOT_FOUND, "Log entry not found");
            }

            // We need to sanitize returned stacktrace as it may also paste part of response HTML content (e.g. 404 page)
            PolicyFactory policy = new HtmlPolicyBuilder().toFactory();
            return buildOkResponse(policy.sanitize(logEntry.getStackTrace()));

        } catch (Throwable th) {
            LOG.log(Level.SEVERE, "id=" + id, th);
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Please, contact site administrator");
        }
    }

}
