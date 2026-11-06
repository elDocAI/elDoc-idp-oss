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
import ai.eldoc.dao.FilesDao;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.model.filemeta.*;
import ai.eldoc.service.DbClientService;
import ai.eldoc.util.FileUtils;
import ai.eldoc.util.MimeType;
import com.mongodb.MongoException;
import com.mongodb.MongoGridFSException;
import com.mongodb.client.*;
import com.mongodb.client.gridfs.*;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import jakarta.inject.Inject;
import org.apache.commons.codec.binary.Hex;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;


/**
 * Abstract MongoDB Files Dao Implementation
 */
public abstract class AbstractMongoFilesDao implements MongoFileDao, FilesDao {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoFilesDao.class);

    /**
     * Default case-insensitive collation with en locale
     */
    protected final static Collation _collation = Collation.builder().locale("en").collationStrength(CollationStrength.SECONDARY).build();

    /**
     * File chunk data (binary)
     */
    protected static final String _CHUNK_DATA = "data";
    /**
     * Chunk index in file
     */
    protected static final String _CHUNK_NUM = "n";
    /**
     * Reference to file, that owns this chunk
     */
    protected static final String _CHUNK_FILE_REF = "files_id";

    @Inject
    protected DbClientService _dbClientService;


    //
    // Implementation for MongoDBDao methods --------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public String getCollectionName() {
        return getBucketName() + ".files";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MongoCollection<Document> getCollection() {
        return getDatabase().getCollection(getCollectionName(), Document.class).withCodecRegistry(MongoUtils.getCodecRegistry());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MongoCollection<GridFSFile> getCollectionTyped() {
        return getDatabase().getCollection(getCollectionName(), GridFSFile.class).withCodecRegistry(MongoUtils.getCodecRegistry());
    }


    /**
     * Returns the name of the collection, used to store file chunks
     *
     * @return
     */
    private String getChunksCollectionName() {
        return getBucketName() + ".chunks";
    }


    /**
     * Returns MongoDB collection for accessing file chunks
     *
     * @return
     */
    public MongoCollection<Document> getChunksCollection() {
        return getDatabase().getCollection(getChunksCollectionName(), Document.class);
    }


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
    public GridFSBucket getGridFSBucket() {
        return GridFSBuckets.create(getDatabase(), getBucketName());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int getChunkSize() {
        return getGridFSBucket().getChunkSizeBytes();
    }


    /**
     * Checks, if collection exists in the database
     *
     * @return
     */
    private boolean isCollectionExists() {
        try (MongoCursor<String> cursor = getDatabase()
                .listCollectionNames()
                .cursor()) {
            while (cursor.hasNext()) {
                if (getCollectionName().equals(cursor.next())) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean initCollection() {
        if (isCollectionExists()) {
            return false;
        }

        // Upload temporary file to the GridFS collection to trigger its creation and initialization
        DbFileRef tmpFileRef = uploadFromByteArray("initializationFile.tmp", new byte[1], MimeType.DEFAULT, null);

        // Remove temporary file
        deleteFile(null, tmpFileRef);

        return true;
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
    public DbFileDownloadStream getFileDownloadStream(DbFileRef dbFileRef) {
        try {
            GridFSDownloadStream grisFsIs = getGridFSBucket().openDownloadStream(new ObjectId(dbFileRef.getId()));
            return new DbFileDownloadStream(grisFsIs, convertGridFSFileToDbFile(grisFsIs.getGridFSFile(), dbFileRef));
        } catch (MongoGridFSException mex1) {
            throw new RecordNotFoundInDbException(mex1);
        } catch (MongoException mex0) {
            throw new DatabaseException(mex0);
        } catch (IllegalArgumentException ex) {
            throw new BadArgumentException(ex);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFileUploadStream getFileUploadStream(String filename, String contentType, List<String> fileTagList) {
        GridFSUploadOptions opts = new GridFSUploadOptions();
        opts.metadata(new Document(FileMeta._CONTENT_TYPE, FileUtils.ensureContentType(contentType, filename)));
        GridFSUploadStream us = getGridFSBucket().openUploadStream(filename, opts);
        return new DbFileUploadStream(us, getBucketName(), us.getObjectId().toHexString(), fileTagList);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getFileBytes(DbFileRef dbFile) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();) {
            getGridFSBucket().downloadToStream(new ObjectId(dbFile.getId()), outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFileRef createDbFileRef(String fileId, List<String> tags) {
        return new DbFileRef(getBucketName(), fileId, tags);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFile getFile(ClientSession dbClientSession, DbFileRef dbFileRef) {
        return convertGridFSFileToDbFile(getGridFsFile(dbClientSession, dbFileRef), dbFileRef);
    }


    /**
     * Converts GridFSFile to DbFile<br>
     * <b>IMPORTANT</b>: this method handles both types of collections with files:
     * <ol>
     * <li>when tags are stored withing the GridFS files itself (e.g. WorkflowDao) - in this case tags are taken from the respective Metadata field;</li>
     * <li>and also when tags are stored withing the DbFileRef inside the doc itself (e.g. RecoFormDao) - in this case files are transferred from the given sourceDbFileRef argument</li>
     * </ol>
     *
     * @param gridFsFile
     * @param sourceDbFileRef optional (can be {@code null}), when provided used for transferring tags (if any) from the source DbFileRef to the DbFileRef embedded into the returned DbFile
     * @return
     */
    protected DbFile convertGridFSFileToDbFile(GridFSFile gridFsFile, DbFileRef sourceDbFileRef) {
        if (gridFsFile == null) {
            return null;
        }

        // Get metadata
        Document metaDoc = gridFsFile.getMetadata();
        if (metaDoc == null) {
            metaDoc = new Document();
            LOG.warn("{}: No Metadata is available for _id={}", getBucketName(), gridFsFile.getObjectId().toHexString()); // This should never happen
        }

        List<String> tags = sourceDbFileRef != null ? sourceDbFileRef.getTags() : null; // Because the provided DbFileRef may contain Tags - we duplicate them here

        // Create brand-new DbFileRef
        return new DbFile(
                createDbFileRef(gridFsFile.getObjectId().toHexString(), tags),
                gridFsFile.getFilename(),
                gridFsFile.getLength(),
                gridFsFile.getChunkSize(),
                gridFsFile.getUploadDate(),
                metaDoc // Consider having clone of values
        );
    }


    /**
     * Converts Document representing GridFSFile to DbFile<br>
     * This method may be required in cases where aggregations are in use which return GridFSFile represented as Document
     *
     * @param document which represents GridFSFile
     * @param sourceDbFileRef currently not used, BUT may be used for transferring tags from the source DbFileRef to the DbFileRef embedded into the returned DbFile
     * @return
     * @deprecated To be replaced with documentToObject method
     */
    @Deprecated
    protected DbFile convertDocumentToDbFile(Document document, DbFileRef sourceDbFileRef) {
        if (document == null) {
            return null;
        }

        // PErform validation for presence of the obligatory fields
        if (!(document.getObjectId(DbFile._ID) instanceof ObjectId
                && document.getString("filename") instanceof String
                && document.getLong("length") instanceof Long
                && document.getDate("uploadDate") instanceof Date)) {
            throw new DatabaseException("Error validating provided document as one representing the GridFSFile document: " + document.toJson(MongoUtils.JSON_SERIALIZER_SETTINGS));
        }

        // Get metadata
        Document metaDoc = document.get(FileMeta._METADATA, Document.class);
        if (metaDoc == null) {
            metaDoc = new Document();
            LOG.warn("{}: No Metadata is available for _id={}", getBucketName(), document.getObjectId(DbFile._ID).toHexString()); // This should never happen
        }

        return new DbFile(
                createDbFileRef(document.getObjectId(DbFile._ID).toHexString(), null),
                document.getString("filename"),
                document.getLong("length"),
                document.getInteger("chunkSize"),
                document.getDate("uploadDate"),
                metaDoc // Consider having clone of values
        );
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<DbFile> getFiles(ClientSession dbClientSession, List<DbFileRef> dbFileRefList) {
        List<DbFile> result = new ArrayList<>();
        List<GridFSFile> gridFsFileList = getGridFsFiles(dbClientSession, dbFileRefList.stream().map(DbFileRef::getId).toList());
        for (GridFSFile gridFsFile : gridFsFileList) {
            // Locate the source DbFileRef for this entry
            DbFileRef sourceDbRef = dbFileRefList
                    .stream()
                    .filter(dbFileRefQry -> gridFsFile.getObjectId().toHexString().equals(dbFileRefQry.getId()))
                    .findFirst()
                    .get(); // Here we always have to get not null result
            result.add(convertGridFSFileToDbFile(gridFsFile, sourceDbRef));
        }
        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public GridFSFile getGridFsFile(ClientSession dbClientSession, DbFileRef dbFile) {
        return getGridFsFile(dbClientSession, dbFile.getId());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public GridFSFile getGridFsFile(ClientSession dbClientSession, String fileId) {
        try {
            if (dbClientSession == null) {
                return getGridFSBucket().find(eq(BasicModel._ID, new ObjectId(fileId))).first();
            } else {
                return getGridFSBucket().find(dbClientSession, eq(BasicModel._ID, new ObjectId(fileId))).first();
            }
        } catch (MongoGridFSException ex1) {
            // Consistency check that document still present in collection with given _id, otherwise it throws exception:
            // com.mongodb.MongoGridFSException: No file found with the id: BsonObjectId{value=5858d3507bd0201fee9cd146}
        }
        return null;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<GridFSFile> getGridFsFiles(ClientSession dbClientSession, List<String> fileIdList) {
        List<GridFSFile> result = new ArrayList<>();
        try {
            if (dbClientSession == null) {
                getGridFSBucket().find(in(BasicModel._ID, fileIdList.stream().map(ObjectId::new).toList())).forEach(result::add);
            } else {
                getGridFSBucket().find(dbClientSession, in(BasicModel._ID, fileIdList.stream().map(ObjectId::new).toList())).forEach(result::add);
            }
        } catch (MongoGridFSException ex1) {
            // Consistency check that document still present in collection with given _id, otherwise it throws exception:
            // com.mongodb.MongoGridFSException: No file found with the id: BsonObjectId{value=5858d3507bd0201fee9cd146}
        }
        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFileRef uploadFromByteArray(String fileName, byte[] fileBytes, String contentType, Map<String, Object> metadata) {
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            return uploadFromStream(fileName, is, contentType, metadata);
        } catch (IOException e) {
            throw new DatabaseException(e);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFileRef uploadFromStream(String fileName, InputStream is, String contentType, Map<String, Object> metadata) {
        try {
            Document meta = new Document();
            if (metadata != null) {
                meta.putAll(metadata);
            }
            meta.put(FileMeta._CONTENT_TYPE, FileUtils.ensureContentType(contentType, fileName)); // Method params have higher priority
            GridFSUploadOptions uploadOptions = new GridFSUploadOptions().metadata(meta);

            ObjectId uploadedFileId;
            if (meta.containsKey(Sha256FileMeta._SHA256)) { // This is kind of workaround at this moment for forcing SHA-256 hash calculation
                MessageDigest digest = getDigestInstance();
                InputStream digestStream = new DigestInputStream(is, digest);
                uploadedFileId = getGridFSBucket().uploadFromStream(fileName, digestStream, uploadOptions);

                // Update Hash in the DB
                UpdateResult hashUpdRslt = getCollection().updateOne(
                        eq(BasicModel._ID, uploadedFileId),
                        set(Sha256FileMeta._PATH_METADATA_SHA256, Hex.encodeHexString(digest.digest()))
                );
                if (!(hashUpdRslt.wasAcknowledged() && hashUpdRslt.getModifiedCount() == 1)) {
                    LOG.error("File[{}-{}]: hash was not updated in DB", getBucketName(), uploadedFileId.toHexString());
                }
            } else {
                uploadedFileId = getGridFSBucket().uploadFromStream(fileName, is, uploadOptions);
            }

            return createDbFileRef(uploadedFileId.toHexString(), null);
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }


    @Override
    public void downloadToStream(DbFileRef dbFileRef, OutputStream os) {
        try {
            getGridFSBucket().downloadToStream(new ObjectId(dbFileRef.getId()), os);
        } catch (MongoGridFSException mex1) {
            throw new RecordNotFoundInDbException(mex1);
        } catch (MongoException mex0) {
            throw new DatabaseException(mex0);
        } catch (IllegalArgumentException ex) {
            throw new BadArgumentException(ex);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteFile(ClientSession dbClientSession, DbFileRef dbFileRef) {
        try {
            deleteFileReleatedItems(dbClientSession, dbFileRef);
            if (dbClientSession != null) {
                getGridFSBucket().delete(dbClientSession, new ObjectId(dbFileRef.getId()));
            } else {
                getGridFSBucket().delete(new ObjectId(dbFileRef.getId()));
            }
        } catch (MongoGridFSException mex1) {
            throw new RecordNotFoundInDbException(mex1);
        } catch (MongoException mex0) {
            throw new DatabaseException(mex0);
        } catch (IllegalArgumentException ex) {
            throw new BadArgumentException(ex);
        }
    }


    /**
     * Deletes any related files (e.g. text-index, etc) in case present
     *
     * @param dbClientSession
     * @param dbFileRef
     */
    private void deleteFileReleatedItems(ClientSession dbClientSession, DbFileRef dbFileRef) {
        getFileRefs(dbClientSession, dbFileRef.getId()).forEach(file -> deleteFile(dbClientSession, file));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<DbFileRef> getFileRefs(ClientSession dbClientSession, String fileId) {
        return getFileRefs(dbClientSession, fileId, null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<DbFileRef> getFileRefs(ClientSession dbClientSession, String fileId, Integer type) {
        Bson filesFilter;
        if (type == null) {
            filesFilter = eq(TextIndexFileMeta._PATH_METADATA_REF_ID, fileId);
        } else {
            filesFilter = and(
                    eq(TextIndexFileMeta._PATH_METADATA_REF_ID, fileId),
                    eq(TextIndexAwareFileMeta._PATH_METADATA_TYPE, type)
            );
        }

        AggregateIterable<GridFSFile> aggregateResults;
        if (dbClientSession == null) {
            aggregateResults = getCollectionTyped().aggregate(List.of(match(filesFilter)));
        } else {
            aggregateResults = getCollectionTyped().aggregate(dbClientSession, List.of(match(filesFilter)));
        }

        List<DbFileRef> refs = new ArrayList<>();
        aggregateResults.allowDiskUse(true).forEach(gridFsFile -> {
            DbFileRef fileRef = createDbFileRef(gridFsFile.getObjectId().toHexString(), null);
            refs.add(fileRef);
        });

        return refs;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteFile(ClientSession dbClientSession, BasicFileAwareModel model, DbFileRef dbFileRef) {
        try {
            deleteFile(dbClientSession, dbFileRef);
        } catch (Exception e) {
            // Throws exception like: com.mongodb.MongoGridFSException: No file found with the id: BsonObjectId{value=686c166ab0a1816ed48e4424}
            // in case cannot find file for deletion, we silently ignore it
            LOG.warn("File[{}] not found for deletion; continuing with the remaining steps...", dbFileRef.toString(), e);
        }
        model.getFiles().removeIf(modelDbFile -> Objects.equals(modelDbFile.getId(), dbFileRef.getId()));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateFileMeta(ClientSession dbClientSession, DbFileRef dbFileRef, Map<String, Object> fileMeta) {
        if (dbFileRef == null || fileMeta == null) {
            // Nothing to do
            return true;
        }

        // Prepare list of updates
        List<Bson> listOfUpdates = new ArrayList();
        fileMeta.entrySet().forEach(entry -> {
            if (PropertyAwareModel._PROPERTIES.equals(entry.getKey())) {
                // Be aware, as of Mongo driver version 4.9.1 on empty Map/Document following exception is thrown:
                // com.mongodb.MongoWriteException: Write operation error on server dev3.dms.local:27017. Write error: WriteError{code=40180, message='Invalid $set :: caused by ::
                // an empty object is not a valid value. Found empty object at path metadata._properties', details={}}.
                // As such we are manually handling known fields which could contain empty Maps, e.g.: _properties
                if (entry.getValue() instanceof Map mapValue && mapValue.isEmpty()) {
                    return; // We ignore empty maps
                }
            }
            listOfUpdates.add(set(FileMeta._METADATA.concat(".").concat(entry.getKey()), entry.getValue()));
        });

        // Perform update
        try {
            if (dbClientSession != null) {
                return getCollection().updateOne(dbClientSession, eq(BasicModel._ID, new ObjectId(dbFileRef.getId())), listOfUpdates).wasAcknowledged();
            } else {
                return getCollection().updateOne(eq(BasicModel._ID, new ObjectId(dbFileRef.getId())), listOfUpdates).wasAcknowledged();
            }
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }


    //
    // Chunks upload related methods --------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public DbFileRef initChunkUpload(ClientSession dbClientSession, String fileName, String contentType, long length, Map<String, Object> metadata) {
        Document meta = new Document();
        if (metadata != null) {
            meta.putAll(metadata);
        }

        meta.put(FileMeta._LENGTH_TARGET, length);
        meta.put(FileMeta._CONTENT_TYPE, FileUtils.ensureContentType(contentType, fileName)); // Method params have higher priority

        int chunkSize = getChunkSize();

        Document fileDocument = new Document()
                .append(DbFile._FILENAME, fileName)
                .append(DbFile._LENGTH, 0L)
                .append(DbFile._UPLOAD_DATE, new Date())
                .append(DbFile._CHUNK_SIZE, chunkSize)
                .append(DbFile._METADATA, meta);

        String fileId;
        if (dbClientSession != null) {
            fileId = getCollection().insertOne(dbClientSession, fileDocument).getInsertedId().asObjectId().getValue().toHexString();
        } else {
            fileId = getCollection().insertOne(fileDocument).getInsertedId().asObjectId().getValue().toHexString();
        }

        // Create brand-new DbFileRef
        return createDbFileRef(fileId, null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void uploadChunkFromByteArray(ClientSession dbClientSession, DbFileRef fileRef, int chunkIdx, byte[] chunkBytes) {
        try {
            DbFile file = getFile(dbClientSession, fileRef);

            long length = file.getLength() + chunkBytes.length;
            long expectedLength = (long) file.getMetadata().getOrDefault(FileMeta._LENGTH_TARGET, 0);
            if (length > expectedLength) {
                // File length
                throw new DatabaseException("Chunk upload consistency check failed: received more bytes then expected. Expected bytes: " + expectedLength + "; actual bytes: " + length + ". " + String.valueOf(fileRef));
            }

            // Initialize chunk Meta for MongoDB
            Document chunkDoc = new Document()
                    .append(_CHUNK_FILE_REF, new ObjectId(fileRef.getId()))
                    .append(_CHUNK_NUM, chunkIdx)
                    .append(_CHUNK_DATA, chunkBytes);

            if (dbClientSession != null) {
                getChunksCollection().insertOne(dbClientSession, chunkDoc);
            } else {
                getChunksCollection().insertOne(chunkDoc);
            }

            List<Bson> listOfUpdates = List.of(
                    set(DbFile._LENGTH, length),
                    set(DbFile._UPLOAD_DATE, new Date())
            );

            if (dbClientSession != null) {
                getCollection().updateOne(dbClientSession, eq(BasicFileAwareModel._ID, new ObjectId(fileRef.getId())), listOfUpdates);
            } else {
                getCollection().updateOne(eq(BasicFileAwareModel._ID, new ObjectId(fileRef.getId())), listOfUpdates);
            }
        } catch (MongoException mdbe) {
            LOG.error(getCollectionName(), mdbe);
            throw new DatabaseException("Database Error: " + mdbe.getMessage() + ". " + String.valueOf(fileRef), mdbe);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean commitChunkUpload(ClientSession dbClientSession, DbFileRef file) {
        List<Bson> listOfUpdates = List.of(
                new Document("$unset", FileMeta._PATH_METADATA_LENGTH_TARGET)
        );

        if (dbClientSession != null) {
            return getCollection().updateOne(dbClientSession, eq(BasicModel._ID, new ObjectId(file.getId())), listOfUpdates).wasAcknowledged();
        } else {
            return getCollection().updateOne(eq(BasicModel._ID, new ObjectId(file.getId())), listOfUpdates).wasAcknowledged();
        }
    }


    //
    // DAO houskeeping methods --------------------------------------------------------------------
    //


    /**
     * Returns list of Indexes for this DAO
     *
     * @return
     */
    abstract protected List<IndexModel> getIndexesList();


    /**
     * Performs indexes house-keeping
     *
     * @param indexesList List of {@link IndexModel} indexes, that are required to be present in Collection
     */
    protected void doIndexHousekeep(List<IndexModel> indexesList) {
        LOG.info(">> START: Indexes housekeeping on the collection: {}", getCollectionName());
        LOG.debug(">> Existing indexes:");
        getCollection().listIndexes().forEach((Consumer<Document>) indexDoc -> LOG.debug("{}", indexDoc));

        LOG.info(">> Drop indexes...");
        getCollection().dropIndexes();

        if (!getIndexesList().isEmpty()) {
            LOG.info(">> Creating indexes...");
            getCollection().createIndexes(indexesList);
        } else {
            // We need this handing as otherwise MongoDB will thow an exception:
            // com.mongodb.MongoCommandException: Command failed with error 2 (BadValue): 'Must specify at least one index to create' on server db1.eldoc.local:27017 (using PSA)
            LOG.info(">> No indexes to be created, skipping indexes creation...");
        }

        LOG.debug(">> Indexes created, list indexes:");
        getCollection().listIndexes().forEach((Consumer<Document>) indexDoc -> LOG.debug("{}", indexDoc));

        LOG.info(">> END: Indexes housekeeping on the collection: {}", getCollectionName());
    }


    //
    // Private Methods ----------------------------------------------------------------------------
    //
    private MessageDigest getDigestInstance() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }
}
