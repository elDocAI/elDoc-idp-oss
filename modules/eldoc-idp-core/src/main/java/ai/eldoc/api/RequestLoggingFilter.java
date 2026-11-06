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

import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;


/**
 * Request Logging Filter
 */
@Provider
public class RequestLoggingFilter implements ContainerRequestFilter {

    // See more: https://stackoverflow.com/questions/33666406/logging-request-and-response-in-one-place-with-jax-rs

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Context
    private ResourceInfo _resourceInfo;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{}[{}] {}:{}",
                    _resourceInfo.getResourceClass().getSimpleName(),
                    _resourceInfo.getResourceMethod().getName(),
                    requestContext.getMethod(),
                    requestContext.getUriInfo().getRequestUri().toString());
        }
    }

}
