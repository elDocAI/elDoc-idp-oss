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
import ai.eldoc.model.BpmProcessDefinition;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import static com.mongodb.client.model.Filters.in;

/**
 * BpmProcessDefinition DAO Mongo Implementation
 */
@ApplicationScoped
public class BpmProcessDefinitionDaoMongoImpl extends AbstractMongoPojoDao<BpmProcessDefinition> implements BpmProcessDefinitionDao {

    private static final Logger LOG = Logger.getLogger(BpmProcessDefinitionDaoMongoImpl.class.getName());

    public static final String COLLECTION_NAME = "bpmPrcsDefs";


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    @Override
    public MongoCollection<BpmProcessDefinition> getCollection() {
        return super.getCollection(BpmProcessDefinition.class);
    }


    @Override
    public List<BpmProcessDefinition> findAll() {
        final List<BpmProcessDefinition> resultList = new ArrayList<>();
        getCollection().find().into(resultList);
        return resultList;
    }


    @Override
    public List<BpmProcessDefinition> findAllByIdList(List<String> sysProcessIdList) {
        final List<BpmProcessDefinition> resultList = new ArrayList<>();
        if (sysProcessIdList == null || sysProcessIdList.isEmpty()) {
            return resultList;
        }
        getCollection().find(in(BpmProcessDefinition._ID, sysProcessIdList.stream().map(ObjectId::new).toList())).into(resultList);
        return resultList;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected List<IndexModel> getIndexesList() {
        List<IndexModel> indexesList = new ArrayList<>();
        return indexesList;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void doIndexHousekeep() {
        // There are no indexes to be updated in this collection except the default one: _id
        // doIndexHousekeep(getIndexesList());
    }

}
