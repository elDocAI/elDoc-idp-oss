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

import ai.eldoc.TenantContext;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SubjectContext;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;


/**
 * Multi-Tenant Security Manager Implementation for elDoc
 */
public final class MultiTenantWebSecurityManager extends DefaultWebSecurityManager {

    private static final String ATTR_TENANT_ID = "__elDoc_tenantId";


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public MultiTenantWebSecurityManager() {
        super();
        setSubjectFactory(new LocalWebSubjectFactory());
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * This method is called on each request<br>
     * Invokes default implementation and afterwards handles tenantId restore into the session attribute and TenantContext
     *
     * @param subjectContext
     * @return
     */
    @Override
    public Subject createSubject(SubjectContext subjectContext) {
        // Invoke the default implementation
        Subject newSubject = super.createSubject(subjectContext);

        // Handling for remembered user
        if (newSubject.isRemembered()) {
            // When Shiro session is restored from the 'RememberMe' cookie - it contains no tenant info,
            // as such we need to retrieve tenantId from the Identity information and store it as session attribute
            newSubject.getSession(false).setAttribute(ATTR_TENANT_ID, ((Identity) newSubject.getPrincipal()).getTenantId());
        }

        // Handling of the tenantId restore into TenantContext thread local variable
        if (newSubject.getSession(false) != null) {
            Object tenantId = newSubject.getSession(false).getAttribute(ATTR_TENANT_ID);
            if (tenantId != null) {
                TenantContext.setCurrentTenant((String) tenantId);
            }
        }

        return newSubject;
    }


    /**
     * This method is called on login action triggered via UI<br>
     * Invokes default implementation and afterwards handles tenantId restore into the session attribute
     *
     * @param subject
     * @param token
     * @return
     * @throws AuthenticationException
     */
    @Override
    public Subject login(Subject subject, AuthenticationToken token) throws AuthenticationException {
        // Invoke the default implementation
        Subject loginSubject = super.login(subject, token);

        // Set initial tenantId into TenantContext and session attribute
        String initialTenant = ((Identity) loginSubject.getPrincipal()).getTenantId();
        if (loginSubject.getSession(false) != null) {
            loginSubject.getSession(false).setAttribute(ATTR_TENANT_ID, initialTenant);
        }
        TenantContext.setCurrentTenant(initialTenant);
        return loginSubject;
    }

}
