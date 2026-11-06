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
package ai.eldoc.bpm;

import java.util.Arrays;
import java.util.List;
import java.util.stream.*;


/**
 * BPM Constants<br>
 *
 * <br><strong>Workflow State:</strong><br>
 * Process.stateId has the following allocation of ids:<br>
 * - 0,1,2,3,95,100 - reserved for the fixed processes<br>
 * - 10-89 - reserved for the dynamic processes<br>
 * <br>
 * IMPORTANT: stateIds 0 and 100 must be always present on the process<br><br>
 *
 * <strong>Workflow Task / DocResponse Task Statuses (workflow participants):</strong><br>
 * Basically we do differentiate the following action types on the Process State:<br>
 * - Done<br>
 * - Done with comments<br>
 * - Decline<br>
 * <br>
 * Standard actions (do not depend on Process State):<br>
 * - Assignment - is optional, some states or even steps on the state may have it, some not (e.g.: eSign workflow: users must have taskAssignment, esign-bot - doesn't have taskAssignment)<br>
 * - Taken in work - created after user first open/accesses workflow<br>
 * <br>
 * Also actions in case delegation is allowed (what is default):<br>
 * - Delegate<br>
 * - Delegation cancel<br>
 * <br>
 * As such Process States can have different action names (according to the Process State purpose), but those actions have meaning of the standard action types, e.g.:<br>
 * - Approval Process State has actions: Approval, Approval with comments, Declined, what actually means action types: Done, Done with comments, Declined<br>
 * - Sign Process State has action Signed, what actually means action type Done;<br>
 * - Follow-up Process has action Done, what exactly means standard action Done, and so on.<br>
 */
public class BpmConstants {

    private static final int VIRTUAL_STATE_OFFSET = 1000;

    //
    // Recognition Process Steps ------------------------------------------------------------------
    //
    public static final int RECO_PROCESS_STEP_INITIATOR = 0;
    public static final int RECO_PROCESS_STEP_RECOBOT = 1;
    public static final int RECO_PROCESS_STEP_RECO_VALIDATOR = 2;
    public static final int RECO_PROCESS_STEP_CONVERSIONBOT = 3;
    public static final int RECO_PROCESS_STEP_CONV_VALIDATOR = 4;

    //
    // Process State Ids --------------------------------------------------------------------------
    //
    /**
     * Process State Draft
     */
    public static final int STATE_ID_DRAFT = 0;

    /**
     * Process State Approval
     */
    public static final int STATE_ID_APPROVE = 1;

    /**
     * Process State Sign-off
     */
    public static final int STATE_ID_SIGN = 2;

    /**
     * Process State Follow-up
     */
    public static final int STATE_ID_EXEC = 3;

    /**
     * Process State Repository
     */
    public static final int STATE_ID_RPSTR = 95;

    /**
     * Process State Archive
     */
    public static final int STATE_ID_ARCH = 100;

    /**
     * Process State (virtual) Annex (Annex State used only with DocResponses/WorkflowTasks)
     */
    public static final int STATE_ID_ANNEX = 101;

    /**
     * Workflow State Follow-up: Virtual state for IDP Recognition Document - Follow-up (Recognition)
     */
    public static final int STATE_ID_EXEC_RECOBOT = STATE_ID_EXEC * VIRTUAL_STATE_OFFSET + RECO_PROCESS_STEP_RECOBOT;
    /**
     * Workflow State Follow-up: Virtual state for IDP Recognition Document - Follow-up (Recognition validation)
     */
    public static final int STATE_ID_EXEC_RECO_VALIDATION = STATE_ID_EXEC * VIRTUAL_STATE_OFFSET + RECO_PROCESS_STEP_RECO_VALIDATOR;
    /**
     * Workflow State Follow-up: Virtual state for IDP Recognition Document - Follow-up (Conversion)
     */
    public static final int STATE_ID_EXEC_CONVERSIONBOT = STATE_ID_EXEC * VIRTUAL_STATE_OFFSET + RECO_PROCESS_STEP_CONVERSIONBOT;
    /**
     * Workflow State Follow-up: Virtual state for IDP Recognition Document - Follow-up (Conversion validation)
     */
    public static final int STATE_ID_EXEC_CONV_VALIDATION = STATE_ID_EXEC * VIRTUAL_STATE_OFFSET + RECO_PROCESS_STEP_CONV_VALIDATOR;

