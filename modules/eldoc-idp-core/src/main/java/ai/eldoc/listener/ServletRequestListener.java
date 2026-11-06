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
package ai.eldoc.listener;

import ai.eldoc.TenantContext;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import java.util.regex.Pattern;


/**
 * Application request listener
 */
@WebListener
public class ServletRequestListener implements jakarta.servlet.ServletRequestListener {

    private final static String API_PREFIX = "/api/v";
    private final static String API_PREFIX_PATTERN = "/api/v\\d{1,2}/.*";

    private static String _contextPathAndApiPrefix = null;
    private static Pattern _contextPathAndApiPrefixPattern = null;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Perform application related hooks on request destroyed call
     *
     * @param event
     */
    @Override
    public void requestDestroyed(ServletRequestEvent event) {
        // For all API requests we need to invalidate session once request completeed
        // Without this listener requests to to the REST API endpoint will create session and cookie which can be reused without JWT
        if (event.getServletRequest() != null) {
            HttpServletRequest httpRequest = (HttpServletRequest) event.getServletRequest();

            if (_contextPathAndApiPrefix == null) {
                // Caching paths to resources for optimization
                _contextPathAndApiPrefix = httpRequest.getContextPath() + API_PREFIX;
                _contextPathAndApiPrefixPattern = Pattern.compile(httpRequest.getContextPath() + API_PREFIX_PATTERN);
            }

            if (httpRequest.getRequestURI() != null
                    && StringUtils.startsWith(httpRequest.getRequestURI(), _contextPathAndApiPrefix)
                    && _contextPathAndApiPrefixPattern.matcher(httpRequest.getRequestURI()).matches()
                    && httpRequest.getSession(false) != null) {
                // Invalidate session created while request execution to the REST API endpoint
                httpRequest.getSession(false).invalidate();
            }
        }

        // For all requests we need to clean-up tenant context id once request is completed
        TenantContext.clear();
    }

}
