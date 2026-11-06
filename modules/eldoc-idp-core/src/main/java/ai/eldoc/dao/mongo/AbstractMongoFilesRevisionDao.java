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
import ai.eldoc.dao.FilesRevisionDao;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.model.filemeta.RevisionFileMeta;
import com.mongodb.client.ClientSession;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.GraphLookupOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import static com.mongodb.client.model.Aggregates.graphLookup;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Updates.set;


/**
 * Abstract MongoDB Files with Revisions Support Dao Implementation
 */
public abstract class AbstractMongoFilesRevisionDao extends AbstractMongoFilesDao implements FilesRevisionDao {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoFilesRevisionDao.class);


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFileRef getFileRootRev(DbFileRef dbFileRef) {
        List<DbFileRef> revisions = getFileRevisions(dbFileRef);
        if (revisions.isEmpty()) {
            // This should naver happen as at least the requested DbFileRef should be present
            // This may happen only when all files were actually already removed
            LOG.warn("{}: No revisions were found. Was it already deleted from the DB?", dbFileRef);
            return dbFileRef;
        }
        // Get the root revision what will be the first element (index = 0) in the revisions list
        return revisions.get(0);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFileRef getFileLatestRev(DbFileRef dbFileRef) {
        List<DbFileRef> revisions = getFileRevisions(dbFileRef);
        if (revisions.isEmpty()) {
            // This should naver happen as at least the requested DbFileRef should be present
            // This may happen only when all files were actually already removed
            LOG.warn("{}: No revisions were found. Was it already deleted from the DB?", dbFileRef);
            return dbFileRef;
        }
        // Get the latest revision what will be the last element in the revisions list
        return revisions.get(revisions.size() - 1);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFile getFileLatestRevAsDbFile(DbFileRef dbFileRef) {
        List<DbFile> revisions = getFileRevisionsAsDbFiles(dbFileRef);
        // Get the latest revision what will be the last element in the revisions list
        return revisions.get(revisions.size() - 1);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<DbFile> getFilesLatestRev(List<DbFileRef> dbFileList) {
        Map<DbFileRef, List<DbFile>> filesRevisionsMap = getFilesRevisionsAsDbFiles(dbFileList);
        List<DbFile> result = new ArrayList<>();

        // In order to keep order - we iterate over the originally requested list of DbFileRef
        dbFileList.forEach(dbFileRefItem -> {
            List<DbFile> fileRevisions = filesRevisionsMap.get(dbFileRefItem); // This list have to contain at least one element
            result.add(fileRevisions.get(fileRevisions.size() - 1));
        });

        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFileHasRevision(DbFileRef dbFileRef) {
        return getGridFSBucket().find(eq(RevisionFileMeta._PATH_METADATA_REVISION_TO, new ObjectId(dbFileRef.getId()))).first() != null;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<DbFileRef> getFileRevisions(DbFileRef dbFileRef) {
        return getFileRevisionsAsDbFiles(dbFileRef).stream().map(DbFile::getDbFileRef).collect(Collectors.toList());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<DbFile> getFileRevisionsAsDbFiles(DbFileRef dbFileRef) {
        return getFilesRevisionsAsDbFiles(Arrays.asList(dbFileRef)).get(dbFileRef);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<DbFileRef, List<DbFile>> getFilesRevisionsAsDbFiles(List<DbFileRef> dbFileRefList) {
        Map<DbFileRef, List<DbFile>> resultMap = new HashMap<>();

        List<String> fileIdList = dbFileRefList.stream().map(DbFileRef::getId).collect(Collectors.toList());

        // Results are returned per requested DbFileRef
        getCollection().aggregate(getRevisionsAggregationPipeline(fileIdList)).allowDiskUse(true).forEach((Document gridFsFileDoc) -> {
            if (LOG.isTraceEnabled()) {
                LOG.trace("DbFile[{}-{}]: parentRevisions:{} - revisions:{}",
                        getBucketName(),
                        gridFsFileDoc.getObjectId(DbFile._ID).toHexString(),
                        gridFsFileDoc.getList("parentRevisions", Document.class, Arrays.asList()).size(),
                        gridFsFileDoc.getList("revisions", Document.class, Arrays.asList()).size()
                );
            }

            List<DbFile> perDbFileRefResult = new ArrayList<>();

            // Locate DbFileRef from the provided list in query
            DbFileRef dbFileRef = dbFileRefList
                    .stream()
                    .filter(dbFileRefItem -> dbFileRefItem.getId().equals(gridFsFileDoc.getObjectId(DbFile._ID).toHexString()))
                    .findFirst() // This always have to return result and be not-null
                    .get();

            // Convert document.parentRevisions into list of DbFiles
            // Add all previous revisions, where parentRevisions.get(0) - is a root revision
            perDbFileRefResult.addAll(
                    gridFsFileDoc.getList("parentRevisions", Document.class, Arrays.asList()).stream()
                            // As MongoDB doesn't maintain order in resulting list - we need to perfrom sort (in desc order: rev:2, rev:1, rev:0)
                            .sorted((Document doc1, Document doc2) -> -Long.compare(doc1.getLong("rev"), doc2.getLong("rev")))
                            // Next we convert Document which represents GridFSFile into DbFile
                            .map(gridFsFileDocItm -> convertDocumentToDbFile(gridFsFileDocItm, null))
                            .collect(Collectors.toList())
            );

            // Add provided dbFileRef as we were searching revisoins/parentVersion relatevelly to it
            perDbFileRefResult.add(convertDocumentToDbFile(gridFsFileDoc, dbFileRef));

            // Convert document.revisions into list of DbFiles
            // Add all remaining revisions
            perDbFileRefResult.addAll(
                    gridFsFileDoc.getList("revisions", Document.class, Arrays.asList()).stream()
                            // As MongoDB doesn't maintain order in resulting list - we need to perfrom sort (in asc order: rev:0, rev:1, rev:2)
                            .sorted((Document doc1, Document doc2) -> Long.compare(doc1.getLong("rev"), doc2.getLong("rev")))
                            // Next we convert Document which represents GridFSFile into DbFile
                            .map(gridFsFileDocItm -> convertDocumentToDbFile(gridFsFileDocItm, null))
                            .collect(Collectors.toList())
            );

            resultMap.put(dbFileRef, perDbFileRefResult);
        });

        // Next we need to ensure that result is present for ech of requested DbFileRef an in case not - add them as NotFound items
        dbFileRefList.forEach(dbFileRef -> {
            if (!resultMap.containsKey(dbFileRef)) {
                resultMap.put(dbFileRef, Arrays.asList(DbFile.asNotFound(dbFileRef)));
            }
        });

        return resultMap;
    }


    /**
     * Returns optimized revisions aggregation pipeline<br>
     * IMPORTANT: check the DB query syntax for specific computed fields names used in this method: rev, parentRevisions, revisions
     *
     * @param fileIdList
     * @return
     */
    private List<Bson> getRevisionsAggregationPipeline(List<String> fileIdList) {
        List<Bson> aggregateList = new ArrayList<>();

        aggregateList.add(match(in(DbFile._ID, fileIdList.stream().map(fileId -> new ObjectId(fileId)).collect(Collectors.toList()))));

        GraphLookupOptions graphLookupOptions = new GraphLookupOptions();
        graphLookupOptions.depthField("rev");

        // Perform lookup for the parent revisions
        aggregateList.add(graphLookup(
                getCollection().getNamespace().getCollectionName(),
                "$" + RevisionFileMeta._PATH_METADATA_REVISION_TO,
                RevisionFileMeta._PATH_METADATA_REVISION_TO,
                DbFile._ID,
                "parentRevisions",
                graphLookupOptions));

        // Perform lookup for the revisions
        aggregateList.add(graphLookup(
                getCollection().getNamespace().getCollectionName(),
                "$" + DbFile._ID,
                DbFile._ID,
                RevisionFileMeta._PATH_METADATA_REVISION_TO,
                "revisions",
                graphLookupOptions));

        return aggregateList;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteFile(ClientSession dbClientSession, DbFileRef dbFileRef, boolean withAllRevisions) {
        if (withAllRevisions) {
            List<DbFileRef> allRevisions = getFileRevisions(dbFileRef);
            Collections.reverse(allRevisions);
            allRevisions.forEach((dbRefItem) -> {
                super.deleteFile(dbClientSession, dbRefItem);
            });
        } else {
            super.deleteFile(dbClientSession, dbFileRef);
        }
    }


    private Map<String, Object> updateFileMetaForRevision(Map<String, Object> metadata, DbFileRef rootRef, DbFileRef revisionToId) {
        Map<String, Object> fileMetadata = new Document(); // Create a locally managed metadata

        if (metadata != null) {
            // Transfer all provided metadata
            fileMetadata.putAll(metadata);
        }

        fileMetadata.put(RevisionFileMeta._REVISION_TO, new ObjectId(revisionToId.getId()));
        fileMetadata.put(RevisionFileMeta._REVISION_ROOT, new ObjectId(rootRef.getId()));

        return fileMetadata;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFileRef uploadRevisionFromStream(String fileName, InputStream is, String contentType, Map<String, Object> metadata, DbFileRef revisionToId)
            throws FileHasRevisionException, FileParentRevisionNotFoundException {
        try {
            DbFileRef rootRef = getFileRootRev(revisionToId);

            Map<String, Object> fileMetadata = updateFileMetaForRevision(metadata, rootRef, revisionToId);

            // Perform consistency cheks
            if (getCollection().countDocuments(eq(BasicModel._ID, new ObjectId(revisionToId.getId()))) != 1) {
                throw new FileParentRevisionNotFoundException().setDbFileRef(revisionToId);
            }

            if (isFileHasRevision(revisionToId)) {
                throw new FileHasRevisionException().setDbFileRef(revisionToId);
            }

            // All looks good, proceed with the revision file upload
            DbFileRef uploadedFileRef = uploadFromStream(fileName, is, contentType, fileMetadata);

            // Update root revision metadata with info about latest revision
            GridFSFile uploadedFile = getGridFsFile(null, uploadedFileRef);
            UpdateResult updRslt = getCollection().updateOne(
                    eq(BasicModel._ID, new ObjectId(rootRef.getId())),
                    List.of(
                            set(RevisionFileMeta._PATH_METADATA_REVISION_LAST_ID, new ObjectId(uploadedFileRef.getId())),
                            set(RevisionFileMeta._PATH_METADATA_REVISION_LAST_SIZE, uploadedFile.getLength()),
                            set(RevisionFileMeta._PATH_METADATA_REVISION_LAST_DATE, uploadedFile.getUploadDate())
                    ));

            if (!(updRslt.wasAcknowledged() && updRslt.getModifiedCount() == 1)) {
                LOG.warn("{}: Error updating root file revision metadata with latest uploaded revision: {}", rootRef, uploadedFileRef);
            }

            return uploadedFileRef;
        } catch (ElDocRuntimeException elex) {
            throw elex;
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void markFileAsRevision(ClientSession dbClientSession, DbFileRef file, Map<String, Object> metadata, DbFileRef revisionToId)
            throws FileHasRevisionException, FileParentRevisionNotFoundException {
        try {
            DbFileRef rootRef = getFileRootRev(revisionToId);

            // Retrieve and update file meta
            Map<String, Object> fileMetadata = updateFileMetaForRevision(metadata, rootRef, revisionToId);

            // Perform consistency cheks
            if (getCollection().countDocuments(eq(BasicModel._ID, new ObjectId(revisionToId.getId()))) != 1) {
                throw new FileParentRevisionNotFoundException().setDbFileRef(revisionToId);
            }

            if (isFileHasRevision(revisionToId)) {
                throw new FileHasRevisionException().setDbFileRef(revisionToId);
            }

            // All looks good, proceed with the revision file meta update
            updateFileMeta(dbClientSession, file, fileMetadata);
            // Update root revision metadata with info about latest revision
            GridFSFile uploadedFile = getGridFsFile(dbClientSession, file);
            UpdateResult updRslt = getCollection().updateOne(
                    eq(BasicModel._ID, new ObjectId(rootRef.getId())),
                    List.of(
                            set(RevisionFileMeta._PATH_METADATA_REVISION_LAST_ID, new ObjectId(file.getId())),
                            set(RevisionFileMeta._PATH_METADATA_REVISION_LAST_SIZE, uploadedFile.getLength()),
                            set(RevisionFileMeta._PATH_METADATA_REVISION_LAST_DATE, uploadedFile.getUploadDate())
                    ));

            if (!(updRslt.wasAcknowledged() && updRslt.getModifiedCount() == 1)) {
                LOG.warn("{}: Error updating root file revision metadata with latest uploaded revision: {}", rootRef, file);
            }
        } catch (ElDocRuntimeException elex) {
            throw elex;
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFileRef restoreRevision(DbFileRef revisionToRestore, Map<String, Object> metadata, DbFileRef revisionTo) throws RevisionRestoreException, FileHasRevisionException, FileParentRevisionNotFoundException {
        List<DbFileRef> revList = getFileRevisions(revisionTo);

        if (revList.size() == 1) {
            throw new RevisionRestoreException(String.format("File[%s] does not have revisions to restore. Revision chain size is 1 (only single file is )", revisionTo));
        }

        if (revList.stream().noneMatch(dbFileRef -> dbFileRef.getId().equals(revisionToRestore.getId()))) {
            throw new RevisionRestoreException(String.format("File[%s] does not have revision[%s] to restore", revisionTo, revisionToRestore));
        }

        if (revList.get(revList.size() - 1).getId().equals(revisionToRestore.getId())) {
            throw new RevisionRestoreException(String.format("File[%s] can not restore already the latest revision[%s]", revisionTo, revisionToRestore));
        }

        try (DbFileDownloadStream revisionToRestoreIS = getFileDownloadStream(revisionToRestore)) {
            DbFileRef restoredRev = uploadRevisionFromStream(
                    revisionToRestoreIS.getDbFile().getFileName(),
                    revisionToRestoreIS,
                    revisionToRestoreIS.getDbFile().getContentType(),
                    metadata,
                    revisionTo
            );
            LOG.debug("File[{}] revision[{}] was restored and saved as: {}", revisionTo, revisionToRestore, restoredRev);
            return restoredRev;
        }
    }

}
