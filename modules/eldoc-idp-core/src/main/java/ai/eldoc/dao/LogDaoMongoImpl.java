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
import ai.eldoc.dao.mongo.MongoUtils;
import ai.eldoc.model.Log;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.inc;


/**
 * Logs DAO Mongo Implementation
 */
@ApplicationScoped
public class LogDaoMongoImpl extends AbstractMongoPojoDao<Log> implements LogDao {

    private static final Logger LOG = LoggerFactory.getLogger(LogDaoMongoImpl.class);

    public static final String COLLECTION_NAME = "logs";


    //
    // DAO Specific Business Methods --------------------------------------------------------------
    //
    /**
     * Finds all logs related to the given document Id
     *
     * @param entId
     * @return
     */
    @Override
    public List<Log> findByEntityId(String entId) {
        final List<Log> resultList = new ArrayList<>();
        try {
            getCollection().find(eq(Log._ENTITY_ID, entId)).into(resultList);
        } catch (Exception e) {
            LOG.error("entId={}", entId, e);
        }
        return resultList;
    }


    /**
     * Finds all logs related to the given document Id
     *
     * @param entRltId
     * @return
     */
    @Override
    public List<Log> findByEntityRelatedId(String entRltId) {
        final List<Log> resultList = new ArrayList<>();
        try {
            getCollection().find(eq(Log._ENTITY_RELATED_ID, entRltId)).into(resultList);
        } catch (Exception e) {
            LOG.error("entRltId={}", entRltId, e);
        }
        return resultList;
    }


    @Override
    public int countIdpPages(Instant sinceDate) {
        return countPages(sinceDate, Log.ENTITY_TYPE_WORKFLOW_IDP, "id00089");
    }


    /**
     * Helper method to count pages in log entries, for example to count IDP processed pages, logged in event with id00089 or
     * preprocessed files by event id00409
     *
     * @param sinceDate
     * @param entityType
     * @param eventId
     * @return
     */
    private int countPages(Instant sinceDate, String entityType, String eventId) {
        List<Bson> aggregateList = new ArrayList<>();

        aggregateList.add(match(and(
                eq(Log._ENTITY_TYPE, entityType),
                eq(Log._EVENT_ID, eventId),
                gt(Log._TIMESTAMP, sinceDate)
        )));

        aggregateList.add(group(null, sum(Log.PROP_COUNT, "$" + Log._PATH_PROPERTIES_COUNT)));

        Document idpPageCountDoc = super.getCollection(Document.class).aggregate(aggregateList).allowDiskUse(true).first();

        if (idpPageCountDoc != null) {
            return idpPageCountDoc.getInteger(Log.PROP_COUNT, 0);
        }

        return 0;
    }


    @Override
    public Log createOrIncrementDailyCount(ClientSession dbClientSession, Log log) {
        Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Document filterDoc = MongoUtils.objectToDocument(log, Log.class);
        filterDoc.put(Log._TIMESTAMP, Date.from(dayStart));
        filterDoc.get(Log._PROPERTIES, Document.class).remove(Log.PROP_COUNT);
        if (filterDoc.get(Log._PROPERTIES, Document.class).isEmpty()) {
            filterDoc.remove(Log._PROPERTIES);
        }

        List<Bson> filters = new ArrayList<>();
        for (String key : filterDoc.keySet()) {
            filters.add(eq(key, filterDoc.get(key)));
        }

        Bson updates = combine(
                inc(Log._PATH_PROPERTIES_COUNT, log.getProperty(Log.PROP_COUNT, 0))
        );

        FindOneAndUpdateOptions foauo = new FindOneAndUpdateOptions()
                .upsert(true)
                .collation(_collation)
                .returnDocument(ReturnDocument.AFTER);

        Log updatedEntry;
        if (dbClientSession == null) {
            updatedEntry = getCollection().findOneAndUpdate(and(filters), updates, foauo);
        } else {
            updatedEntry = getCollection().findOneAndUpdate(dbClientSession, and(filters), updates, foauo);
        }

        return updatedEntry;
    }


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    @Override
    public MongoCollection<Log> getCollection() {
        return super.getCollection(Log.class);
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
        indexesList.add(new IndexModel(new Document(Log._TIMESTAMP, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Log._EVENT_ID, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Log._SYSTEM_ID, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Log._ENTITY_ID, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Log._ENTITY_TYPE, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Log._ENTITY_RELATED_ID, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Log._PROCESS_STATE, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Log._PROCESS_STEP, 1), indexOptions1));

        IndexOptions indexOptions2 = new IndexOptions().collation(_collation).background(true);

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
