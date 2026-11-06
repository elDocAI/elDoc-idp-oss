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
package ai.eldoc.util;

import ai.eldoc.TenantContext;
import ai.eldoc.ai.config.LLMProviderConfig;
import ai.eldoc.dao.*;
import ai.eldoc.model.*;
import ai.eldoc.model.filemeta.RevisionFileMeta;


/**
 * DbWebRef Utils Class
 */
public class DbWebRefUtils {

    //
    // Constructor --------------------------------------------------------------------------------
    //
    private DbWebRefUtils() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Converts given DbFile into DbWebRef<br>
     * This method analyses metadata if the given DbFile is a file revision or a root version and constructs DbWebRef accordingly
     *
     * @param dbFile
     * @return
     */
    public static DbWebRef toDbWebRef(DbFile dbFile) {
        RevisionFileMeta revFileMeta = RevisionFileMeta.fromJson(dbFile.getMetadataAsJson());
        if (revFileMeta.getRevisionRoot() != null && !revFileMeta.getRevisionRoot().isBlank()) {
            // This is a revision file, proceed respectively
            return toDbWebRef(DaoUtils.getCollectionIdByName(dbFile.getDbFileRef().getCollection(), true), revFileMeta.getRevisionRoot(), dbFile.getId());
        }
        // The given file doesn't have reviosions, treat it as a root revision file
        return toDbWebRef(dbFile.getDbFileRef(), null);
    }


    /**
     * Converts DbFileRef (which is treated as root revision of the given file) into DbWebRef<br>
     * TenantId is retrieved from the current TenantContext
     *
     * @param dbFileRootRef which is considered as root revision of the file referenced by the given DbFileRef
     * @return DbWebRef represents root revision (without stating revisionId) of the given DbFileRef
     */
    public static DbWebRef toDbWebRef(DbFileRef dbFileRootRef) {
        return toDbWebRef(dbFileRootRef, null);
    }


    /**
     * Converts DbFileRef into DbWebRef<br>
     * TenantId is retrieved from the current TenantContext
     *
     * @param dbFileRootRef
     * @param revisionId
     * @return DbWebRef for the given arguments
     */
    public static DbWebRef toDbWebRef(DbFileRef dbFileRootRef, String revisionId) {
        return toDbWebRef(DaoUtils.getCollectionIdByName(dbFileRootRef.getCollection(), true), dbFileRootRef.getId(), revisionId);
    }


    /**
     * Converts DbFileRef into DbWebRef
     *
     * @param collectionId
     * @param rootRevisionId
     * @param revisionId
     * @return
     */
    private static DbWebRef toDbWebRef(byte collectionId, String rootRevisionId, String revisionId) {
        if (revisionId == null) {
            return new DbWebRef(TenantContext.getCurrentTenantId(), collectionId, rootRevisionId);
        } else {
            return new DbWebRef(TenantContext.getCurrentTenantId(), collectionId, rootRevisionId, revisionId);
        }
    }


    /**
     * Converts DbWebRef back into DbFileRef
     *
     * @param dbWebRef
     * @return
     */
    public static DbFileRef toDbFileRef(DbWebRef dbWebRef) {
        String collName = DaoUtils.getCollectionNameById(dbWebRef.getColId());
        if (collName.endsWith(DaoUtils.FILES_DAO_SUFFIX)) {
            // For backward compatibility we need to remove .files suffix as it is stored without suffix in the DbFileRef
            collName = collName.replaceAll(DaoUtils.FILES_DAO_SUFFIX_PATTERN_STR, "");
        }
        return new DbFileRef(collName, dbWebRef.getIdAsString()); // TODO: here we are losing revId in case it was set in dbWebRef
    }

    //
    // Workflow related methods -------------------------------------------------------------------
    //


    /**
     * Converts {@link Workflow} into {@link DbWebRef}
     *
     * @param workflow
     * @return
     */
    public static DbWebRef toDbWebRef(Workflow workflow) {
        if (workflow.getTenantId() == null) {
            // Workaround: absense of tenantId indicates that this Workflow belongs to the Tenant DB
            return fromWorkflowId(workflow.getId(), TenantContext.getCurrentTenantId(), false);
        }
        return fromWorkflowId(workflow.getId(), workflow.getTenantId(), true);
    }


    /**
     * Constructs DbWebRef from the given Workflow
     *
     * @param workflowId
     * @return
     */
    public static DbWebRef fromWorkflowId(String workflowId) {
        return new DbWebRef(TenantContext.getCurrentTenantId(), DaoUtils.getCollectionIdByName(WorkflowDaoTenantMongoImpl.COLLECTION_NAME, false), workflowId);
    }


    /**
     * Constructs DbWebRef from the given workflowId
     *
     * @param workflowId
     * @param globalContext
     * @return
     */
    public static DbWebRef fromWorkflowId(String workflowId, boolean globalContext) {
        byte colId = DaoUtils.getCollectionIdByName(WorkflowDaoTenantMongoImpl.COLLECTION_NAME, false);
        return new DbWebRef(TenantContext.getCurrentTenantId(), colId, workflowId);
    }


    /**
     * Constructs DbWebRef from the given workflowId, tenantId and flag globalContext
     *
     * @param workflowId
     * @param tenantId
     * @param globalContext
     * @return
     */
    private static DbWebRef fromWorkflowId(String workflowId, String tenantId, boolean globalContext) {
        byte colId = DaoUtils.getCollectionIdByName(WorkflowDaoTenantMongoImpl.COLLECTION_NAME, false);
        return new DbWebRef(tenantId, colId, workflowId);
    }


    /**
     * Constructs DbWebRef from the given RecoData
     *
     * @param recoDataId
     * @return
     */
    public static DbWebRef fromRecoDataId(String recoDataId) {
        return new DbWebRef(TenantContext.getCurrentTenantId(), DaoUtils.getCollectionIdByName(RecoDataDaoMongoImpl.COLLECTION_NAME, false), recoDataId);
    }


    //
    // LLMProviderConfig related methods ----------------------------------------------------------
    //
    public static DbWebRef toDbWebRef(LLMProviderConfig llmProviderConfig) {
        if (llmProviderConfig == null || llmProviderConfig.getId() == null) {
            return null;
        }

        return new DbWebRef(
                TenantContext.getCurrentTenantId(),
                DaoUtils.getCollectionIdByName(
                        ConfigDaoTenantMongoImpl.COLLECTION_NAME,
                        false),
                llmProviderConfig.getId());
    }
}
