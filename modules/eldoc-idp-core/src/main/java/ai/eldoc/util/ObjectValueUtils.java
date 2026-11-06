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
package ai.eldoc.util;

import java.util.*;


/**
 */
public class ObjectValueUtils {


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns structure for table data representation stored in the Custom Fields mostly.<br>
     * <b>IMPORTANT</b>: Returns copy of the value, passed as argument
     *
     * @param value The value, that should be validated and returned as List of String Lists
     * @throws IllegalArgumentException in case not all values fulfil the data structure of {@code List<List<String>>}
     * @return {@code List<List<String>>} converted value, if all checks pass
     */
    public static List<List<String>> getValueAsListOfStringList(Object value) throws IllegalArgumentException {
        List<List<String>> resultList = new ArrayList<>();
        if (value instanceof Collection rowsList && !rowsList.isEmpty()) { // covers check for value != null
            if (rowsList.stream().allMatch(rowObject -> (rowObject instanceof Collection))) {
                resultList.addAll(rowsList);
            } else {
                throw new IllegalArgumentException("Not all elements fulfil the rule to be a list in the List<List<String>> field");
            }
        }
        return resultList;
    }

    /**
     * Returns null in case Set is null or empty otherwise returns the same Set
     *
     * @param <T>
     * @param set
     * @return
     */
    public static <T> Set<T> nullIfEmpty(Set<T> set) {
        return (set == null || set.isEmpty()) ? null : set;
    }
}
