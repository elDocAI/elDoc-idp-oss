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
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.web.subject.support.WebDelegatingSubject;


/**
 * Local Web Delegating Subject
 */
public class LocalWebSubject extends WebDelegatingSubject {

    //
    // Constructor --------------------------------------------------------------------------------
    //
    public LocalWebSubject(PrincipalCollection principals,
            boolean authenticated,
            String host,
            Session session,
            ServletRequest request,
            ServletResponse response,
            SecurityManager securityManager) {
        super(principals, authenticated, host, session, request, response, securityManager);
    }


    public LocalWebSubject(PrincipalCollection principals,
            boolean authenticated,
            String host,
            Session session,
            boolean sessionEnabled,
            ServletRequest request,
            ServletResponse response,
            SecurityManager securityManager) {
        super(principals, authenticated, host, session, sessionEnabled, request, response, securityManager);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * This overrides default behaviour by returning the original principles only
     *
     * @return
     */
    @Override
    public PrincipalCollection getPreviousPrincipals() {
        return this.principals;
    }

}
