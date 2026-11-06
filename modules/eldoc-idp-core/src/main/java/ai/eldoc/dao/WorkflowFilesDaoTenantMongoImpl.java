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

import ai.eldoc.dao.mongo.AbstractMongoFilesRevisionDao;
import ai.eldoc.exceptions.FileHasRevisionException;
import ai.eldoc.exceptions.FileParentRevisionNotFoundException;
import ai.eldoc.model.DbFile;
import ai.eldoc.model.DbFileRef;
import ai.eldoc.model.filemeta.*;
import com.mongodb.client.ClientSession;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import java.io.InputStream;
import java.util.*;
import static com.mongodb.client.model.Filters.*;


/**
 * Workflow Files DAO MongoDB Implementation
 */
@ApplicationScoped
public class WorkflowFilesDaoTenantMongoImpl extends AbstractMongoFilesRevisionDao implements WorkflowFilesDao {

    public static final String BUCKET_NAME = WorkflowDaoTenantMongoImpl.COLLECTION_NAME;


    //
    // DAO Specific Business Methods --------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteFile(ClientSession dbClientSession, DbFileRef dbFileRef) {
        deleteFile(dbClientSession, dbFileRef, true);
        getFileRefs(dbClientSession, dbFileRef.getId()).forEach(ref -> deleteFile(dbClientSession, ref));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<DbFile> findAllBySha256(String sha256hash, int limit) {
        List<DbFile> result = new ArrayList<>();
        getGridFSBucket()
                .find(eq(Sha256FileMeta._PATH_METADATA_SHA256, sha256hash))
                .collation(_collation)
                .limit(limit)
                .forEach(gridFsFile -> result.add(convertGridFSFileToDbFile(gridFsFile, createDbFileRef(gridFsFile.getObjectId().toHexString(), null))));
        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<DbFileRef> findPendingTextIndexProcessing() {
        List<GridFSFile> gridFSFilesList = new ArrayList<>();
        getGridFSBucket()
                .find(and(
                        or(
                                eq(WorkflowFileMeta._PATH_METADATA_TYPE, WorkflowFileMeta.TYPE_DEFAULT),
                                exists(WorkflowFileMeta._PATH_METADATA_TYPE, false) // Missing Type field is same as 0 = WorkflowFileMeta.TYPE_WORKFLOW_FILE
                        ),
                        or(
                                eq(WorkflowFileMeta._PATH_METADATA_TEXT_INDEX_STATE, WorkflowFileMeta.TEXT_INDEX_STATE_DEFAULT),
                                exists(WorkflowFileMeta._PATH_METADATA_TEXT_INDEX_STATE, false) // Missing TEXT_INDEX_STATE is same as 0 = STATE_INDEX_DEFFAULT (not processed)
                        )
                ))
                .into(gridFSFilesList);

        if (gridFSFilesList.isEmpty()) {
            return new ArrayList<>();
        }

        return gridFSFilesList.stream()
                .map(gridFSFile -> createDbFileRef(gridFSFile.getObjectId().toHexString(), null))
                .toList();
    }


    @Override
    public DbFileRef uploadRevisionFromStream(String fileName, InputStream is, String contentType, Map<String, Object> metadata, DbFileRef revisionToId) throws FileHasRevisionException, FileParentRevisionNotFoundException {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        if (!metadata.containsKey(Sha256FileMeta._SHA256)) {
            metadata.put(Sha256FileMeta._SHA256, ""); // Force SHA-256 hash calculation
        }
        return super.uploadRevisionFromStream(fileName, is, contentType, metadata, revisionToId);
    }


    @Override
    public DbFileRef uploadFromStream(String fileName, InputStream is, String contentType, Map<String, Object> metadata) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        if (!metadata.containsKey(Sha256FileMeta._SHA256)) {
            metadata.put(Sha256FileMeta._SHA256, ""); // Force SHA-256 hash calculation
        }
        return super.uploadFromStream(fileName, is, contentType, metadata);
    }


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

        IndexOptions indexOptions1 = new IndexOptions().background(true);
        indexesList.add(new IndexModel(new Document(WorkflowFileMeta._PATH_METADATA_TYPE, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(WorkflowFileMeta._PATH_METADATA_TEXT_INDEX_STATE, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(TextIndexFileMeta._PATH_METADATA_REF_ID, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(RevisionFileMeta._PATH_METADATA_REVISION_ROOT, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(RevisionFileMeta._PATH_METADATA_REVISION_TO, 1), indexOptions1));

        IndexOptions indexOptions2 = new IndexOptions().background(true).collation(_collation);
        indexesList.add(new IndexModel(new Document(WorkflowFileMeta._PATH_METADATA_SHA256, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(DbFile._FILENAME, 1), indexOptions2));

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
