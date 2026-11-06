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

import ai.eldoc.dao.mongo.AbstractMongoPojoDao;
import ai.eldoc.exceptions.DatabaseException;
import ai.eldoc.model.*;
import ai.eldoc.service.LogHelper;
import com.mongodb.MongoException;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import static ai.eldoc.bpm.BpmConstants.*;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Workflow Task DAO Mongo Implementation
 */
@ApplicationScoped
public class WorkflowTaskDaoTenantMongoImpl extends AbstractMongoPojoDao<WorkflowTask> implements WorkflowTaskDao {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowTaskDaoTenantMongoImpl.class);

    public static final String COLLECTION_NAME = "wfTsks";

    @Inject
    private WorkflowTaskFilesDao _workflowTaskFilesDao;


    //
    // DAO Specific Business Methods --------------------------------------------------------------
    //
    /**
     * {@inheritDoc}
     */
    @Override
    public WorkflowTask find(String id) {
        if (isNotBlank(id)) {
            try {
                return find(eq(WorkflowTask._ID, new ObjectId(id)));
            } catch (IllegalArgumentException ie) {
                LOG.debug(id, ie);
            } catch (Exception e) {
                LOG.error(id, e);
            }
        }
        return null;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public WorkflowTask find(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author, String delegatedFrom, String delegatedTo) {
        return find(buildCriteria(wfId, state, step, iteration, taskStatusArray, author, delegatedFrom, delegatedTo, null, false));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public WorkflowTask findProcBackup(String docUnid, Integer state, Integer iteration) {
        return find(buildCriteria(docUnid, state, null, iteration, null, null, null, null, null, true));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public long count(String docUnid, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author) {
        return getCollection().countDocuments(buildCriteria(docUnid, state, step, iteration, taskStatusArray, author, null, null, null, false), new CountOptions().collation(_collation));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public long count(String docUnid,
            Integer state,
            Integer step,
            Integer iteration,
            List<Integer> taskStatusArray,
            String author,
            String delegatedFrom,
            String delegatedTo,
            String resolTo) {
        return getCollection().countDocuments(buildCriteria(docUnid, state, step, iteration, taskStatusArray, author, delegatedFrom, delegatedTo, resolTo, false), new CountOptions().collation(_collation));
    }


    /**
     * Finds all WorkflowTasks by _PARENT_ID field
     *
     * @param refId
     * @return
     */
    private List<WorkflowTask> findAllByWfId(String wfId) {
        final List<WorkflowTask> resultList = new ArrayList<>();
        getCollection().find(eq(WorkflowTask._PARENT_ID, wfId)).into(resultList);
        return resultList;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkflowTask> findAll(String docUnid, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author) {
        return findAll(docUnid, state, step, iteration, taskStatusArray, author, null, null, null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkflowTask> findAll(String docUnid, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author, boolean sorted) {
        return findAllInner(docUnid, state, step, iteration, taskStatusArray, author, null, null, null, sorted);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkflowTask> findAll(String docUnid,
            Integer state,
            Integer step,
            Integer iteration,
            List<Integer> taskStatusArray,
            String author,
            String delegatedFrom,
            String delegatedTo,
            String taskTo) {
        return findAllInner(docUnid, state, step, iteration, taskStatusArray, author, delegatedFrom, delegatedTo, taskTo, false);
    }


    /**
     * {@inheritDoc}
     */
    private List<WorkflowTask> findAllInner(String wfId,
            Integer state,
            Integer step,
            Integer iteration,
            List<Integer> taskStatusArray,
            String author,
            String delegatedFrom,
            String delegatedTo,
            String taskTo,
            boolean sorted) {
        final ArrayList<WorkflowTask> resultList = new ArrayList<>();

        Document filterDoc = buildCriteria(wfId, state, step, iteration, taskStatusArray, author, delegatedFrom, delegatedTo, taskTo, false);

        if (sorted) {
            getCollection().find(filterDoc).collation(_collation).sort(new Document(Meta._PATH_META_CREATED, 1)).into(resultList);
        } else {
            getCollection().find(filterDoc).collation(_collation).into(resultList);
        }

        return resultList;
    }


    private Document buildCriteria(String wfId,
            Integer state,
            Integer step,
            Integer iteration,
            List<Integer> taskStatusArray,
            String author,
            String delegatedFrom,
            String delegatedTo,
            String taskTo,
            boolean withProcBackup) {

        Document filterDoc = new Document();

        if (wfId != null && !wfId.isEmpty()) {
            filterDoc.put(WorkflowTask._PARENT_ID, wfId);
        }

        if (state != null) {
            filterDoc.put(WorkflowTask._PROCESS_STATE, state);
        }

        if (step != null) {
            filterDoc.put(WorkflowTask._PROCESS_STEP, step);
        }

        if (iteration != null) {
            filterDoc.put(WorkflowTask._PROCESS_ITERATION, iteration);
        }

        if (taskStatusArray != null) {
            filterDoc.put(WorkflowTask._TASK_STATUS, new Document("$in", taskStatusArray));
        }

        if (author != null && !author.isBlank()) {
            filterDoc.put(WorkflowTask._AUTHOR, author);
        }

        if (delegatedFrom != null && !delegatedFrom.isBlank()) {
            filterDoc.put(WorkflowTask._DELEGATED_FROM, delegatedFrom);
        }

        if (delegatedTo != null && !delegatedTo.isBlank()) {
            filterDoc.put(WorkflowTask._DELEGATED_TO, delegatedTo);
        }

        if (taskTo != null && !taskTo.isBlank()) {
            filterDoc.put(WorkflowTask._TASK_TO, taskTo);
        }

        if (withProcBackup) {
            filterDoc.put(WorkflowTask._PROCESS, new Document("$exists", true));
        }

        return filterDoc;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delegationChainCancel(ClientSession dbClientSession, String wfId, int state, int step, int iteration, String author, String delegatedFrom) {
        boolean resultCancelDelegation = false;
        WorkflowTask wfTask = null;
        try {
            Document criteriaDoc = buildCriteria(wfId, state, step, iteration, null, author, delegatedFrom, null, null, false);
            criteriaDoc.put(WorkflowTask._TASK_STATUS, new Document("$ne", TASK_STATUS_DELEGATE_CANCEL));
            try (MongoCursor<WorkflowTask> cursor = getCollection().find(criteriaDoc).collation(_collation).iterator()) {
                while (cursor.hasNext()) {
                    wfTask = cursor.next();

                    String delegatedTo = wfTask.getDelegatedTo();
                    String respAuthor = wfTask.getAuthor();
                    wfTask.setTaskStatus(TASK_STATUS_DELEGATE_CANCEL);

                    if (delegatedTo == null || delegatedTo.isEmpty()) {
                        if (updateOrCreate(dbClientSession, wfTask)) {
                            resultCancelDelegation = true;
                        } else {
                            LogHelper.setEvent("id00080", null, null, new LogExt(wfTask));
                        }
                    } else {
                        if (delegationChainCancel(dbClientSession, wfId, state, step, iteration, delegatedTo, respAuthor)) {
                            if (updateOrCreate(dbClientSession, wfTask)) {
                                resultCancelDelegation = true;
                            } else {
                                LogHelper.setEvent("id00081", null, null, new LogExt(wfTask));
                            }
                        }
                    }

                }
            }
        } catch (Exception e) {
            LogExt logExt = null;
            if (wfTask != null) {
                logExt = new LogExt(wfTask);
            }
            LogHelper.setEvent("id00082", e, "WorkflowTaskDaoMongoImpl.delegationCancelChain()", logExt);
        }
        return resultCancelDelegation;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(String docUnid, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author, String delegatedFrom, String delegatedTo, String resolTo) {
        DeleteResult delResult = getCollection().deleteOne(buildCriteria(docUnid, state, step, iteration, taskStatusArray, author, delegatedFrom, delegatedTo, resolTo, false));
        return delResult.getDeletedCount() == 1 && delResult.wasAcknowledged();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(ClientSession dbClientSession, WorkflowTask entity) {
        deleteEmbeddedAttachmnets(dbClientSession, entity);
        return super.delete(dbClientSession, entity);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(ClientSession dbClientSession, String wfId) {
        deleteEmbeddedAttachmnets(dbClientSession, wfId);
        //Throws:
        //com.mongodb.MongoWriteException - if the write failed due to some specific write exception
        //com.mongodb.MongoWriteConcernException - if the write failed due to being unable to fulfil the write concern
        //com.mongodb.MongoCommandException - if the write failed due to a specific command exception
        //com.mongodb.MongoException - if the write failed due some other failure
        try {
            if (dbClientSession != null) {
                return getCollection().deleteMany(dbClientSession, new Document(WorkflowTask._PARENT_ID, wfId)).wasAcknowledged();
            } else {
                return getCollection().deleteMany(new Document(WorkflowTask._PARENT_ID, wfId)).wasAcknowledged();
            }
        } catch (MongoException mdbe) {
            throw new DatabaseException("Database Error: " + mdbe.getMessage(), mdbe);
        }
    }


    /**
     * Deletes all attachments from the DcoResponses that belongs to the given refId
     *
     * @param dbClientSession
     * @param refId id of the main document
     * @return
     */
    private void deleteEmbeddedAttachmnets(ClientSession dbClientSession, String refId) {
        findAllByWfId(refId).forEach(docResponse -> deleteEmbeddedAttachmnets(dbClientSession, docResponse));
    }


    private void deleteEmbeddedAttachmnets(ClientSession dbClientSession, WorkflowTask wfTask) {
        wfTask.getFiles().forEach(dbFileRef -> {
            getFilesDao().deleteFile(dbClientSession, dbFileRef);
        });
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPresent(String unid, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author) {
        return count(unid, state, step, iteration, taskStatusArray, author) > 0;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayList<Integer> getIretationsOnState(String docUnid, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author) {
        ArrayList<Integer> iterationsList = new ArrayList<>();
        List<WorkflowTask> docRespsList = findAll(docUnid, state, step, iteration, taskStatusArray, author);
        docRespsList.forEach(docResp -> iterationsList.add(docResp.getIteration()));
        // remove dublicates and sort iterations
        HashSet<Integer> hs = new HashSet<>();
        hs.addAll(iterationsList);
        iterationsList.clear();
        iterationsList.addAll(hs);
        Collections.sort(iterationsList);
        return iterationsList;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getTaskStatusIfProcessed(String unid, int state, Integer stepNumber, int iteration, String authorName, String delegatedFromName) {
        Integer result = null;
        try {
            WorkflowTask docResp = find(unid, state, stepNumber, iteration, TASK_STATUS_LIST_PROCESSED, authorName, delegatedFromName, null);
            if (docResp != null) {
                int taskStatus = docResp.getTaskStatus();
                if (TASK_STATUS_DELEGATE_OUT == taskStatus) {
                    String delegatedToName = docResp.getDelegatedTo();
                    result = getTaskStatusIfProcessed(unid, state, stepNumber, iteration, delegatedToName, authorName);
                } else if (TASK_STATUS_LIST_COMPLETED.contains(taskStatus)) {
                    result = taskStatus;
                }
            }
        } catch (Exception e) {
            LogHelper.setEvent(e, unid + " | " + state + " | " + stepNumber + " | " + iteration + " | " + authorName + " | " + delegatedFromName);
        }
        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkflowTask> getAssignedTasksList(String wfId, int stateId) {
        return findAll(wfId, stateId, null, null, Arrays.asList(TASK_STATUS_ASSIGNMENT), null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public WorkflowTask getAssignedTask(String wfId, int stateId, int step, int iter, String taskTo) {
        return findAll(wfId, stateId, step, iter, Arrays.asList(TASK_STATUS_ASSIGNMENT), null, null, null, taskTo).stream().findFirst().orElse(null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<WorkflowTask> getAssignedTasksListForCurrentProcessStep(Workflow workflow) {
        return findAll(workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), List.of(TASK_STATUS_ASSIGNMENT), null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public WorkflowTask getAssignedTaskForUserOnCurrentProcessStep(Workflow workflow, String systemId) {
        return getAssignedTasksListForCurrentProcessStep(workflow).stream()
                .filter(wfTask -> Objects.equals(wfTask.getTaskTo(), systemId))
                .findFirst()
                .orElse(null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shiftAssignedTaskStep(String wfId, int stateId, int oldStage, int newStage) {
        UpdateResult updResult = getCollection().updateMany(
                and(
                        eq(WorkflowTask._PARENT_ID, wfId),
                        eq(WorkflowTask._PROCESS_STATE, stateId),
                        eq(WorkflowTask._TASK_STATUS, TASK_STATUS_ASSIGNMENT),
                        eq(WorkflowTask._PROCESS_STEP, oldStage)),
                set(WorkflowTask._PROCESS_STEP, newStage));
        return updResult.wasAcknowledged() && updResult.getModifiedCount() > 0;
    }


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    @Override
    public MongoCollection<WorkflowTask> getCollection() {
        return super.getCollection(WorkflowTask.class);
    }


    @Override
    public FilesDao getFilesDao() {
        return _workflowTaskFilesDao;
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

        IndexOptions indexOptions1 = new IndexOptions().background(true).collation(_collation);
        indexesList.add(new IndexModel(new Document(WorkflowTask._AUTHOR, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(WorkflowTask._PARENT_ID, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(WorkflowTask._TASK_TO, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(WorkflowTask._DELEGATED_TO, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(WorkflowTask._DELEGATED_FROM, 1), indexOptions1));

        IndexOptions indexOptions2 = new IndexOptions().background(true);
        indexesList.add(new IndexModel(new Document(WorkflowTask._PROCESS_STATE, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(WorkflowTask._PROCESS_STEP, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(WorkflowTask._PROCESS_ITERATION, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(WorkflowTask._TASK_STATUS, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Meta._PATH_META_CREATED, 1), indexOptions2));

        Document partialFilterExpression = new Document(WorkflowTask._PROCESS, new Document("$exists", true));
        IndexOptions indexOptionsCustom = new IndexOptions().background(true).partialFilterExpression(partialFilterExpression);
        indexesList.add(new IndexModel(new Document(WorkflowTask._PROCESS, 1), indexOptionsCustom));

        return indexesList;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void doIndexHousekeep() {
        doIndexHousekeep(getIndexesList());

        // Maintaining indexes in the GridFS Bucket collection
        getFilesDao().doIndexHousekeep();
    }

}
