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

import ai.eldoc.dao.mongo.AbstractMongoFilesDao;
import com.mongodb.client.model.IndexModel;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;


/**
 * RecoForm related Files Dao Implementation
 */
@ApplicationScoped
public class RecoFormFilesDaoMongoImpl extends AbstractMongoFilesDao implements RecoFormFilesDao {

    public static final String BUCKET_NAME = RecoFormDaoMongoImpl.COLLECTION_NAME + MONGO_GRIDFS_SUFFIX;


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //
    @Override
    public String getBucketName() {
        return BUCKET_NAME;
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
