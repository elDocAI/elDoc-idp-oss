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

import ai.eldoc.exceptions.*;
import ai.eldoc.model.DbFile;
import ai.eldoc.model.DbFileRef;
import com.mongodb.client.ClientSession;
import java.io.InputStream;
import java.util.List;
import java.util.Map;


/**
 * File Revision Aware DAO Interface<br>
 * DAO marked with this interface provides support for file-revisions
 *
 * <pre>
 * Files revision logic is built based on the block-chain principle: each new revision refers to the previous version of the file
 * Example:
 * Root file version:
 * {@code
 * {
 *    _id: ObjectId("11"),
 *    fileName: "Document.docx"
 *  }
 * }
 * Revision 1:
 * {@code
 * {
 *      _id: ObjectId("22"),
 *      fileName: "Document.docx"
 *      metadata: {
 *          revisionTo: ObjectId("11")
 *      }
 *  }
 * }
 * </pre>
 */
public interface FilesRevisionDao extends FilesDao {

    /**
     * Checks whether the given file has revisions
     *
     * @param dbFileRef
     * @return TRUE in case the given file has revisions, or FALSE otherwise
     */
    public boolean isFileHasRevision(DbFileRef dbFileRef);


    /**
     * Retrieves DbFileRef of the root revision for the given DbFileRef<br>
     * In case given dbFileRef does not have any root revisions (already a root revision) - method returns provided dbFileRef itself
     *
     * @param dbFileRef
     * @return
     */
    public DbFileRef getFileRootRev(DbFileRef dbFileRef);


    /**
     * Retrieves DbFileRef of the latest revision for the given DbFileRef<br>
     * This method ensures that latest revision is used, it checks for the latest revision and retrieves it (if present) instead of using the provided DbFileRef
     *
     * @param dbFileRef
     * @return DbFileRef of the latest revision for the given DbFileRef
     * <strong>IMPORTANT:</strong><br>
     * - in case no files were found at all in the DB - method ensures that DbFileRef returned
     * (under the hood it uses {@link ai.eldoc.model.DbFile#asNotFound(ai.eldoc.model.DbFileRef)})<br>
     * See more: {@link #getFilesRevisionsAsDbFiles(java.util.List)}
     */
    public DbFileRef getFileLatestRev(DbFileRef dbFileRef);


    /**
     * Retrieves DbFile of the latest revision for the given DbFileRef<br>
     * This method ensures that latest revision is used, it checks for the latest revision and retrieves it (if present) instead of using the provided DbFileRef
     *
     * @param dbFileRef
     * @return DbFile of the latest revision for the given DbFileRef
     * <strong>IMPORTANT:</strong><br>
     * - in case no files were found at all in the DB - method ensures that DbFile returned
     * (under the hood it uses {@link ai.eldoc.model.DbFile#asNotFound(ai.eldoc.model.DbFileRef)})<br>
     * See more: {@link #getFilesRevisionsAsDbFiles(java.util.List)}
     */
    public DbFile getFileLatestRevAsDbFile(DbFileRef dbFileRef);


    /**
     * Retrieves DbFile list by the provided DbFileRef (ensures that latest revision of the given DbFileRef are returned)
     * Forces to ensure that latest revision is used. Checks for the latest revision and retrieve it if present instead of using the provided DbFileRef.
     *
     * @param dbFileList
     * @return List with files revisions as DbFiles
     * <strong>IMPORTANT:</strong><br>
     * - in case no files were found at all in the DB - method ensures that List with
     * one DbFile returned (under the hood it uses {@link ai.eldoc.model.DbFile#asNotFound(ai.eldoc.model.DbFileRef)})<br>
     * See more: {@link #getFilesRevisionsAsDbFiles(java.util.List)}
     */
    public List<DbFile> getFilesLatestRev(List<DbFileRef> dbFileList);


    /**
     * Returns list of all file revisions for the given file<br>
     * Method first of all finds root file revision, and then builds revisions history chain starting from the root version
     *
     * @param dbFileRef
     * @return list which contains root version (element with index 0) and all revisions to it in chronological order<br>
     * <strong>IMPORTANT:</strong><br>
     * - in case no files were found at all in the DB - method ensures that List with
     * one DbFileRef returned (under the hood it uses {@link ai.eldoc.model.DbFile#asNotFound(ai.eldoc.model.DbFileRef)})<br>
     * See more: {@link #getFilesRevisionsAsDbFiles(java.util.List)}
     */
    public List<DbFileRef> getFileRevisions(DbFileRef dbFileRef);


