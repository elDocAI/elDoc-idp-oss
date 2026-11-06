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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.TenantContext;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.dao.*;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.model.directory.BpmNameItem;
import ai.eldoc.model.directory.DirectoryItem;
import ai.eldoc.service.*;
import ai.eldoc.util.I18n;
import ai.eldoc.util.LabelUtils;
import com.mongodb.client.ClientSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import static ai.eldoc.bpm.BpmConstants.*;
import static ai.eldoc.bpm.BpmParams.*;
import static ai.eldoc.constants.DirectoryConstants.BPM_PERS_INITIATOR;
import static ai.eldoc.model.directory.BpmNameItem.isBpmName;
import static ai.eldoc.workflow.WorkflowUtils.getProcessNameLabelId;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * BPM Engine Service
 */
@ApplicationScoped
public class BpmEngine {

    private static final Logger LOG = LoggerFactory.getLogger(BpmEngine.class);

    @Inject
    private DbClientService _dbClientService;

    @Inject
    private NumSequenceDao _numSequenceDao;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private WorkflowTaskDao _workflowTaskDao;

    @Inject
    private WorkflowService _workflowService;

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private Event<ProcessUpdateEvent> _procUpdateEvent;


    /**
     * Prepares process for starting workflow
     * Performs replacement of BPM-variables with target values
     * IMPORTANT:
     * - it is assumed that workflow.author field was already set
     * - it is also required to have workflow saved in DB (_id should be present) for proper work on EXEC State calculation
     *
     * @param workflow
     * @param raiseOptinalBpmExceptions
     */
    public void calculateProcess(final Workflow workflow, boolean raiseOptinalBpmExceptions) {
        final Process process = workflow.getProcess();
        if (workflow.isRecoWF()) {
            // For RecoWF we should leave Initiator in the Process even when Initiator is appearing on several steps
            process.getStates().forEach(processState -> calculateState(workflow, process, processState, true, true, raiseOptinalBpmExceptions));
        } else {
            // For regular Workflows system automatically removes Initiator in case it appears several times in the process (this is feature, not a bug)
            process.getStates().forEach(processState -> calculateState(workflow, process, processState, false, false, raiseOptinalBpmExceptions));
        }
    }


