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

import ai.eldoc.service.LogHelper;
import static ai.eldoc.bpm.BpmConstants.*;


/**
 * Workflow Utils Class
 */
public final class WorkflowUtils {

    //
    // Constructor --------------------------------------------------------------------------------
    //
    private WorkflowUtils() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    //
    // Labaling Methods ---------------------------------------------------------------------------
    //
    /**
     * Returns Workflow status labelId based on the Workflow Status value
     *
     * @param statusId id of the Workflow status
     * @return status labelId
     */
    public static String getProcessStatusLabelId(Integer statusId) {
        if (statusId == PROCESS_STATUS_NORMAL) {
            return "general.dfStatusNormal";
        } else if (statusId == PROCESS_STATUS_SUSPENDED) {
            return "general.dfStatusSuspend";
        } else if (statusId == PROCESS_STATUS_TERMINATED) {
            return "general.dfStatusTerminated";
        } else if (statusId == PROCESS_STATUS_BLOCKED) {
            return "general.dfStatusBlock";
        }
        return "undefined";
    }


    /**
     * Returns process name labelId for UI based on the Process ID
     *
     * @param stateId - Process ID
     * @return Process name
     */
    public static String getProcessNameLabelId(Integer stateId) {
        if (STATE_ID_DRAFT == stateId) {
            return "general.dfStateDraft";
        } else if (STATE_ID_APPROVE == stateId) {
            return "general.dfStateApprove";
        } else if (STATE_ID_SIGN == stateId) {
            return "general.dfStateSign";
        } else if (STATE_ID_EXEC == stateId) {
            return "general.dfStateExec";
        } else if (STATE_ID_RPSTR == stateId) {
            return "general.dfStateRpstr";
        } else if (STATE_ID_ARCH == stateId) {
            return "general.dfStateArch";
        }
        return "bpm-undefined";
    }


    /**
     *
     * @param stateId ID of the process state
     * @param stepId ID of the process step
     * @return ID of the label with process state/step name to be used on UI
     */
    public static String getProcessNameLabelIdReco(Integer stateId, Integer stepId) {
        if (STATE_ID_DRAFT == stateId) {
            return "general.dfStateDraft";
        } else if (STATE_ID_APPROVE == stateId) {
            return "general.dfStateApprove";
        } else if (STATE_ID_SIGN == stateId) {
            return "general.dfStateSign";
        } else if (STATE_ID_EXEC == stateId) {
            if (RECO_PROCESS_STEP_RECOBOT == stepId) {
                return "general.dfStateReco";
            } else if (RECO_PROCESS_STEP_RECO_VALIDATOR == stepId) {
                return "general.dfStateRecoValidation";
            } else if (RECO_PROCESS_STEP_CONVERSIONBOT == stepId) {
                return "general.dfStateConv";
            } else if (RECO_PROCESS_STEP_CONV_VALIDATOR == stepId) {
                return "general.dfStateConvValidation";
            }
            return "general.dfStateExec";
        } else if (STATE_ID_RPSTR == stateId) {
            return "general.dfStateRpstr";
        } else if (STATE_ID_ARCH == stateId) {
            return "general.dfStateArch";
        }
        return "bpm-undefined";
    }


    public static String getRespStatusName(int taskStatus) {
        switch (taskStatus) {
            case TASK_STATUS_TO_APPROVAL:
                return "general.TASK_STATUS_TO_APPROVAL";
            case TASK_STATUS_SUSPEND:
                return "general.TASK_STATUS_SUSPEND";
            case TASK_STATUS_RESUME:
                return "general.TASK_STATUS_RESUME";
            case TASK_STATUS_TERMINATE:
                return "general.TASK_STATUS_TERMINATE";
            case TASK_STATUS_TO_WORK_AGAIN:
                return "general.TASK_STATUS_TO_WORK_AGAIN";
            case TASK_STATUS_TO_SIGN:
                return "general.TASK_STATUS_TO_SIGN";
            case TASK_STATUS_TO_EXECUTION:
                return "general.TASK_STATUS_TO_EXECUTION";
            case TASK_STATUS_BLOCK:
                return "general.TASK_STATUS_BLOCK";
            case TASK_STATUS_TO_ARCHIVE:
                return "general.TASK_STATUS_TO_ARCHIVE";
            case TASK_STATUS_TO_REPOSITORY:
                return "general.TASK_STATUS_TO_REPOSITORY";
            case TASK_STATUS_ASSIGNMENT:
                return "general.TASK_STATUS_ASSIGNMENT";
            case TASK_STATUS_INWORK:
                return "general.TASK_STATUS_INWORK";
            case TASK_STATUS_DONE:
                return "general.TASK_STATUS_DONE";
            case TASK_STATUS_DONE_W_COMMENTS:
                return "general.TASK_STATUS_DONE_W_COMMENTS";
            case TASK_STATUS_DECLINE:
                return "general.TASK_STATUS_DECLINE";
            case TASK_STATUS_RETURN_TO_INITIATOR:
                return "general.TASK_STATUS_RETURN_TO_INITIATOR";
            case TASK_STATUS_DELEGATE_OUT:
                return "general.TASK_STATUS_DELEGATE_OUT";
            case TASK_STATUS_DELEGATED_IN:
                return "general.TASK_STATUS_DELEGATED_IN";
            case TASK_STATUS_DELEGATE_CANCEL:
                return "general.TASK_STATUS_DELEGATE_CANCEL";
            case TASK_STATUS_DELETE:
                return "general.TASK_STATUS_DELETE";
            default:
                String comment = "TS:" + taskStatus;
                LogHelper.setEvent("id00100", comment);
                return "bpm-undefined(" + taskStatus + ")";
        }
    }
}
