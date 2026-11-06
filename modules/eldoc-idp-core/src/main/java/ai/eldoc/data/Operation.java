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
package ai.eldoc.data;

import static java.text.MessageFormat.format;


/**
 * Operation enum class
 */
public enum Operation {

    /**
     * Set operation
     */
    SET,
    /**
     * Add operation
     */
    ADD,
    /**
     * Remove operation
     */
    REMOVE;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns labelId for using in localization strings
     *
     * @param op
     * @return
     */
    public static String getLabelId(Operation op) {
        return format("general.operation.{0}", op.name());
    }
}
