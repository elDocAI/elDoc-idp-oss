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
package ai.eldoc.workflow;

import ai.eldoc.bpm.Process;
import ai.eldoc.bpm.ProcessState;
import ai.eldoc.dao.WorkflowTaskDao;
import ai.eldoc.model.*;
import ai.eldoc.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static ai.eldoc.bpm.BpmAclConstants.*;
import static ai.eldoc.bpm.BpmConstants.*;


/**
 * Workflow ACL
 */
public class WorkflowAcl {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowAcl.class);

    private final DirectoryService _directoryService;

    private final WorkflowTaskDao _workflowTaskDao;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * Creates Workflow ACL Instance
     *
     * @param directoryService
     * @param workflowTaskDao
     */
    public WorkflowAcl(DirectoryService directoryService, WorkflowTaskDao workflowTaskDao) {
        _directoryService = directoryService;
        _workflowTaskDao = workflowTaskDao;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Calculates and returns List of available actions on the document for active user (user which is viewing the this document)
     *
     * @param workflow Document model
     * @param formDef FormDefinition model reflects settings for the current document
     * @param aclContext AclContext, for whom to calculate the ACL
     * @return
     */
    public Set<String> get(final Workflow workflow, final FormDefinition formDef, final AclContext aclContext) {

        LOG.trace("Workflow[{}] systemId[{}]: ACL actions check start, state={}, step={}, iteration={}",
                workflow.getId(), aclContext.getSystemId(), workflow.getState(), workflow.getStep(), workflow.getIteration());

        final Set<String> aclSet = new HashSet<>();
        List<Integer> stepsRuleTSReturn;

        try {
            final String docId = workflow.getId();
            final Integer state = workflow.getState();
            final Integer status = workflow.getStatus();
            final Integer step = workflow.getStep();
            final Integer iteration = workflow.getIteration();
            final Process process = workflow.getProcess();
            final ProcessState processState = workflow.getProcessState();

            // Get taskStatuses on which workflow should not be allowed to sent to next state
            // In case there at least one wfTask with taskStatus from getStepsRuleNotAllowedTaskStatuses() - workflow can not be sent to next state
            if (STATE_ID_LIST_FIXED_AND_DYNAMIC.contains(state)) {
                stepsRuleTSReturn = processState.getStepsRuleNotAllowedTaskStatuses(); // Do we need if/else here?
            } else {
                stepsRuleTSReturn = RULE_ANYACTION_TSARR;
            }

            final int nextState = workflow.getNextState();
            aclSet.add(ACTION_REMINDER);

            // Determines if current user is Author
            if (workflow.isAuthor(aclContext.getSystemId())) {

                LOG.trace("Workflow[{}] systemId[{}]: ACL actions check for author", workflow.getId(), aclContext.getSystemId());

                // Compute rights for showing & editing document access control
                if (PROCESS_STATUS_BLOCKED != status) {
                    aclSet.add(ACTION_ACC_CONTROL);
                    if (formDef.isAccessReadEditable()) {
                        aclSet.add(ACC_CONTROL_EDITABLE);
                    }
                }

                if (state == STATE_ID_DRAFT) {
                    aclSet.add(ACTION_SAVE_DRAFT);
                    aclSet.add(ACTION_EDIT);
                    aclSet.add(ACTION_DELETE);

                    if (processState.getAttachmentManager().isAllowEdit()) {
                        aclSet.add(ACTION_ONLINE_EDIT);
                    }

                    if (nextState == STATE_ID_APPROVE) {
                        aclSet.add(ACTION_TO_APPROVAL);
                        aclSet.add(ACTION_TO_WORK);
                    } else if (nextState == STATE_ID_SIGN) {
                        aclSet.add(ACTION_TO_SIGN);
                        aclSet.add(ACTION_TO_WORK);
                    } else if (nextState == STATE_ID_EXEC) {
                        aclSet.add(ACTION_TO_EXEC);
                        aclSet.add(ACTION_TO_WORK);
                    } else if (STATE_ID_LIST_DYNAMIC.contains(nextState)) {
                        aclSet.add(ACTION_TO_WORK);
                    } else if (nextState == STATE_ID_RPSTR) {
                        aclSet.add(ACTION_TO_REPO);
                        aclSet.add(ACTION_TO_WORK);
                    } else if (nextState == STATE_ID_ARCH) {
                        aclSet.add(ACTION_TO_ARCH);
                        aclSet.add(ACTION_TO_WORK);
                    }
                }

                if (STATE_ID_LIST_FIXED_AND_DYNAMIC.contains(state) && status == PROCESS_STATUS_NORMAL && step == 0) {

                    //
                    // If there are any taskStatuses for holding document on current state - we check them
                    //
                    if (!stepsRuleTSReturn.isEmpty() && _workflowTaskDao.count(docId, state, null, iteration, stepsRuleTSReturn, null) > 0) {
                        // If there are wfTasks with the given task statuses (TaskStatuses not allowed for continuing the process),
                        // then workflow cannot be sent to the next state and has to be sent for re-work
                        //
                        // Do not allow adding the response on toWorkAgain step/state, as it will be confusing
                        // and anyway attachments, content, ref fields will be replaced from original document

                        aclSet.add(ACTION_TO_WORK_AGAIN);
                        aclSet.add(ACTION_EDIT);

                        if (processState.getAttachmentManager().isAllowEdit()) {
                            // allow edit document before sending back to work
                            aclSet.add(ACTION_ONLINE_EDIT);
                        }
                    } else {
                        //
                        // If there are no taskStatuses for checking (stepsRuleTSReturn.isEmpty()) - just allow to proceed workflow
                        // OR
                        // If there are no docResponses with given task statuses - workflow can be sent to next state
                        //

                        if (nextState == STATE_ID_SIGN) {
                            aclSet.add(ACTION_TO_SIGN);
                            aclSet.add(ACTION_TO_WORK);
                        } else if (nextState == STATE_ID_EXEC) {
                            aclSet.add(ACTION_TO_EXEC);
                            aclSet.add(ACTION_TO_WORK);
                        } else if (STATE_ID_LIST_DYNAMIC.contains(nextState)) {
                            aclSet.add(ACTION_TO_WORK);
                        } else if (nextState == STATE_ID_RPSTR) {
                            aclSet.add(ACTION_TO_REPO);
                            aclSet.add(ACTION_TO_WORK);
                        } else if (nextState == STATE_ID_ARCH) {
                            aclSet.add(ACTION_TO_ARCH);
                            aclSet.add(ACTION_TO_WORK);
                        }

                        //
                        // http://jira.dms-solutions.co/browse/ELDOC-171
                        // Allow Author to edit document before sending it to the next process phase in case it was at least one "Approved with remarks" DocResponse
                        //
                        if (state == STATE_ID_APPROVE && _workflowTaskDao.count(docId, state, null, iteration, List.of(TASK_STATUS_DONE_W_COMMENTS), null) > 0) {
                            aclSet.add(ACTION_EDIT);
                            if (processState.getAttachmentManager().isAllowEdit()) {
                                // allow edit document before sending back to work
                                aclSet.add(ACTION_ONLINE_EDIT);
                            }
                        }
                    }
                }

                if (STATE_ID_LIST_FIXED_AND_DYNAMIC.contains(state)) {

                    if (status == PROCESS_STATUS_NORMAL || status == PROCESS_STATUS_SUSPENDED) {
                        aclSet.add(ACTION_TERMINATE);
                    }

                    if (status == PROCESS_STATUS_NORMAL) {
                        aclSet.add(ACTION_SUSPEND);
                    }

                    if (status == PROCESS_STATUS_SUSPENDED) {
                        aclSet.add(ACTION_RESUME);
                    }
                }

                if (state == STATE_ID_RPSTR && status == PROCESS_STATUS_NORMAL && step == 0) {
                    if (nextState == STATE_ID_ARCH) {
                        aclSet.add(ACTION_TO_ARCH);
                        aclSet.add(ACTION_TO_WORK);
                    }

                    // Add btnEdit button in case Author can edit doc on Repo state (according to sysFromSettings)
                    if (formDef.getAccessEditByAuthorOnState().contains(STATE_ID_RPSTR)) {
                        aclSet.add(ACTION_EDIT);
                        aclSet.add(ACTION_SAVE_EDIT);
                    }
                }

                if (state == STATE_ID_ARCH && status == PROCESS_STATUS_NORMAL && step == 0) {
                    // Add btnEdit button in case Author can edit doc on Arch state (according to sysFromSettings)
                    if (formDef.getAccessEditByAuthorOnState().contains(STATE_ID_ARCH)) {
                        aclSet.add(ACTION_EDIT);
                        aclSet.add(ACTION_SAVE_EDIT);
                    }
                }

            }

            // If given user has AdminDoc any Roles
            if (aclContext.isAdminDocAny()) {

                LOG.trace("Workflow[{}] systemId[{}]: ACL actions check for adminDocAny", workflow.getId(), aclContext.getSystemId());

                if (status != PROCESS_STATUS_BLOCKED) {
                    aclSet.add(ACTION_ACC_CONTROL);
                }

                if (status == PROCESS_STATUS_BLOCKED && (aclContext.isAdminDocR() || aclContext.isAdminDocRW()) && aclContext.isAdminDocD()) {
                    aclSet.add(ACTION_DELETE);
                }

                if (aclContext.isAdminDocRW()) {

                    LOG.trace("Workflow[{}] systemId[{}]: ACL actions check for adminDocRW", workflow.getId(), aclContext.getSystemId());

                    aclSet.add(ACC_CONTROL_EDITABLE); // Allow AdminDocRW roles to edit additional access to the document

                    if (state == STATE_ID_DRAFT) {
                        aclSet.add(ACTION_DELETE);
                    }

                    if (STATE_ID_LIST_FIXED_AND_DYNAMIC.contains(state)) {

                        if (status == PROCESS_STATUS_NORMAL) {
                            aclSet.add(ACTION_SUSPEND);
                        }

                        if (status == PROCESS_STATUS_SUSPENDED) {
                            aclSet.add(ACTION_RESUME);
                        }

                        if (status == PROCESS_STATUS_NORMAL || status == PROCESS_STATUS_SUSPENDED) {
                            aclSet.add(ACTION_TERMINATE);
                        }
                    }

                    if (state == STATE_ID_RPSTR) {
                        aclSet.add(ACTION_TO_ARCH);
                        aclSet.add(ACTION_TO_WORK);

                        // Add btnEdit button in case AdminDocRW can edit doc on Repo state (according to sysFromSettings)
                        if (formDef.getAccessEditByAdminOnState().contains(STATE_ID_RPSTR)) {
                            aclSet.add(ACTION_EDIT);
                            aclSet.add(ACTION_SAVE_EDIT);
                        }
                    }

                    if (state == STATE_ID_ARCH) {

                        if (process.getState(STATE_ID_RPSTR) != null) {
                            // allow AdminDocRW move doc to Repo from Arch
                            aclSet.add(ACTION_TO_REPO);
                            aclSet.add(ACTION_TO_WORK);
                        }

                        // Add btnEdit button in case AdminDocRW can edit doc on Arch state (according to sysFromSettings)
                        if (formDef.getAccessEditByAdminOnState().contains(STATE_ID_ARCH)) {
                            aclSet.add(ACTION_EDIT);
                            aclSet.add(ACTION_SAVE_EDIT);
                        }
                    }

                    if (status == PROCESS_STATUS_NORMAL || status == PROCESS_STATUS_SUSPENDED || status == PROCESS_STATUS_TERMINATED) {
                        aclSet.add(ACTION_BLOCK);
                    }

                    if (status == PROCESS_STATUS_BLOCKED) {
                        aclSet.add(ACTION_UNBLOCK);
                    }
                }
            }

            // We continue calculating ACL for other users only in case document is in normal status
            // If doc is Suspended or Cancelled or Blocked - we do not calculate access rights for other users
            if (PROCESS_STATUS_NORMAL != status) {
                return aclSet;
            }

            // Case when current user is participant on the process (not author)
            if (STATE_ID_LIST_FIXED_AND_DYNAMIC.contains(state) && step > 0) {

                if (WorkflowService.isNameExistOnStateStep(workflow, state, step, _directoryService.getUserGroupsAndSysId(aclContext.getSystemId()))) {
                    // User present in the Process

                    LOG.trace("Workflow[{}] systemId[{}]: ACL actions check for state: {}", workflow.getId(), aclContext.getSystemId(), state);
                    List<Integer> checkGetIn = Stream.concat(Stream.of(TASK_STATUS_INWORK), TASK_STATUS_LIST_PROCESSED.stream()).collect(Collectors.toList());
                    if (_workflowTaskDao.count(docId, state, step, iteration, checkGetIn, aclContext.getSystemId()) == 0) {
                        // Participant on the process haven't taken this wokflow in processing before - allow to take it into processing
                        aclSet.add(ACTION_GET_IN);
                    } else if (_workflowTaskDao.count(docId, state, step, iteration, List.of(TASK_STATUS_INWORK), aclContext.getSystemId()) > 0) {
                        // In case workflow was already taking in work (wfTasks availbale with taskStatus=TASK_STATUS_INWORK) - allow respective actions

                        aclSet.add(ACTION_DONE);
                        if (STATE_ID_APPROVE == state || STATE_ID_LIST_DYNAMIC.contains(state)) {
                            aclSet.add(ACTION_DONE_W_COMMENTS);
                        }
                        aclSet.add(ACTION_DECLINE);
                        aclSet.add(ACTION_DLG);
                        aclSet.add(ACTION_RESP_ADD);
                        if (processState.getAttachmentManager().isAllowEdit()) {
                            aclSet.add(ACTION_ONLINE_EDIT);
                        }
                    } else if (_workflowTaskDao.count(docId, state, step, iteration, List.of(TASK_STATUS_DELEGATE_OUT), aclContext.getSystemId()) > 0) {
                        // In case workflow was already taking in work and delegated (wfTasks availbale with taskStatus=TASK_STATUS_DELEGATE_OUT) - allow respective actions
                        aclSet.add(ACTION_DLG_CANCEL);
                    }

                } else if (_workflowTaskDao.count(docId, state, step, iteration, List.of(TASK_STATUS_DELEGATE_OUT), null, null, aclContext.getSystemId(), null) > 0) {
                    // Current user is Delegated user on this step

                    LOG.trace("Workflow[{}] systemId[{}]: ACL actions check for the delegated user", workflow.getId(), aclContext.getSystemId());

                    if (_workflowTaskDao.count(docId, state, step, iteration, List.of(TASK_STATUS_INWORK), aclContext.getSystemId()) > 0) {
                        // In case Delegated user alredy took document InWork (response with taskStatus = TASK_STATUS_INWORK present) - show him respective actions

                        aclSet.add(ACTION_DONE);
                        if (STATE_ID_APPROVE == state || STATE_ID_LIST_DYNAMIC.contains(state)) {
                            aclSet.add(ACTION_DONE_W_COMMENTS);
                        }
                        aclSet.add(ACTION_DECLINE);
                        aclSet.add(ACTION_DLG);
                        aclSet.add(ACTION_RESP_ADD);
                        if (processState.getAttachmentManager().isAllowEdit()) {
                            aclSet.add(ACTION_ONLINE_EDIT);
                        }

                    } else if (_workflowTaskDao.count(docId, state, step, iteration, List.of(TASK_STATUS_DELEGATED_IN), aclContext.getSystemId()) > 0) {
                        // Delegated user: allow to take document in processing

                        aclSet.add(ACTION_GET_IN);

                    }

                } else if (_workflowTaskDao.count(docId, state, step, iteration, List.of(TASK_STATUS_DELEGATE_OUT), aclContext.getSystemId()) > 0) {
                    // User that delegated his response to smbdy: If there is response with taskStatus = TASK_STATUS_DELEGATE_OUT where author = currentUser then show button btnDlgateCncl

                    aclSet.add(ACTION_DLG_CANCEL);

                }
            }
        } catch (Exception e) {
            LogHelper.setEvent("id00273", e, null, new LogExt(workflow));
        } finally {
            LOG.trace("Workflow[{}] systemId[{}]: ACL actions: {}", workflow.getId(), aclContext.getSystemId(), aclSet);
        }

        return aclSet;
    }

}