    /**
     * Calculates phase (state)<br>
     * For the fixed-route: all BPM-names are to be replaced with the actual(computed) target names of users (or groups where applicable)
     * Fixed-route definition: when no steps defined on the phase (state) - route is flexible, in case steps defined on the phase(state) - rout is fixed
     *
     * @param workflow can be null because of ProcBuilder
     * @param process workflow configuration
     * @param processState workflow phase (state) to be calculated
     * @param skipInitiatorCheckOnDuplicates
     * @param checkDuplicatesPerStepOnly
     * @param raiseOptinalBpmExceptions
     */
    public void calculateState(
            final ProcessableDocument workflow,
            final Process process,
            final ProcessState processState,
            boolean skipInitiatorCheckOnDuplicates,
            boolean checkDuplicatesPerStepOnly,
            boolean raiseOptinalBpmExceptions) {
        // We need to check on whether we have a fixed or flexible route for the EXEC state
        if (processState.isFixedRoute()) {

            // Special handling for the Exec State is required
            if (STATE_ID_EXEC == processState.getStateId()) {
                // in case of Fixed Route - we need to copy all Resolution DocResponses from SysProcess to the current Workflow
                // IMPORTANT: workflow has to be saved in the DB at this stage
                if (workflow.isNew()) {
                    throw new ElDocRuntimeException("Workflow has to be saved in the DB for EXEC BPM State proper calculation. Can not continue and copy Resolutions");
                }
                List<WorkflowTask> resolTemplateList = getWorkflowTaskDao().getAssignedTasksList(workflow.getProcessId(), STATE_ID_EXEC);
                resolTemplateList.forEach(resolTemplateItem -> {
                    // First we need to check validity of the resolTo name item
                    String resolTo = resolTemplateItem.getResolutionTo();
                    String resolToResolvedAs;

                    if (isBpmName(resolTo)) {
                        // Resolve target name, that should be used instead of bpmName
                        // To avoid duplicated logs - we do not log events at this point
                        resolToResolvedAs = resolveBpmName(workflow, resolTo, workflow.getAuthor(), false);
                    } else {
                        resolToResolvedAs = resolTo;
                    }

                    if (resolToResolvedAs != null && _directoryService.getDirectoryItem(resolToResolvedAs).isDeleted()) {
                        // in case DitrectoryItem is Deleted - remove it from the process
                        resolToResolvedAs = null;
                    }

                    // In case name was properly resolved and is valid - let's prcoess it
                    if (resolToResolvedAs != null) {
                        WorkflowTask resolNew = WorkflowTask.createFollowupFromTo(workflow.getId(), workflow.getAuthor(), resolToResolvedAs);
                        // We need to copy the following items only:
                        resolNew.setTaskKFId(resolTemplateItem.getTaskKFId());
                        resolNew.setNote(resolTemplateItem.getNote());
                        resolNew.setStep(resolTemplateItem.getStep());
                        Date dateDue = null;
                        if (resolTemplateItem.getDateDue() != null) {
                            dateDue = new Date(new Date().getTime() + resolTemplateItem.getDateDue().getTime());
                        }
                        resolNew.setDateDue(dateDue);
                        getWorkflowTaskDao().updateOrCreate(null, resolNew);
                    }
                });
            }
        } else {
            // Add initiator step for flexible route
            ProcessStep procStep = process.createStep();
            procStep.addUser(BPM_PERS_INITIATOR);
            processState.getSteps().add(procStep);
        }

        for (ProcessStep processStep : processState.getSteps()) {
            calculateStep(workflow, process, processState, processStep, raiseOptinalBpmExceptions);
        }

        // As there can be duplicate names on the process - they should be cleaned up
        // For the STATE_ID_EXEC phase we need also to check whether created Resolution WorkflowTask should be removed as well
        int maxSteps = processState.getSteps().size(); // Total number of Steps on the Phase (before steps clean-up)
        Map<Integer, List<String>> removedNamesOnStepMap = processState.removeNameDuplication(skipInitiatorCheckOnDuplicates, checkDuplicatesPerStepOnly); // Resolve duplicate names on the process
        List<Integer> removedStepsIndexes = processState.removeEmptySteps(); // As there can be empty Steps after removing duplicate names - do steps cleanup

        // Resolve all removed names on all steps and log them
        for (Entry<Integer, List<String>> removedNamesOnStep : removedNamesOnStepMap.entrySet()) {
            if (!removedNamesOnStep.getValue().isEmpty()) {
                // Removed names from the step are present - process them accordingly
                List<DirectoryItem> removedNamesOnStepAsDI = new ArrayList<>();

                // Resolve removed names
                removedNamesOnStep.getValue().forEach(systemId -> removedNamesOnStepAsDI.add(_directoryService.getDirectoryItem(systemId)));

                // Log removed names
                LogHelper.setEvent("id00282",
                        null,
                        null,
                        new LogExt(workflow),
                        new Object[]{
                                LabelUtils.getLabel(getProcessNameLabelId(processState.getStateId()), I18n.LOCALE_ENG),
                                removedNamesOnStep.getKey(),
                                removedNamesOnStepAsDI.stream().map(di -> di.getItemNameUI()).collect(Collectors.joining(", "))
                        });

                // Special handling is required for the ExecPhase: we need to delete abandoned resolutions
                if (STATE_ID_EXEC == processState.getStateId()) {
                    removedNamesOnStep.getValue().forEach(resoltToSystemId -> {
                        if (!getWorkflowTaskDao().delete(workflow.getId(), processState.getStateId(), removedNamesOnStep.getKey(), 1, Arrays.asList(TASK_STATUS_ASSIGNMENT), null, null, null, resoltToSystemId)) {
                            LogHelper.setEvent("id00283",
                                    null,
                                    null,
                                    new LogExt(workflow),
                                    new Object[]{
                                            LabelUtils.getLabel(getProcessNameLabelId(processState.getStateId()), I18n.LOCALE_ENG),
                                            removedNamesOnStep.getKey(),
                                            resoltToSystemId
                                    });
                        }
                    });
                }
            }
        }

        // For the STATE_ID_EXEC phase we also need to shift Resolutions stages for cleared steps
        if (STATE_ID_EXEC == processState.getStateId() && !removedStepsIndexes.isEmpty()) {
            int startStep = removedStepsIndexes.get(0); // Min Step idx which was removed
            int shift = 0; // Step shift to be applied, increments with each removed Step

            // Iterate from first removed Step to the last one (according to the original number of Steps)
            // Visualization:
            // Orig:         0, 1, 2, 3, 4, 5
            // Removed:         1,    3, 4
            // Resulting:    0, 2, 5
            // ------------------------------
            // Approach:
            // iteration 1:  0, 1,          5
            // and so on
            for (int i = startStep; i < maxSteps; i++) {
                if (removedStepsIndexes.contains(i)) {
                    // With the removed step 3 considering removed steps [1, 3, 4] means that
                    // all subsequent steps has to be decreased by 1 (because indexOf(3) = 1) additionally
                    // to the standard decrease
                    shift = removedStepsIndexes.indexOf(i);
                }
                getWorkflowTaskDao().shiftAssignedTaskStep(workflow.getId(), STATE_ID_EXEC, i + 1, i - shift);
            }
        }
    }


