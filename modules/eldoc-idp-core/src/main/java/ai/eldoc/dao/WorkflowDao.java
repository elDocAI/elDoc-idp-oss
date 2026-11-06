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

import ai.eldoc.data.WorkflowLazyQuery;
import ai.eldoc.exceptions.DBEntryNotValidException;
import ai.eldoc.model.AclContext;
import ai.eldoc.model.Workflow;
import com.mongodb.client.ClientSession;
import org.bson.conversions.Bson;
import java.util.*;


/**
 * Workflow DAO Interface
 */
public interface WorkflowDao extends BasicLazyDao<Workflow>, FileRevisionAwareDao {

    public Workflow find(final String id, final AclContext aclContext);


    public Workflow findByWebId(String id, AclContext subjectAclContext);


    public Workflow findByExtId(String id, AclContext subjectAclContext);


    /**
     * Finds document by its attachment id (fileId)
     *
     * @param fileId
     * @param aclContext
     * @return
     */
    public Workflow findByFileId(String fileId, final AclContext aclContext);


    /**
     * Returns list of Workflow._id that have references to the given {@link Workflow} _id
     *
     * @param workflowId
     * @return
     */
    public List<String> findAllReferencedBy(String workflowId);


    /**
     * Updates record in the database, if it exists, with provided edits. Fields, that not specified in updates map are kept without changes.<br>
     * <b>IMPORTANT:</b> this method do not check revision to ensure concurrency safety and do not update revision number.
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param entity
     * @param updates
     * @return
     */
    public boolean applyUpdates(ClientSession dbClientSession, Workflow entity, Map<String, Object> updates);


    /**
     * Prepares ACL filter based on the given AclContext
     *
     * @param aclContext
     * @return
     */
    public Bson getACLFilterBase(final AclContext aclContext);


    /**
     * Returns all tags (unique only) used within this model
     *
     * @return
     */
    public Set<String> getTagsDistinct();


    /**
     * Checks whether Workflow is still present in the DB (of the same revision)
     *
     * @param workflow
     * @return
     * @throws DBEntryNotValidException in case document in not valid anymore
     */
    public boolean isValid(Workflow workflow) throws DBEntryNotValidException;


    public long viewInboxCount(WorkflowLazyQuery lazyQuery, AclContext aclContext);


    /**
     * Finds all documents of the form "frd1" (RecoWorkflow) which are pending pre-processing (checks that number of attachments is 1)
     *
     * @return
     */
    public List<Workflow> findAllPendingPreprocessing();


    /**
     * Updates last activity date for provided {@link Workflow}
     *
     * @param dbClientSession can be NULL, in such case it is ignored
     * @param workflow
     * @return
     */
    public boolean updateDateLastActivity(ClientSession dbClientSession, Workflow workflow);


    @Override
    public WorkflowFilesDao getFilesDao();
}
