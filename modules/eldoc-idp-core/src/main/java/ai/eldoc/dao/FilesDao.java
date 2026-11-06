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

import ai.eldoc.model.*;
import com.mongodb.client.ClientSession;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;


/**
 * File Aware DAO Interface
 */
public interface FilesDao {

    /**
     * Returns Collection name
     *
     * @return
     */
    public String getCollectionName();


    /**
     * Returns Bucket name (Files collection name)
     *
     * @return
     */
    public String getBucketName();


    /**
     * Initializes GridFS collection, if needed, by uploading (and then removing) temporary file.<br>
     * This will trigger default collection initialization logic
     *
     * @return {@code true} if collection was successfully initialized or {@code false}, if no initialization required
     */
    public boolean initCollection();


    /**
     * Returns chunk size (in bytes), defined for collection
     *
     * @return
     */
    public int getChunkSize();


    /**
     * Initializes DB session to be used for database operations encapsulation or transactions management
     *
     * @return
     */
    public ClientSession startSession();


    /**
     * Creates new DbFileRef object for the given fileId
     *
     * @param fileId
     * @param tags
     * @return
     */
    public DbFileRef createDbFileRef(String fileId, List<String> tags);


    /**
     * Returns file found by the given db file reference
     *
     * @param dbClientSession
     * @param dbFileRef
     * @return
     */
    public DbFile getFile(ClientSession dbClientSession, DbFileRef dbFileRef);


    /**
     * Returns all files found by the given list of db file references
     *
     * @param dbClientSession
     * @param dbFileList
     * @return
     */
    public List<DbFile> getFiles(ClientSession dbClientSession, List<DbFileRef> dbFileList);

    /**
     * Finds all files, that refer to provided file by {@link WorkflowFileMeta#_REF_ID} field
     *
     * @param dbClientSession
     * @param fileId
     * @return
     */
    public List<DbFileRef> getFileRefs(ClientSession dbClientSession, String fileId);


    /**
     * Finds all files of specific type, that refer to provided file by {@link WorkflowFileMeta#_REF_ID} field
     *
     * @param dbClientSession
     * @param fileId
     * @param type
     * @return
     */
    public List<DbFileRef> getFileRefs(ClientSession dbClientSession, String fileId, Integer type);


    /**
     * Returns file download stream for file download from DB
     *
     * @param dbFileRef
     * @return
     */
    public DbFileDownloadStream getFileDownloadStream(DbFileRef dbFileRef);


    /**
     * Returns file upload stream for file upload into DB
     *
     * @param filename
     * @param contentType
     * @param fileTagList
     * @return
     */
    public DbFileUploadStream getFileUploadStream(String filename, String contentType, List<String> fileTagList);


    /**
     * Returns file content in bytes
     *
     * @param dbFileRef
     * @return
     */
    public byte[] getFileBytes(DbFileRef dbFileRef);


    /**
     * Performs file upload to the database
     *
     * @param fileName
     * @param fileBytes
     * @param contentType
     * @param metadata
     * @return
     */
    public DbFileRef uploadFromByteArray(String fileName, byte[] fileBytes, String contentType, Map<String, Object> metadata);


    /**
     * Performs file upload to the database
     *
     * @param fileName
     * @param is
     * @param contentType
     * @param metadata
     * @return
     */
    public DbFileRef uploadFromStream(String fileName, InputStream is, String contentType, Map<String, Object> metadata);


    /**
     * Performs file download to provided output stream
     *
     * @param dbFileRef
     * @param os
     */
    public void downloadToStream(DbFileRef dbFileRef, OutputStream os);


    /**
     * Removes file from the DB
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param dbFileRef
     */
    public void deleteFile(ClientSession dbClientSession, DbFileRef dbFileRef);


    /**
     * Removes file from the DB and also from the provided model
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param model
     * @param dbFileRef
     */
    public void deleteFile(ClientSession dbClientSession, BasicFileAwareModel model, DbFileRef dbFileRef);


    /**
     * Updates fileMeta for the the given DbFileRef
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param dbFileRef
     * @param fileMeta
     * @return
     */
    public boolean updateFileMeta(ClientSession dbClientSession, DbFileRef dbFileRef, Map<String, Object> fileMeta);


    /**
     * Initializes file upload to GridFs by creating record in '.files' collection and DbFileRef for it
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param fileName
     * @param contentType
     * @param length
     * @param metadata
     * @return
     */
    public DbFileRef initChunkUpload(ClientSession dbClientSession, String fileName, String contentType, long length, Map<String, Object> metadata);


    /**
     * Uploads data (additional chunk) for the provided file<br>
     * <b>IMPORTANT:</b> chunk size should match chunk size, defined in collection
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param fileRef
     * @param chunkIdx
     * @param chunkBytes
     */
    public void uploadChunkFromByteArray(ClientSession dbClientSession, DbFileRef fileRef, int chunkIdx, byte[] chunkBytes);


    /**
     * Performs post-upload validation, update (calculating hash, etc) and marks file as uploaded
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param file
     * @return
     */
    public boolean commitChunkUpload(ClientSession dbClientSession, DbFileRef file);


    //
    // DAO houskeeping methods --------------------------------------------------------------------
    //
    /**
     * Performs indexes house-keeping
     */
    public void doIndexHousekeep();
}
