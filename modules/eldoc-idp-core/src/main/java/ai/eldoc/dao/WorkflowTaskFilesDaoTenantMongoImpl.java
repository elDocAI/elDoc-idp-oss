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
import ai.eldoc.model.DbFileRef;
import ai.eldoc.model.filemeta.Sha256FileMeta;
import com.mongodb.client.model.IndexModel;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.util.*;


/**
 * WorkflowTask Files DAO MongoDB Implementation
 */
@ApplicationScoped
public class WorkflowTaskFilesDaoTenantMongoImpl extends AbstractMongoFilesDao implements WorkflowTaskFilesDao {

    public static final String BUCKET_NAME = WorkflowTaskDaoTenantMongoImpl.COLLECTION_NAME;


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //
    @Override
    public String getBucketName() {
        return BUCKET_NAME;
    }


    @Override
    public DbFileRef uploadFromStream(String fileName, InputStream is, String contentType, Map<String, Object> metadata) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        if (!metadata.containsKey(Sha256FileMeta._SHA256)) {
            metadata.put(Sha256FileMeta._SHA256, ""); // Force SHA-256 hash calculation, added since v7.2026.4 for WorkflowTaskFilesDaoTenantMongoImpl
        }
        return super.uploadFromStream(fileName, is, contentType, metadata);
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
        doIndexHousekeep(getIndexesList());
    }
}