    /**
     * List of all fixed Process States: STATE_ID_DRAFT, STATE_ID_APPROVE, STATE_ID_SIGN, STATE_ID_EXEC, STATE_ID_RPSTR, STATE_ID_ARCH
     */
    public static final List<Integer> STATE_ID_LIST = Arrays.asList(STATE_ID_DRAFT, STATE_ID_APPROVE, STATE_ID_SIGN, STATE_ID_EXEC, STATE_ID_RPSTR, STATE_ID_ARCH);

    /**
     * List of fixed work Process States: STATE_ID_APPROVE, STATE_ID_SIGN, STATE_ID_EXEC
     */
    public static final List<Integer> STATE_ID_LIST_FIXED = Arrays.asList(STATE_ID_APPROVE, STATE_ID_SIGN, STATE_ID_EXEC);

    /**
     * List of dynamic work Process States: those with stateIds 10-89 and excluding fixed work Process States: STATE_ID_APPROVE, STATE_ID_SIGN, STATE_ID_EXEC
     */
    public static final List<Integer> STATE_ID_LIST_DYNAMIC = IntStream.rangeClosed(10, 89).boxed().toList();

    /**
     * Combined List of fixed and dynamic work Process States: STATE_ID_APPROVE, STATE_ID_SIGN, STATE_ID_EXEC and stateIds 10-89
     */
    public static final List<Integer> STATE_ID_LIST_FIXED_AND_DYNAMIC = Stream.concat(STATE_ID_LIST_FIXED.stream(), STATE_ID_LIST_DYNAMIC.stream()).collect(Collectors.toList());

    /**
     * List of all Virtual Process State Ids
     */
    public static final List<Integer> STATE_ID_VIRTUAL_LIST = List.of(STATE_ID_EXEC_RECOBOT, STATE_ID_EXEC_RECO_VALIDATION, STATE_ID_EXEC_CONVERSIONBOT, STATE_ID_EXEC_CONV_VALIDATION);

    //
    // Process Statuses ---------------------------------------------------------------------------
    //
    /**
     * Process status: Normal (default)
     */
    public static final int PROCESS_STATUS_NORMAL = 1;

    /**
     * Process status: Suspended (can be resumed)
     */
    public static final int PROCESS_STATUS_SUSPENDED = 2;

    /**
     * Process status: Terminated (cannot be resumed)
     */
    public static final int PROCESS_STATUS_TERMINATED = 3;

    /**
     * Process status: Blocked (hidden from all users in the system)
     */
    public static final int PROCESS_STATUS_BLOCKED = 4;

    /**
     * List of Process Statuses
     */
    public static final List<Integer> PROCESS_STATUS_LIST = Arrays.asList(PROCESS_STATUS_NORMAL, PROCESS_STATUS_SUSPENDED, PROCESS_STATUS_TERMINATED, PROCESS_STATUS_BLOCKED);

    //
    // WorkflowTask / DocResponse Task Statuses (workflow preparation) ----------------------------
    //
    /**
     * TaskStatus: Assignment - what means WorkflowTask with this taskStatus provides context of the task assignment to the given user
     */
    public static final int TASK_STATUS_ASSIGNMENT = 500;

    //
    // WorkflowTask / DocResponse Task Statuses (workflow participants) ---------------------------
    //
    /**
     * TaskStatus: InWork
     */
    public static final int TASK_STATUS_INWORK = 1;

    /**
     * TaskStatus: Done
     */
    public static final int TASK_STATUS_DONE = 2;

    /**
     * TaskStatus: Done with comments
     */
    public static final int TASK_STATUS_DONE_W_COMMENTS = 3;

    /**
     * TaskStatus: Declined
     */
    public static final int TASK_STATUS_DECLINE = 4;

    /**
     * TaskStatus: Delegated (delegated to someone)
     */
    public static final int TASK_STATUS_DELEGATE_OUT = 6;

    /**
     * TaskStatus: Delegation Cancelled
     */
    public static final int TASK_STATUS_DELEGATE_CANCEL = 7;

    /**
     * List of task statuses with the meaning Done (used for backward compatibility), specifically contains the following task statuses:<br>
     * - TASK_STATUS_DONE
     */
    public static final List<Integer> TASK_STATUS_DONE_LIST = List.of(TASK_STATUS_DONE);

