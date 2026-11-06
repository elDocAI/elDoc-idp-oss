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
package ai.eldoc.dao;


/**
 * Basic Model Processor
 * @param <T>
 */
public abstract class BasicModelProcessor<T> {

    /**
     * Holds the total number of matched records
     */
    private int _totalCount;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    abstract public void process(T model);


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Total count of matched records, updated after execution by DAO
     *
     * @return
     */
    public int getTotalCount() {
        return _totalCount;
    }


    /**
     * Total count of matched records, updated after execution by DAO
     *
     * @param totalCount
     */
    public void setTotalCount(int totalCount) {
        _totalCount = totalCount;
    }

}
