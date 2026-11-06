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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.exceptions.BadArgumentException;
import ai.eldoc.model.*;
import jakarta.enterprise.inject.spi.CDI;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.naming.*;
import java.util.*;
import static java.util.Map.entry;


/**
 * DAO Utils Class
 */
public class DaoUtils {

    private static final Logger LOG = LoggerFactory.getLogger(DaoUtils.class);

    private static String APP_JNDI_PREFIX = null;

    public static final String LOOKUP_SUFFIX = "_lookup";
    public static final String FILES_DAO_SUFFIX = ".files";
    public static final String FILES_DAO_SUFFIX_PATTERN_STR = "\\.files$";

    private static final byte COL_ID_FORM_DEF = 7;
    private static final byte COL_ID_FORM_DEF_FILES = 8;
    private static final byte COL_ID_RECODATA = 9;
    private static final byte COL_ID_RECODATA_FILES = 10;
    private static final byte COL_ID_RECOFORMS = 11;
    private static final byte COL_ID_RECOFORMS_FILES = 12;
    // WorkflowDaoTenant
    private static final byte COL_ID_WORKFLOWS = 15;
    private static final byte COL_ID_WORKFLOWS_FILES = 16;
    private static final byte COL_ID_WORKFLOWS_TASKS = 17;
    private static final byte COL_ID_WORKFLOWS_TASKS_FILES = 18;

    private static final byte COL_ID_CONFIGS = 27;
    private static final byte COL_ID_CONFIGS_FILES = 28;

    private static final Map<String, Byte> COL_NAME_TO_COL_ID_MAP = Map.ofEntries(
            entry(FormDefinitionDaoMongoImpl.COLLECTION_NAME, COL_ID_FORM_DEF),
            entry(FormDefinitionFilesDaoMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX, COL_ID_FORM_DEF_FILES),
            entry(RecoDataDaoMongoImpl.COLLECTION_NAME, COL_ID_RECODATA),
            entry(RecoDataFilesDaoMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX, COL_ID_RECODATA_FILES),
            entry(RecoFormDaoMongoImpl.COLLECTION_NAME, COL_ID_RECOFORMS),
            entry(RecoFormFilesDaoMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX, COL_ID_RECOFORMS_FILES),
            // WorkflowDaoTenant & WorkflowTaskDaoTenant
            entry(WorkflowDaoTenantMongoImpl.COLLECTION_NAME, COL_ID_WORKFLOWS),
            entry(WorkflowFilesDaoTenantMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX, COL_ID_WORKFLOWS_FILES),
            entry(WorkflowTaskDaoTenantMongoImpl.COLLECTION_NAME, COL_ID_WORKFLOWS_TASKS),
            entry(WorkflowTaskFilesDaoTenantMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX, COL_ID_WORKFLOWS_TASKS_FILES),
            // Configs & Configs Global
            entry(ConfigDaoTenantMongoImpl.COLLECTION_NAME, COL_ID_CONFIGS),
            entry(ConfigDaoTenantMongoImpl.COLLECTION_NAME + FILES_DAO_SUFFIX, COL_ID_CONFIGS_FILES)
    );


    //
    // Dao Static Getters -------------------------------------------------------------------------
    //


    public static ShortIdSequenceDao getShortIdSequenceDao() {
        return CDI.current().select(ShortIdSequenceDao.class).get();
    }


    public static FormDefinitionDao getFormDefDao() {
        return CDI.current().select(FormDefinitionDao.class).get();
    }


    public static LogDao getLogDao() {
        return CDI.current().select(LogDao.class).get();
    }


    public static ReactionDao getReactionDao() {
        return CDI.current().select(ReactionDao.class).get();
    }


    public static ReminderDao getReminderDao() {
        return CDI.current().select(ReminderDao.class).get();
    }


    public static ListValueDao getListValueDao() {
        return CDI.current().select(ListValueDao.class).get();
    }


