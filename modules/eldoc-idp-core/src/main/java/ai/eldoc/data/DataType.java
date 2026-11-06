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

import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 * Data types available for use in system
 */
public class DataType implements Serializable {

    @Serial
    private static final long serialVersionUID = -8604443131209132695L;

    /**
     * UTC (GMT) Date Time
     */
    public static final int DATE = 0;

    /**
     * General text string
     */
    public static final int STRING = 1;

    /**
     * Number, either integer or double<br>
     * Currently used only with Integer fields. In case Double fields are to be handled - consider splitting into INTEGER and DOUBLE
     */
    //public static final int DOUBLE = 3; // Consider adding in future
    public static final int NUMBER = 2; // Cosider renaming into INTEGER = 2;

    /**
     * Boolean value
     */
    public static final int BOOLEAN = 6;


    // List of items. But consider that it can be List of Date, List of Integer, List of String, etc.
    //public static final int __LIST = 6;
    public static List<Comparison> getComparisons(int datatype) {
        switch (datatype) {
            case STRING: {
                return Arrays.asList(Comparison.EQ, Comparison.NE, Comparison.CT, Comparison.NCT);
            }
            case DATE: {
                return Arrays.asList(Comparison.EQ, Comparison.NE, Comparison.GT, Comparison.GE, Comparison.LT, Comparison.LE);
            }
            case NUMBER: {
                return Arrays.asList(Comparison.EQ, Comparison.NE, Comparison.GT, Comparison.GE, Comparison.LT, Comparison.LE);
            }
            case BOOLEAN: {
                return Arrays.asList(Comparison.EQ, Comparison.NE);
            }
            default: {
                return new ArrayList<>();
            }
        }
    }
}
