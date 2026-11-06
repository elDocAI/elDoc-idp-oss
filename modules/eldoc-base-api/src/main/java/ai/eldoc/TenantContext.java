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
package ai.eldoc;


/**
 * Represents current tenant context
 */
public class TenantContext {

    private static final ThreadLocal<String> _currentTenantId = new ThreadLocal<>();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Clears assigned tenantId to the thread
     */
    public static void clear() {
        _currentTenantId.remove();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Assigns TenantId to the current thread. In case given tenantId is null or empty - removes value from the thread
     *
     * @param tenantId tenantId to set
     */
    public static void setCurrentTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            clear();
            return;
        }
        _currentTenantId.set(tenantId);
    }


    public static String getCurrentTenantId() {
        return _currentTenantId.get();
    }

}
