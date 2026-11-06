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

import com.mongodb.client.ClientSession;
import java.util.Set;


/**
 * Basic DAO Interface
 * @param <T> type of the entity for which this DAO is created
 */
public interface BasicDao<T> {

    /**
     * Returns Collection name
     *
     * @return
     */
    public String getCollectionName();


    /**
     * Initializes DB session to be used for database operations encapsulation or transactions management
     *
     * @return
     */
    public ClientSession startSession();


    /**
     * Finds and returns Model Entity by id
     *
     * @param id String representation of ObjectId, or just a string id representation
     * @return null in case entity not found or Entity object
     */
    public T find(String id);


    /**
     * Persists Entity in the database
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param entity Entity object
     */
    public void create(ClientSession dbClientSession, T entity);


    /**
     * Updates (concurrency safety to be implemented by specific DAO) record in the database in case record with given _id exist,
     * or Creates a new record in the database in case this is an unsaved entity
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param entity Entity object
     * @return
     */
    public boolean updateOrCreate(ClientSession dbClientSession, T entity);


    /**
     * Deletes record (Entity) concurrency safe from the database, record is found using its _id
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param entity
     * @return
     */
    public boolean delete(ClientSession dbClientSession, T entity);


    /**
     * Returns distinct values from the given field
     *
     * @param <TResult>
     * @param fieldName
     * @param clazz
     * @return
     */
    public <TResult> Set<TResult> getDistinctValuesByField(String fieldName, Class<TResult> clazz);


    //
    // DAO houskeeping methods --------------------------------------------------------------------
    //
    /**
     * Performs indexes house-keeping
     */
    public void doIndexHousekeep();

}
