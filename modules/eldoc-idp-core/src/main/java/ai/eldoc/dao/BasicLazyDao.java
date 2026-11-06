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

import ai.eldoc.data.LazyQuery;
import ai.eldoc.data.LazyResult;
import ai.eldoc.model.AclContext;


/**
 * Interface for data lazy load from MongoDB
 * @param <T>
 */
public interface BasicLazyDao<T> extends BasicDao<T> {

    /**
     * Returns records in a lazy way based on the given lazy query<br>
     * You need to override this method implementation in particular DAO in case custom logic is required
     *
     * @param lazyQuery
     * @return
     * @deprecated to be replaced with method which takes AclContext as 2nd argument
     */
    @Deprecated
    public LazyResult<T> findAllLazy(LazyQuery lazyQuery);


    /**
     * Returns records in a lazy way based on the given lazy query<br>
     * You need to override this method implementation in particular DAO in case custom logic is required
     *
     * @param lazyQuery
     * @param aclContext
     * @return
     */
    public LazyResult<T> findAllLazy(LazyQuery lazyQuery, AclContext aclContext);


    /**
     * Processes records by iterating through cursor
     *
     * @param lazyQuery
     * @param processor
     * @deprecated left for the backwards compatibility until fully migrated to the methods where AclContext is clearly defined as argument
     */
    @Deprecated
    public void processWithCursor(LazyQuery lazyQuery, BasicModelProcessor<T> processor);


    /**
     * Processes records by iterating through cursor
     *
     * @param lazyQuery
     * @param processor
     * @param aclContext
     */
    public void processWithCursor(LazyQuery lazyQuery, BasicModelProcessor<T> processor, AclContext aclContext);

}
