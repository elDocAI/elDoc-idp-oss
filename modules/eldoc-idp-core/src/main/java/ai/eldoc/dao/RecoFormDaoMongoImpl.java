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
import ai.eldoc.model.reco.RecoForm;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import static com.mongodb.client.model.Filters.*;


@ApplicationScoped
public class RecoFormDaoMongoImpl extends AbstractMongoPojoDao<RecoForm> implements RecoFormDao {

    private static final Logger LOG = Logger.getLogger(RecoFormDaoMongoImpl.class.getName());

    public static final String COLLECTION_NAME = "recoForms";

    @Inject
    private RecoFormFilesDao _recoFormFilesDao;


    //
    // DAO Specific Business Methods --------------------------------------------------------------
    //
    @Override
    public List<RecoForm> findAll() {
        List<RecoForm> templates = new ArrayList<>();
        FindIterable<RecoForm> find = getCollection().find();
        find.forEach((Consumer<RecoForm>) templates::add);
        return templates;
    }


    /**
     * {@inheritDoc}
     *
     * @param name
     * @return
     */
    @Override
    public RecoForm findByName(String name) {
        return getCollection().find(eq(RecoForm._NAME, name)).first();
    }


    @Override
    public List<RecoForm> findAllByStatus(List<Integer> statusList) {
        List<RecoForm> templates = new ArrayList<>();
        FindIterable<RecoForm> find = getCollection().find(in(RecoForm._STATUS, statusList));
        find.forEach((Consumer<RecoForm>) templates::add);
        return templates;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getTagsDistinct() {
        final Set<String> result = new HashSet<>();
        Bson filter = and(
                ne(RecoForm._TAGS, null),
                nin(RecoForm._TAGS, new Object[]{null}),
                eq(RecoForm._STATUS, RecoForm.ACTIVE)); // Get tags from Active RecoForms only
        getCollection()
                .distinct(RecoForm._TAGS, filter, String.class)
                .into(result);
        return result;
    }


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    @Override
    public MongoCollection<RecoForm> getCollection() {
        return super.getCollection(RecoForm.class);
    }


    @Override
    public FilesDao getFilesDao() {
        return _recoFormFilesDao;
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
        indexesList.add(new IndexModel(new Document(RecoForm._STATUS, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(RecoForm._TAGS, 1), indexOptions1));

        IndexOptions indexOptions3 = new IndexOptions().collation(_collation).background(true);
        indexesList.add(new IndexModel(new Document(RecoForm._NAME, 1), indexOptions3));

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
