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

import ai.eldoc.dao.mongo.AbstractMongoPojoDao;
import ai.eldoc.model.config.Config;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;


/**
 * Config DAO Mongo Implementation
 */
@ApplicationScoped
public class ConfigDaoTenantMongoImpl extends AbstractMongoPojoDao<Config> implements ConfigDao {

    public static final String COLLECTION_NAME = "configs";


    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    public <T extends Config> List<T> findAllByType(Class<T> typeClass) {
        return getCollection()
                .find(eq(Config._DISCRIMINATOR, typeClass.getName()))
                .map(typeClass::cast)
                .into(new ArrayList<>());
    }


    /**
     * Finds first Config document with the given type
     *
     * @param <T>
     * @param typeClass
     * @return
     */
    @Override
    public <T extends Config> T findByType(Class<T> typeClass) {
        Object result = find(eq(Config._DISCRIMINATOR, typeClass.getName()));
        return result == null ? null : typeClass.cast(result);
    }


    /**
     * Finds Config document by Type and Id
     *
     * @param typeClass
     * @param id
     * @param <T>
     * @return
     */
    @Override
    public <T extends Config> T findByTypeAndId(Class<T> typeClass, String id) {
        if (!ObjectId.isValid(id)) {
            return null;
        }
        Object result = find(
                and(
                        eq(Config._DISCRIMINATOR, typeClass.getName()),
                        eq(Config._ID, new ObjectId(id))
                )
        );
        return result == null ? null : typeClass.cast(result);
    }


    /**
     * Finds Config document by Type and Key, returns only the first found document
     *
     * @param <T>
     * @param typeClass
     * @param key
     * @return
     */
    @Override
    public <T extends Config> T findByTypeAndKey(Class<T> typeClass, String key) {
        Object result = find(
                and(
                        eq(Config._DISCRIMINATOR, typeClass.getName()),
                        eq(Config._KEY, key)
                )
        );
        return result == null ? null : typeClass.cast(result);
    }


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //
    @Override
    public MongoCollection<Config> getCollection() {
        return super.getCollection(Config.class);
    }


    //
    // DAO Index Housekeeping methods -------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    protected List<IndexModel> getIndexesList() {
        List<IndexModel> indexesList = new ArrayList<>();

        IndexOptions indexOptions1 = new IndexOptions().background(true);
        indexesList.add(new IndexModel(new Document(Config._DISCRIMINATOR, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Config._KEY, 1), indexOptions1));

        return indexesList;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void doIndexHousekeep() {
        doIndexHousekeep(getIndexesList());
    }
}
