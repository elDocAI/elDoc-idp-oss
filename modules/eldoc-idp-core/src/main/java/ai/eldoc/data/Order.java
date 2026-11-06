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

import java.util.logging.Level;
import java.util.logging.Logger;
import static java.text.MessageFormat.format;


/**
 * Sorting order type
 */
public class Order {

    // TODO: should this class be converted to ENUM? (the same as AggregateFunc and Comparison)

    private static final Logger LOG = Logger.getLogger(Order.class.getName());

    /**
     * Sort ascending
     */
    public static final int ASC = 0;

    /**
     * Sort descending
     */
    public static final int DESC = 1;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private Order() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns labelId for using in localization strings
     *
     * @param order
     * @return
     */
    public static String getLabelId(int order) {
        switch (order) {
            case Order.ASC:
                return "general.sortOrder.ASC";

            case Order.DESC:
                return "general.sortOrder.DESC";

            default:
                LOG.log(Level.WARNING, "Invalid value for order: {0}", order);
                return format("general.sortOrder.{0,number,#}", order);
        }
    }
}