    /**
     * Calculates step of the phase (state)<br>
     * For the fixed-route: all BPM-names are to be replaced with the actual(computed) target names of users (or groups where applicable)
     * Fixed-route definition: when no steps defined on the phase (state) - route is flexible, in case steps defined on the phase(state) - rout is fixed
     */
    private void calculateStep(
            final ProcessableDocument workflow,
            final Process process,
            final ProcessState processState,
            final ProcessStep processStep,
            boolean raiseOptinalBpmExceptions) {

        // Approve and Sign States require Groups to be extracted and added as users
        if (STATE_ID_APPROVE == processState.getStateId() || STATE_ID_SIGN == processState.getStateId()) {
            // Remove all assigned groups
            processStep.removeAllGroups();
        }

        // Start processing assigned users
        List<String> usersAndGroupsIdList = processStep.getNamesAll(); //we need to check as User as well as Groups/Units systemIds
        ArrayList<Pair<String, String>> replaceList = new ArrayList<>();

        // Resolve BPM names
        usersAndGroupsIdList.forEach(systemId -> {
            if (isBpmName(systemId)) {
                // Resolve target name, that should be used instead of bpmName
                String nameResolved = resolveBpmName(workflow, systemId, workflow.getAuthor(), true);
                if (nameResolved != null) {
                    DirectoryItem di = _directoryService.getDirectoryItem(nameResolved);
                    if (di.isDeleted()) {
                        // in case DirectoryItem is Deleted - remove it from the process
                        LogHelper.setEvent("id00169", null, di.getItemNameUI(), new LogExt(workflow).setLogAccount(DirectoryConstants.ELDOC_ROBOT));
                        nameResolved = null;
                    }
                }
                replaceList.add(Pair.of(systemId, nameResolved));
            }
        });

        // Check that names are available (e.g., user can be deleted)
        usersAndGroupsIdList.forEach(systemId -> {
            DirectoryItem di = _directoryService.getDirectoryItem(systemId);
            if (di.isDeleted()) {
                // in case DirectoryItem is Deleted - remove it from the process
                replaceList.add(Pair.of(systemId, null));
                LogHelper.setEvent("id00170", null, di.getItemNameUI(), new LogExt(workflow).setLogAccount(DirectoryConstants.ELDOC_ROBOT));
            }
        });

        // Replace BPM names with target names
        for (Pair<String, String> bpmNamePair : replaceList) {
            if (isNotBlank(bpmNamePair.getValue())) {
                // replace bpmName with calculated systemId
                processStep.replaceName(bpmNamePair.getKey(), bpmNamePair.getValue());
            } else {
                // If target name is not resolved - then just remove it from the process
                processStep.removeName(bpmNamePair.getKey());
                // In general this is normal behaviour but for some document types (like for frd1) it can be very critical
                if (raiseOptinalBpmExceptions) {
                    BpmNameItem bpmItem = new BpmNameItem(bpmNamePair.getKey());
                    throw new BpmNameNotResolvedException("BPM name [" + bpmItem.getItemNameUI() + "] can not be resolved").setSourceId(bpmNamePair.getKey());
                }
            }
        }
    }


    /**
     * Resolves BPM-names
     *
     * @param workflow
     * @param nameToBeResolved
     * @param initiatorId
     * @param logEvent because this method can be triggered several times per document creation this param helps to avoid duplicated logs
     * @return
     */
    private String resolveBpmName(final ProcessableDocument workflow, String nameToBeResolved, final String initiatorId, boolean logEvent) {
        String result = null;
        try {
            result = _directoryService.resolveBpmName(nameToBeResolved, initiatorId);
        } catch (BpmNameGroupByMemberAndTagNotFoundException bpmn2) {
            if (logEvent) {
                LogHelper.setEvent("id00162", null, "tag='" + bpmn2.getTag() + "'", new LogExt(workflow).setLogAccount(DirectoryConstants.ELDOC_ROBOT));
            }
        }
        return result;
    }


    /**
     * Updates document process based on the type of the document: standard Workflow (Form Dynamic) or RecoWorkflow (frd1 form)
     *
     * @param workflow
     * @param bpmParams
     * @return
     */
    public ProcessUpdateResult updateProcess(Workflow workflow, BpmParams bpmParams) {
        if (bpmParams == null) {
            // In order to avoid NPE - we init BpmParams with an empty object here (before first usage)
            bpmParams = new BpmParams();
        }

        if (bpmParams.getProperty(BPM_SKIP_PROCESS_UPDATE, false)) {
            return new ProcessUpdateResult(true);
        }

        if (workflow.isRecoWF()) {
            // Use the Recognition document process specific logic
            return updateProcessInnerReco(workflow, bpmParams);
        } else {
            // Use standard process logic
            return updateProcessInner(workflow, bpmParams);
        }
    }


    /**
     * Process update logic for recognition documents
     *
     * @param workflow {@link Workflow} that should be updated
     * @param bpmParams Additional parameters for the update process
     * @return Workflow update result
     */
    private ProcessUpdateResult updateProcessInnerReco(Workflow workflow, BpmParams bpmParams) {
        ProcessUpdateResult updProcResult = new ProcessUpdateResult();
        try {
            List<String> systemIdOnStepList = workflow.getProcessState().getStep(workflow.getStep()).getNamesAll(true);
            List<Integer> stepsRuleTSreturn = workflow.getProcessState().getStepsRuleNotAllowedTaskStatuses();

            boolean nextState = false; // Mark that Document has to be moved to the next State
            int nextStep = -1;

            LOG.trace("Workflow[{}] UpdateProcess->state={}; step={}; iteration={}; namesOnStep={}; ruleOnStep={}; ruleTsReturn={}",
                    workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), systemIdOnStepList, workflow.getProcessState().getStepsRule(), workflow.getProcessState().getStepsRuleNotAllowedTaskStatuses());

            //
            // Check the Recognition step
            //
            if (RECO_PROCESS_STEP_RECOBOT == workflow.getStep()) {
                String systemIdOnStep = DirectoryConstants.ELDOC_RECO_BOT;
                Integer respTS = getWorkflowTaskDao().getTaskStatusIfProcessed(workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), systemIdOnStep, null);

                LOG.trace("WorkflowReco[{}] UpdateProcess->WorkflowTask(systemId[{}]): taskStatus={}", workflow.getId(), systemIdOnStep, respTS);

                if (respTS != null) {
                    // Action is present - proceed with it
                    if (TASK_STATUS_DECLINE == respTS) {
                        // RecoDoc was declined by RecoBot
                        if (stepsRuleTSreturn.contains(respTS)) {
                            // Document has to be moved to the Reco Validation
                            nextStep = RECO_PROCESS_STEP_RECO_VALIDATOR;
                        } else {
                            // Mark that any action is allowed thus move document to the next phase in such case
                            nextState = true;
                        }
                    } else if (TASK_STATUS_DONE == respTS) {
                        // RecoDoc was processed by RecoBot
                        if (bpmParams.getProperty(IDP_RECO_REQUIRES_VALIDATION, false)) {
                            // Requires manual validation
                            nextStep = RECO_PROCESS_STEP_RECO_VALIDATOR;
                        } else if (bpmParams.getProperty(IDP_RECO_REQUIRES_CONVERSION, false)) {
                            // Requires conversion
                            nextStep = RECO_PROCESS_STEP_CONVERSIONBOT;
                        } else {
                            // Validation and conversion are not required - move to the next State
                            nextState = true;
                        }
                    }
                }
            }