    public static BpmProcessDefinitionDao getBpmProcessDefinitionDao() {
        return CDI.current().select(BpmProcessDefinitionDao.class).get();
    }


    public static NumSequenceDao getNumSequenceDao() {
        return CDI.current().select(NumSequenceDao.class).get();
    }


    public static ConfigDao getConfigDao() {
        return CDI.current().select(ConfigDao.class).get();
    }


    public static RecoFormDao getRecoFormDao() {
        return CDI.current().select(RecoFormDao.class).get();
    }


    public static RecoDataDao getRecoDataDao() {
        return CDI.current().select(RecoDataDao.class).get();
    }


    public static WorkflowDao getWorkflowDaoTenant() {
        return CDI.current().select(WorkflowDao.class).get();
    }


    public static WorkflowTaskDao getWorkflowTaskDaoTenant() {
        return CDI.current().select(WorkflowTaskDao.class).get();
    }


    public static CodeSnippetDao getCodeSnippetDao() {
        return CDI.current().select(CodeSnippetDao.class).get();
    }


    /**
     * Computes and returns app JNDI prefix used in lookups. Caches it after 1st successful lookup
     *
     * @param ic
     * @return
     */
    private static String lookupAndGetAppNameJndiPrefix(InitialContext ic) {
        if (APP_JNDI_PREFIX != null) {
            return APP_JNDI_PREFIX; // We have it already cached, just return what we have
        }

        // Perform standard appName lookup
        try {
            String appName = (String) ic.lookup("java:app/AppName");
            APP_JNDI_PREFIX = "java:global/" + appName;
            return APP_JNDI_PREFIX; // No need to continue
        } catch (NamingException ex) {
            LOG.debug("Failed to lookup 'java:app/AppName' in InitialContext, will try to find it via 'java:global' next... Error: {}", ex.getMessage());
        }

        // Try to find appName via 'java:global' context
        try {
            NamingEnumeration<NameClassPair> list = ic.list("java:global");
            while (list.hasMoreElements()) {
                String icItemName = list.next().getName();
                if (icItemName != null && icItemName.startsWith("elDoc")) {
                    APP_JNDI_PREFIX = "java:global/" + icItemName;
                    return APP_JNDI_PREFIX; // No need to continue
                }
            }
        } catch (NamingException ex) {
            LOG.error("Failed to lookup 'java:global' in InitialContext", ex.getMessage());
        }

        // In case we reach to here means we could not lookup appName, throw exception then
        throw new ElDocRuntimeException("Error lookup appName JNDI prefix in InitialContext");
    }


    //
    // Utility Methods ----------------------------------------------------------------------------
    //


    /**
     * Returns Collection Name by Collection Id:<br>
     * <pre>
     *  - for (byte) 1 returns "persons"
     *  - for (byte) 2 returns "personsData.files"
     *  - for (byte) 9 returns "recoData"
     *  - for (byte) 10 returns "recoData.files"
     * </pre>
     *
     * @param collectionId
     * @return Collection name or null
     */
    public static String getCollectionNameById(Byte collectionId) {
        for (Map.Entry<String, Byte> entry : COL_NAME_TO_COL_ID_MAP.entrySet()) {
            if (Byte.compare(collectionId, entry.getValue()) == 0) {
                return entry.getKey();
            }
        }
        LOG.warn("No collection name mapping present for collectionId={}", collectionId);
        return null;
    }


    /**
     * Returns Collection Id by Collection Name:<br>
     * <pre>
     *  - for "persons" returns (byte) 1
     *  - for "personsData" (filesDao=true) return (byte) 2
     *  - for "recoData" return (byte) 9
     *  - for "recoData" (filesDao=true) return (byte) 10
     * </pre>
     *
     * @param collectionName
     * @param filesDao
     * @return Collection Id or null
     */
    public static Byte getCollectionIdByName(String collectionName, boolean filesDao) {
        if (filesDao) {
            return COL_NAME_TO_COL_ID_MAP.get(collectionName + FILES_DAO_SUFFIX);
        }
        return COL_NAME_TO_COL_ID_MAP.get(collectionName);
    }


