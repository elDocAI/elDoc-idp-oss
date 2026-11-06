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

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.web.util.WebUtils;
import org.omnifaces.util.Faces;
import org.omnifaces.util.Servlets;
import java.io.IOException;


/**
 * Pass-Thru Authentication Filter
 */
public class PassThruAuthenticationFilter extends org.apache.shiro.ee.filters.PassThruAuthenticationFilter {

    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public void saveRequestAndRedirectToLogin(ServletRequest request, ServletResponse response) throws IOException {
        // Temporary workaround for saving equest in the session instead of cookies (what was the new approach starting with Shiro v2)
        // See more:
        // https://github.com/apache/shiro/blob/shiro-root-2.0.1/support/jakarta-ee/src/main/java/org/apache/shiro/ee/filters/AuthenticationFilterDelegate.java#L153
        // https://github.com/apache/shiro/blob/shiro-root-2.0.1/support/jakarta-ee/src/main/java/org/apache/shiro/ee/filters/FormResubmitSupport.java#L165
        // https://github.com/apache/shiro/blob/shiro-root-2.0.1/support/jakarta-ee/src/main/java/org/apache/shiro/ee/filters/FormResubmitSupport.java#L343
        saveRequestAndRedirectToLogin(WebUtils.toHttp(request), WebUtils.toHttp(response), WebUtils.toHttp(request).getContextPath() + getLoginUrl());
    }


    public static void saveRequestAndRedirectToLogin(ServletRequest request, ServletResponse response, String loginPath) {
        WebUtils.saveRequest(request);
        doFacesRedirect(WebUtils.toHttp(request), WebUtils.toHttp(response), loginPath);
    }


    //
    // Private Methods ----------------------------------------------------------------------------
    //
    private static void doFacesRedirect(HttpServletRequest request, HttpServletResponse response, String loginPath) {
        if (hasFacesContext()) {
            Faces.redirect(loginPath);
        } else {
            Servlets.facesRedirect(request, response, loginPath);
        }
    }


    private static boolean hasFacesContext() {
        try {
            return Faces.hasContext();
        } catch (Throwable e) {
            return false;
        }
    }

}
