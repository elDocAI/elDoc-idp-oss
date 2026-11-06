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
package ai.eldoc.ai.model;

import ai.eldoc.Util;
import ai.eldoc.model.AclContext;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * AI Request Context
 */
public final class AiRequestContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 8512771961249718287L;

    private final String _id;

    private final AclContext _aclContext;
    private final String _lang;
    private final String _tenantId;

    private final Map<String, Object> _requestScopeMap;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private AiRequestContext(AclContext aclContext,String lang, String tenantId) {
        this(null, aclContext, lang, tenantId);
    }


    private AiRequestContext(String id, AclContext aclContext, String lang, String tenantId) {
        _id = Objects.requireNonNullElseGet(id, () -> Util.uuidToBase62String(UUID.randomUUID()));

        _requestScopeMap = new ConcurrentHashMap<>();
        _aclContext = aclContext;
        _lang = lang;
        _tenantId = tenantId;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static AiRequestContext forSrcIDP(String lang, String tenantId, AclContext aclContext) {
        return new AiRequestContext(aclContext, lang, tenantId);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getId() {
        return _id;
    }


    public AclContext getAclContext() {
        return _aclContext;
    }


    public String getLang() {
        return _lang;
    }


    public String getTenantId() {
        return _tenantId;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        AiRequestContext that = (AiRequestContext) o;
        return Objects.equals(_id, that._id)
                && Objects.equals(_aclContext, that._aclContext)
                && Objects.equals(_lang, that._lang)
                && Objects.equals(_tenantId, that._tenantId)
                && Objects.equals(_requestScopeMap, that._requestScopeMap);
    }


    @Override
    public int hashCode() {
        int result = Objects.hashCode(_id);
        result = 31 * result + Objects.hashCode(_aclContext);
        result = 31 * result + Objects.hashCode(_lang);
        result = 31 * result + Objects.hashCode(_tenantId);
        result = 31 * result + Objects.hashCode(_requestScopeMap);
        return result;
    }
}