    /**
     * Returns FileAwareDao by Collection Id<br>
     *
     * @param collectionId - Collection Id
     * @return FileAwareDao in case located or NULL
     */
    private static FileAwareDao getFileAwareDaoByColId(final Byte collectionId) {
        return getFileAwareDaoByColName(getCollectionNameById(collectionId));
    }


    /**
     * Returns FileAwareDao by DbFileRef
     *
     * @param dbFileRef
     * @return FileAwareDao in case located or NULL
     */
    public static FileAwareDao getFileAwareDao(final DbFileRef dbFileRef) {
        return getFileAwareDaoByColName(dbFileRef.getCollection());
    }


    /**
     * Returns FileAwareDao by DbWebRef
     *
     * @param dbWebRef
     * @return FileAwareDao in case located or NULL
     */
    public static FileAwareDao getFileAwareDao(final DbWebRef dbWebRef) {
        return getFileAwareDaoByColName(getCollectionNameById(dbWebRef.getColId()));
    }


    /**
     * Returns FileRevisionAwareDao by DbWebRef
     *
     * @param dbWebRef
     * @return
     */
    public static FileRevisionAwareDao getFileRevisionAwareDao(DbWebRef dbWebRef) {
        return getFileRevisionAwareDaoByColId(dbWebRef.getColId());
    }


    /**
     * Returns FileRevisionAwareDao by DbFileRef
     *
     * @param dbFileRef
     * @return
     */
    public static FileRevisionAwareDao getFileRevisionAwareDao(DbFileRef dbFileRef) {
        return getFileRevisionAwareDaoByColName(dbFileRef.getCollection());
    }


    /**
     * Returns FileRevisionAwareDao by Collection Id
     *
     * @param collectionId
     * @return
     */
    private static FileRevisionAwareDao getFileRevisionAwareDaoByColId(final Byte collectionId) {
        return getFileRevisionAwareDaoByColName(getCollectionNameById(collectionId));
    }


    /**
     * Returns FileRevisionAwareDao by Collection Name
     *
     * @param collectionName
     * @return
     */
    private static FileRevisionAwareDao getFileRevisionAwareDaoByColName(final String collectionName) {
        FileAwareDao fileAwareDao = getFileAwareDaoByColName(collectionName);
        if (fileAwareDao instanceof FileRevisionAwareDao fileRevisionAwareDao) {
            return fileRevisionAwareDao;
        }
        LOG.debug("No FileRevisionAwareDao was located by collectionName={}", collectionName);
        return null;
    }


    /**
     * Returns FileAwareDao by the given Collection Name
     *
     * @param collectionName collection name
     * @return FileAwareDao in case located or NULL
     */
    private static FileAwareDao getFileAwareDaoByColName(final String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new BadArgumentException("Collection name can not be blank or NULL");
        }

        switch (collectionName) {

            case FormDefinitionDaoMongoImpl.COLLECTION_NAME:
            case FormDefinitionFilesDaoMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX:
                return getFormDefDao();

            case RecoDataDaoMongoImpl.COLLECTION_NAME:
            case RecoDataFilesDaoMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX:
                return getRecoDataDao();

            case RecoFormDaoMongoImpl.COLLECTION_NAME:
            case RecoFormFilesDaoMongoImpl.BUCKET_NAME: // For backward compatibility as DbFileRef stores GridFS collection name wihtout .files suffix
            case RecoFormFilesDaoMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX:
                return getRecoFormDao();

            case WorkflowDaoTenantMongoImpl.COLLECTION_NAME:
            case WorkflowFilesDaoTenantMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX:
                return getWorkflowDaoTenant();

            case WorkflowTaskDaoTenantMongoImpl.COLLECTION_NAME:
            case WorkflowTaskFilesDaoTenantMongoImpl.BUCKET_NAME + FILES_DAO_SUFFIX:
                return getWorkflowTaskDaoTenant();

            default:
                break;
        }

