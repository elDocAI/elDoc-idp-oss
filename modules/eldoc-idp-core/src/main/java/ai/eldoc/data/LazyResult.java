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
import java.util.List;
import java.util.Objects;

/**
 * Lazy Result Model. Represents a lazy-loaded result set. This class provides a wrapper for a list of data items
 * and the total count of those items. It is designed to handle data that is loaded or counted
 * on-demand, rather than all at once.
 *
 * @param <T> the type of elements in this LazyResult
 */
public class LazyResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 7704255161551589244L;

    private final List<T> _datasource;
    private final int _count;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public LazyResult(List<T> datasource, int count) {
        _datasource = datasource;
        _count = count;
    }


    //
    // Getters ------------------------------------------------------------------------------------
    //


    /**
     * Returns datasource with actual data
     *
     * @return datasource
     */
    public List<T> getDatasource() {
        return _datasource;
    }


    /**
     * Returns the total count of items in the target DB or -1 if the count is not available or was set to be not counted
     *
     * @return count
     */
    public int getCount() {
        return _count;
    }


    //
    // Other Methods ------------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LazyResult<?> that = (LazyResult<?>) o;
        return _count == that._count && Objects.equals(_datasource, that._datasource);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_datasource, _count);
    }
}
