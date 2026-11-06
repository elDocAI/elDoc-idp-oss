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
import ai.eldoc.model.AclContext;
import ai.eldoc.model.ListValue;
import ai.eldoc.service.LogHelper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import static com.mongodb.client.model.Filters.*;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * List Values DAO Mongo Implementation
 */
@ApplicationScoped
public class ListValueDaoMongoImpl extends AbstractMongoPojoDao<ListValue> implements ListValueDao {

    private static final Logger LOG = Logger.getLogger(ListValueDaoMongoImpl.class.getName());

    public static final String COLLECTION_NAME = "listValues";


    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    @Override
    public MongoCollection<ListValue> getCollection() {
        return super.getCollection(ListValue.class);
    }


    /**
     * {@inheritDoc }
     */
    @Override
    public ListValue findByType(int systemType) {
        return find(eq(ListValue._TYPE, systemType));
    }


    /**
     * {@inheritDoc }
     */
    @Override
    public List<ListValue> findAllByKF(String idKf, AclContext aclContext) {
        return findAllByKF(idKf, null, null, false, aclContext);
    }


    /**
     * {@inheritDoc }
     */
    @Override
    public List<ListValue> findAllByKF(String idKf, String searchFT, String lang, boolean showDeletedItems, AclContext aclContext) {
        final List<ListValue> resultList = new ArrayList<>();
        try {
            List<Bson> filters = new ArrayList<>();

            Bson aclMatchFilter = aclMatchCriteria(aclContext);
            if (aclMatchFilter != null) {
                filters.add(aclMatchFilter);
            }

            filters.add(isBlank(idKf) ? eq(ListValue._ID_KF, null) : eq(ListValue._ID_KF, idKf));
            if (!showDeletedItems) {
                filters.add(ne(ListValue._STATUS, ListValue.STATUS_DELETED));
            }

            if (searchFT != null && searchFT.trim().length() >= 2) {
                Pattern regex = Pattern.compile(Pattern.quote(searchFT), Pattern.CASE_INSENSITIVE);
                filters.add(eq(ListValue._VALUE + "." + lang, regex));
            }

            getCollection().find(and(filters)).into(resultList);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "id00317: " + idKf, e);
            LogHelper.setEvent("id00317", e);
        }
        return resultList;
    }


    /**
     * {@inheritDoc }
     */
    @Override
    public List<ListValue> findAllByIdExtAndKF(String extId, String idKf) {
        if (isBlank(extId) && isBlank(idKf)) {
            return null;
        }
        final List<ListValue> resultList = new ArrayList<>();
        getCollection().find(and(eq(ListValue._ID_EXT, extId), eq(ListValue._ID_KF, idKf))).into(resultList);
        return resultList;

    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected List<IndexModel> getIndexesList() {
        List<IndexModel> indexesList = new ArrayList<>();

        IndexOptions indexOptions1 = new IndexOptions().background(true);
        indexesList.add(new IndexModel(new Document(ListValue._TYPE, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(ListValue._ID_EXT, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(ListValue._ID_KF, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(ListValue._PATH_ACL_R, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(ListValue._PATH_ACL_W, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(ListValue._PATH_ACL_RH, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(ListValue._PATH_ACL_WH, 1), indexOptions1));

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
