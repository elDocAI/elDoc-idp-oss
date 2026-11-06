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
package ai.eldoc.model;

import ai.eldoc.model.config.AppConfig;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;


/**
 * Tenant Data model
 */
public class TenantData implements Serializable {

    @Serial
    private static final long serialVersionUID = 7043931699705230320L;

    private final String _tenantId;
    private AppConfig _appConfig;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public TenantData(String tenantId) {
        _tenantId = tenantId;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getTenantId() {
        return _tenantId;
    }


    public AppConfig getAppConfig() {
        return _appConfig;
    }


    public void setAppConfig(AppConfig appConfig) {
        _appConfig = appConfig;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TenantData that = (TenantData) o;
        return Objects.equals(_tenantId, that._tenantId) && Objects.equals(_appConfig, that._appConfig);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_tenantId, _appConfig);
    }
}