    /**
     * Returns list of all file revisions for the given file<br>
     * Method finds root file revision, and then builds revisions history chain starting from the root version<br>
     * So even in case the given DbFileRef is intermediate revision - method will still work properly for it as well
     *
     *
     * @param dbFileRef
     * @return List of revisions for the given DbFileRef with root version (element with index 0) and all revisions to it in chronological order.
     * <strong>IMPORTANT:</strong><br>
     * - in case no files were found at all in the DB - method ensures that List with
     * one DbFile created as {@link ai.eldoc.model.DbFile#asNotFound(ai.eldoc.model.DbFileRef)} will be present<br>
     * - in case at least one DbFile was found in DB for the given DbFileRef - List with size(1) will be returned.<br>
     * See more: {@link #getFilesRevisionsAsDbFiles(java.util.List)}
     */
    public List<DbFile> getFileRevisionsAsDbFiles(DbFileRef dbFileRef);


    /**
     * Returns list of all file revisions for the given file<br>
     * Method finds root file revision, and then builds revisions history chain starting from the root version<br>
     * So even in case the given DbFileRef is intermediate revision - method will still work properly for it as well
     *
     * @param dbFileRefList
     * @return Map which contains:<br>
     * - key as DbFileRef from the provided list of (DbFileRef).<br>
     * - value as List of revisions for the given DbFileRef with root version (element with index 0) and all revisions to it in chronological order.<br>
     * <strong>IMPORTANT:</strong><br>
     * - in case no DbFile were found at all for the given DbFileRef - method ensures that
     * List with one DbFile created as {@link ai.eldoc.model.DbFile#asNotFound(ai.eldoc.model.DbFileRef)} will be present in the returned Map<br>
     * - in case at least one DbFile was found in DB for the given DbFileRef - List with size(1) will be returned.
     */
    public Map<DbFileRef, List<DbFile>> getFilesRevisionsAsDbFiles(List<DbFileRef> dbFileRefList);


    /**
     * Removes file and all its revisions from the DB
     *
     * @param dbClientSession
     * @param dbFileRef DbFileRef to be deleted
     * @param withAllRevisions whether all revisions should be deleted or not
     */
    public void deleteFile(ClientSession dbClientSession, DbFileRef dbFileRef, boolean withAllRevisions);


    /**
     * Uploads file which is a revision to the given fileId set as {@code revisionToId}
     *
     * @param fileName
     * @param is
     * @param contentType
     * @param metadata file metadata usually used from {@link ai.eldoc.model.filemeta.RevisionFileMeta} or derivatives
     * @param revisionToId
     * @return
     * @throws FileHasRevisionException in case revisionToId file already has revision
     * @throws FileParentRevisionNotFoundException in case file parent revision was not found in the DB
     */
    public DbFileRef uploadRevisionFromStream(String fileName, InputStream is, String contentType, Map<String, Object> metadata, DbFileRef revisionToId)
            throws FileHasRevisionException, FileParentRevisionNotFoundException;


    /**
     * Marks provided file as a new revision to existing file and updates both files metadata
     *
     * @param dbClientSession
     * @param file
     * @param metadata
     * @param revisionToId
     * @throws FileHasRevisionException
     * @throws FileParentRevisionNotFoundException
     */
    public void markFileAsRevision(ClientSession dbClientSession, DbFileRef file, Map<String, Object> metadata, DbFileRef revisionToId)
            throws FileHasRevisionException, FileParentRevisionNotFoundException;


    /**
     * Restores given revision from the revision chain and appends it as revision to the given DbFileRef
     *
     * @param revisionToRestore revision to be restored
     * @param metadata metadata to be used with the restored revision
     * @param revisionTo restored revision to be set as revision to this DbFileRef
     * @return DbFileRef of the restored revision
     * @throws RevisionRestoreException
     * @throws FileHasRevisionException
     * @throws FileParentRevisionNotFoundException
     */
    public DbFileRef restoreRevision(DbFileRef revisionToRestore, Map<String, Object> metadata, DbFileRef revisionTo)
            throws RevisionRestoreException, FileHasRevisionException, FileParentRevisionNotFoundException;
}
