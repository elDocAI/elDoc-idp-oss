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

import ai.eldoc.TenantContext;
import ai.eldoc.dao.mongo.MongoDao;
import ai.eldoc.exceptions.BadArgumentException;
import ai.eldoc.model.BasicModel;
import ai.eldoc.service.DbClientService;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.Set;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.inc;


/**
 * ShortId Sequence DAO MongoDB implementation
 */
@ApplicationScoped
public class ShortIdSequenceDaoMongoImpl implements MongoDao<Document>, BasicDao<Document>, ShortIdSequenceDao {

    public static final String COLLECTION_NAME = "shortIdSeqs";
    public static final String _SEQUENCE = "seq";

    @Inject
    private DbClientService _dbClientService;


    protected MongoDatabase getDatabase() {
        if (TenantContext.getCurrentTenantId() == null) {
            throw new BadArgumentException("TenantId cannot be null");
        }
        return _dbClientService.getDatabase(TenantContext.getCurrentTenantId());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ClientSession startSession() {
        return _dbClientService.startSession(TenantContext.getCurrentTenantId());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MongoCollection<Document> getCollection() {
        return getCollection(Document.class);
    }


    protected <T> MongoCollection<T> getCollection(Class<T> clazz) {
        return getDatabase().getCollection(getCollectionName(), clazz);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    /**
     * {@inheritDoc}
     */
    public void initCounters() {
        if (getCollection().countDocuments(new Document(BasicModel._ID, WorkflowDaoTenantMongoImpl.COLLECTION_NAME)) == 0) {
            // Initializes workflows shortIdSeq for first usage (160 dec = A0 hex - just for nice UI presentation)
            getCollection().insertOne(new Document(BasicModel._ID, WorkflowDaoTenantMongoImpl.COLLECTION_NAME).append(_SEQUENCE, 160));
        }

        if (getCollection().countDocuments(new Document(BasicModel._ID, FormDefinitionDaoMongoImpl.COLLECTION_NAME)) == 0) {
            // Initializes formDefs shortIdSeq for first usage
            getCollection().insertOne(new Document(BasicModel._ID, FormDefinitionDaoMongoImpl.COLLECTION_NAME).append(_SEQUENCE, 0));
        }
    }


    private synchronized Integer getNextSequence(ClientSession dbClientSession, String sequenceName, int incrementValue) {
        Bson filter = eq(BasicModel._ID, sequenceName);
        Bson update = inc(_SEQUENCE, incrementValue);
        // Set options: create if missing and return the post-update document
        FindOneAndUpdateOptions opts = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

        Document result;
        if (dbClientSession != null) {
            result = getCollection().findOneAndUpdate(dbClientSession, filter, update, opts);
        } else {
            result = getCollection().findOneAndUpdate(filter, update, opts);
        }

        if (result == null) {
            throw new IllegalStateException("findOneAndUpdate returned null for sequence: " + sequenceName);
        }

        return result.getInteger(_SEQUENCE);
    }


    private synchronized String getNextSequenceHEX(String sequenceName, int incrementValue) {
        return Integer.toHexString(getNextSequence(null, sequenceName, incrementValue)).toUpperCase();
    }


    /**
     * Inits counters
     */
    @Override
    public String getNextSequenceHEXforWorkflow() {
        return getNextSequenceHEX(WorkflowDaoTenantMongoImpl.COLLECTION_NAME, 1);
    }


    /**
     * Inits counters
     */
    @Override
    public String getNextSequenceForFormDefinition(final String prefix) {
        return prefix + getNextSequence(null, FormDefinitionDaoMongoImpl.COLLECTION_NAME, 1);
    }


    @Override
    public void create(ClientSession dbClientSession, Document entity) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }


    @Override
    public boolean updateOrCreate(ClientSession dbClientSession, Document entity) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }


    @Override
    public Document find(Bson filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    @Override
    public Document find(Bson filter, Collation collation) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    @Override
    public Document find(String id) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    @Override
    public boolean delete(ClientSession dbClientSession, Document entity) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    @Override
    public <TResult> Set<TResult> getDistinctValuesByField(String fieldName, Class<TResult> clazz) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    /**
     * Deletes all indexes (except the default one: _id_) and creates valid indexes again (in background)
     */
    @Override
    public void doIndexHousekeep() {
        // There are no indexes to be added except default one _id
    }
}
