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
import ai.eldoc.model.CodeSnippet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import static com.mongodb.client.model.Filters.eq;


/**
 * CodeSnippet DAO Mongo implementation
 */
@ApplicationScoped
public class CodeSnippetDaoMongoImpl extends AbstractMongoPojoDao<CodeSnippet> implements CodeSnippetDao {

    public static final String COLLECTION_NAME = "codeSnips";


    @Override
    public MongoCollection<CodeSnippet> getCollection() {
        return super.getCollection(CodeSnippet.class);
    }


    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    @Override
    public List<CodeSnippet> findByType(int type) {
        return getCollection().find(eq(CodeSnippet._TYPE, type)).into(new ArrayList<>());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected List<IndexModel> getIndexesList() {
        List<IndexModel> indexesList = new ArrayList<>();
        indexesList.add(indexModelAsc(CodeSnippet._TYPE, indexOptions(null)));
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