    //
    // WorkflowTask / DocResponse Task Statuses (workflow initiator) ------------------------------
    //
    /**
     * TaskStatus: To Work (meaning: Send the Workflow to the next Process State (except: Repo or Arch) in work)
     */
    public static final int TASK_STATUS_TO_WORK = 100;

    /**
     * TaskStatus: To Work Repeatedly (To Work Again)
     */
    public static final int TASK_STATUS_TO_WORK_AGAIN = 104;

    /**
     * TaskStatus: To Archive State
     */
    public static final int TASK_STATUS_TO_ARCHIVE = 107;

    /**
     * TaskStatus: To Store (Repository) State
     */
    public static final int TASK_STATUS_TO_REPOSITORY = 108;

    /**
     * TaskStatus: To Approval State
     *
     * @deprecated use TASK_STATUS_TO_WORK instead (has the same code as TASK_STATUS_TO_WORK)
     */
    @Deprecated
    public static final int TASK_STATUS_TO_APPROVAL = 100;

    /**
     * TaskStatus: To Sign-off State
     *
     * @deprecated use TASK_STATUS_TO_WORK instead
     */
    @Deprecated
    public static final int TASK_STATUS_TO_SIGN = 105;

    /**
     * TaskStatus: To Follow-up (Execution) State
     *
     * @deprecated use TASK_STATUS_TO_WORK instead
     */
    @Deprecated
    public static final int TASK_STATUS_TO_EXECUTION = 106;

    /**
     * TaskStatus: Suspend
     */
    public static final int TASK_STATUS_SUSPEND = 101;

    /**
     * TaskStatus: Resume (resumes workflow after {@link #TASK_STATUS_SUSPEND} and {@link #TASK_STATUS_BLOCK})
     */
    public static final int TASK_STATUS_RESUME = 102;

    /**
     * TaskStatus: Terminate
     */
    public static final int TASK_STATUS_TERMINATE = 103;

    /**
     * TaskStatus: Block
     */
    public static final int TASK_STATUS_BLOCK = 110;

    /**
     * TaskStatus: Delete Document
     */
    public static final int TASK_STATUS_DELETE = 111;

    /**
     * TaskStatus: Returned to initiator
     */
    public static final int TASK_STATUS_RETURN_TO_INITIATOR = 112;

    /**
     * TaskStatus: Delegated in
     */
    public static final int TASK_STATUS_DELEGATED_IN = 200;

    /**
     * TaskStatus: Process completed (normal)
     */
    public static final int TASK_STATUS_PROCESS_COMPLETED = 300;

    /**
     * TaskStatus: Process move to the next Process State
     */
    public static final int TASK_STATUS_PROCESS_NEXT_STEP = 301;

    /**
     * TaskStatus: Process interruption and return to Initiator
     */
    public static final int TASK_STATUS_PROCESS_INTERRUPT = 302;

    //
    // Workflow step action rules -----------------------------------------------------------------
    //
    /**
     * Workflow step action rule: Action Processed Any (e.g. Approved, Approved with Edits, Signed)
     */
    public static final String RULE_APPROVE = "approveAny";

    /**
     * Workflow step action rule: Action Processed Any: list of TaskStatuses which are not allowed<br>
     * E.g.: if docResponse taskStatus hits this status - workflow will be returned to Author
     */
    public static final List<Integer> RULE_APPROVE_TSARR = Arrays.asList(TASK_STATUS_DECLINE, TASK_STATUS_RETURN_TO_INITIATOR);

    /**
     * Workflow step action rule: Action Processed Strict (e.g. Approved, Signed)
     */
    public static final String RULE_APPRSTRCT = "approveStrict";

    /**
     * Workflow step action rule: Action Processed Strict: list of TaskStatuses which are not allowed<br>
     * E.g.: if docResponse taskStatus hits this status - workflow will be returned to Author
     */
    public static final List<Integer> RULE_APPRSTRCT_TSARR = Arrays.asList(TASK_STATUS_DONE_W_COMMENTS, TASK_STATUS_DECLINE, TASK_STATUS_RETURN_TO_INITIATOR);

    /**
     * Workflow step action rule: Action Any (e.g. Approved, Approved with Edits, Signed, Declined)
     */
    public static final String RULE_ANYACTION = "anyAction";

    /**
     * Workflow step action rule: Action Processed Any: list of TaskStatuses which are not allowed<br>
     * E.g.: if docResponse taskStatus hits this status - workflow will be returned to Author
     */
    public static final List<Integer> RULE_ANYACTION_TSARR = Arrays.asList(TASK_STATUS_RETURN_TO_INITIATOR); // (!) used as default for exceptions (find in project code)

