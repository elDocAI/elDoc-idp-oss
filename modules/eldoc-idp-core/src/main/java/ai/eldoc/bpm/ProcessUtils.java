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

import ai.eldoc.bpm.v3.ProcessImpl;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.dao.WorkflowTaskDao;
import ai.eldoc.model.BpmProcessDefinition;
import ai.eldoc.model.WorkflowTask;
import static ai.eldoc.bpm.BpmConstants.*;
import static ai.eldoc.constants.DirectoryConstants.BPM_PERS_INITIATOR;


/**
 * Process Utils Class
 */
public final class ProcessUtils {

    //
    // Constructor --------------------------------------------------------------------------------
    //
    private ProcessUtils() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    //
    // Static methods -----------------------------------------------------------------------------
    //
    /**
     * Creates Process for the Recognition Workflow processing
     * Also creates respective WorkflowTask Model (Resolution) and saves in DB
     *
     * @param workflowTaskDao
     * @return
     */
    public static Process createProcessForRecoWorkflow(WorkflowTaskDao workflowTaskDao) {
        WorkflowTask wfTask;
        Process process = createProcessWithDraftAndArchStates();

        // Create EXEC State
        ProcessState procStateExec = process.addState(STATE_ID_EXEC);
        procStateExec.setAutoNextState(true);
        procStateExec.setLockSteps(true);
        procStateExec.setStepsRule(RULE_APPRSTRCT);

        // Create Initiator Step
        ProcessStep initiatorStep = process.createStep();
        initiatorStep.addUser(BPM_PERS_INITIATOR);
        procStateExec.getSteps().add(RECO_PROCESS_STEP_INITIATOR, initiatorStep);

        // Create Recognition Step for elDocRecoBot
        ProcessStep recoStep = process.createStep();
        recoStep.addUser(DirectoryConstants.ELDOC_RECO_BOT);
        procStateExec.getSteps().add(RECO_PROCESS_STEP_RECOBOT, recoStep);

        // Create Validation Step (after recognition)
        ProcessStep validationRecoStep = process.createStep();
        validationRecoStep.addUser(BPM_PERS_INITIATOR);
        procStateExec.getSteps().add(RECO_PROCESS_STEP_RECO_VALIDATOR, validationRecoStep);

        // Create Conversion Step for elDocConversionBot
        ProcessStep conversionStep = process.createStep();
        conversionStep.addUser(DirectoryConstants.ELDOC_CONVERTER_BOT);
        procStateExec.getSteps().add(RECO_PROCESS_STEP_CONVERSIONBOT, conversionStep);

        // Create Validation Step (after Conversion)
        ProcessStep validationConvStep = process.createStep();
        validationConvStep.addUser(BPM_PERS_INITIATOR);
        procStateExec.getSteps().add(RECO_PROCESS_STEP_CONV_VALIDATOR, validationConvStep);

        // Clear previously created DocResponses for the RecoWorkflow Process
        workflowTaskDao.delete(null, BpmProcessDefinition.RECO_PROCESS_ID);

        // Create WorkflowTask (Resolution) for elDoc Reco Bot
        wfTask = WorkflowTask.createFollowupFromTo(BpmProcessDefinition.RECO_PROCESS_ID, BPM_PERS_INITIATOR, DirectoryConstants.ELDOC_RECO_BOT);
        wfTask.setNote("Recognition");
        wfTask.setStep(RECO_PROCESS_STEP_RECOBOT);
        workflowTaskDao.create(null, wfTask);

        // Create WorkflowTask (Resolution) for Validator (after Recognition)
        wfTask = WorkflowTask.createFollowupFromTo(BpmProcessDefinition.RECO_PROCESS_ID, BPM_PERS_INITIATOR, BPM_PERS_INITIATOR);
        wfTask.setNote("Validation");
        wfTask.setStep(RECO_PROCESS_STEP_RECO_VALIDATOR);
        workflowTaskDao.create(null, wfTask);

        // Create WorkflowTask (Resolution) for elDoc Conversion Bot
        wfTask = WorkflowTask.createFollowupFromTo(BpmProcessDefinition.RECO_PROCESS_ID, BPM_PERS_INITIATOR, DirectoryConstants.ELDOC_CONVERTER_BOT);
        wfTask.setNote("Conversion");
        wfTask.setStep(RECO_PROCESS_STEP_CONVERSIONBOT);
        workflowTaskDao.create(null, wfTask);

        // Create WorkflowTask (Resolution) for Validator (after Conversion)
        wfTask = WorkflowTask.createFollowupFromTo(BpmProcessDefinition.RECO_PROCESS_ID, BPM_PERS_INITIATOR, BPM_PERS_INITIATOR);
        wfTask.setNote("Validation");
        wfTask.setStep(RECO_PROCESS_STEP_CONV_VALIDATOR);
        workflowTaskDao.create(null, wfTask);

        return process;
    }

    /**
     * Creates blank Process Model
     *
     * @return
     */
    public static ai.eldoc.bpm.Process createProcess() {
        return new ProcessImpl();
    }


    /**
     * Creates Process with Process State: Draft
     *
     * @return
     */
    public static ai.eldoc.bpm.Process createProcessWithDraftState() {
        ai.eldoc.bpm.Process process = createProcess();

        // Create Draft Process State (has to be always present)
        ProcessState procDraftState = process.addState(STATE_ID_DRAFT);
        ProcessStep procInitiatorStep = process.createStep();
        procInitiatorStep.addUser(BPM_PERS_INITIATOR);
        procDraftState.getSteps().add(procInitiatorStep);

        return process;
    }


    /**
     * Creates Process with two must-have Process States: Draft and Arch
     *
     * @return
     */
    public static ai.eldoc.bpm.Process createProcessWithDraftAndArchStates() {
        ai.eldoc.bpm.Process process = createProcessWithDraftState();

        // Create Arch Process State (has to be always present)
        process.addState(STATE_ID_ARCH);

        return process;
    }


    /**
     * Creates Process from JSON-String containing Process configuration
     *
     * @param processJsonString
     * @return
     */
    public static ai.eldoc.bpm.Process getProcess(String processJsonString) {
        return ProcessImpl.fromJson(processJsonString);
    }

}
