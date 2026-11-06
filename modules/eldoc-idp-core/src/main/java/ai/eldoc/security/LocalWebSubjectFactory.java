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
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.*;
import org.apache.shiro.web.mgt.DefaultWebSubjectFactory;
import org.apache.shiro.web.subject.WebSubject;
import org.apache.shiro.web.subject.WebSubjectContext;


/**
 * Local Default Web Subject Factory
 */
public class LocalWebSubjectFactory extends DefaultWebSubjectFactory {

    //
    // Constructor --------------------------------------------------------------------------------
    //
    public LocalWebSubjectFactory() {
        super();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Overrides method from the super class asis (to be checked & content copied on SHIRO lib upgrades)
     * and builds custom Subject
     *
     * @param context
     * @return
     */
    @Override
    public Subject createSubject(SubjectContext context) {
        //SHIRO-646
        //Check if the existing subject is NOT a WebSubject. If it isn't, then call super.createSubject instead.
        //Creating a WebSubject from a non-web Subject will cause the ServletRequest and ServletResponse to be null, which wil fail when creating a session.
        boolean isNotBasedOnWebSubject = context.getSubject() != null && !(context.getSubject() instanceof WebSubject);
        if (!(context instanceof WebSubjectContext) || isNotBasedOnWebSubject) {
            return super.createSubject(context);
        }
        WebSubjectContext wsc = (WebSubjectContext) context;
        org.apache.shiro.mgt.SecurityManager securityManager = wsc.resolveSecurityManager();
        Session session = wsc.resolveSession();
        boolean sessionEnabled = wsc.isSessionCreationEnabled();
        PrincipalCollection principals = wsc.resolvePrincipals();
        boolean authenticated = wsc.resolveAuthenticated();
        String host = wsc.resolveHost();
        ServletRequest request = wsc.resolveServletRequest();
        ServletResponse response = wsc.resolveServletResponse();

        return new LocalWebSubject(principals, authenticated, host, session, sessionEnabled, request, response, securityManager);
    }
}
