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
package ai.eldoc.dao.mongo;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.TenantContext;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.dao.*;
import ai.eldoc.data.LazyQuery;
import ai.eldoc.data.LazyResult;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.service.DbClientService;
import com.mongodb.MongoException;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.function.Consumer;
import static com.mongodb.client.model.Filters.*;


/**
 * Abstract Mongo POJO Basic DAO
 *
 * @param <T> type of the entity for which this DAO is created
 */
public abstract class AbstractMongoPojoDao<T extends BasicModel> extends AbstractMongoDao implements MongoDao<T>, BasicLazyDao<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoPojoDao.class);

    @Inject
    protected DbClientService _dbClientService;


    //
    // Database implementation related methods ----------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    public abstract String getCollectionName();


    /**
     * Returns Tenant's Database
     *
     * @return
     */
    protected MongoDatabase getDatabase() {
        if (TenantContext.getCurrentTenantId() == null) {
            throw new ElDocRuntimeException("TenantId cannot be null");
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
    abstract public MongoCollection<T> getCollection();


    protected <T> MongoCollection<T> getCollection(Class<T> clazz) {
        return getDatabase().getCollection(getCollectionName(), clazz).withCodecRegistry(MongoUtils.getCodecRegistry());
    }


    //
    // Business models methods --------------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    public T find(String id) {
        if (id == null || id.isEmpty() || !ObjectId.isValid(id)) {
            return null;
        }
        return find(eq(BasicModel._ID, new ObjectId(id)));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public T find(Bson filter) {
        return find(filter, _collation);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public T find(Bson filter, Collation collation) {
        try {
            return getCollection().find(filter).collation(collation).first();
        } catch (IllegalArgumentException ex1) {
            LOG.error(getCollectionName(), ex1);
            throw new ElDocRuntimeException("elDoc Error: " + ex1.getMessage(), ex1);
        } catch (MongoException mdbe) {
            LOG.error(getCollectionName(), mdbe);
            throw new DatabaseException("Database Error: " + mdbe.getMessage(), mdbe);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public LazyResult<T> findAllLazy(LazyQuery lazyQuery) {
        return findAllLazy(lazyQuery, new AclContext(DirectoryConstants.ELDOC_ROBOT, null, null, null, null, null).toFullAccessMode()); // Default AclContext for providing backward copatibility
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public LazyResult<T> findAllLazy(LazyQuery lazyQuery, AclContext aclContext) {
        final List<T> resultList = new ArrayList<>();
        LazyResult<T> lazyResult = null;
        Integer resultCount = 0;

        try {
            List<Bson> aggregateList = buildAggregatesList(lazyQuery, aclContext);

            // Get the count of documents matching the filter
            ArrayList<Bson> aggregateCountList = new ArrayList<>(aggregateList);
            aggregateCountList.add(Aggregates.count());
            Document countDoc = getDatabase()
                    .getCollection(getCollectionName())
                    .aggregate(aggregateCountList)
                    .allowDiskUse(true)
                    .collation(_collation)
                    .first();
            if (countDoc != null) {
                resultCount = countDoc.getInteger("count");
            }

            //
            // Add parameters which do not affect result set
            //
            // add sort parametrs
            if (!lazyQuery.getSort().isEmpty()) {
                aggregateList.add(new Document("$sort", lazyQuery.getSort()));
            }

            // add skip and limit parameters
            if (lazyQuery.getFirst() != -1) {
                aggregateList.add(new Document("$skip", lazyQuery.getFirst()));
            }
            if (lazyQuery.getPageSize() != -1) {
                aggregateList.add(new Document("$limit", lazyQuery.getPageSize()));
            }

            // perfrom the DB query
            getCollection()
                    .aggregate(aggregateList)
                    .allowDiskUse(true)
                    .collation(_collation)
                    .into(resultList);

            lazyResult = new LazyResult(resultList, resultCount);
        } catch (Exception e) {
            LOG.error(String.valueOf(lazyQuery), e);
        }
        return lazyResult;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public void processWithCursor(LazyQuery lazyQuery, BasicModelProcessor<T> processor) {
        processWithCursor(lazyQuery, processor, new AclContext(DirectoryConstants.ELDOC_ROBOT, null, null, null, null, null).toFullAccessMode()); // Default AclContext for providing backward copatibility
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void processWithCursor(LazyQuery lazyQuery, BasicModelProcessor<T> processor, AclContext aclContext) {
        List<Bson> aggregateList = buildAggregatesList(lazyQuery, aclContext);

        // Get the count of documents matching the filter
        ArrayList<Bson> aggregateCountList = new ArrayList<>(aggregateList);
        aggregateCountList.add(Aggregates.count());
        Document countDoc = getDatabase()
                .getCollection(getCollectionName())
                .aggregate(aggregateCountList)
                .allowDiskUse(true)
                .collation(_collation)
                .first();
        if (countDoc != null) {
            processor.setTotalCount(countDoc.getInteger("count"));
        }

        // add skip and limit parameters
        if (lazyQuery.getFirst() != -1) {
            aggregateList.add(new Document("$skip", lazyQuery.getFirst()));
        }
        if (lazyQuery.getPageSize() != -1) {
            aggregateList.add(new Document("$limit", lazyQuery.getPageSize()));
        }

        // Get the cursor and process entities
        try (MongoCursor<T> cursor = getCollection()
                .aggregate(aggregateList)
                .allowDiskUse(true)
                .collation(_collation)
                .cursor()) {
            while (cursor.hasNext()) {
                processor.process(cursor.next());
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void create(ClientSession dbClientSession, T entity) {
        //Throws:
        //MongoWriteException - if the write failed due to some specific write exception
        //MongoWriteConcernException - if the write failed due to being unable to fulfil the write concern
        //MongoCommandException - if the write failed due to a specific command exception
        //MongoException - if the write failed due some other failure
        try {
            if (dbClientSession != null) {
                getCollection().insertOne(dbClientSession, entity);
            } else {
                getCollection().insertOne(entity);
            }
        } catch (MongoException mdbe) {
            LOG.error(getCollectionName(), mdbe);
            throw new DatabaseException("Database Error: " + mdbe.getMessage(), mdbe);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateOrCreate(ClientSession dbClientSession, T entity) {
        if (entity.getId() == null) {
            create(dbClientSession, entity);
            return true;
        } else {
            if (entity instanceof BasicMetaAwareModel metaAwareEntity) {
                return updateOneConcurrencySafe(dbClientSession, metaAwareEntity);
            } else {
                throw new UnsupportedOperationException("Not supported yet by this DAO: " + this.getClass().getSimpleName());
            }
        }
    }


    /**
     * Updates one document concurrency safe: it checks for document revision first, and performs update only in case revision matches
     *
     * @param entity
     * @param objectId - as this method is being called from the updateOrCreate() method which already retrieved ObjectId from the given entity - just reuse it here
     * @return
     */
    private <C extends BasicMetaAwareModel> boolean updateOneConcurrencySafe(ClientSession dbClientSession, C entity) {
        int currentRevision = entity.getMeta().getRev();
        Bson filter = and(eq(BasicModel._ID, new ObjectId(entity.getId())), eq(Meta._PATH_META_REVISION, currentRevision));

        // Update lastmodified property and revision (FYI: $currentDate and $inc was not able to make them working)
        entity.getMeta().setLastModified(new Date());
        entity.getMeta().setRev(++currentRevision);

        UpdateResult updateResult;
        try {
            //Throws:
            //MongoWriteException - if the write failed due to some specific write exception
            //MongoWriteConcernException - if the write failed due to being unable to fulfil the write concern
            //MongoCommandException - if the write failed due to a specific command exception
            //MongoException - if the write failed due some other failure
            if (dbClientSession != null) {
                updateResult = getCollection().replaceOne(dbClientSession, filter, (T) entity);
            } else {
                updateResult = getCollection().replaceOne(filter, (T) entity);
            }
        } catch (MongoException mdbe) {
            LOG.error(getCollectionName(), mdbe);
            throw new DatabaseException("Database Error: " + mdbe.getMessage(), mdbe);
        }

        if (updateResult.getMatchedCount() == 0) {
            // at this point we assume that as document was loaded to business logic (UI, etc) and now it is being written back to the DB
            // thus as no matches found means that revision of the document in DB was already updated OR document was deleted, etc.
            // thus throwing concerrent write exception
            throw new ConcurrentWriteException("Document has been saved by another process - save has not been performed");
        }
        return updateResult.wasAcknowledged();
    }


    /**
     * Checks if the entry still present in the DB (checks if present in the DB with the give revision in case entity is of type BasicMetaAwareModel)
     *
     * @param entity
     * @return
     * @throws DBEntryNotValidException
     */
    public boolean isValid(T entity) throws DBEntryNotValidException {
        Bson filter = eq(BasicModel._ID, new ObjectId(entity.getId()));

        if (entity instanceof BasicMetaAwareModel metaAwareEntity) {
            int revision = 0;
            if (metaAwareEntity.getMeta() != null) {
                revision = metaAwareEntity.getMeta().getRev();
            }
            filter = and(eq(BasicModel._ID, new ObjectId(entity.getId())), eq(Meta._PATH_META_REVISION, revision));
        }

        if (getCollection().countDocuments(filter) == 1L) {
            return true;
        } else {
            throw new DBEntryNotValidException();
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(ClientSession dbClientSession, T entity) {
        if (entity instanceof BasicFileAwareModel basicFileAwareModelEntity && this instanceof FileAwareDao thisFileAwareDao) {
            basicFileAwareModelEntity.getFiles().forEach(dbFileRef -> {
                try {
                    thisFileAwareDao.getFilesDao().deleteFile(dbClientSession, dbFileRef);
                } catch (Throwable th) {
                    LOG.error("Error deleting file: {}", dbFileRef, th);
                }
            });
        }
        DeleteResult result;
        if (dbClientSession != null) {
            result = getCollection().deleteOne(dbClientSession, eq(BasicModel._ID, new ObjectId(entity.getId())));
        } else {
            result = getCollection().deleteOne(eq(BasicModel._ID, new ObjectId(entity.getId())));
        }
        return result.wasAcknowledged();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public <TResult> Set<TResult> getDistinctValuesByField(String fieldName, Class<TResult> clazz) {
        final Set<TResult> result = new HashSet<>();
        // To avoid null in results we use filter below
        getCollection()
                .distinct(fieldName, and(ne(fieldName, null), nin(fieldName, new Object[]{null})), clazz)
                .forEach((Consumer<TResult>) result::add);
        return result;
    }


    //
    // DAO houskeeping methods --------------------------------------------------------------------
    //
    /**
     * Returns the list of Indexes for this DAO
     *
     * @return - list of IndexModels
     */
    abstract protected List<IndexModel> getIndexesList();


    /**
     * Returns a new instance of the IndexModel for ascending field
     *
     * @param fieldName - name of the field for which the index is created
     * @param indexOptions - IndexOptions for the index
     * @return - new instance of the IndexModel
     */
    protected IndexModel indexModelAsc(String fieldName, IndexOptions indexOptions) {
        return new IndexModel(Indexes.ascending(fieldName), indexOptions);
    }


    /**
     * Returns a new instance of the IndexModel for ascending field
     *
     * @param fieldNames - names of the fields for which the index is created
     * @param indexOptions - IndexOptions for the index
     * @return - new instance of the IndexModel
     */
    protected IndexModel indexModelAsc(List<String> fieldNames, IndexOptions indexOptions) {
        return new IndexModel(Indexes.ascending(fieldNames), indexOptions);
    }


    /**
     * Returns a new instance of the IndexOptions without defining collation
     *
     * @param name - name of the index
     * @return - new instance of the IndexOptions model
     */
    protected IndexOptions indexOptions(String name) {
        return new IndexOptions()
                .name(name);
    }


    /**
     * Returns a new instance of the IndexOptions for text fields with case-insensitive collation
     *
     * @param name - name of the index
     * @return - new instance of the IndexOptions model
     */
    protected IndexOptions indexOptionsText(String name) {
        return new IndexOptions()
                .name(name)
                .collation(_collation);
    }


    /**
     * Returns a new instance of the IndexOptions for text fields (unique) with case-insensitive collation
     *
     * @param name - name of the index
     * @return - new instance of the IndexOptions model
     */
    protected IndexOptions indexOptionsTextUnique(String name) {
        return new IndexOptions()
                .name(name)
                .unique(true)
                .collation(_collation);
    }


    /**
     * Performs indexes house-keeping
     */
    protected void doIndexHousekeep(List<IndexModel> indexesList) {
        LOG.info(">> START: Indexes housekeeping on the collection: {}", getCollectionName());
        LOG.debug(">> Existing indexes:");
        getCollection().listIndexes().forEach((Consumer<Document>) indexDoc -> LOG.debug("{}", indexDoc));

        LOG.info(">> Drop indexes...");
        getCollection().dropIndexes();

        LOG.info(">> Creating indexes...");
        getCollection().createIndexes(indexesList);

        LOG.debug(">> Indexes created, list indexes:");
        getCollection().listIndexes().forEach((Consumer<Document>) indexDoc -> LOG.debug("{}", indexDoc));

        LOG.info(">> END: Indexes housekeeping on the collection: {}", getCollectionName());
    }
}
