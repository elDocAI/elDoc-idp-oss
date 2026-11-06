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
import ai.eldoc.data.*;
import ai.eldoc.exceptions.DBEntryNotValidException;
import ai.eldoc.exceptions.DatabaseException;
import ai.eldoc.model.*;
import ai.eldoc.service.LogHelper;
import ai.eldoc.service.WorkflowService;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static ai.eldoc.bpm.BpmConstants.*;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Workflow DAO MongoDB Implementation
 */
@ApplicationScoped
public class WorkflowDaoTenantMongoImpl extends AbstractMongoPojoDao<Workflow> implements WorkflowDao {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowDaoTenantMongoImpl.class);

    public static final String COLLECTION_NAME = "wfs";

    public static final String _PATH_PROCESS_STATES_STATEID = Workflow._PROCESS + ".states.stateId";
    public static final String _PATH_PROCESS_STATES_STEPS_USERS_ID = Workflow._PROCESS + ".states.steps.users.id";
    public static final String _PATH_PROCESS_STATES_STEPS_GROUPS_ID = Workflow._PROCESS + ".states.steps.groups.id";
    public static final String _PATH_FILES_ID = BasicFileAwareModel._FILES + "." + DbFileRef._ID;

    // `ne(..)` can't be used, as it is internally translated to `not(eq(...))`, and `not` is not supported for partial indexes
    // However, in MongoDB, `null < "" < anyNonEmptyString`, so we can simply use `gt` for finding non-empty fields
    private static final Bson ID_EXT_PRESENT_FILTER = gt(Workflow._ID_EXT, "");

    private static final List<Integer> INBOX_RELATED_TASK_STATUSES = Stream.concat(TASK_STATUS_LIST_PROCESSED.stream(), Stream.of(200, 500)).toList();

    @Inject
    private ShortIdSequenceDao _shortIdSequenceDao;

    @Inject
    private WorkflowFilesDao _workflowFilesDao;

    @Inject
    private WorkflowTaskDao _workflowTaskDao;


    //
    // DAO Specific Business Methods --------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public Workflow find(final String id, final AclContext aclContext) {
        if (isNotBlank(id)) {
            try {
                return find(getACLFilter(aclContext, eq(BasicModel._ID, new ObjectId(id))));
            } catch (IllegalArgumentException ie) {
                // Most probably it is something like:
                // IllegalArgumentException: invalid hexadecimal representation of an ObjectId: [9523C292533B0FAD48257F28004D84C7]
                LOG.debug("{} | {}", aclContext.getSystemId(), id, ie);
            } catch (Exception e) {
                LOG.error("{} | {}", aclContext.getSystemId(), id, e);
            }
        }
        return null;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Workflow findByWebId(final String webId, final AclContext aclContext) {
        return find(getACLFilter(aclContext, eq(Workflow._ID_WEB, webId)), _collation);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Workflow findByExtId(final String extId, final AclContext aclContext) {
        return find(getACLFilter(aclContext, eq(Workflow._ID_EXT, extId)), _collation);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Workflow findByFileId(String fileId, final AclContext aclContext) {
        Bson filter = eq(_PATH_FILES_ID, fileId);
        return find(getACLFilter(aclContext, filter));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> findAllReferencedBy(String workflowId) {
        ArrayList<Bson> aggregateList = new ArrayList<>();
        aggregateList.add(match(
                and(
                        in(Workflow._STATE, Stream.concat(STATE_ID_LIST_FIXED_AND_DYNAMIC.stream(), Stream.of(STATE_ID_RPSTR, STATE_ID_ARCH)).toList()),
                        eq(Workflow._REF_ITEMS, workflowId)
                )));

        // Remove all fields, except _id, as they won't be used
        aggregateList.add(project(
                fields(
                        include(BasicModel._ID)
                )));

        return getCollection().aggregate(aggregateList).allowDiskUse(true).collation(_collation).map(Workflow::getId).into(new ArrayList<>());
    }


    /**
     * @inheritDoc
     */
    @Override
    public List<Workflow> findAllPendingPreprocessing() {
        List<Workflow> result = new ArrayList<>();
        try {
            // It is important to locate only records with size of attachments equals to 1
            // in order to exclude RecoDocs which are in the process of being submitted during the invocation of this query
            Bson filter = and(
                    eq(Workflow._FORM, FormDefinition.FORM_ID_RECO),
                    eq(Workflow._STATE, STATE_ID_DRAFT),
                    eq(Workflow._STATUS, PROCESS_STATUS_NORMAL),
                    size(Workflow._FILES, 1)
            );
            getCollection().find(filter).collation(_collation).into(result);
        } catch (Exception e) {
            LogHelper.setEvent(e, "WorkflowDaoMongoImpl.findPendingPreprocessing()");
        }
        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateOrCreate(ClientSession dbClientSession, Workflow workflow) {
        // Ensure default ACL
        WorkflowService.ensureDefaultAcl(workflow);

        return super.updateOrCreate(dbClientSession, workflow);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void create(ClientSession dbClientSession, Workflow entity) {
        // idWeb to be set for the new Workflow records in the Tenant collection ONLY!
        if (COLLECTION_NAME.equals(getCollectionName())) {
            // idWeb must not be set in the Global collection as they will conflict with each other,
            // as counter taken from the local per-Tenant DB
            entity.setIdWeb(_shortIdSequenceDao.getNextSequenceHEXforWorkflow());
        }
        entity.setDateLastActivity(new Date());
        super.create(dbClientSession, entity);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean applyUpdates(ClientSession dbClientSession, Workflow workflow, Map<String, Object> updates) {
        List<Bson> updatesList = updates
                .entrySet()
                .stream()
                .map(entry -> set(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        UpdateResult updResult;
        try {
            //Throws:
            //MongoWriteException - if the write failed due to some specific write exception
            //MongoWriteConcernException - if the write failed due to being unable to fulfil the write concern
            //MongoCommandException - if the write failed due to a specific command exception
            //MongoException - if the write failed due some other failure
            if (dbClientSession == null) {
                updResult = getCollection().updateOne(eq(Workflow._ID, new ObjectId(workflow.getId())), combine(updatesList));
            } else {
                updResult = getCollection().updateOne(dbClientSession, eq(Workflow._ID, new ObjectId(workflow.getId())), combine(updatesList));
            }
        } catch (MongoException mdbe) {
            LOG.error("Error applying updates for the Workflow[{}]", workflow.getId(), mdbe);
            throw new DatabaseException("Database Error: " + mdbe.getMessage(), mdbe);
        }
        return updResult.wasAcknowledged();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getTagsDistinct() {
        final Set<String> result = new HashSet<>();
        // To avoid null in results we use filter below
        Bson filter = and(
                ne(Workflow._TAGS, null),
                nin(Workflow._TAGS, new Object[]{null}),
                gt(Workflow._STATE, STATE_ID_DRAFT), // Skip docs in Draft state
                eq(Workflow._STATUS, PROCESS_STATUS_NORMAL));
        getCollection()
                .distinct(Workflow._TAGS, filter, String.class)
                .collation(_collation)
                .into(result);
        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValid(Workflow workflow) throws DBEntryNotValidException {
        return super.isValid(workflow);
    }


    //
    // findAllLazy related methods --------------------------------------------------------------
    //


    /**
     * Overrides default behaviour of the super implementation
     *
     * @param lazyQuery
     * @param aclContext
     * @return
     */
    @Override
    public LazyResult<Workflow> findAllLazy(LazyQuery lazyQuery, AclContext aclContext) {
        if (lazyQuery instanceof WorkflowLazyQuery dfLazyQuery) {
            if (dfLazyQuery.getView() != null) {
                return findAllLazyByView(dfLazyQuery, aclContext);
            } else {
                return findAllLazyInner(null, dfLazyQuery, aclContext);
            }
        }
        return super.findAllLazy(lazyQuery, aclContext);
    }


    /**
     * Returns documents by view
     *
     * @param lazyQuery
     * @param aclContext
     * @return
     */
    private LazyResult<Workflow> findAllLazyByView(WorkflowLazyQuery lazyQuery, AclContext aclContext) {
        if (null == lazyQuery.getView()) {
            LOG.warn("WorkflowLazyDataModel -> viewId is not set, nothing to return");
            return new LazyResult<>(new ArrayList<>(), 0);
        }

        switch (lazyQuery.getView()) {
            case INBOX -> {
                return findAllLazyByViewInbox(lazyQuery, aclContext);
            }

            case RECOQUEUE -> {
                Bson baseFilter = and(
                        eq(Workflow._FORM, FormDefinition.FORM_ID_RECO), // This repeats what is coming from UI, but it also helps to optimize query
                        or(
                                in(_PATH_PROCESS_STATES_STEPS_USERS_ID, aclContext.toSetSystemIdWithAliases()),
                                in(_PATH_PROCESS_STATES_STEPS_GROUPS_ID, aclContext.toSetGroupsUnits())
                        ));
                return findAllLazyInner(baseFilter, lazyQuery, aclContext);
            }

            case ALL -> {
                return findAllLazyInner(null, lazyQuery, aclContext);
            }

            case PROCESSED -> {
                return findAllLazyByViewProcessed(lazyQuery, aclContext);
            }

            case PROCESSED_AND_COMPLETED -> {
                return findAllLazyByViewProcessedAndCompleted(lazyQuery, aclContext);
            }

            case INWORK -> {
                // Returns workflows in progress (using base filter which doesn't apply filter on Status, so it includes: Normal, Suspended, Terminated, etc).
                // It is responsibility of the calling method to set proper filters for the Workflow Status
                Bson baseFilter = and(
                        in(Workflow._STATE, STATE_ID_LIST_FIXED_AND_DYNAMIC),
                        or(
                                // documents of the author on step>0 are shown on the InProgress view, and on step=0 - whown on the Inbox view
                                and(
                                        eq(Workflow._AUTHOR, aclContext.getSystemId()),
                                        gt(Workflow._STEP, 0)
                                ),
                                // documents where user is not author - shown from any step, as documents on the step=0 from other users also mean it is InProgress,
                                // as it is still no in Repo or Arch
                                ne(Workflow._AUTHOR, aclContext.getSystemId())
                        )
                );
                return findAllLazyInner(baseFilter, lazyQuery, aclContext);
            }

            case MYDOCS -> {
                Bson baseFilter = or(
                        eq(Workflow._AUTHOR, aclContext.getSystemId()),
                        eq(Workflow._AUTHOR_BY, aclContext.getSystemId())
                );
                return findAllLazyInner(baseFilter, lazyQuery, aclContext);
            }

            case DRAFTS -> {
                Bson baseFilter = and(
                        eq(Workflow._AUTHOR, aclContext.getSystemId()),
                        eq(Workflow._STATE, STATE_ID_DRAFT)
                );
                return findAllLazyInner(baseFilter, lazyQuery, aclContext);
            }

            case REPOSITORY -> {
                Bson baseFilter = and(
                        eq(Workflow._STATE, STATE_ID_RPSTR),
                        eq(Workflow._STATUS, PROCESS_STATUS_NORMAL)
                );
                return findAllLazyInner(baseFilter, lazyQuery, aclContext);
            }

            case ARCHIVE -> {
                Bson baseFilter = and(
                        eq(Workflow._STATE, STATE_ID_ARCH),
                        eq(Workflow._STATUS, PROCESS_STATUS_NORMAL)
                );
                return findAllLazyInner(baseFilter, lazyQuery, aclContext);
            }

            case FORREF -> {
                Bson baseFilter = and(
                        in(Workflow._STATE, Stream.concat(STATE_ID_LIST_FIXED_AND_DYNAMIC.stream(), Stream.of(STATE_ID_RPSTR, STATE_ID_ARCH)).toList()),
                        eq(Workflow._STATUS, PROCESS_STATUS_NORMAL)
                );
                return findAllLazyInner(baseFilter, lazyQuery, aclContext);
            }

            case ADMIN -> {
                return findAllLazyInner(null, lazyQuery, aclContext);
            }

            default -> {
                LOG.warn("WorkflowLazyDataModel -> No view availbale for viewId: {}", lazyQuery.getView());
                return new LazyResult<>(new ArrayList<>(), 0);
            }
        }
    }


    /**
     * Standard entry point for retrieving documents (uses lazy query)
     *
     * @param baseFilter base filter to be applied, can be NULL
     * @param lazyQuery
     * @param aclContext
     * @return
     */
    private LazyResult<Workflow> findAllLazyInner(Bson baseFilter, WorkflowLazyQuery lazyQuery, AclContext aclContext) {
        final List<Workflow> result = new ArrayList<>();
        int resultCount = 0;

        try {
            List<Bson> aggregateList = new ArrayList<>();

            // add base match
            Bson baseMatch = getACLFilter(aclContext, baseFilter);
            aggregateList.add(match(baseMatch));

            // UI User defined filters
            if (!lazyQuery.getFilter().isEmpty()) {
                aggregateList.add(match(lazyQuery.getFilter()));
            }

            // get the count of documents matching the filter in case count was not disabled
            if (lazyQuery.isSkipCount()) {
                resultCount = -1;
            } else {
                List<Bson> aggregateCountList = new ArrayList<>(aggregateList);
                aggregateCountList.add(count());
                Document countDoc = getCollection(Document.class).aggregate(aggregateCountList).allowDiskUse(true).collation(_collation).first();
                if (countDoc != null) {
                    resultCount = countDoc.getInteger("count");
                }
            }

            // sorting
            if (!lazyQuery.getSort().isEmpty()) {
                aggregateList.add(sort(lazyQuery.getSort()));
            }

            // add skip and limit parameters
            if (lazyQuery.getFirst() != -1) {
                aggregateList.add(skip(lazyQuery.getFirst()));
            }
            if (lazyQuery.getPageSize() != -1) {
                aggregateList.add(limit(lazyQuery.getPageSize()));
            }

            // perfrom the DB query
            getCollection().aggregate(aggregateList).allowDiskUse(true).collation(_collation).into(result);

        } catch (Exception e) {
            LogHelper.setEvent(e, "Error retrieving documents. See server log for details");
            LOG.error("Error retrieving documents", e);
        }

        return new LazyResult<>(result, resultCount);
    }


    private static Bson getInboxBaseMatch() {
        return and(
                in(Workflow._STATE, STATE_ID_LIST_FIXED_AND_DYNAMIC),
                eq(Workflow._STATUS, PROCESS_STATUS_NORMAL)
        );
    }


    /**
     * Generates basic aggregation pipeline to get documents in user's inbox
     *
     * @param lazyQuery
     * @param aclContext
     * @return
     */
    private List<Bson> getInboxDocListAggregation(WorkflowLazyQuery lazyQuery, AclContext aclContext) {
        List<Bson> aggregateList = new ArrayList<>();
        // NOTE: Delegation IN/OUT logic implementation to be verified and tested

        aggregateList.add(match(and(
                getInboxBaseMatch(),
                or(
                        in(_PATH_PROCESS_STATES_STEPS_USERS_ID, aclContext.toSetSystemIdWithAliases()),
                        in(_PATH_PROCESS_STATES_STEPS_GROUPS_ID, aclContext.toSetGroupsUnits()),
                        in(Workflow._DELEGATED_TO, aclContext.toSetSystemIdWithAliases()))
        )));

        if (!lazyQuery.getFilter().isEmpty()) {
            aggregateList.add(match(lazyQuery.getFilter()));
        }

        if (!lazyQuery.getSort().isEmpty()) {
            aggregateList.add(sort(lazyQuery.getSort()));
        }

        // Perform the base match
        aggregateList.add(project(fields(computed("root", "$$ROOT"))));
        addCurrentStepLookupAggregation(aggregateList, "root", "processStep");

        aggregateList.add(match(
                or(
                        in("processStep.users.id", aclContext.toSetSystemIdWithAliases()),
                        in("processStep.groups.id", aclContext.toSetGroupsUnits()),
                        in("root." + Workflow._DELEGATED_TO, aclContext.toSetSystemIdWithAliases()) // field _BPM_ACL_DELEGATED_TO can be either Array or String
                )
        ));

        // Attach wfTasks (with related to the INBOX processing taskStatuses only) to the workflow
        aggregateList.add(
                lookup(getWorkflowTaskDao().getCollectionName(),
                        asList(
                                new Variable<>("docId", new Document("$toString", "$" + Workflow._ID)),
                                new Variable<>(Workflow._ITERATION, "$root." + Workflow._ITERATION),
                                new Variable<>(Workflow._STATE, "$root." + Workflow._STATE),
                                new Variable<>(Workflow._STEP, "$root." + Workflow._STEP),
                                new Variable<>("aliases", aclContext.toSetSystemIdWithAliases())
                        ),
                        asList(
                                match(expr(new Document("$and", asList(
                                        new Document("$eq", asList("$pId", "$$docId")),
                                        new Document("$eq", asList("$" + WorkflowTask._PROCESS_STEP, "$$" + Workflow._STEP)),
                                        new Document("$eq", asList("$" + WorkflowTask._PROCESS_STATE, "$$" + Workflow._STATE)),
                                        new Document("$eq", asList("$" + WorkflowTask._PROCESS_ITERATION, "$$" + Workflow._ITERATION)),
                                        new Document("$in", asList("$" + WorkflowTask._TASK_STATUS, INBOX_RELATED_TASK_STATUSES)),
                                        new Document("$or", asList(
                                                new Document("$in", asList("$" + WorkflowTask._AUTHOR, "$$aliases")),
                                                new Document("$in", asList("$" + WorkflowTask._DELEGATED_TO, "$$aliases")),
                                                new Document("$in", asList("$" + WorkflowTask._TASK_TO, "$$aliases"))))
                                ))))
                        ),
                        "workflowTasks")
        );

        aggregateList.add(addFields(
                new Field("wftDoneCount",
                        new Document("$size",
                                new Document("$filter", new Document("input", "$workflowTasks")
                                        .append("as", "workflowTask")
                                        .append("cond", new Document("$and",
                                                asList(
                                                        new Document("$in", asList("$$workflowTask." + WorkflowTask._AUTHOR, aclContext.toSetSystemIdWithAliases())),
                                                        new Document("$in", asList("$$workflowTask." + WorkflowTask._TASK_STATUS, TASK_STATUS_LIST_PROCESSED))
                                                )
                                        ))))),
                new Field("wftAssignmentCount",
                        new Document("$size",
                                new Document("$filter", new Document("input", "$workflowTasks")
                                        .append("as", "workflowTask")
                                        .append("cond", new Document("$or",
                                                asList(
                                                        new Document("$and", List.of(
                                                                new Document("$eq", asList("$$workflowTask." + WorkflowTask._TASK_STATUS, TASK_STATUS_ASSIGNMENT)),
                                                                new Document("$in", asList("$$workflowTask." + WorkflowTask._TASK_TO, aclContext.toSetSystemIdWithAliases())))),
                                                        new Document("$and", List.of(
                                                                new Document("$eq", asList("$$workflowTask." + WorkflowTask._TASK_STATUS, TASK_STATUS_DELEGATED_IN)),
                                                                new Document("$in", asList("$$workflowTask." + WorkflowTask._AUTHOR, aclContext.toSetSystemIdWithAliases()))))
                                                )
                                        )))))
        ));

        // Perform checks, if document needs to be shown in user's inbox
        aggregateList.add(project(fields(
                include("root"),
                // check if this is doc author and step 0 - meaning document returned back to Author's inbox
                computed("inAuthorInbox", and(asList(
                        new Document("$in", asList("$root." + WorkflowTask._AUTHOR, aclContext.toSetSystemIdWithAliases())),
                        new Document("$eq", asList("$root." + Workflow._STEP, 0))
                ))),
                // Check, if user already processed current document
                computed("alreadyResponded",
                        new Document("$or", List.of(
                                // If number of assignment = 0 - need to have exactly 1 WorkflowTask with Task Status meaning DONE
                                new Document("$and", List.of(new Document("$eq", List.of("$wftAssignmentCount", 0)), new Document("$eq", List.of("$wftDoneCount", 1)))),
                                // If number of assignments != 0 - need to have number of WorkflowTasks with TaskStatus meaning DONE exactly same, as number of assignments
                                new Document("$and", List.of(new Document("$ne", List.of("$wftAssignmentCount", 0)), new Document("$eq", List.of("$wftAssignmentCount", "$wftDoneCount"))))
                        ))),
                computed("isUserOnCurrentStep", new Document("$or",
                        asList(
                                new Document("$ne",
                                        asList(
                                                new Document("$size",
                                                        new Document("$filter", new Document("input", "$processStep.users")
                                                                .append("as", "user")
                                                                .append("cond", new Document("$in", asList("$$user.id", aclContext.toSetSystemIdWithAliases()))))),
                                                0
                                        )),
                                new Document("$ne",
                                        asList(
                                                new Document("$size",
                                                        new Document("$filter", new Document("input", "$processStep.groups")
                                                                .append("as", "group")
                                                                .append("cond", new Document("$in", asList("$$group.id", aclContext.toSetGroupsUnits()))))),
                                                0
                                        ))
                        )
                )),
                // Check, if current user is delegated on current step
                computed("isDelegatedOut",
                        new Document("$ne",
                                asList(
                                        new Document("$size",
                                                new Document("$filter", new Document("input", "$workflowTasks")
                                                        .append("as", "workflowTask")
                                                        .append("cond", new Document("$and", asList(
                                                                new Document("$eq", asList("$$workflowTask." + WorkflowTask._TASK_STATUS, TASK_STATUS_DELEGATE_OUT)),
                                                                new Document("$in", asList("$$workflowTask." + WorkflowTask._DELEGATED_TO, aclContext.toSetSystemIdWithAliases()))
                                                        ))))),
                                        0
                                )))
        )));

        // Keep only valid documents, with one of three previous checks worked for current document
        aggregateList.add(match(
                or(
                        // User is author, document on initiator step
                        eq("inAuthorInbox", true),
                        // User is on process, task not finished
                        and(
                                eq("alreadyResponded", false),
                                eq("isUserOnCurrentStep", true) // We need separate check, as 'not isDelegatedOut' contains users from previous steps
                        ),
                        // Document delegated to user, task not finished
                        and(
                                eq("alreadyResponded", false),
                                eq("isDelegatedOut", true)
                        )
                )
        ));
        aggregateList.add(replaceRoot("$root"));
        return aggregateList;
    }


    /**
     * Returns documents in Inbox view for specific user or group
     *
     * @param aclContext
     * @param lazyQuery
     * @return
     */
    private LazyResult<Workflow> findAllLazyByViewInbox(WorkflowLazyQuery lazyQuery, AclContext aclContext) {
        final List<Workflow> result = new ArrayList<>();
        int resultCount = 0;
        try {
            List<Bson> aggregateList = getInboxDocListAggregation(lazyQuery, aclContext);

            // get the count of documents matching the filter in case count was not disabled
            if (lazyQuery.isSkipCount()) {
                resultCount = -1;
            } else {
                List<Bson> aggregateCountList = new ArrayList<>(aggregateList);
                aggregateCountList.add(count());
                Document countDoc = getCollection(Document.class).aggregate(aggregateCountList).allowDiskUse(true).collation(_collation).first();
                if (countDoc != null) {
                    resultCount = countDoc.getInteger("count");
                }
            }

            // add skip and limit parameters
            if (lazyQuery.getFirst() != -1) {
                aggregateList.add(skip(lazyQuery.getFirst()));
            }
            if (lazyQuery.getPageSize() != -1) {
                aggregateList.add(limit(lazyQuery.getPageSize()));
            }

            // perfrom the DB query
            getCollection().aggregate(aggregateList).allowDiskUse(true).collation(_collation).into(result);

        } catch (Exception e) {
            LOG.error("Error processing Inbox view for systemId[{}] ", aclContext.getSystemId(), e);
            LogHelper.setEvent(e, "Error processing Inbox view");
        }

        return new LazyResult<>(result, resultCount);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public long viewInboxCount(WorkflowLazyQuery lazyQuery, AclContext aclContext) {
        long resultCount = 0;
        List<Bson> aggregateList = getInboxDocListAggregation(lazyQuery, aclContext);

        // Add another stage to count documents in aggregation pipeline
        aggregateList.add(limit(100));
        aggregateList.add(count());

        Document countDoc = getCollection(Document.class).aggregate(aggregateList).allowDiskUse(true).collation(_collation).first();
        if (countDoc != null) {
            resultCount = countDoc.get("count", Number.class).longValue();
        }

        return resultCount;
    }


    private LazyResult<Workflow> findAllLazyByViewProcessed(WorkflowLazyQuery lazyQuery, AclContext aclContext) {
        final List<Workflow> result = new ArrayList<>();
        int resultCount = 0;

        try {
            List<Bson> aggregateList = new ArrayList<>();

            List<Integer> statusList = getAndRemoveStatusFilter(lazyQuery, List.of(PROCESS_STATUS_NORMAL));

            aggregateList.add(match(and(
                    in(Workflow._STATE, STATE_ID_LIST_FIXED_AND_DYNAMIC),
                    in(Workflow._STATUS, statusList),
                    getProcessParticipantMatch(aclContext))));

            // UI User defined filters
            if (!lazyQuery.getFilter().isEmpty()) {
                aggregateList.add(match(lazyQuery.getFilter()));
            }

            // Attach all WorkflowTasks for the document
            aggregateList.add(
                    lookup(getWorkflowTaskDao().getCollectionName(),
                            asList(
                                    new Variable<>("docId", new Document("$toString", "$_id"))
                            ),
                            asList(
                                    match(expr(new Document("$eq", asList("$" + WorkflowTask._PARENT_ID, "$$docId")))),
                                    match(expr(new Document("$in", asList("$" + WorkflowTask._AUTHOR, aclContext.toSetSystemIdWithAliases()))))
                            ),
                            "workflowTasks")
            );

            // Check, if user already processed current document
            aggregateList.add(new Document("$addFields", new Document(
                    "alreadyResponded",
                    new Document("$ne",
                            asList(
                                    new Document("$size",
                                            new Document("$filter", new Document("input", "$workflowTasks")
                                                    .append("as", "wfTask")
                                                    .append("cond", new Document("$in", asList("$$wfTask." + WorkflowTask._TASK_STATUS, TASK_STATUS_LIST_PROCESSED))))),
                                    0
                            )))
            ));
            aggregateList.add(match(eq("alreadyResponded", true)));

            // get the count of documents matching the filter in case count was not disabled
            if (lazyQuery.isSkipCount()) {
                resultCount = -1;
            } else {
                List<Bson> aggregateCountList = new ArrayList<>(aggregateList);
                aggregateCountList.add(count());
                Document countDoc = getCollection(Document.class).aggregate(aggregateCountList).allowDiskUse(true).collation(_collation).first();
                if (countDoc != null) {
                    resultCount = countDoc.getInteger("count");
                }
            }

            // sorting
            if (!lazyQuery.getSort().isEmpty()) {
                aggregateList.add(sort(lazyQuery.getSort()));
            }

            // add skip and limit parameters
            if (lazyQuery.getFirst() != -1) {
                aggregateList.add(skip(lazyQuery.getFirst()));
            }
            if (lazyQuery.getPageSize() != -1) {
                aggregateList.add(limit(lazyQuery.getPageSize()));
            }

            // perfrom the DB query
            getCollection().aggregate(aggregateList).allowDiskUse(true).collation(_collation).into(result);

        } catch (Exception e) {
            LogHelper.setEvent(e, "WorkflowDaoMongoImpl.viewProcessed()");
        }

        return new LazyResult<>(result, resultCount);
    }


    private LazyResult<Workflow> findAllLazyByViewProcessedAndCompleted(WorkflowLazyQuery lazyQuery, AclContext aclContext) {
        final List<Workflow> result = new ArrayList<>();
        int resultCount = 0;

        try {
            List<Bson> aggregateList = new ArrayList<>();

            List<Integer> statusList = getAndRemoveStatusFilter(lazyQuery, List.of(PROCESS_STATUS_NORMAL));

            aggregateList.add(match(and(
                    in(Workflow._STATE, Stream.concat(STATE_ID_LIST_FIXED_AND_DYNAMIC.stream(), Stream.of(STATE_ID_RPSTR, STATE_ID_ARCH)).toList()),
                    in(Workflow._STATUS, statusList),
                    getProcessParticipantMatch(aclContext))));

            // UI User defined filters
            if (!lazyQuery.getFilter().isEmpty()) {
                aggregateList.add(match(lazyQuery.getFilter()));
            }

            // Attach all WorkflowTasks for the document
            aggregateList.add(
                    lookup(getWorkflowTaskDao().getCollectionName(),
                            asList(
                                    new Variable<>("docId", new Document("$toString", "$_id"))
                            ),
                            asList(
                                    match(expr(new Document("$eq", asList("$" + WorkflowTask._PARENT_ID, "$$docId")))),
                                    match(expr(new Document("$in", asList("$" + WorkflowTask._AUTHOR, aclContext.toSetSystemIdWithAliases()))))
                            ),
                            "wfTasks")
            );

            // Check, if user already processed current document
            aggregateList.add(new Document("$addFields", new Document(
                    "alreadyResponded",
                    new Document("$ne",
                            asList(
                                    new Document("$size",
                                            new Document("$filter", new Document("input", "$wfTasks")
                                                    .append("as", "wfTask")
                                                    .append("cond", new Document("$in", asList("$$wfTask." + WorkflowTask._TASK_STATUS, TASK_STATUS_LIST_PROCESSED))))),
                                    0
                            )))
            ));
            aggregateList.add(match(or(
                    eq("alreadyResponded", true),
                    in(Workflow._STATE, Arrays.asList(STATE_ID_RPSTR, STATE_ID_ARCH))
            )));

            // get the count of documents matching the filter in case count was not disabled
            if (lazyQuery.isSkipCount()) {
                resultCount = -1;
            } else {
                List<Bson> aggregateCountList = new ArrayList<>(aggregateList);
                aggregateCountList.add(count());
                Document countDoc = getCollection(Document.class).aggregate(aggregateCountList).allowDiskUse(true).collation(_collation).first();
                if (countDoc != null) {
                    resultCount = countDoc.getInteger("count");
                }
            }

            // sorting
            if (!lazyQuery.getSort().isEmpty()) {
                aggregateList.add(sort(lazyQuery.getSort()));
            }

            // add skip and limit parameters
            if (lazyQuery.getFirst() != -1) {
                aggregateList.add(skip(lazyQuery.getFirst()));
            }
            if (lazyQuery.getPageSize() != -1) {
                aggregateList.add(limit(lazyQuery.getPageSize()));
            }

            // perfrom the DB query
            getCollection().aggregate(aggregateList).allowDiskUse(true).collation(_collation).into(result);

        } catch (Exception e) {
            LogHelper.setEvent(e, "WorkflowDaoMongoImpl.viewProcessedAndCompleted()");
        }

        return new LazyResult<>(result, resultCount);
    }


    private static List<Integer> getAndRemoveStatusFilter(LazyQuery lazyQuery, List<Integer> defaultStatusFilter) {
        if (lazyQuery.getFilter().containsKey(Workflow._STATUS)) {
            Document statusFilter = lazyQuery.getFilter().get(Workflow._STATUS, Document.class);
            // Assume filters by Status can now only contain list of acceptable statuses
            List<Integer> statusList = statusFilter.get("$in", defaultStatusFilter);
            lazyQuery.getFilter().remove(Workflow._STATUS);
            return statusList;
        }

        return defaultStatusFilter;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateDateLastActivity(ClientSession dbClientSession, Workflow workflow) {
        Date newDateLastActivity = new Date();
        Map<String, Object> updates = Map.of(Workflow._DATE_LAST_ACTIVITY, newDateLastActivity);
        boolean updateResult = applyUpdates(dbClientSession, workflow, updates);
        workflow.setDateLastActivity(newDateLastActivity);
        return updateResult;
    }


    //
    // Private Methods ----------------------------------------------------------------------------
    //


    /**
     * Adds ACL filter to provided base filter
     *
     * @param aclContext Current {@link AclContext}
     * @param baseFilter Base Workflow filter
     * @return
     */
    protected Bson getACLFilter(final AclContext aclContext, final Bson baseFilter) {
        if (baseFilter == null) {
            return getACLFilterBase(aclContext);
        } else {
            return and(baseFilter, getACLFilterBase(aclContext));
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Bson getACLFilterBase(final AclContext aclContext) {
        if (aclContext.isFullAccessMode()) {
            return empty(); // For FullAccessMode we do not imply any restrictions
        }

        // Build filters for internal users - they need to have additional filter by tenant, if applicable by context
        // (use addTenantBaseFilter for this handling)
        List<Bson> internalUserFilters = new ArrayList<>();
        internalUserFilters.add(or(
                eq(Workflow._AUTHOR, aclContext.getSystemId()),
                in(Workflow._PATH_ACL_R, aclContext.toSet()),
                in(Workflow._PATH_ACL_W, aclContext.toSet()),
                in(Workflow._DELEGATED_TO, aclContext.toSetSystemIdWithAliases()),
                in(_PATH_PROCESS_STATES_STEPS_GROUPS_ID, aclContext.toSetGroupsUnits())
        ));

        // Add TenantID check, if required
        addTenantBaseFilter(internalUserFilters);

        // Flatten filter, if needed (i.e. no tenant filter was added)
        Bson internalUserMatch = internalUserFilters.size() == 1
                ? internalUserFilters.getFirst()
                : and(internalUserFilters);

        return or(
                internalUserMatch,
                // Process participants can be added directly from other tenants - do not add optional tenantId filter
                in(_PATH_PROCESS_STATES_STEPS_USERS_ID, aclContext.toSetSystemIdWithAliases())
        );
    }


    /**
     * Adds per-tenant filter. Required for the Global DAO.
     * To be used inside $and/$or operators
     *
     * @param aggregateList
     */
    protected void addTenantBaseMatch(List<Bson> aggregateList) {
    }


    /**
     * Adds per-tenant filter. Required for the Global DAO.
     * To be used inside $and/$or operators
     *
     * @param checks
     */
    protected void addTenantBaseFilter(List<Bson> checks) {
    }


    @Override
    protected Bson aclMatchCriteria(AclContext aclContext) {
        return getACLFilterBase(aclContext);
    }


    /**
     * Builds query with conditions that user is present on Workflow process<br>
     * User is considered to be present on process if he either specified on process by his main SystemID inside his own
     * tenant (if applicable) or by one of his aliases (globally)
     *
     * @param aclContext {@link AclContext} of the user, for whom presence on Process should be checked
     * @return {@link Bson} match criteria, that can be used to check, if user present on process
     */
    private Bson getProcessParticipantMatch(AclContext aclContext) {
        // Query participants in scope of the current tenant only (it is by Groups/OrgUnits or Deligated, as as of now it works only in scope of the single tenant)
        List<Bson> participantBySystemIdFilters = new ArrayList<>();
        participantBySystemIdFilters.add(or(
                in(_PATH_PROCESS_STATES_STEPS_GROUPS_ID, aclContext.toSetGroupsUnits()),
                in(Workflow._DELEGATED_TO, aclContext.toSetSystemIdWithAliases())
        ));
        addTenantBaseFilter(participantBySystemIdFilters);
        Bson participantRestrictedByTenant = participantBySystemIdFilters.size() == 1
                ? participantBySystemIdFilters.get(0)
                : and(participantBySystemIdFilters);

        // Query participants without limiting them by single tenant. E.g.: for eSignWF - user's systemId and his aliases (emails) - valid globally, without per-tenant restriction
        Bson participantNotRestrictedByTenant = or(
                in(_PATH_PROCESS_STATES_STEPS_USERS_ID, aclContext.toSetSystemIdWithAliases())
        );

        return or(
                participantRestrictedByTenant,
                participantNotRestrictedByTenant
        );
    }


    /**
     * Adds aggregation name, that will add field with provided name, containing current document step information
     *
     * @param aggregateList
     * @param workflowPath
     * @param targetPath
     */
    private void addCurrentStepLookupAggregation(List<Bson> aggregateList, String workflowPath, String targetPath) {
        String sourceDoc = "";
        if (isNotBlank(workflowPath)) {
            sourceDoc = workflowPath + ".";
        }

        // Find current state and save to temporary variable
        aggregateList.add(new Document("$addFields",
                new Document("processState", new Document("$first",
                        new Document("$filter", new Document("input", "$" + sourceDoc + Workflow._PROCESS + ".states")
                                .append("as", "procState")
                                .append("cond", new Document("$eq", asList("$$procState.stateId", "$" + sourceDoc + Workflow._STATE))))
                ))));

        // Find current step
        aggregateList.add(new Document("$addFields",
                new Document(targetPath, new Document("$arrayElemAt", asList("$processState.steps", "$" + sourceDoc + Workflow._STEP)))
        ));

        aggregateList.add(new Document("$unset", "processState"));
    }


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    @Override
    public WorkflowFilesDao getFilesDao() {
        return _workflowFilesDao;
    }


    protected WorkflowTaskDao getWorkflowTaskDao() {
        return _workflowTaskDao;
    }


    @Override
    public MongoCollection<Workflow> getCollection() {
        return super.getCollection(Workflow.class);
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
        indexesList.add(new IndexModel(new Document(Workflow._STATE, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Workflow._STEP, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Workflow._STATUS, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Workflow._ITERATION, 1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Workflow._DATE_CREATED, -1), indexOptions1));
        indexesList.add(new IndexModel(new Document(Workflow._DATE_LAST_ACTIVITY, -1), indexOptions1));
        indexesList.add(new IndexModel(new Document(_PATH_PROCESS_STATES_STATEID, 1), indexOptions1));

        IndexOptions indexOptions2 = new IndexOptions().collation(_collation).background(true);
        indexesList.add(new IndexModel(new Document(Workflow._AUTHOR, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Workflow._AUTHOR_BY, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Workflow._DELEGATED_TO, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Workflow._TOPIC, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Workflow._NUMBER, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Workflow._FORM, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Workflow._TAGS, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Workflow._REF_ITEMS, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Workflow._PATH_ACL_R, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(Workflow._PATH_ACL_W, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(_PATH_PROCESS_STATES_STEPS_USERS_ID, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(_PATH_PROCESS_STATES_STEPS_GROUPS_ID, 1), indexOptions2));
        indexesList.add(new IndexModel(new Document(_PATH_FILES_ID, 1), indexOptions2));

        if (COLLECTION_NAME.equals(getCollectionName())) {
            // Fields which are used in Tenant collections only
            IndexOptions indexOptions3 = new IndexOptions().unique(true).collation(_collation).background(true);
            indexesList.add(new IndexModel(new Document(Workflow._ID_WEB, 1), indexOptions3)); // Present in Tenant collections only

            // Need to use partial index, as unique indexes in MongoDB do not support multiple null values
            IndexOptions indexOptions4 = new IndexOptions().unique(true).partialFilterExpression(ID_EXT_PRESENT_FILTER).collation(_collation).background(true);
            indexesList.add(new IndexModel(new Document(Workflow._ID_EXT, 1), indexOptions4));
        }

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