    //
    // WorkflowTask / DocResponse Task Statuses Sets ----------------------------------------------
    //
    /**
     * Task Statuses with meaning that Workflow is completed by User (or his delegants)
     */
    public final static List<Integer> TASK_STATUS_LIST_COMPLETED = Arrays.asList(
            TASK_STATUS_DONE,
            TASK_STATUS_DONE_W_COMMENTS,
            TASK_STATUS_DECLINE
    );

    /**
     * Task Statuses with meaning that Workflow completed by User
     */
    public final static List<Integer> TASK_STATUS_LIST_PROCESSED = Arrays.asList(
            TASK_STATUS_DONE,
            TASK_STATUS_DONE_W_COMMENTS,
            TASK_STATUS_DECLINE,
            TASK_STATUS_DELEGATE_OUT
    );

    /**
     * Task Statuses for displaying in Document Responses List on the UI
     */
    public static final List<Integer> TASK_STATUS_LIST_RESPONSES_TO_SHOW = Arrays.asList(TASK_STATUS_INWORK,
            TASK_STATUS_DONE,
            TASK_STATUS_DONE_W_COMMENTS,
            TASK_STATUS_DECLINE,
            TASK_STATUS_RETURN_TO_INITIATOR,
            TASK_STATUS_DELEGATE_OUT,
            TASK_STATUS_TO_WORK,
            TASK_STATUS_TO_APPROVAL,
            TASK_STATUS_SUSPEND,
            TASK_STATUS_RESUME,
            TASK_STATUS_TERMINATE,
            TASK_STATUS_BLOCK,
            TASK_STATUS_TO_WORK_AGAIN,
            TASK_STATUS_TO_SIGN,
            TASK_STATUS_TO_EXECUTION,
            TASK_STATUS_TO_ARCHIVE,
            TASK_STATUS_DELEGATED_IN
    );

    /**
     * Task Statuses of the Document Initiator with meaning Workflow initiation
     */
    public static final List<Integer> TASK_STATUS_LIST_WORKFLOW_INIT = Arrays.asList(
            TASK_STATUS_TO_WORK,
            TASK_STATUS_TO_APPROVAL,
            TASK_STATUS_TO_SIGN,
            TASK_STATUS_TO_EXECUTION,
            TASK_STATUS_TO_REPOSITORY,
            TASK_STATUS_TO_ARCHIVE
    );

    /**
     * Task Statuses of the Document Initiator with meaning Workflow initiation or repeated initiation
     */
    public static final List<Integer> TASK_STATUS_LIST_WORKFLOW_INIT_REINIT = Stream.concat(
            TASK_STATUS_LIST_WORKFLOW_INIT.stream(),
            Stream.of(TASK_STATUS_TO_WORK_AGAIN))
            .collect(Collectors.toList());

    /**
     * Task Statuses for the delegated user Inbox
     */
    public static final List<Integer> TASK_STATUS_LIST_INBOX = Arrays.asList(
            TASK_STATUS_INWORK,
            TASK_STATUS_DELEGATED_IN
    );

    /**
     * TaskStatuses of Workflow control actions
     */
    public static final List<Integer> TASK_STATUS_LIST_WF_CONTROL = Arrays.asList(TASK_STATUS_SUSPEND,
            TASK_STATUS_TERMINATE,
            TASK_STATUS_BLOCK,
            TASK_STATUS_RESUME,
            TASK_STATUS_DELETE
    );


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private BpmConstants() {
    }


    //
    // Staic Methods ------------------------------------------------------------------------------
    //
    /**
     * Checks, if requested state is virtual
     *
     * @param virtualState
     * @return
     */
    public static boolean isVirtualState(int virtualState) {
        return STATE_ID_VIRTUAL_LIST.contains(virtualState);
    }


    /**
     * Returns actual process step, encoded into virtual state
     *
     * @param virtualState
     * @return
     */
    public static int getStepByVirtualState(int virtualState) {
        int actualState = getStateByVirtualState(virtualState);
        return virtualState % (actualState * VIRTUAL_STATE_OFFSET);
    }


    /**
     * Returns actual process state, encoded into virtual state
     *
     * @param virtualState
     * @return
     */
    public static int getStateByVirtualState(int virtualState) {
        return virtualState / VIRTUAL_STATE_OFFSET;
    }

}