        LOG.warn("Not implemented for collectionName={}", collectionName);
        return null;
    }


    //
    // Files Helper Methods (to be placed in more proper place & refactored) ----------------------
    //


    /**
     * Returns requested file by the given DbFilRef
     *
     * @param dbFileRef
     * @return
     */
    public static DbFile getFile(DbFileRef dbFileRef) {
        return DaoUtils.getFileAwareDao(dbFileRef).getFilesDao().getFile(null, dbFileRef);
    }


    /**
     * Returns latest revision (in case requested on the FileRevisionAwareDao) of the requested file by the given DbFilRef
     *
     * @param dbFileRef
     * @return
     */
    public static DbFile getFileLatestRev(DbFileRef dbFileRef) {
        FileRevisionAwareDao fileRevisionAwareDao = DaoUtils.getFileRevisionAwareDao(dbFileRef);
        if (fileRevisionAwareDao == null) {
            LOG.debug("{}: Latest Revision was requested on DAO which doesn't support revisions, returning DbFile for the given DbFileRef", dbFileRef);
            return getFile(dbFileRef);
        }
        DbFileRef latestRevRef = fileRevisionAwareDao.getFilesDao().getFileLatestRev(dbFileRef);
        return fileRevisionAwareDao.getFilesDao().getFile(null, latestRevRef);
    }


    /**
     * Returns latest revision (in case requested on the FileRevisionAwareDao) of the requested file by the given DbFilRef
     *
     * @param dbFileRef
     * @return
     */
    public static DbFile getFileRootRev(DbFileRef dbFileRef) {
        FileRevisionAwareDao fileRevisionAwareDao = DaoUtils.getFileRevisionAwareDao(dbFileRef);
        if (fileRevisionAwareDao == null) {
            LOG.debug("{}: Latest Revision was requested on DAO which doesn't support revisions, returning DbFile for the given DbFileRef", dbFileRef);
            return getFile(dbFileRef);
        }
        DbFileRef latestRevRef = fileRevisionAwareDao.getFilesDao().getFileRootRev(dbFileRef);
        return fileRevisionAwareDao.getFilesDao().getFile(null, latestRevRef);
    }


    /**
     * Locates FileRevisionAwareDao by the given DbFileRef and returns revisions history for the given DbFileRef
     * by calling method: {@link ai.eldoc.dao.FilesRevisionDao#getFileRevisionsAsDbFiles(ai.eldoc.model.DbFileRef)}
     *
     * @param dbFileRef
     * @return
     */
    public static List<DbFile> getFileRevisions(DbFileRef dbFileRef) {
        FileRevisionAwareDao fileRevisionAwareDao = DaoUtils.getFileRevisionAwareDao(dbFileRef);
        if (fileRevisionAwareDao == null) {
            LOG.debug("{}: Revisions history was requested on DAO which doesn't support revisions, returning revisions list contaning DbFile itself for the given DbFileRef", dbFileRef);
            return List.of(getFile(dbFileRef));
        }
        return fileRevisionAwareDao.getFilesDao().getFileRevisionsAsDbFiles(dbFileRef);
    }


    //
    // Files copy methods -------------------------------------------------------------------------
    //


    /**
     * Copies file from provided file ref to target DAO and adds to target model Files field<br>
     * IMPORTANT:<br>
     * 1. This methods doesn't save targetModel in DB, it only attaches files to it without saving. You have to save it after calling this method<br>
     * 2. This method doesn't copy file metadata, only file content. Caller of this method is responsible for setting file metadata on the copied file.
     *
     * @param dbFileRef {@link DbFileRef} to be copied
     * @param targetDao {@link FileAwareDao} target DAO
     * @return {@link DbFileRef} reference to the copied file
     */
    public static DbFileRef copyFile(DbFileRef dbFileRef, FileAwareDao targetDao) {
        try (DbFileDownloadStream is = DaoUtils.getFileAwareDao(dbFileRef).getFilesDao().getFileDownloadStream(dbFileRef)) {
            List<String> tags = dbFileRef.getTags();
            Map<String, Object> metadata = CollectionUtils.isEmpty(tags) ? null : Map.of(TagAwareModel._TAGS, tags);
            DbFileRef dbRefCopy = targetDao.getFilesDao().uploadFromStream(
                    is.getDbFile().getFileName(),
                    is,
                    is.getDbFile().getContentType(),
                    metadata
            );
            dbRefCopy.setTags(tags);
            return dbRefCopy;
        } catch (Exception e) {
            LOG.atError()
                    .setCause(e)
                    .setMessage("Error copying file | Source: {} | Target DAO: {}")
                    .addArgument(dbFileRef)
                    .addArgument(targetDao.getClass().getSimpleName())
                    .log();
        }
        return null;
    }


    /**
     * Copies file from provided file ref to target DAO and adds to target model Files field<br>
     * IMPORTANT:<br>
     * 1. This methods doesn't save targetModel in DB, it only attaches files to it without saving. You have to save it after calling this method<br>
     * 2. This method doesn't copy file metadata, only file content. Caller of this method is responsible for setting file metadata on the copied file.
     *
     * @param dbFileRef {@link DbFileRef} to be copied
     * @param targetDao {@link FileAwareDao} target DAO
     * @param targetModel {@link BasicFileAwareModel} target model, that will "own" copied file
     * @return {@link DbFileRef} reference to the copied file
     */
    public static DbFileRef copyFile(DbFileRef dbFileRef, FileAwareDao targetDao, final BasicFileAwareModel targetModel) {
        DbFileRef fileCopyDbFileRef = copyFile(dbFileRef, targetDao);
        if (fileCopyDbFileRef != null) {
            targetModel.getFiles().add(fileCopyDbFileRef);
        }
        return fileCopyDbFileRef;
    }


    /**
     * Copies attachment from sourceModel document field to the targetModel document and given field.<br>
     * IMPORTANT:<br>
     * 1. This methods doesn't save targetModel in DB, it only attaches files to it without saving. You have to save it after calling this method<br>
     * 2. This method doesn't copy file metadata, only file content. Caller of this method is responsible for setting file metadata on the copied file.
     *
     * @param sourceDao {@link FileAwareDao} source DAO, from which files should be copied
     * @param sourceModel {@link BasicFileAwareModel}, whose files should be copied
     * @param targetDao {@link FileAwareDao} target DAO
     * @param targetModel {@link BasicFileAwareModel} target model, that will "own" copied file
     * @return list of copied files
     */
    public static List<DbFileRef> copyFile(FileAwareDao sourceDao, final BasicFileAwareModel sourceModel, FileAwareDao targetDao, final BasicFileAwareModel targetModel) {
        List<DbFileRef> copiedFilesList = new ArrayList<>();
        List<DbFileRef> sourceList = sourceModel.getFiles();
        sourceList.forEach(sourceAttach -> {
            List<String> tags = sourceAttach.getTags();
            Map<String, Object> metadata = CollectionUtils.isEmpty(tags) ? null : Map.of(TagAwareModel._TAGS, tags);
            try (DbFileDownloadStream is = sourceDao.getFilesDao().getFileDownloadStream(sourceAttach)) {
                DbFileRef dbRefCopy = targetDao.getFilesDao().uploadFromStream(
                        is.getDbFile().getFileName(),
                        is,
                        is.getDbFile().getContentType(),
                        metadata
                );
                dbRefCopy.setTags(tags);
                targetModel.getFiles().add(dbRefCopy);
                copiedFilesList.add(dbRefCopy);
            } catch (Exception e) {
                LOG.atError()
                        .setCause(e)
                        .setMessage("Error copying attachment | Source: {} | Target DAO: {}")
                        .addArgument(sourceAttach)
                        .addArgument(targetDao.getClass().getSimpleName())
                        .log();
            }
        });
        return copiedFilesList;
    }

}
