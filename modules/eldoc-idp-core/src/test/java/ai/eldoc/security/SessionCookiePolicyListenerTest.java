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
import org.apache.shiro.web.env.EnvironmentLoader;
import jakarta.servlet.SessionCookieConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCookiePolicyListenerTest {

    @Test
    void explicitOverrideClearsShirosSecureFlag() {
        assertFalse(initialize("true"));
    }

    @Test
    void absentOrDisabledOverridePreservesSecureCookies() {
        assertTrue(initialize(null));
        assertTrue(initialize("false"));
        assertTrue(initialize("invalid"));
    }

    private boolean initialize(String override) {
        AtomicBoolean secure = new AtomicBoolean(true);
        SessionCookieConfig cookies = (SessionCookieConfig) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SessionCookieConfig.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setSecure")) {
                        secure.set((Boolean) args[0]);
                        return null;
                    }
                    throw new AssertionError("Unexpected cookie change: " + method.getName());
                });
        ServletContext context = (ServletContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ServletContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getInitParameter" -> {
                        if (!SessionCookiePolicyListener.DISABLE_SECURE_COOKIE.equals(args[0])) {
                            throw new AssertionError("Unexpected parameter: " + args[0]);
                        }
                        yield override;
                    }
                    case "getSessionCookieConfig" -> cookies;
                    default -> throw new AssertionError("Unexpected call: " + method.getName());
                });
        SessionCookiePolicyListener listener = new SessionCookiePolicyListener();
        listener.attributeAdded(new ServletContextAttributeEvent(context, "unrelated", new Object()));
        assertTrue(secure.get(), "Unrelated context attributes must not change cookie policy");
        listener.attributeAdded(new ServletContextAttributeEvent(context,
                EnvironmentLoader.ENVIRONMENT_ATTRIBUTE_KEY, new Object()));
        return secure.get();
    }
}