            //
            // Check the Validation step (after recognition)
            //
            if (RECO_PROCESS_STEP_RECO_VALIDATOR == workflow.getStep()) {
                Integer respTS = null;
                for (String systemIdOnStep : systemIdOnStepList) {
                    // Get the resp Done TaskStatus for user (from array TASK_STATUS_LIST_COMPLETED)
                    respTS = getWorkflowTaskDao().getTaskStatusIfProcessed(workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), systemIdOnStep, null);

                    LOG.trace("WorkflowReco[{}] UpdateProcess->WorkflowTask(systemId[{}]): taskStatus={}", workflow.getId(), systemIdOnStep, respTS);

                    if (TASK_STATUS_LIST_COMPLETED.contains(respTS)) {
                        // We iterate until first hit to the executed statuses
                        break;
                    }
                }

                if (respTS != null) {
                    if (TASK_STATUS_DECLINE == respTS) {
                        // Document was Cancelled on the Validation after Recognition step
                        // According to the RecoDoc Process document should be placed in Archive with the Cancelled status
                        // This is handled in the Bean
                    } else if (TASK_STATUS_DONE == respTS) {
                        // Document was Validated on the Validation after Recognition step
                        if (bpmParams.getProperty(IDP_RECO_REQUIRES_CONVERSION, false)) {
                            // Requires conversion
                            nextStep = RECO_PROCESS_STEP_CONVERSIONBOT;
                        } else {
                            // Conversion is not required - move to the next State
                            nextState = true;
                        }
                    }
                }
            }

            //
            // Check the Conversion step
            //
            if (RECO_PROCESS_STEP_CONVERSIONBOT == workflow.getStep()) {
                String systemIdOnStep = DirectoryConstants.ELDOC_CONVERTER_BOT;
                Integer respTS = getWorkflowTaskDao().getTaskStatusIfProcessed(workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), systemIdOnStep, null);

                LOG.trace("WorkflowReco[{}] UpdateProcess->WorkflowTask(systemId[{}]): taskStatus={}", workflow.getId(), systemIdOnStep, respTS);

                if (respTS != null) {
                    // Action is present - proceed with it
                    if (TASK_STATUS_DECLINE == respTS) {
                        // RecoDoc was declined by ConversionBot
                        if (stepsRuleTSreturn.contains(respTS)) {
                            // Document has to be moved to the Reco Validation
                            nextStep = RECO_PROCESS_STEP_CONV_VALIDATOR;
                        } else {
                            // Mark that any action is allowed thus move document to the next phase in such case
                            nextState = true;
                        }
                    } else if (TASK_STATUS_DONE == respTS) {
                        // RecoDoc was processed by ConversionBot - then move it to the next phase
                        nextState = true;
                    }
                }
            }

            //
            // Check the Validation step (after conversion)
            //
            if (RECO_PROCESS_STEP_CONV_VALIDATOR == workflow.getStep()) {
                Integer respTS = null;
                for (String systemIdOnStep : systemIdOnStepList) {
                    // Get the resp Done TaskStatus for user (from array TASK_STATUS_LIST_COMPLETED)
                    respTS = getWorkflowTaskDao().getTaskStatusIfProcessed(workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), systemIdOnStep, null);

                    LOG.trace("WorkflowReco[{}] UpdateProcess->WorkflowTask(systemId[{}]): taskStatus={}", workflow.getId(), systemIdOnStep, respTS);

                    if (TASK_STATUS_LIST_COMPLETED.contains(respTS)) {
                        // We iterate until first hit to the executed statuses
                        break;
                    }
                }

                if (respTS != null) {
                    if (TASK_STATUS_DONE == respTS) {
                        // Document was Validated on the Validation after Conversion step
                        if (bpmParams.getProperty(IDP_RECO_REQUIRES_CONVERSION, false)) {
                            // Requires conversion
                            nextStep = RECO_PROCESS_STEP_CONVERSIONBOT;
                        } else {
                            // Conversion is not required - move to the next State
                            nextState = true;
                        }
                    }
                }
            }

            LOG.trace("WorkflowReco[{}] UpdateProcess Result: nextState={}; nextStep={}", workflow.getId(), nextState, nextStep);

            //
            // Move workflow accordingly
            //
            if (nextStep >= 0) {

                // In case we have identified that workflow to be moved to the nextStep
                updProcResult.setSuccess(proceedWithNextStep(workflow, nextStep));
                return updProcResult;

            } else if (nextState) {

                // Moving to the next state
                updProcResult.setSuccess(proceedWithNextState(workflow));
                return updProcResult;

            }

            updProcResult.setSuccess(true);
            return updProcResult;
        } catch (ConcurrentWriteException cwe1) {
            // This is to be re-thrown and handled by downstream code
            throw cwe1;
        } catch (Exception e) {
            LogHelper.setEvent("id00197", e, null, new LogExt(workflow));
            updProcResult.setSuccess(false);
            return updProcResult;
        }
    }


    /**
     * Process update logic for standard workflow
     *
     * @param workflow
     * @return
     */
    private ProcessUpdateResult updateProcessInner(Workflow workflow, BpmParams bpmParams) {
        ProcessUpdateResult updProcResult = new ProcessUpdateResult();
        try {
            List<String> systemIdOnStepList = workflow.getProcessState().getStep(workflow.getStep()).getNamesAll(true);

            LOG.trace("Workflow[{}] UpdateProcess->state={}; step={}; iteration={}; namesOnStep={}; ruleOnStep={}; ruleTsReturn={}",
                    workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), systemIdOnStepList, workflow.getProcessState().getStepsRule(), workflow.getProcessState().getStepsRuleNotAllowedTaskStatuses());

            // Collect WorkflowTask Reponse Statuses by Process participant
            Map<String, Integer> participantWorkflowTaskStatusMap = new HashMap<>();
            for (String systemIdOnStep : systemIdOnStepList) {
                // Get the resp Done TaskStatus for user (from array TASK_STATUS_LIST_COMPLETED)
                // This will also recurse into the delegation chain
                Integer userRespResult = getWorkflowTaskDao().getTaskStatusIfProcessed(workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), systemIdOnStep, null);
                participantWorkflowTaskStatusMap.put(systemIdOnStep, userRespResult);
                LOG.trace("Workflow[{}] UpdateProcess->WorkflowTask(systemId[{}]): taskStatus={}", workflow.getId(), systemIdOnStep, userRespResult);
            }

            // If no resp even from one single user, or resp not with taskStatus "Done Meaning" - we do not go to next step (means we still have pending participants on the step)
            boolean pendingParticipantOnStep = participantWorkflowTaskStatusMap.values().stream().anyMatch(taskStatus -> taskStatus == null || !TASK_STATUS_LIST_COMPLETED.contains(taskStatus));
            updProcResult.setNextStep(!pendingParticipantOnStep);

            // If taskStatus has "Done Meaning", lets check docRules for the returning doc to owner
            boolean responseRequiresReturnPresentOnStep = participantWorkflowTaskStatusMap.values().stream().anyMatch(taskStatus -> workflow.getProcessState().getStepsRuleNotAllowedTaskStatuses().contains(taskStatus));
            updProcResult.setReturnToInitiator(responseRequiresReturnPresentOnStep);

            LOG.trace("Workflow[{}] UpdateProcess Result: returnDoc={}; nextStep={}", workflow.getId(), updProcResult.isReturnToInitiator(), updProcResult.isNextStep());

            // In case returnDoc to owner is true
            if (updProcResult.isReturnToInitiator() && updProcResult.isNextStep()) { // we need to check for nextStep here in order to wait until all stepUsers provide responses
                updProcResult.setSuccess(interruptProcessAndReturnToInitiator(workflow));
                if (updProcResult.isSuccess()) {
                    _procUpdateEvent.fireAsync(new ProcessUpdateEvent(workflow, ProcessUpdateEvent.TYPE.RETURN_TO_INITIATOR, TenantContext.getCurrentTenantId()));
                }
                return updProcResult;
            }

            // In case nextStep is true (without returning doc to owner)
            if (!updProcResult.isReturnToInitiator() && updProcResult.isNextStep()) {
                if (workflow.getStep() < workflow.getProcessState().getSteps().size() - 1) {
                    // This is a next Step on the Process State
                    updProcResult.setSuccess(proceedWithNextStep(workflow));
                    if (updProcResult.isSuccess()) {
                        _procUpdateEvent.fireAsync(new ProcessUpdateEvent(workflow, ProcessUpdateEvent.TYPE.NEXT_STEP, TenantContext.getCurrentTenantId()));
                    }
                    return updProcResult;
                } else {
                    // This is a next State on the Process
                    updProcResult.setSuccess(proceedWithNextState(workflow));
                    if (updProcResult.isSuccess()) {
                        _procUpdateEvent.fireAsync(new ProcessUpdateEvent(workflow, ProcessUpdateEvent.TYPE.NEXT_STATE, TenantContext.getCurrentTenantId()));
                    }
                    return updProcResult;
                }
            }

            updProcResult.setSuccess(true);
            return updProcResult;
        } catch (Exception e) {
            LogHelper.setEvent("id00198", e, null, new LogExt(workflow));
            updProcResult.setSuccess(false);
            return updProcResult;
        }
    }


    private boolean interruptProcessAndReturnToInitiator(Workflow workflow) {
        final int logStep = workflow.getStep();
        workflow.setStep(0);
        workflow.setDateReceived(new Date());

        LogExt logExt = new LogExt(workflow).setLogAccount(DirectoryConstants.ELDOC_ROBOT);

        if (getWorkflowDao().updateOrCreate(null, workflow)) {
            logExt.setProcessTaskStatus(TASK_STATUS_PROCESS_INTERRUPT);
            logExt.setProcessStep(logStep);
            LogHelper.setEvent("id00136", null, null, logExt);
            return true;
        } else {
            LogHelper.setEvent("id00137", null, "Error saving workflow", logExt);
            return false;
        }
    }


    /**
     * Proceeds to the next step on the current Workflow Process Phase
     *
     * @param workflow
     * @return
     */
    private boolean proceedWithNextStep(Workflow workflow) {
        return proceedWithNextStep(workflow, -1);
    }


    /**
     * Proceeds to the next step on the current Workflow Process Phase
     *
     * @param workflow
     * @param nextStep next step to proceed with or -1 automatically compute the next step
     * @return
     */
    private boolean proceedWithNextStep(Workflow workflow, int nextStep) {
        LogExt logExt = new LogExt(workflow)
                .setLogAccount(DirectoryConstants.ELDOC_ROBOT)
                .setProcessStep(workflow.getStep());

        if (nextStep == -1) {
            nextStep = workflow.getStep() + 1;
        }
        workflow.setStep(nextStep);
        workflow.setDateReceived(new Date());

        if (getWorkflowDao().updateOrCreate(null, workflow)) {
            logExt.setProcessTaskStatus(TASK_STATUS_PROCESS_NEXT_STEP);
            LogHelper.setEvent("id00133", null, null, logExt);
            return true;
        } else {
            LogHelper.setEvent("id00092", null, "Error saving workflow", logExt);
            return false;
        }
    }


    private boolean proceedWithNextState(Workflow workflow) {
        final int logStep = workflow.getStep();

        Workflow workflowClone = SerializationUtils.clone(workflow); // We need a clone as during autoNext changes process-related fields of the original workflow object

        LOG.trace("Workflow[{}] UpdateProcess->isAutoNextState={}", workflow.getId(), workflow.getProcessState().isAutoNextState());
        if (workflow.getProcessState().isAutoNextState() && applyNextProcessState(workflow, workflow.getNextState())) {
            ClientSession dbClientSession = dbClientStartSession();
            try {
                workflow.setDateReceived(new Date());
                LogExt logExt = new LogExt(workflow).setLogAccount(DirectoryConstants.ELDOC_ROBOT);

                dbClientSession.startTransaction();
                if (!getWorkflowDao().updateOrCreate(dbClientSession, workflow)) {
                    LogHelper.setEvent("id00221", null, "Error saving workflow (#1)", logExt);
                    throw new WorkflowSaveException().setEntity(workflow);
                }

                int taskStatus = -1;
                if (STATE_ID_APPROVE == workflow.getState()) {
                    taskStatus = TASK_STATUS_TO_APPROVAL;
                } else if (STATE_ID_SIGN == workflow.getState()) {
                    taskStatus = TASK_STATUS_TO_SIGN;
                } else if (STATE_ID_EXEC == workflow.getState()) {
                    taskStatus = TASK_STATUS_TO_EXECUTION;
                } else if (STATE_ID_LIST_DYNAMIC.contains(workflow.getState())) {
                    taskStatus = TASK_STATUS_TO_WORK;
                } else if (STATE_ID_RPSTR == workflow.getState()) {
                    taskStatus = TASK_STATUS_TO_REPOSITORY;
                } else if (STATE_ID_ARCH == workflow.getState()) {
                    taskStatus = TASK_STATUS_TO_ARCHIVE;
                }

                WorkflowTask wfTask = getWorkflowService().workflowTaskCreateAndInitForInitiator(workflow, taskStatus, DirectoryConstants.ELDOC_ROBOT);
                if (List.of(TASK_STATUS_TO_APPROVAL, TASK_STATUS_TO_SIGN).contains(taskStatus) || STATE_ID_LIST_DYNAMIC.contains(workflow.getState())) {
                    if (!workflow.isRecoWF()) {
                        // Copy attachments to WorkflowTask, if current Workflow is not RecoWorkflow and not eSign Workflow
                        // Create copy of the originally present files (required only on the process phases where iterations are supported)
                        DaoUtils.copyFile(getWorkflowDao(), workflow, getWorkflowTaskDao(), wfTask);
                    }
                }

                if (!getWorkflowTaskDao().updateOrCreate(dbClientSession, wfTask)) {
                    throw new ElDocRuntimeException("Workflow execution error: automatic move to the next phase failed");
                }

                dbClientSession.commitTransaction();
                getWorkflowService().workflowLogStateStartAction(wfTask);
                LogHelper.setEvent("id00223", null, null, logExt);

                return true;
            } catch (Throwable e) {
                dbClientSession.abortTransaction();
                LogHelper.setEvent("id00171", e, null, new LogExt(workflow).setLogAccount(DirectoryConstants.ELDOC_ROBOT));
            } finally {
                dbClientSession.close();
            }
        }

        // AutoNextState was not requested or failed - then move document back to initiator
        workflowClone.setStep(0);
        workflowClone.setDateReceived(new Date());

        LogExt logExt = new LogExt(workflowClone).setLogAccount(DirectoryConstants.ELDOC_ROBOT);

        if (getWorkflowDao().updateOrCreate(null, workflowClone)) {
            logExt.setProcessStep(logStep);
            logExt.setProcessTaskStatus(TASK_STATUS_PROCESS_COMPLETED);
            LogHelper.setEvent("id00134", null, null, logExt);
            return true;
        } else {
            LogHelper.setEvent("id00093", null, "Error saving workflow (#2)", logExt);
            return false;
        }
    }


    /**
     * Moves document automatically to the next State
     */
    private boolean applyNextProcessState(Workflow workflow, int nextState) {
        // Does necessary checks and in case they passed - auto move workflow to the next Process Phase
        // Necessary checks:
        // - is next Process Phase has users (in case of Approve, Sign, Execute)
        try {
            if (nextState == -1) {
                return false; // Should not happen
            }

            if (workflow.getProcess().getStateIdOrderExceptDraftRepoArch().contains(nextState) && workflow.getProcess().getState(nextState).getNamesAll(false, false).isEmpty()) {
                // In case next State is one of the work states (not Repo or Arch) we need to check if any users are present on it and if no users present - perform the following check:
                if (workflow.getProcess().getState(workflow.getState()).isAutoNextStateSkipEmpty()) {
                    // Process allows to skip next state in case it is empty (no users assigned) - so we're trying to skip it and proceeding with a recursion
                    workflow.setState(nextState);
                    return applyNextProcessState(workflow, workflow.getNextState());
                } else {
                    // Process doesn't allow to proceed with the next state in case no users assigned to it - stop moving to the next state and exit
                    return false;
                }
            }

            // Perform Document Number automatic registration (autoReg) if necessary
            if (!doAutoReg(workflow, nextState)) {
                return false;
            }

            workflow.setState(nextState);
            workflow.setStatus(PROCESS_STATUS_NORMAL);
            workflow.setStep(1);
            workflow.setIteration(1);

            if (STATE_ID_EXEC == nextState) {
                workflow.setDateDue(null); // we do not use dateDue on dfStateExec, so clear it
            } else if (STATE_ID_RPSTR == nextState || STATE_ID_ARCH == nextState) {
                workflow.setStep(0);
                workflow.setDateCompleted(new Date());
            }

            return true;
        } catch (Exception e) {
            LogHelper.setEvent("id00114", e, "", new LogExt(workflow));
            return false;
        }
    }


    /**
     * Performs Document Number automatic registration (autoReg)
     *
     * @param workflow
     * @param nextState
     * @return
     */
    public boolean doAutoReg(Workflow workflow, Integer nextState) {
        // Check if document has to be autoRegistered
        if (!workflow.getSfDef().getNum().isAutoReg()) {
            return true; // nothing to do
        }

        if (nextState >= workflow.getSfDef().getNum().getAutoRegState()) {
            // As of v3.9.0 settings in SysForm are not connected with the actual process (as there are several processes could be assigned to
            // the single SysForm), and some process may simply miss some ProcessStates - we do check whether the nextState is state set for autoReg
            // or in case some State is missing - rigister with the next State after the target state (This relies on the current logic that all states are
            // ordered ASC based on their stateId)
            if (isBlank(workflow.getNum())) {
                // do autoReg only in case no DocNum was assigned earlier
                String docRegNum = _numSequenceDao.getValue(workflow.getForm(), true);

                if (isBlank(docRegNum)) {
                    LogHelper.setEvent("id00058", null, "nextState: " + String.valueOf(nextState), new LogExt(workflow));
                    return false;
                }

                workflow.setNum(docRegNum);
            }
        }

        return true;
    }


    /**
     * Optimizes process by removing persons who has already confirmed document on the current iteration
     * This method is to be called on the ToWorkAgain action (BEFORE toWorkAgain logic execution),
     * thus it is assumed that at least one iteration already passed
     *
     * @param workflow
     * @return
     * @throws BpmEmptyStateException in case no users left on the state
     */
    public boolean optimizeProcessRoute(Workflow workflow) throws BpmEmptyStateException {
        try {
            final String docId = workflow.getId();
            final int state = workflow.getState();
            final int checkIteration = workflow.getIteration();

            LOG.trace(">>> Begin optimizeProcess() diag <<<");

            ProcessState currentState = workflow.getProcessState();
            List<Integer> stepsRuleTSreturn = currentState.getStepsRuleNotAllowedTaskStatuses();

            if (LOG.isTraceEnabled()) {
                LOG.trace(">> rule on state: {}", currentState.getStepsRule());
                LOG.trace(">> rule tsReturn array: {}", stepsRuleTSreturn.toString());
            }

            // Starting checking the Steps from 1 as we do not need to check Initiator Step
            for (int stepIndex = 1; stepIndex < currentState.getSteps().size(); stepIndex++) {

                ProcessStep procStep = currentState.getSteps().get(stepIndex);
                List<String> namesOnStep = procStep.getNamesAll(true);

                if (LOG.isTraceEnabled()) {
                    LOG.trace(">>>>>> Step {0}", stepIndex);
                    LOG.trace(">> namesOnStep: {0}", namesOnStep.toString());
                }

                for (int n = 0; n < namesOnStep.size(); n++) {

                    String systemId = namesOnStep.get(n); // take the user's systemId for processing
                    boolean excludeName = false;

                    // Get the resp Done TaskStatus for user (from array TASK_STATUS_LIST_COMPLETED)
                    // we do not specifically define step assuming check for the whole previous iteration
                    Integer userRespResult = getWorkflowTaskDao().getTaskStatusIfProcessed(docId, state, null, checkIteration, systemId, null);

                    if (LOG.isTraceEnabled()) {
                        LOG.trace(">> name: {} taskStatus: {}", systemId, userRespResult);
                    }

                    if (userRespResult != null && TASK_STATUS_LIST_COMPLETED.contains(userRespResult)) {
                        // If Response from the previous iter is present and it is from the "Done Meaning" list - we continue this check
                        if (!stepsRuleTSreturn.contains(userRespResult)) {
                            // In case the result was "not to return for re-work", meaning user has agreed (approved or signed) the doc - we may exclude him
                            excludeName = true;
                        }
                    }

                    if (excludeName) {
                        LOG.trace(">> name: {} is to be excluded from the next iteration", systemId);
                        procStep.removeName(systemId);
                        if (!workflow.getAcl().getR().contains(systemId)) {
                            // in order to save access to the document for the user that gets removed - add him to the additional access list
                            workflow.getAcl().getR().add(systemId);
                        }
                    } else {
                        LOG.trace(">> name: {} is NOT to be excluded from the next iteration", systemId);
                    }
                }
            }

            // After adjusting the Process Step - clean empty Stages in case such occured after removing users
            currentState.removeEmptySteps();

            if (currentState.getNamesAll(false, true).isEmpty()) {
                throw new BpmEmptyStateException();
            }

            return true;
        } catch (BpmEmptyStateException bpe) {
            LOG.debug("No BPM Process State members left after process optimization");
            throw bpe;
        } catch (Exception e) {
            LOG.error(null, e);
            return false;
        }
    }


    /**
     * Re-inits EXEC State with Resolution WorkflowTask Documents available in the DB and
     * also adjusts Step number sequence of the Resolutions stored in the DB (be filling gaps in Step numbers and shifting resolutions)
     *
     * @param docId
     * @param process
     * @param resolutionList
     */
    public void initExecPhaseWithFollowupList(String docId, Process process, List<WorkflowTask> resolutionList) {
        LOG.trace("STATE_ID_EXEC is to be initialized with FollowUp WorkflowTasks");

        // First we need to delete all assigned before Resolutions (except Initiator Step)
        ProcessStep initiatorStep = process.getState(STATE_ID_EXEC).getSteps().get(0);
        process.getState(STATE_ID_EXEC).getSteps().clear();
        process.getState(STATE_ID_EXEC).getSteps().add(initiatorStep);
        LOG.trace("Steps on the STATE_ID_EXEC cleared except the Initiator Step");

        // Collect the unique Steps defined by end-user on the UI (the list is sorted ASC)
        LinkedHashSet<Integer> procStepNumberSet = resolutionList.stream().map(docResp -> docResp.getStep()).collect(Collectors.toCollection(LinkedHashSet::new));
        LOG.trace("FollowUp WorkflowTasks found for the following Steps: {0}", procStepNumberSet.toString());

        // Handle cases when some resolutions were removed from the middle, e.g.: procSteps = [1, 3, 5, 7],
        // but this logic adjusts it to the proper order: [1, 2, 3, 4]
        List<Integer> procStepNumberList = new ArrayList<>(procStepNumberSet);
        LinkedHashSet<Integer> lastFilledStepNumber = new LinkedHashSet<>();
        for (int i = 0; i < procStepNumberList.size(); i++) {
            int actualStepNumber = procStepNumberList.get(i);
            int targetNumber = i + 1; // Increment by one because numbers coming excluding Author step 0
            lastFilledStepNumber.add(targetNumber);

            // Compare actual number with target number and make necessary adjustments if required
            if (actualStepNumber != targetNumber) {
                // In case Step index doesn't equal element index - means we have skipped steps and need to fix it
                getWorkflowTaskDao().shiftAssignedTaskStep(
                        docId,
                        STATE_ID_EXEC,
                        actualStepNumber,
                        lastFilledStepNumber.stream().skip(lastFilledStepNumber.size() - 1).findFirst().get());
            }

            // Collect Resolution DocResponses for the UI-defined Step
            List<WorkflowTask> resolutionsForTargetStep = resolutionList.stream()
                    .filter(docResp -> Objects.equals(docResp.getStep(), actualStepNumber))
                    .collect(Collectors.toList());

            LOG.trace("Number of FollowUp WorkflowTasks found for the Step={} is: {} (to be added to the STATE_ID_EXEC Step: {})",
                    actualStepNumber, resolutionsForTargetStep.size(), targetNumber);

            final ProcessStep execProcStep = process.createStep();
            resolutionsForTargetStep.forEach(docRespResol -> addNameToExecStep(execProcStep, docRespResol.getResolutionTo()));
            process.getState(STATE_ID_EXEC).getSteps().add(targetNumber, execProcStep); // targetNumber is optional here just to show how it is related to Resolution WorkflowTask doc
        }
    }


    /**
     * Adds name to the EXEC State
     *
     * @param systemId
     */
    private void addNameToExecStep(ProcessStep execProcStep, String systemId) {
        DirectoryItem dirItem = _directoryService.getDirectoryItem(systemId);
        if (dirItem.isGroupItem()) {
            execProcStep.addGroup(systemId);
        } else {
            execProcStep.addUser(systemId);
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    protected ClientSession dbClientStartSession() {
        return _dbClientService.startSession(TenantContext.getCurrentTenantId());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public WorkflowDao getWorkflowDao() {
        return _workflowDao;
    }


    public WorkflowTaskDao getWorkflowTaskDao() {
        return _workflowTaskDao;
    }


    public WorkflowService getWorkflowService() {
        return _workflowService;
    }


    public static BpmEngine getInstance() {
        return CDI.current().select(BpmEngine.class).get();
    }
}
