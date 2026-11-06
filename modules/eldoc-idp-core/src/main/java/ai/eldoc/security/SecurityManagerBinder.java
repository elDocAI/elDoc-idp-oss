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

import jakarta.servlet.ServletContextAttributeEvent;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.annotation.WebListener;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.web.env.EnvironmentLoader;
import org.apache.shiro.web.env.WebEnvironment;


/**
 * SecurityManager Binder Servlet Listener Class<br><br>
 *
 * Published SecurityManager as VM-static singleton so SecurityUtils.getSecurityManager() works in timers/background threads
 */
@WebListener
public class SecurityManagerBinder implements ServletContextAttributeListener {

    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public void attributeAdded(ServletContextAttributeEvent event) {
        bindIfShiroEnvironment(event);
    }


    @Override
    public void attributeReplaced(ServletContextAttributeEvent event) {
        bindIfShiroEnvironment(event);
    }


    private void bindIfShiroEnvironment(ServletContextAttributeEvent event) {
        if (!EnvironmentLoader.ENVIRONMENT_ATTRIBUTE_KEY.equals(event.getName())) {
            return;
        }

        Object value = event.getServletContext().getAttribute(EnvironmentLoader.ENVIRONMENT_ATTRIBUTE_KEY);
        if (value instanceof WebEnvironment env) {
            // Publish as VM-static singleton so SecurityUtils.getSecurityManager() works in timers/background threads
            SecurityUtils.setSecurityManager(env.getWebSecurityManager());
        }
    }
}
