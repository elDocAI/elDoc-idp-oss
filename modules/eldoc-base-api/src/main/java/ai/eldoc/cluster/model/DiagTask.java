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
package ai.eldoc.cluster.model;

import java.io.Serializable;
import static java.text.MessageFormat.format;


/**
 * Diagnostic Task Model
 */
public final class DiagTask extends MultiTenantTask implements Serializable {

    private static final long serialVersionUID = 7539172834031084710L;

    public static final String TASK_NAME_TEMPLATE = DiagTask.class.getSimpleName() + "({0})";


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * Diagnostic Event Constructor
     *
     * @param tenantId event payload
     */
    public DiagTask(String tenantId) {
        super(tenantId);
    }


    @Override
    public String getName() {
        return format(TASK_NAME_TEMPLATE, getTenantId());
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return getName();
    }
}
