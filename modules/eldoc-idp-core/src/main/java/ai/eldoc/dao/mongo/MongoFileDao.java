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

import ai.eldoc.model.DbFileRef;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import java.util.List;


/**
 * MongoDB Files Dao Interface
 */
public interface MongoFileDao {

    @Deprecated
    public static final String MONGO_GRIDFS_SUFFIX = "Data";


    /**
     * Returns MongoDB Collection
     *
     * @return
     */
    public MongoCollection<Document> getCollection();


    /**
     * Returns MongoDB Collection typed to {@link GridFSFile}
     *
     * @return
     */
    public MongoCollection<GridFSFile> getCollectionTyped();


    /**
     * Returns GridFSBucket for the current DAO
     *
     * @return
     */
    public GridFSBucket getGridFSBucket();


    /**
     * Returns GridFSFile file
     *
     * @param dbClientSession
     * @param dbFile
     * @return
     */
    public GridFSFile getGridFsFile(ClientSession dbClientSession, DbFileRef dbFile);


    /**
     * Returns GridFSFile file
     *
     * @param dbClientSession
     * @param fileId
     * @return
     */
    public GridFSFile getGridFsFile(ClientSession dbClientSession, String fileId);


    /**
     * Returns GridFSFile files list based on the given list of ids
     *
     * @param dbClientSession
     * @param fileIdList
     * @return
     */
    public List<GridFSFile> getGridFsFiles(ClientSession dbClientSession, List<String> fileIdList);

}
