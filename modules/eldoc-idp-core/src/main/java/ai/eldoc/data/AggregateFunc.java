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


/**
 * General aggregation functions, available with filters
 */
public enum AggregateFunc {
    /**
     * Counts amount of rows per each unique value in grouping field
     */
    COUNT,
    /**
     * Calculates maximum value in the field
     */
    MAX,
    /**
     * Calculates minimum value in the field
     */
    MIN,
    /**
     * Calculates sum of all values in the field
     */
    SUM,
    /**
     * Calculates average value in the field
     */
    AVG
}
