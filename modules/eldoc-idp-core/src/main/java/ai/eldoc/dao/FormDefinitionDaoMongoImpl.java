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
import ai.eldoc.model.FormDefinition;
import ai.eldoc.service.DirectoryService;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import static com.mongodb.client.model.Filters.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * FormDefinition DAO Mongo Implementation
 */
@ApplicationScoped
public class FormDefinitionDaoMongoImpl extends AbstractMongoPojoDao<FormDefinition> implements FormDefinitionDao {

    private static final Logger LOG = LoggerFactory.getLogger(FormDefinitionDaoMongoImpl.class);

    public static final String COLLECTION_NAME = "formDefs";

    @Inject
    private FormDefinitionFilesDao _formDefFilesDao;

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private ShortIdSequenceDao _shortIdSequenceDao;


    //
    // DAO Generic Methods ------------------------------------------------------------------------
    //
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    @Override
    public MongoCollection<FormDefinition> getCollection() {
        return super.getCollection(FormDefinition.class);
    }


    @Override
    public FilesDao getFilesDao() {
        return _formDefFilesDao;
    }


    //
    // DAO Specific Business Methods --------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public FormDefinition find(String id) {
        if (isNotBlank(id)) {
            try {
                return find(eq(FormDefinition._ID, new ObjectId(id)));
            } catch (IllegalArgumentException ie) {
                LOG.debug(id, ie);
            } catch (Exception e) {
                LOG.error(id, e);
            }
        }
        return null;
    }


    @Override
    public List<FormDefinition> findAll() {
        final List<FormDefinition> resultList = new ArrayList<>();
        getCollection().find().into(resultList);
        return resultList;
    }


    /**
     * Returns FormDefinition model by webId, which equals to the target Workflow name
     *
     * @param webId webId of the FormDefinition (e.g.: fd1, fd2, frd1), case-insensitive
     * @param skipAccessCreateCheck - skips ACL check for CanCreate option
     * @param systemId of the user (can be {@code null} when {@code skipAccessCreateCheck == true})
     * @return
     */
    @Override
    public FormDefinition findByWebId(String webId, boolean skipAccessCreateCheck, final String systemId) {
        Bson filter;
        if (skipAccessCreateCheck) {
            filter = eq(FormDefinition._ID_WEB, webId);
        } else {
            filter = and(eq(FormDefinition._ID_WEB, webId), getCanCreateFilterDoc(systemId));
        }
        return find(filter, _collation);
    }


    @Override
    public List<FormDefinition> findAllByStatus(List<Integer> statusList, boolean skipAccessCreateCheck, final String systemId) {
        final ArrayList<FormDefinition> result = new ArrayList<>();
        try {
            Bson filter;
            if (skipAccessCreateCheck) {
                filter = in(FormDefinition._STATUS, statusList);
            } else {
                filter = and(in(FormDefinition._STATUS, statusList), getCanCreateFilterDoc(systemId));
            }
            getCollection().find(filter).collation(_collation).into(result);
        } catch (Exception e) {
            LOG.error("Error finding forms by status: {}, excludeAccessCreateCheck={}, systemId={}", statusList, skipAccessCreateCheck, systemId, e);
        }
        return result;
    }


    @Override
    public List<FormDefinition> findAllByStatusAndRetentionAction(List<Integer> statusList, List<Integer> retentionActionList) {
        final ArrayList<FormDefinition> result = new ArrayList<>();
        try {
            Bson filter = and(in(FormDefinition._STATUS, statusList), in(FormDefinition._RETENTION_ACTION, retentionActionList));
            getCollection().find(filter).collation(_collation).into(result);
        } catch (Exception e) {
            LOG.error("Error finding forms by status: {}, retentionActionList: {}", statusList, retentionActionList, e);
        }
        return result;
    }


    private Bson getCanCreateFilterDoc(final String systemId) {
        Set<String> userNameGroupsRolesList = _directoryService.getUserGroupsAndNameAndRoles(systemId);
        return in(FormDefinition._ACCESS_CREATE, userNameGroupsRolesList);
    }


    /**
     * Creates new record in the DB
     */
    @Override
    public void create(ClientSession dbClientSession, FormDefinition entity) {
        if (entity.isTypeDynamic()) {
            entity.setFormId(_shortIdSequenceDao.getNextSequenceForFormDefinition(FormDefinition.FORM_ID_DYNAMIC_PREFIX));
        }
        super.create(dbClientSession, entity);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected List<IndexModel> getIndexesList() {
        List<IndexModel> indexesList = new ArrayList<>();

        // IndexOptions indexOptions1 = new IndexOptions().background(true);
        // indexesList.add(new IndexModel(new Document(Workflow._STATE, 1), indexOptions1));
        IndexOptions indexOptions2 = new IndexOptions().collation(_collation).background(true);
        indexesList.add(new IndexModel(new Document(FormDefinition._ACCESS_CREATE, 1), indexOptions2));

        IndexOptions indexOptions3 = new IndexOptions().unique(true).collation(_collation).background(true);
        indexesList.add(new IndexModel(new Document(FormDefinition._ID_WEB, 1), indexOptions3));

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
