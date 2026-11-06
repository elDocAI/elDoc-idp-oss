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
package ai.eldoc.security;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextAttributeEvent;
import jakarta.servlet.ServletContextAttributeListener;
import org.apache.shiro.web.env.EnvironmentLoader;

/**
 * Honors Shiro's explicit local HTTP override after its Jakarta EE listener runs.
 * Workaround for Shiro 3.0.1's secure-cookie condition in Faces Production mode.
 * Observes environment publication during startup, after Shiro sets its cookie policy.
 */
public class SessionCookiePolicyListener implements ServletContextAttributeListener {

    static final String DISABLE_SECURE_COOKIE =
            "org.apache.shiro.ee.secure-session-cookie.disabled";

    @Override
    public void attributeAdded(ServletContextAttributeEvent event) {
        if (!EnvironmentLoader.ENVIRONMENT_ATTRIBUTE_KEY.equals(event.getName())) {
            return;
        }
        ServletContext context = event.getServletContext();
        if (Boolean.parseBoolean(context.getInitParameter(DISABLE_SECURE_COOKIE))) {
            context.getSessionCookieConfig().setSecure(false);
        }
    }
}
