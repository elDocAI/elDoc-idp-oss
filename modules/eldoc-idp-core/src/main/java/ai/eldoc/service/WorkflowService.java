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
package ai.eldoc.service;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.TenantContext;
import ai.eldoc.bpm.*;
import ai.eldoc.bpm.Process;
import ai.eldoc.callback.WorkflowBeforeSaveCallback;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.dao.*;
import ai.eldoc.data.*;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.model.directory.BpmNameItem;
import ai.eldoc.model.directory.SystemNameItem;
import ai.eldoc.model.filemeta.WorkflowFileMeta;
import ai.eldoc.util.I18n;
import ai.eldoc.util.LabelUtils;
import ai.eldoc.workflow.WorkflowAcl;
import com.mongodb.client.ClientSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static ai.eldoc.bpm.BpmAclConstants.*;
import static ai.eldoc.bpm.BpmConstants.*;
import static ai.eldoc.bpm.BpmParams.*;
import static ai.eldoc.workflow.WorkflowUtils.getProcessNameLabelId;
import static ai.eldoc.workflow.WorkflowUtils.getRespStatusName;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Workflow Service
 */
@ApplicationScoped
public class WorkflowService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowService.class);

    @Inject
    private DbClientService _dbClientService;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private WorkflowTaskDao _workflowTaskDao;

    @Inject
    private RecoWorkflowService _recoWorkflowService;

    @Inject
    private FormDefinitionService _formDefService;

    @Inject
    private BpmProcessDefinitionService _bpmProcessDefinitionService;

    @Inject
    private BpmEngine _bpmEngine;

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private WorkflowScriptingService _workflowScriptingService;

    @Inject
    private Event<ProcessUpdateEvent> _procUpdateEvent;


    //
    // Create Workflow related methods ------------------------------------------------------------
    //


    /**
     * Creates Workflow and stores it in the DB based on the given FormName and for the provided user systemId
     *
     * @param formName webId of the FormDefinition (e.g.: fd1, fd2, frd1), case-insensitive
     * @param aclContext author ACL Context
     * @return
     */
    public Workflow create(final String formName, final AclContext aclContext) {
        Workflow workflow = null;
        try {
            FormDefinition formDef = _formDefService.getByWebId(formName, false, aclContext.getSystemId());
            if (formDef == null) {
                // This can happen when user simply doesn't have access rights to the form (CAN_CREATE field)
                throw new FormDefinitionNotFoundException();
            }

            if (formDef.getProcessIds().isEmpty()) {
                throw new FormDefinitionHasNoAssignedBpmProcessesException();
            }

            List<BpmProcessDefinition> sysProcessList = _bpmProcessDefinitionService.getAllByIdList(formDef.getProcessIds().stream().toList());
            if (sysProcessList.isEmpty()) {
                throw new BpmProcessDefinitionNotFoundException();
            }

            // create new workflow
            workflow = createWithFormDef(formDef, aclContext);

            // Assign and calculate process
            configureWorkflowProcess(workflow, formDef);

            // in case FormDefinition contains doc templates - we need to copy them to the newly created document
            // IMPORTANT: workflow should be already saved in DB at this stage
            if (!formDef.getTemplates().isEmpty()) {
                // copy the templates configuration
                workflow.setTemplates(formDef.getTemplates());

                // replace original references in the template config with the references to the files copied to the Workflow GridFS
                for (TemplateRef templateRef : workflow.getTemplates()) {
                    DbFileRef dbFileRef = DaoUtils.copyFile(templateRef.getDbFileRef(), getWorkflowDao(), workflow);
                    getWorkflowDao().getFilesDao().updateFileMeta(null, dbFileRef, Map.of(WorkflowFileMeta._TYPE, WorkflowFileMeta.TYPE_DEFAULT));
                    templateRef.setDbFileRef(dbFileRef);
                }
            }
            // Perform Document Number automatic registration (autoReg) if necessary
            if (getBpmEngine().doAutoReg(workflow, STATE_ID_DRAFT) == false) {
                LogHelper.setEvent("id00159", null, null, new LogExt(workflow));
            }

            // Execute Post-create hook if required
            if (isNotBlank(formDef.getPostCreateHook())) {
                try {
                    _workflowScriptingService.executePostCreateHook(workflow, formDef.getPostCreateHookDecoded());
                } catch (TimeoutException e1) {
                    throw e1;
                } catch (Exception e) {
                    throw new ScriptingException("Post-create hook error", e);
                }
            }

            if (getWorkflowDao().updateOrCreate(null, workflow)) {
                LogHelper.setEvent("id00002", null, null, new LogExt(workflow));
            } else {
                throw new WorkflowSaveException().setEntity(workflow);
            }
            return workflow;
        } catch (FormDefinitionNotFoundException | FormDefinitionHasNoAssignedBpmProcessesException e1) {
            LogHelper.setEvent("id00157", e1, formName, new LogExt(workflow));
            throw e1;
        } catch (BpmNameNotResolvedException e4) {
            LogHelper.setEvent("id00183", e4, formName, new LogExt(workflow));
            throw e4;
        } catch (BpmProcessDefinitionNotFoundException e2) {
            LogHelper.setEvent("id00158", e2, formName, new LogExt(workflow));
            throw e2;
        } catch (DatabaseException e3) {
            // also includes WorkflowSaveException
            LogHelper.setEvent("id00001", e3, formName, new LogExt(workflow));
            throw e3;
        }
    }


    /**
     * Sets the BPM Process for the given Workflow based on FormDefinition configuration
     *
     * @param workflow {@link Workflow} that should be updated
     * @param formDef {@link FormDefinition} that contains configuration for the BPM Process
     */
    public void configureWorkflowProcess(Workflow workflow, FormDefinition formDef) {
        List<BpmProcessDefinition> sysProcessList = _bpmProcessDefinitionService.getAllByIdList(formDef.getProcessIds().stream().toList());
        if (sysProcessList.isEmpty()) {
            throw new BpmProcessDefinitionNotFoundException();
        }

        // Select which Process to use and assign it
        if (sysProcessList.size() == 1) {
            // this condition replicates previous behavior
            workflow.setProcess(sysProcessList.getFirst().getProcess());
            workflow.setProcessId(sysProcessList.getFirst().getId());
            workflow.setProcessName(sysProcessList.getFirst().getName());
        } else {
            // create a temp process consisting only of the Draft state
            workflow.setProcess(ProcessUtils.createProcessWithDraftState());
        }

        // Here we need to do the first save so document will get ObjectId and in case it contains Fixed Route for EXEC state
        // Resolutions will be added to the given ObjectId
        if (!getWorkflowDao().updateOrCreate(null, workflow)) {
            LogHelper.setEvent("id00003", formDef.getFormId());
            throw new WorkflowSaveException().setEntity(workflow);
        }

        // Calculate BPM Process
        getBpmEngine().calculateProcess(workflow, false);
    }


    /**
     * Creates Workflow model (in-memory only, not stored in DB) with the provided arguments
     *
     * @param aclContext author ACL Context
     * @return
     */
    private Workflow createDefault(final AclContext aclContext) {
        Workflow workflow = new Workflow(aclContext.getSystemId(), Set.of(DirectoryConstants.ROLE_ADMINDOCRW), Set.of(DirectoryConstants.ROLE_ADMINDOCR));

        if (isNotBlank(aclContext.getSystemIdOwn())) {
            // Author Done By is not empty - add him to the document as well
            workflow.setAuthorBy(aclContext.getSystemIdOwn());
            // And also provide read access, so user who created this document on behalf of someone else
            // is able to see this doc in MyDocs view as well. This is particurarly useful for using with API,
            // so service account can get access to documents created by it
            if (!SystemNameItem.isSystemName(aclContext.getSystemIdOwn())) {
                // Check that this is not a system account and add it to ACL.R
                workflow.getAcl().getR().add(aclContext.getSystemIdOwn());
            }
        }

        return workflow;
    }


    /**
     * Creates Workflow based on given FormDefinition and inits it as Draft
     *
     * @param formDef FormDefinition to be used for the document creation
     * @param aclContext Author ACL Context
     * @return
     */
    public Workflow createWithFormDef(final FormDefinition formDef, final AclContext aclContext) {
        Workflow workflow = createDefault(aclContext);

        workflow.setForm(formDef.getFormId());
        workflow.setType(formDef.getType()); // remember system type of the form (dinamic or static)

        if (formDef.isTypeDynamic()) {
            // if dynamic form: add config for dynamic fields
            workflow.setSfDef(formDef.getSfDef());
            workflow.setCfDef(formDef.getCfDef()); // custom fields data
        } else {
            // if static form: compute specific static form with form-fields declaration in libForms
            // currently uses the same fields, particurarly form frd1
            workflow.setSfDef(formDef.getSfDef());
            workflow.setCfDef(formDef.getCfDef()); // custom fields data
        }

        workflow.setLayoutType(formDef.getLayoutType()); // Set Form Layout Type

        // Transfer Workflow Title for the initialized languages from FormDefinition to the target Workflow
        I18n.LANG_SYSTEM_CODES_ALL.forEach(langSysCode -> {
            if (isNotBlank(formDef.getName().get(langSysCode))) {
                workflow.setNameByLang(langSysCode, formDef.getName().get(langSysCode));
            } else {
                workflow.setNameByLang(langSysCode, formDef.getFormId() + "[" + langSysCode + "]");
            }
        });

        // provide additional access to the document
        List<String> defaultReadAccessLst = new ArrayList<>();
        formDef.getAccessRead().forEach(defaultReaderAccessSystemId -> {
            if (BpmNameItem.isBpmName(defaultReaderAccessSystemId)) {
                try {
                    String resolvedName = _directoryService.resolveBpmName(defaultReaderAccessSystemId, aclContext.getSystemId());
                    defaultReadAccessLst.add(resolvedName);
                } catch (BpmNameNotResolvedException bpmn1) {
                    // nothing to do, just skip adding it
                }
            } else {
                defaultReadAccessLst.add(defaultReaderAccessSystemId);
            }
        });
        workflow.getAcl().getR().addAll(defaultReadAccessLst);

        // init it as Draft
        workflow.setState(STATE_ID_DRAFT);
        workflow.setStep(0);
        workflow.setIteration(0);
        workflow.setStatus(PROCESS_STATUS_NORMAL);

        return workflow;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    public void uploadFromStreamToWorkflow(Workflow workflow, InputStream is, String fileName, String contentType, long fileSize) {
        WorkflowFileMeta fileMeta = new WorkflowFileMeta();
        fileMeta.setType(WorkflowFileMeta.TYPE_DEFAULT);
        DbFileRef uploadedFileRef = _workflowDao.getFilesDao().uploadFromStream(fileName, is, contentType, fileMeta.toMetadata());
        workflow.getFiles().add(uploadedFileRef);
        _workflowDao.updateOrCreate(null, workflow);
    }


    /**
     * Sends workflow to the APPROVAL STATE (updates workflow in DB as well)
     * Automatically adds author WorkflowTask (WorkflowTask[author] is set based on the Workflow[author])
     *
     * @param workflow Workflow to be processed
     * @param aclContext of the user account who executes the action
     * @param userRequestMeta of the user account who executes the action
     * @param beforeSaveCallback Callback to be executed before saving Workflow
     * @throws BpmEmptyStateException in case no users left on the state after process optimization
     */
    public void actionToApproveState(final Workflow workflow, final AclContext aclContext, UserRequestMeta userRequestMeta, final WorkflowBeforeSaveCallback beforeSaveCallback) throws BpmEmptyStateException {
        ClientSession dbClientSession = dbClientStartSession();
        try {
            dbClientSession.startTransaction();

            // Check if any Names exist on State
            if (workflow.getProcessStateNamesAll(STATE_ID_APPROVE, false, false).isEmpty()) {
                throw new BpmEmptyStateException();
            }

            // Perform Document Number automatic registration (autoReg) if necessary
            if (!getBpmEngine().doAutoReg(workflow, STATE_ID_APPROVE)) {
                throw new WorkflowAutoRegistrationException().setEntity(workflow);
            }

            workflow.setState(STATE_ID_APPROVE);
            workflow.setStep(1);
            workflow.setIteration(1);
            workflow.setStatus(PROCESS_STATUS_NORMAL);
            workflow.setDateReceived(new Date());

            WorkflowTask wfTask = workflowTaskCreateAndInitForInitiator(workflow, TASK_STATUS_TO_APPROVAL, aclContext.getSystemIdOwn());
            wfTask.apply(userRequestMeta);

            if (!workflow.isRecoWF()) {
                // Create copy of the originally present files (required only on the process phases where iterations are supported), if current Workflow is not RecoWorkflow and not eSign Workflow
                DaoUtils.copyFile(getWorkflowDao(), workflow, getWorkflowTaskDao(), wfTask);
            }

            if (beforeSaveCallback != null) {
                beforeSaveCallback.apply(workflow, wfTask);
            }

            if (!getWorkflowDao().updateOrCreate(dbClientSession, workflow)) {
                throw new WorkflowSaveException().setEntity(workflow);
            }

            if (!getWorkflowTaskDao().updateOrCreate(dbClientSession, wfTask)) {
                LogHelper.setEvent("id00084", null, null, new LogExt(workflow));
                throw new ElDocRuntimeException("[id00084] Workflow execution error");
            }

            dbClientSession.commitTransaction();
            workflowLogStateStartAction(wfTask);
        } catch (DatabaseException e1) {
            dbClientSession.abortTransaction();
            LogHelper.setEvent("id00011", e1, null, new LogExt(workflow));
            throw e1;
        } catch (Throwable thr1) {
            dbClientSession.abortTransaction();
            throw thr1;
        } finally {
            dbClientSession.close();
        }
    }


    /**
     * Sends workflow to the SIGN STATE (updates workflow in DB as well)
     * Automatically adds author WorkflowTask (WorkflowTask[author] is set based on the Workflow[author])
     *
     * @param workflow Workflow to be processed
     * @param aclContext of the user account who executes the action
     * @param userRequestMeta user request details like userAgent and userIP
     * @param beforeSaveCallback Callback to be executed before saving Workflow
     * @throws BpmEmptyStateException in case no users left on the state after process optimization
     */
    public void actionToSignState(final Workflow workflow, final AclContext aclContext, UserRequestMeta userRequestMeta, final WorkflowBeforeSaveCallback beforeSaveCallback) throws BpmEmptyStateException {
        ClientSession dbClientSession = dbClientStartSession();
        try {
            dbClientSession.startTransaction();

            // Check if any Names exist on State
            if (workflow.getProcessStateNamesAll(STATE_ID_SIGN, false, false).isEmpty()) {
                throw new BpmEmptyStateException();
            }

            // Perform Document Number automatic registration (autoReg) if necessary
            if (!getBpmEngine().doAutoReg(workflow, STATE_ID_SIGN)) {
                throw new WorkflowAutoRegistrationException().setEntity(workflow);
            }

            WorkflowTask wfTask = workflowTaskCreateAndInitForInitiator(workflow, TASK_STATUS_TO_SIGN, aclContext.getSystemIdOwn());
            wfTask.apply(userRequestMeta);

            if (!workflow.isRecoWF()) {
                // Create copy of the originally present files (required only on the process phases where iterations are supported), if current Workflow is not RecoWorkflow and not eSign Workflow
                DaoUtils.copyFile(getWorkflowDao(), workflow, getWorkflowTaskDao(), wfTask);
            }

            if (beforeSaveCallback != null) {
                beforeSaveCallback.apply(workflow, wfTask);
            }

            if (!getWorkflowDao().updateOrCreate(dbClientSession, workflow)) {
                throw new WorkflowSaveException().setEntity(workflow);
            }

            if (!getWorkflowTaskDao().updateOrCreate(dbClientSession, wfTask)) {
                LogHelper.setEvent("id00029", null, null, new LogExt(workflow));
                throw new ElDocRuntimeException("[id00029] Workflow execution error");
            }

            dbClientSession.commitTransaction();
            workflowLogStateStartAction(wfTask);
        } catch (DatabaseException e1) {
            dbClientSession.abortTransaction();
            LogHelper.setEvent("id00030", e1, null, new LogExt(workflow));
            throw e1;
        } catch (Throwable thr1) {
            dbClientSession.abortTransaction();
            throw thr1;
        } finally {
            dbClientSession.close();
        }
    }


    /**
     * Sends workflow to the EXEC STATE (updates workflow in DB as well)
     * Automatically adds author WorkflowTask (WorkflowTask[author] is set based on the Workflow[author])
     *
     * @param workflow Workflow to be processed
     * @param aclContext of the user account who executes the action
     * @param userRequestMeta user request details like userAgent and userIP
     * @param beforeSaveCallback Callback to be executed before saving Workflow
     * @throws BpmEmptyStateException in case no users left on the state after process optimization
     */
    public void actionToExecState(final Workflow workflow, final AclContext aclContext, UserRequestMeta userRequestMeta, final WorkflowBeforeSaveCallback beforeSaveCallback) throws BpmEmptyStateException {
        ClientSession dbClientSession = dbClientStartSession();
        try {
            dbClientSession.startTransaction();

            // Check if any Names exist on State
            if (workflow.getProcessStateNamesAll(STATE_ID_EXEC, false, false).isEmpty()) {
                throw new BpmEmptyStateException();
            }

            // Perform Document Number automatic registration (autoReg) if necessary
            if (!getBpmEngine().doAutoReg(workflow, STATE_ID_EXEC)) {
                throw new WorkflowAutoRegistrationException().setEntity(workflow);
            }

            // Process Template files
            workflow.setState(STATE_ID_EXEC);
            workflow.setStep(1);
            workflow.setIteration(1);
            workflow.setStatus(PROCESS_STATUS_NORMAL);
            workflow.setDateDue(null); // we do not use dateDue on dfStateExec, so clear it
            workflow.setDateReceived(new Date());

            WorkflowTask wfTask = workflowTaskCreateAndInitForInitiator(workflow, TASK_STATUS_TO_EXECUTION, aclContext.getSystemIdOwn());
            wfTask.apply(userRequestMeta);

            if (beforeSaveCallback != null) {
                beforeSaveCallback.apply(workflow, wfTask);
            }

            if (!getWorkflowDao().updateOrCreate(dbClientSession, workflow)) {
                throw new WorkflowSaveException().setEntity(workflow);
            }

            if (!getWorkflowTaskDao().updateOrCreate(dbClientSession, wfTask)) {
                LogHelper.setEvent("id00062", null, null, new LogExt(workflow));
                throw new ElDocRuntimeException("[id00062] Failed to execute workflow");
            }

            dbClientSession.commitTransaction();
            workflowLogStateStartAction(wfTask);
        } catch (DatabaseException e1) {
            dbClientSession.abortTransaction();
            LogHelper.setEvent("id00059", e1, null, new LogExt(workflow));
            throw e1;
        } catch (Throwable thr1) {
            dbClientSession.abortTransaction();
            throw thr1;
        } finally {
            dbClientSession.close();
        }
    }


    /**
     * Sends workflow to the REPO STATE (updates workflow in DB as well)
     * Automatically adds author WorkflowTask (WorkflowTask[author] is set based on the Workflow[author])
     *
     * @param workflow Workflow to be processed
     * @param aclContext of the user account who executes the action
     * @param userRequestMeta user request details like userAgent and userIP
     * @param bpmParams
     * @param beforeSaveCallback Callback to be executed before saving Workflow
     */
    public void actionToRepoState(final Workflow workflow, final AclContext aclContext, UserRequestMeta userRequestMeta, BpmParams bpmParams, final WorkflowBeforeSaveCallback beforeSaveCallback) {
        ClientSession dbClientSession = dbClientStartSession();
        try {
            dbClientSession.startTransaction();

            if (bpmParams == null) {
                // In order to avoid NPE in the upstream methods - we init BpmParams with an empty object (before first usage)
                bpmParams = new BpmParams();
            }

            // Perform Document Number automatic registration (autoReg) if necessary
            if (!getBpmEngine().doAutoReg(workflow, STATE_ID_RPSTR)) {
                throw new WorkflowAutoRegistrationException().setEntity(workflow);
            }

            workflow.setState(STATE_ID_RPSTR);
            workflow.setStep(0); // we need to make sure it is set to step 0 (Initiator step) as RecoWorkflow can be sent to archive from different steps
            workflow.setIteration(1);
            workflow.setStatus(bpmParams.getProperty(BPM_STATUS, PROCESS_STATUS_NORMAL));
            workflow.setDateReceived(new Date());
            workflow.setDateCompleted(new Date());

            WorkflowTask wfTask = workflowTaskCreateAndInitForInitiator(workflow, TASK_STATUS_TO_REPOSITORY, aclContext.getSystemIdOwn());
            wfTask.apply(userRequestMeta);

            if (beforeSaveCallback != null) {
                beforeSaveCallback.apply(workflow, wfTask);
            }

            if (!getWorkflowDao().updateOrCreate(dbClientSession, workflow)) {
                throw new WorkflowSaveException().setEntity(workflow);
            }

            if (!getWorkflowTaskDao().updateOrCreate(dbClientSession, wfTask)) {
                LogHelper.setEvent("id00225", null, null, new LogExt(workflow));
                throw new ElDocRuntimeException("[id00225] Workflow execution error");
            }

            dbClientSession.commitTransaction();
        } catch (DatabaseException e1) {
            dbClientSession.abortTransaction();
            LogHelper.setEvent("id00226", e1, e1.getMessage(), new LogExt(workflow));
            throw e1;
        } catch (Throwable thr1) {
            dbClientSession.abortTransaction();
            throw thr1;
        } finally {
            dbClientSession.close();
        }
    }


    /**
     * Sends workflow to the ARCHIVE STATE (updates workflow in DB as well)
     * Automatically adds author WorkflowTask (WorkflowTask[author] is set based on the Workflow[author])
     *
     * @param workflow Workflow to be processed
     * @param aclContext of the user account who executes the action
     * @param userRequestMeta user request details like userAgent and userIP
     * @param bpmParams
     * @param beforeSaveCallback Callback to be executed before saving Workflow
     */
    public void actionToArchiveState(final Workflow workflow, final AclContext aclContext, UserRequestMeta userRequestMeta, BpmParams bpmParams, final WorkflowBeforeSaveCallback beforeSaveCallback) {
        ClientSession dbClientSession = dbClientStartSession();
        try {
            dbClientSession.startTransaction();

            if (bpmParams == null) {
                // In order to avoid NPE in the upstream methods - we init BpmParams with an empty object (before first usage)
                bpmParams = new BpmParams();
            }

            // Perform Document Number automatic registration (autoReg) if necessary
            if (!getBpmEngine().doAutoReg(workflow, STATE_ID_ARCH)) {
                throw new WorkflowAutoRegistrationException().setEntity(workflow);
            }

            workflow.setState(STATE_ID_ARCH);
            workflow.setStep(0); // we need to make sure it is set to step 0 (Initiator step) as RecoWorkflow can be sent to archive from different steps
            workflow.setIteration(1);
            workflow.setStatus(bpmParams.getProperty(BPM_STATUS, PROCESS_STATUS_NORMAL));
            workflow.setDateReceived(new Date());
            workflow.setDateCompleted(new Date());

            WorkflowTask wfTask = workflowTaskCreateAndInitForInitiator(workflow, TASK_STATUS_TO_ARCHIVE, aclContext.getSystemIdOwn());
            wfTask.apply(userRequestMeta);

            if (beforeSaveCallback != null) {
                beforeSaveCallback.apply(workflow, wfTask);
            }

            if (!getWorkflowDao().updateOrCreate(dbClientSession, workflow)) {
                throw new WorkflowSaveException().setEntity(workflow);
            }

            if (!getWorkflowTaskDao().updateOrCreate(dbClientSession, wfTask)) {
                LogHelper.setEvent("id00024", null, null, new LogExt(workflow));
                throw new ElDocRuntimeException("[id00024] Workflow execution error");
            }

            dbClientSession.commitTransaction();
        } catch (DatabaseException e1) {
            dbClientSession.abortTransaction();
            LogHelper.setEvent("id00025", e1, e1.getMessage(), new LogExt(workflow));
            throw e1;
        } catch (Throwable thr1) {
            dbClientSession.abortTransaction();
            throw thr1;
        } finally {
            dbClientSession.close();
        }
    }


    /**
     * Sends workflow to the next Process State (updates workflow in DB as well)
     * Automatically adds author WorkflowTask (WorkflowTask[author] is set based on the Workflow[author])
     *
     * @param workflow Workflow to be processed
     * @param aclContext of the user account who executes the action
     * @param userRequestMeta user request details like userAgent and userIP
     * @param beforeSaveCallback Callback to be executed before saving Workflow
     * @throws BpmEmptyStateException in case no users left on the state after process optimization
     */
    public void actionToWork(final Workflow workflow, final AclContext aclContext, UserRequestMeta userRequestMeta, final WorkflowBeforeSaveCallback beforeSaveCallback) throws BpmEmptyStateException {
        ClientSession dbClientSession = dbClientStartSession();
        try {
            dbClientSession.startTransaction();

            final int nextState = workflow.getNextState();

            if (nextState == -1) {
                throw new ElDocRuntimeException("Next Process State not available");
            }

            // Check if any Names exist on State (applicable for all work Process States except REPO and ARCH)
            if (workflow.getProcess().getStateIdOrderExceptDraftRepoArch().contains(nextState) && workflow.getProcess().getState(nextState).getNamesAll(false, false).isEmpty()) {
                throw new BpmEmptyStateException();
            }

            // Perform Document Number automatic registration (autoReg) if necessary
            if (!getBpmEngine().doAutoReg(workflow, nextState)) {
                throw new WorkflowAutoRegistrationException().setEntity(workflow);
            }

            workflow.setState(nextState);
            workflow.setStep(1);
            workflow.setIteration(1);
            workflow.setStatus(PROCESS_STATUS_NORMAL);
            workflow.setDateReceived(new Date());
            workflow.setDateLastActivity(new Date());

            WorkflowTask wfTask = workflowTaskCreateAndInitForInitiator(workflow, TASK_STATUS_TO_WORK, aclContext.getSystemIdOwn());
            wfTask.apply(userRequestMeta);

            if (List.of(TASK_STATUS_TO_APPROVAL, TASK_STATUS_TO_SIGN).contains(wfTask.getTaskStatus()) || STATE_ID_LIST_DYNAMIC.contains(workflow.getState())) {
                if (!workflow.isRecoWF()) {
                    // Create copy of the originally present files (required only on the process phases where iterations are supported), if current Workflow is not RecoWorkflow and not eSign Workflow
                    DaoUtils.copyFile(getWorkflowDao(), workflow, getWorkflowTaskDao(), wfTask);
                }
            }

            if (beforeSaveCallback != null) {
                beforeSaveCallback.apply(workflow, wfTask);
            }

            if (!getWorkflowDao().updateOrCreate(dbClientSession, workflow)) {
                throw new WorkflowSaveException().setEntity(workflow);
            }

            if (!getWorkflowTaskDao().updateOrCreate(dbClientSession, wfTask)) {
                LogHelper.setEvent("id00095", null, null, new LogExt(workflow));
                throw new ElDocRuntimeException("[id00095] Workflow execution error");
            }

            dbClientSession.commitTransaction();
            workflowLogStateStartAction(wfTask);
            _procUpdateEvent.fireAsync(new ProcessUpdateEvent(workflow, ProcessUpdateEvent.TYPE.NEXT_STATE, TenantContext.getCurrentTenantId()));
            _procUpdateEvent.fireAsync(new ProcessUpdateEvent(workflow, ProcessUpdateEvent.TYPE.NEXT_STEP, TenantContext.getCurrentTenantId()));
        } catch (DatabaseException e1) {
            dbClientSession.abortTransaction();
            LogHelper.setEvent("id00094", e1, null, new LogExt(workflow));
            throw e1;
        } catch (Throwable thr1) {
            dbClientSession.abortTransaction();
            throw thr1;
        } finally {
            dbClientSession.close();
        }
    }


    /**
     * Sends workflow to work again (by incrementing the Iteration number)
     *
     * @param workflow
     * @param aclContext of the user account who executes the action
     * @param userRequestMeta user request details like userAgent and userIP
     * @param bpmParams
     * @param beforeSaveCallback Callback to be executed before saving Workflow
     * @throws BpmEmptyStateException in case no users left on the state after process optimization
     */
    public void actionToWorkAgain(final Workflow workflow, final AclContext aclContext, UserRequestMeta userRequestMeta, BpmParams bpmParams, final WorkflowBeforeSaveCallback beforeSaveCallback) throws BpmEmptyStateException {
        ClientSession dbClientSession = dbClientStartSession();
        try {
            dbClientSession.startTransaction();

            if (bpmParams == null) {
                // In order to avoid NPE in the upstream methods - we init BpmParams with an empty object (before first usage)
                bpmParams = new BpmParams();
            }

            // Check if we need to optimize process by removing persons who has already approved the doc
            if (workflow.getProcessState().isOptimizeRoute()) {
                getBpmEngine().optimizeProcessRoute(workflow);
            }

            workflow.setStep(bpmParams.getProperty(BPM_STEP, 1));
            workflow.setIteration(workflow.getIteration() + 1);
            workflow.setDateReceived(new Date());

            WorkflowTask wfTask = workflowTaskCreateAndInitForInitiator(workflow, TASK_STATUS_TO_WORK_AGAIN, aclContext.getSystemIdOwn());
            wfTask.apply(userRequestMeta);

            if (List.of(TASK_STATUS_TO_APPROVAL, TASK_STATUS_TO_SIGN).contains(wfTask.getTaskStatus()) || STATE_ID_LIST_DYNAMIC.contains(workflow.getState())) {
                if (!workflow.isRecoWF()) {
                    // Create copy of the originally present files (required only on the process phases where iterations are supported), if current Workflow is not RecoWorkflow and not eSign Workflow
                    DaoUtils.copyFile(getWorkflowDao(), workflow, getWorkflowTaskDao(), wfTask);
                }
            }

            if (beforeSaveCallback != null) {
                beforeSaveCallback.apply(workflow, wfTask);
            }

            if (!getWorkflowDao().updateOrCreate(dbClientSession, workflow)) {
                LogHelper.setEvent("id00020", null, null, new LogExt(workflow));
                throw new WorkflowSaveException().setEntity(workflow);
            }

            if (!getWorkflowTaskDao().updateOrCreate(dbClientSession, wfTask)) {
                LogHelper.setEvent("id00019", null, null, new LogExt(workflow));
                throw new ElDocRuntimeException("[id00019] Workflow execution error");
            }

            dbClientSession.commitTransaction();
            workflowLogStateStartAction(wfTask);
        } catch (Throwable thr1) {
            dbClientSession.abortTransaction();
            throw thr1;
        } finally {
            dbClientSession.close();
        }
    }


    /**
     * Sends workflow to the State requested by the given taskStatus in case requested state is available
     * and allowed by the document ACL
     *
     * @param workflow
     * @param taskStatus
     * @param aclContext of the user account who executes the action
     * @param userRequestMeta user request details like userAgent and userIP
     * @param beforeSaveCallback Callback to be executed before saving Workflow
     * @return TRUE in case workflow could be successfully moved to the requested state, FALSE - otherwise
     */
    public boolean actionToStateByTaskStatus(final Workflow workflow, final int taskStatus, final AclContext aclContext, UserRequestMeta userRequestMeta, final WorkflowBeforeSaveCallback beforeSaveCallback) {
        Set<String> docACL = getWorkflowACL(workflow, aclContext);

        switch (taskStatus) {

            case TASK_STATUS_TO_APPROVAL: {
                if (!docACL.contains(ACTION_TO_APPROVAL)) {
                    LogHelper.setEvent("id00345",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getProcessNameLabelId(STATE_ID_APPROVE), I18n.LOCALE_ENG)});
                    return false;
                }
                actionToApproveState(workflow, aclContext, userRequestMeta, beforeSaveCallback);
                return true;
            }

            case TASK_STATUS_TO_SIGN: {
                if (!docACL.contains(ACTION_TO_SIGN)) {
                    LogHelper.setEvent("id00346",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getProcessNameLabelId(STATE_ID_SIGN), I18n.LOCALE_ENG)});
                    return false;
                }
                actionToSignState(workflow, aclContext, userRequestMeta, beforeSaveCallback);
                return true;
            }

            case TASK_STATUS_TO_EXECUTION: {
                if (!docACL.contains(ACTION_TO_EXEC)) {
                    LogHelper.setEvent("id00347",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getProcessNameLabelId(STATE_ID_EXEC), I18n.LOCALE_ENG)});
                    return false;
                }
                actionToExecState(workflow, aclContext, userRequestMeta, beforeSaveCallback);
                return true;
            }

            case TASK_STATUS_TO_REPOSITORY: {
                if (!docACL.contains(ACTION_TO_REPO)) {
                    LogHelper.setEvent("id00348",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getProcessNameLabelId(STATE_ID_RPSTR), I18n.LOCALE_ENG)});
                    return false;
                }
                actionToRepoState(workflow, aclContext, userRequestMeta, null, beforeSaveCallback);
                return true;
            }

            case TASK_STATUS_TO_ARCHIVE: {
                if (!docACL.contains(ACTION_TO_ARCH)) {
                    LogHelper.setEvent("id00349",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getProcessNameLabelId(STATE_ID_ARCH), I18n.LOCALE_ENG)});
                    return false;
                }
                actionToArchiveState(workflow, aclContext, userRequestMeta, null, beforeSaveCallback);
                return true;
            }

            case TASK_STATUS_TO_WORK_AGAIN: {
                if (!docACL.contains(ACTION_TO_WORK_AGAIN)) {
                    LogHelper.setEvent("id00343",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getProcessNameLabelId(workflow.getState()), I18n.LOCALE_ENG)});
                    return false;
                }
                actionToWorkAgain(workflow, aclContext, userRequestMeta, null, beforeSaveCallback);
                return true;
            }

            default: {
                LogHelper.setEvent("id00344",
                        null,
                        "taskStatus=" + taskStatus,
                        new LogExt(workflow));
                return false;
            }
        }
    }


    //
    // Workflow Actions by Participants methods ---------------------------------------------------
    //


    /**
     * Performs requested author's or admin's Action based on the given TaskStatus by checking if action is allowed by the document ACL
     *
     * @param workflow
     * @param taskStatus
     * @param aclContext of the user account who executes the action
     * @param userRequestMeta user request meta like userAgent and userIP
     * @return TRUE in case action could be performed, FALSE - otherwise
     */
    public boolean actionByTaskStatus(final Workflow workflow, int taskStatus, AclContext aclContext, UserRequestMeta userRequestMeta) {
        Set<String> docACL = getWorkflowACL(workflow, aclContext);

        switch (taskStatus) {

            case TASK_STATUS_SUSPEND: {
                if (!docACL.contains(ACTION_SUSPEND)) {
                    LogHelper.setEvent("id00341",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getRespStatusName(TASK_STATUS_SUSPEND), I18n.LOCALE_ENG)});
                    return false;
                }
                actionSuspend(workflow, aclContext.getSystemId(), aclContext.getSystemIdOwn(), userRequestMeta);
                return true;
            }

            case TASK_STATUS_TERMINATE: {
                if (!docACL.contains(ACTION_TERMINATE)) {
                    LogHelper.setEvent("id00340",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getRespStatusName(TASK_STATUS_TERMINATE), I18n.LOCALE_ENG)});
                    return false;
                }
                actionTerminate(workflow, aclContext.getSystemId(), aclContext.getSystemIdOwn(), userRequestMeta);
                return true;
            }

            case TASK_STATUS_BLOCK: {
                if (!docACL.contains(ACTION_BLOCK)) {
                    LogHelper.setEvent("id00339",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getRespStatusName(TASK_STATUS_BLOCK), I18n.LOCALE_ENG)});
                    return false;
                }
                actionBlock(workflow, aclContext.getSystemId(), aclContext.getSystemIdOwn(), userRequestMeta);
                return true;
            }

            case TASK_STATUS_RESUME: {
                if (docACL.contains(ACTION_RESUME)) {
                    actionResume(workflow, aclContext.getSystemId(), aclContext.getSystemIdOwn(), userRequestMeta);
                    return true;
                } else if (docACL.contains(ACTION_UNBLOCK)) {
                    actionUnBlock(workflow, aclContext.getSystemId(), aclContext.getSystemIdOwn(), userRequestMeta);
                    return true;
                } else {
                    LogHelper.setEvent("id00338",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getRespStatusName(TASK_STATUS_RESUME), I18n.LOCALE_ENG)});
                    return false;
                }
            }

            case TASK_STATUS_DELETE: {
                // Step 1: in case document is not allowed for deletion but allowed for blocking (and not blocked yet) - we need to block it first
                if (!docACL.contains(ACTION_DELETE)
                        && docACL.contains(ACTION_BLOCK)
                        && PROCESS_STATUS_BLOCKED != workflow.getStatus()) {
                    // Block document
                    actionBlock(workflow, aclContext.getSystemId(), aclContext.getSystemIdOwn(), userRequestMeta);
                    // Refresh ACL after blocking the document
                    docACL = getWorkflowACL(workflow, aclContext);
                }

                // Step 2: check that document allows deletion
                if (!docACL.contains(ACTION_DELETE)) {
                    LogHelper.setEvent("id00337",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getRespStatusName(TASK_STATUS_DELETE), I18n.LOCALE_ENG)});
                    return false;
                }

                // Step 3: delete document in case delete action was allowed
                actionDelete(workflow, aclContext.getSystemId(), aclContext.getSystemIdOwn(), userRequestMeta);
                return true;
            }

            default: {
                LogHelper.setEvent("id00342",
                        null,
                        "taskStatus=" + taskStatus,
                        new LogExt(workflow));
                return false;
            }
        }
    }


    /**
     * Performs requested workflow participant Action based on the given TaskStatus (by checking if action is allowed by the document ACL).
     * Also saves the WorkflowTask and updates document process
     *
     * @param workflow
     * @param wfTask
     * @param taskStatus
     * @param aclContext
     * @param userRequestMeta user request details like userAgent and userIP
     * @return TRUE in case action could be performed, FALSE - otherwise
     */
    public boolean actionByTaskStatus(final Workflow workflow, final WorkflowTask wfTask, int taskStatus, AclContext aclContext, UserRequestMeta userRequestMeta) {
        Set<String> docACL = getWorkflowACL(workflow, aclContext);

        switch (taskStatus) {

            case TASK_STATUS_DONE: {
                if (!docACL.contains(ACTION_DONE)) {
                    LogHelper.setEvent("id00331",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getRespStatusName(TASK_STATUS_DONE), I18n.LOCALE_ENG)});
                    return false;
                }
                actionDone(workflow, wfTask, aclContext, userRequestMeta, null);
                return true;
            }

            case TASK_STATUS_DONE_W_COMMENTS: {
                if (!docACL.contains(ACTION_DONE_W_COMMENTS)) {
                    LogHelper.setEvent("id00332",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getRespStatusName(TASK_STATUS_DONE_W_COMMENTS), I18n.LOCALE_ENG)});
                    return false;
                }
                actionDoneWithComments(workflow, wfTask, aclContext, null);
                return true;
            }

            case TASK_STATUS_DECLINE: {
                if (!docACL.contains(ACTION_DECLINE)) {
                    LogHelper.setEvent("id00335",
                            null,
                            null,
                            new LogExt(workflow),
                            new Object[]{LabelUtils.getLabel(getRespStatusName(TASK_STATUS_DECLINE), I18n.LOCALE_ENG)});
                    return false;
                }
                actionDecline(workflow, wfTask, aclContext, userRequestMeta, null);
                return true;
            }

            default: {
                LogHelper.setEvent("id00336",
                        null,
                        "taskStatus=" + taskStatus,
                        new LogExt(workflow));
                return false;
            }
        }
    }


    /**
     * Suspends Workflow processing, saves it in DB, and creates respective WorkflowTask which is also saved in the DB
     *
     * @param workflow
     * @param systemId
     * @param doneBySystemId
     * @param userRequestMeta user request details like userAgent and userIP
     * @return WorkflowTask with suspend action (saved in the DB)
     */
    public WorkflowTask actionSuspend(final Workflow workflow, final String systemId, final String doneBySystemId, UserRequestMeta userRequestMeta) {
        workflow.setStatus(PROCESS_STATUS_SUSPENDED);
        workflow.setDateLastActivity(new Date());
        // workflow.setValue(Workflow.PRPRTY_DATE_INCOME, new Date());
        if (getWorkflowDao().updateOrCreate(null, workflow)) {
            WorkflowTask wfTask = WorkflowTask.createFor(workflow, systemId);
            wfTask.setTaskStatus(TASK_STATUS_SUSPEND);
            wfTask.setAuthorBy(doneBySystemId);
            wfTask.apply(userRequestMeta);

            if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
                LogHelper.setEvent("id00065", null, null, new LogExt(wfTask));
                return wfTask;
            } else {
                LogHelper.setEvent("id00066", null, null, new LogExt(wfTask));
                throw new WorkflowTaskSaveException().setEntity(wfTask);
            }
        } else {
            LogHelper.setEvent("id00067", null, null, new LogExt(workflow));
            throw new WorkflowSaveException().setEntity(workflow);
        }
    }


    /**
     * Resumes Workflow processing, saves it in DB, and creates respective WorkflowTask which is also saved in the DB
     *
     * @param workflow
     * @param systemId
     * @param doneBySystemId
     * @param userRequestMeta user request details like userAgent and userIP
     * @return
     */
    public WorkflowTask actionResume(final Workflow workflow, final String systemId, final String doneBySystemId, UserRequestMeta userRequestMeta) {
        workflow.setStatus(PROCESS_STATUS_NORMAL);
        workflow.setDateLastActivity(new Date());
        //_currentWF.setValue(Workflow.PRPRTY_DATE_INCOME, new Date());

        if (!getWorkflowDao().updateOrCreate(null, workflow)) {
            LogHelper.setEvent("id00071", null, null, new LogExt(workflow));
            throw new WorkflowSaveException().setEntity(workflow);
        }

        WorkflowTask wfTask = WorkflowTask.createFor(workflow, systemId);
        wfTask.setTaskStatus(TASK_STATUS_RESUME);
        wfTask.setAuthorBy(doneBySystemId);
        wfTask.apply(userRequestMeta);

        if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
            LogHelper.setEvent("id00069", null, null, new LogExt(wfTask));
        } else {
            LogHelper.setEvent("id00070", null, null, new LogExt(wfTask));
            throw new WorkflowTaskSaveException().setEntity(wfTask);
        }

        // In case somebody gave resp while Workflow was suspended - updateDocProcess
        getBpmEngine().updateProcess(workflow, null);

        return wfTask;
    }


    /**
     * Performs pre-delegation validation whether the given user is allowed to be used for delegation
     * This method is mandatory to call before calling the {@link #actionDelegate } method and make sure it returns {@code true}
     *
     * @param workflow
     * @param delegateTo
     * @return {@code true} in case of allowed delegation or {@code false} in case delegation is not allowed to be done for the given user
     */
    public boolean actionDelegateIsAllowed(final Workflow workflow, final String delegateTo) {
        return !(workflow.getProcessStateNamesAll(workflow.getState(), true, true).contains(delegateTo)
                || getWorkflowTaskDao().count(workflow.getId(), workflow.getState(), null, workflow.getIteration(), TASK_STATUS_LIST_PROCESSED, delegateTo) > 0);
    }


    /**
     * Performs Workflow delegation logic. All entities: Workflow, WorkflowTask and WorkflowTask (which is created for the delegated person) are saved in the DB.
     * IMPORTANT: Before calling this method it is mandatory to call {@link #actionDelegateIsAllowed(Workflow, String)} method and make sure it returns {@code true}
     *
     * @param workflow Workflow to be delegated
     * @param wfTask WorkflowTask for the current action
     * @param systemId SystemId of the current user (or user on behalf of whom current user acts)
     * @param doneBySystemId SystemId of current user
     * @param delegateTo SystemId of the user to whom the Workflow is to be delegated
     * @param userRequestMeta user request details like userAgent and userIP
     * @return WorkflowTask created for the delegated user
     */
    public WorkflowTask actionDelegate(final Workflow workflow, final WorkflowTask wfTask, final String systemId, final String doneBySystemId, final String delegateTo, UserRequestMeta userRequestMeta) {
        wfTask.setDelegatedTo(delegateTo);
        wfTask.setTaskStatus(TASK_STATUS_DELEGATE_OUT);
        wfTask.setAuthorBy(doneBySystemId);
        wfTask.apply(userRequestMeta);

        // Create new workDocResp for delegation
        WorkflowTask delegatedWfTask = WorkflowTask.createFor(workflow, wfTask.getDelegatedTo());
        delegatedWfTask.setDelegatedFrom(wfTask.getAuthor());
        delegatedWfTask.setState(wfTask.getState());
        delegatedWfTask.setStep(wfTask.getStep());
        delegatedWfTask.setIteration(wfTask.getIteration());
        delegatedWfTask.setTaskStatus(TASK_STATUS_DELEGATED_IN);
        delegatedWfTask.setStatus(wfTask.getStatus());

        workflow.addDelegatedUser(wfTask.getDelegatedTo());

        if (getWorkflowDao().updateOrCreate(null, workflow)) {
            if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
                if (getWorkflowTaskDao().updateOrCreate(null, delegatedWfTask)) {
                    LogHelper.setEvent("id00053", null, null, new LogExt(wfTask));
                    return delegatedWfTask;
                } else {
                    LogHelper.setEvent("id00048");
                    throw new WorkflowTaskSaveException().setEntity(delegatedWfTask);
                }
            } else {
                LogHelper.setEvent("id00049");
                throw new WorkflowTaskSaveException().setEntity(wfTask);
            }
        } else {
            LogHelper.setEvent("id00054");
            throw new WorkflowSaveException().setEntity(workflow);
        }
    }


    /**
     * Executes action Sign
     *
     * @param workflow Workflow Model
     * @param wfTask WorkflowTask Model
     * @param aclContext
     * @param userRequestMeta user request details like userAgent and userIP
     * @param bpmParams BpmParams (can be null)
     * @return
     */
    public WorkflowTask actionDone(final Workflow workflow, final WorkflowTask wfTask, final AclContext aclContext, UserRequestMeta userRequestMeta, BpmParams bpmParams) {
        wfTask.setTaskStatus(TASK_STATUS_DONE);
        wfTask.setState(workflow.getState());
        wfTask.setStatus(workflow.getStatus());
        wfTask.setStep(workflow.getStep());
        wfTask.setIteration(workflow.getIteration());
        wfTask.setAuthorBy(aclContext.getSystemIdOwn());
        wfTask.apply(userRequestMeta);

        LogExt logExt = new LogExt(wfTask).setLogAccount(wfTask.getAuthor());

        if (bpmParams == null) {
            bpmParams = new BpmParams();
        }
        bpmParams.setProperty(USER_SYSTEM_ID, aclContext.getSystemId());

        if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
            LogHelper.setEvent("id00146", null, null, logExt);
            getBpmEngine().updateProcess(workflow, bpmParams);
        } else {
            LogHelper.setEvent("id00147", null, null, logExt);
            throw new WorkflowTaskSaveException().setEntity(wfTask);
        }

        // Required to be set explicitly, as on actionDone BpmEngine.updateProcess may skip updating Workflow itself, i.e. if there is pending participants
        // on current step
        getWorkflowDao().updateDateLastActivity(null, workflow);
        return wfTask;
    }


    /**
     * Executes action ConfirmEdit
     *
     * @param workflow Workflow Model
     * @param wfTask WorkflowTask Model
     * @param aclContext
     * @param bpmParams BpmParams (can be null)
     * @return
     */
    public WorkflowTask actionDoneWithComments(final Workflow workflow, final WorkflowTask wfTask, final AclContext aclContext, BpmParams bpmParams) {
        wfTask.setTaskStatus(TASK_STATUS_DONE_W_COMMENTS);
        wfTask.setState(workflow.getState());
        wfTask.setStatus(workflow.getStatus());
        wfTask.setStep(workflow.getStep());
        wfTask.setIteration(workflow.getIteration());
        wfTask.setAuthorBy(aclContext.getSystemIdOwn());

        LogExt logExt = new LogExt(wfTask).setLogAccount(wfTask.getAuthor());

        if (bpmParams == null) {
            bpmParams = new BpmParams();
        }
        bpmParams.setProperty(USER_SYSTEM_ID, aclContext.getSystemId());

        if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
            LogHelper.setEvent("id00038", null, null, logExt);
            getBpmEngine().updateProcess(workflow, bpmParams);
        } else {
            LogHelper.setEvent("id00039", null, null, logExt);
            throw new WorkflowTaskSaveException().setEntity(wfTask);
        }

        // Required to be set explicitly, as on actionDone BpmEngine.updateProcess may skip updating Workflow itself, i.e. if there is pending participants
        // on current step
        getWorkflowDao().updateDateLastActivity(null, workflow);
        return wfTask;
    }


    /**
     * Executes action Decline
     *
     * @param workflow Workflow Model
     * @param wfTask WorkflowTask Model
     * @param aclContext
     * @param userRequestMeta user request details like userAgent and userIP
     * @param bpmParams BpmParams (can be null)
     * @return
     */
    public WorkflowTask actionDecline(final Workflow workflow, final WorkflowTask wfTask, final AclContext aclContext, UserRequestMeta userRequestMeta, BpmParams bpmParams) {
        wfTask.setTaskStatus(TASK_STATUS_DECLINE);
        wfTask.setState(workflow.getState());
        wfTask.setStatus(workflow.getStatus());
        wfTask.setStep(workflow.getStep());
        wfTask.setIteration(workflow.getIteration());
        wfTask.setAuthorBy(aclContext.getSystemIdOwn());
        wfTask.apply(userRequestMeta);

        LogExt logExt = new LogExt(wfTask).setLogAccount(wfTask.getAuthor());

        if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
            LogHelper.setEvent("id00041", null, null, logExt);
            getBpmEngine().updateProcess(workflow, bpmParams);
        } else {
            LogHelper.setEvent("id00042", null, null, logExt);
            throw new WorkflowTaskSaveException().setEntity(wfTask);
        }

        return wfTask;
    }


    /**
     * Executes action Decline
     *
     * @param workflow Workflow Model
     * @param wfTask WorkflowTask Model
     * @param aclContext
     * @param userRequestMeta user request details like userAgent and userIP
     * @param bpmParams BpmParams (can be null)
     * @return
     */
    public WorkflowTask actionReturnToInitiator(final Workflow workflow, final WorkflowTask wfTask, final AclContext aclContext, UserRequestMeta userRequestMeta, BpmParams bpmParams) {
        wfTask.setTaskStatus(TASK_STATUS_RETURN_TO_INITIATOR);
        wfTask.setState(workflow.getState());
        wfTask.setStatus(workflow.getStatus());
        wfTask.setStep(workflow.getStep());
        wfTask.setIteration(workflow.getIteration());
        wfTask.setAuthorBy(aclContext.getSystemIdOwn());
        wfTask.apply(userRequestMeta);

        LogExt logExt = new LogExt(wfTask).setLogAccount(wfTask.getAuthor());

        if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
            LogHelper.setEvent("id00412", null, null, logExt);
            workflow.setStep(0);
            if (!updateOrCreate(workflow)) {
                LogHelper.setEvent("id00414", null, null, new LogExt(workflow));
                throw new WorkflowSaveException().setEntity(workflow);
            }
        } else {
            LogHelper.setEvent("id00413", null, null, logExt);
            throw new WorkflowTaskSaveException().setEntity(wfTask);
        }

        return wfTask;
    }


    /**
     * Deletes Workflow and all its WorkflowTasks (including all attachments with revisions) from the DB
     *
     * @param workflow {@link Workflow} that should be deleted
     * @param systemId SystemId of the current user (or user on behalf of whom the current user acts)
     * @param doneBySystemId SystemId of current user
     * @param userRequestMeta user request details like userAgent and userIP
     */
    public void actionDelete(final Workflow workflow, final String systemId, final String doneBySystemId, UserRequestMeta userRequestMeta) {

        // Find all WorkflowTasks to be able to properly send notifications after Workflow is deleted
        List<WorkflowTask> workflowTasks = getWorkflowTaskDao().findAll(workflow.getId(), null, null, null, List.of(TASK_STATUS_ASSIGNMENT), workflow.getAuthor());

        LogExt logExt = new LogExt(workflow).setProcessTaskStatus(TASK_STATUS_DELETE);
        logExt.apply(userRequestMeta);

        if (!getWorkflowTaskDao().delete(null, workflow.getId())) {
            LogHelper.setEvent("id00181", null, "_id:" + workflow.getId(), logExt);
            throw new WorkflowDeleteException("Can not delete WorkflowTasks for pId=" + workflow.getId());
        }

        if (workflow.isRecoWF()) {
            _recoWorkflowService.deleteRecoData(null, workflow);
        }

        if (!getWorkflowDao().delete(null, workflow)) {
            LogHelper.setEvent("id00006", null, "_id:" + workflow.getId(), logExt);
            throw new WorkflowDeleteException().setEntity(workflow);
        }

        LogHelper.setEvent("id00180", null, "_id:" + workflow.getId(), logExt);
    }


    /**
     * Blocks document and creates respective WorkflowTask action (saved in the DB)
     *
     * @param workflow {@link Workflow} that should be blocked
     * @param systemId SystemId of the current user (or user on behalf of whom the current user acts)
     * @param doneBySystemId SystemId of current user
     * @param userRequestMeta user request details like userAgent and userIP
     * @return Respective WorkflowTask action (saved in the DB)
     */
    public WorkflowTask actionBlock(final Workflow workflow, final String systemId, final String doneBySystemId, UserRequestMeta userRequestMeta) {
        workflow.setStatus(PROCESS_STATUS_BLOCKED);
        if (!getWorkflowDao().updateOrCreate(null, workflow)) {
            LogHelper.setEvent("id00175", null, null, new LogExt(workflow));
            throw new WorkflowSaveException().setEntity(workflow);
        }

        WorkflowTask wfTask = WorkflowTask.createFor(workflow, systemId);
        wfTask.setTaskStatus(TASK_STATUS_BLOCK);
        wfTask.setAuthorBy(doneBySystemId);
        wfTask.apply(userRequestMeta);

        if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
            LogHelper.setEvent("id00173", null, null, new LogExt(wfTask));
            return wfTask;
        } else {
            LogHelper.setEvent("id00174", null, null, new LogExt(wfTask));
            throw new WorkflowTaskSaveException().setEntity(wfTask);
        }
    }


    /**
     * Un-Blocks the document and creates the respective WorkflowTask action (saved in the DB)
     *
     * @param workflow {@link Workflow} that should be unblocked
     * @param systemId SystemId of the current user (or user on behalf of whom the current user acts)
     * @param doneBySystemId SystemId of current user
     * @param userRequestMeta user request details like userAgent and userIP
     * @return Respective WorkflowTask action (saved in the DB)
     */
    public WorkflowTask actionUnBlock(final Workflow workflow, final String systemId, final String doneBySystemId, UserRequestMeta userRequestMeta) {
        // Get last status of the document (terminated or normal)
        List<WorkflowTask> statusChangeWfTasks = getWorkflowTaskDao().findAll(workflow.getId(), null, null, null, List.of(TASK_STATUS_TERMINATE, TASK_STATUS_SUSPEND, TASK_STATUS_RESUME), null, true);
        int newStatus = PROCESS_STATUS_NORMAL;
        if (!statusChangeWfTasks.isEmpty()) {
            WorkflowTask lastWfTask = statusChangeWfTasks.get(statusChangeWfTasks.size() - 1);
            if (lastWfTask.getTaskStatus() == TASK_STATUS_TERMINATE) {
                // Document was canceled before blocking - restore in canceled state
                newStatus = PROCESS_STATUS_TERMINATED;
            } else if (lastWfTask.getTaskStatus() == TASK_STATUS_SUSPEND) {
                // Document was suspended before blocking - restore in suspended state
                newStatus = PROCESS_STATUS_SUSPENDED;
            } else {
                newStatus = lastWfTask.getStatus();
            }
        }
        workflow.setStatus(newStatus); // check/test ACL in case you change target status on unBlock
        workflow.setDateLastActivity(new Date());
        if (!getWorkflowDao().updateOrCreate(null, workflow)) {
            LogHelper.setEvent("id00179", null, null, new LogExt(workflow));
            throw new WorkflowSaveException().setEntity(workflow);
        }

        WorkflowTask wfTask = WorkflowTask.createFor(workflow, systemId);
        wfTask.setTaskStatus(TASK_STATUS_RESUME);
        wfTask.setAuthorBy(doneBySystemId);
        wfTask.apply(userRequestMeta);

        if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
            LogHelper.setEvent("id00177", null, null, new LogExt(wfTask));
            return wfTask;
        } else {
            LogHelper.setEvent("id00178", null, null, new LogExt(wfTask));
            throw new WorkflowTaskSaveException().setEntity(wfTask);
        }
    }


    /**
     * Cancels document and creates respective WorkflowTask action (saved in the DB)
     *
     * @param workflow {@link Workflow} that should be terminated
     * @param systemId SystemId of the current user (or user on behalf of whom the current user acts)
     * @param doneBySystemId SystemId of current user
     * @param userRequestMeta user request details like userAgent and userIP
     * @return Respective WorkflowTask action (saved in the DB)
     */
    public WorkflowTask actionTerminate(final Workflow workflow, final String systemId, final String doneBySystemId, UserRequestMeta userRequestMeta) {
        String logAccount = (doneBySystemId == null ? systemId : doneBySystemId);
        workflow.setStatus(PROCESS_STATUS_TERMINATED);
        workflow.setDateLastActivity(new Date());
        if (!getWorkflowDao().updateOrCreate(null, workflow)) {
            LogHelper.setEvent("id00075", null, null, new LogExt(workflow).setLogAccount(logAccount));
            throw new WorkflowSaveException().setEntity(workflow);
        }

        WorkflowTask wfTask = WorkflowTask.createFor(workflow, systemId);
        wfTask.setTaskStatus(TASK_STATUS_TERMINATE);
        wfTask.setAuthorBy(doneBySystemId);
        wfTask.apply(userRequestMeta);

        if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
            LogHelper.setEvent("id00073", null, null, new LogExt(wfTask).setLogAccount(logAccount));
            return wfTask;
        } else {
            LogHelper.setEvent("id00074", null, null, new LogExt(wfTask).setLogAccount(logAccount));
            throw new WorkflowTaskSaveException().setEntity(wfTask);
        }
    }


    /**
     * Returns Workflow to the initiator step, saves it in DB, and creates the respective WorkflowTask, which is also saved in the DB
     *
     * @param workflow Workflow that needs to be returned to the initiator step
     * @param aclContext AclContext of the user performing the action
     * @param userRequestMeta user request details like userAgent and userIP
     * @return WorkflowTask with suspend action (saved in the DB)
     */
    public WorkflowTask actionReturnToInitiator(final Workflow workflow, final AclContext aclContext, UserRequestMeta userRequestMeta) {
        WorkflowTask wfTask = WorkflowTask.createFor(workflow, aclContext.getSystemId()); // We need to track the step on which this action was taken
        workflow.setStep(0);
        if (_workflowDao.updateOrCreate(null, workflow)) {
            wfTask.setStatus(TASK_STATUS_RETURN_TO_INITIATOR);
            wfTask.setAuthorBy(aclContext.getSystemIdOwn());
            wfTask.apply(userRequestMeta);

            if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
                LogHelper.setEvent("id00412", null, null, new LogExt(wfTask));
                return wfTask;
            } else {
                LogHelper.setEvent("id00413", null, null, new LogExt(wfTask));
                throw new WorkflowTaskSaveException().setEntity(wfTask);
            }
        } else {
            LogHelper.setEvent("id00414", null, null, new LogExt(workflow));
            throw new WorkflowSaveException().setEntity(workflow);
        }
    }


    //
    // Genral Workflow related methods ------------------------------------------------------------
    //


    /**
     * Calculates ACL for the given Workflow and given User systemId
     *
     * @param workflow
     * @param aclContext
     * @return
     */
    public Set<String> getWorkflowACL(final Workflow workflow, final AclContext aclContext) {
        // excludeACLcheck = true to allow users that don't have access to the FormDefinition (due to lack of ACL rights)
        // to be able to open such documents sent to them for processing
        FormDefinition formDef = _formDefService.getByWebId(workflow.getForm(), true, null);

        // Returns the ACL for user actions (allowed user actions on the current workflow)
        // systemId of the User for whom to calculate ACL
        return new WorkflowAcl(_directoryService, getWorkflowTaskDao()).get(workflow, formDef, aclContext);
    }


    /**
     * Adds additional step and resolution to the ongoing Exec Process
     *
     * @param workflow
     * @param systemId
     * @param doneByName
     * @param taskToSystemId
     * @param taskContent
     */
    public void addUserToOngoingFollowupProcess(final Workflow workflow, final String systemId, final String doneByName, final String taskToSystemId, String taskContent) {
        Process process = workflow.getProcess();
        ProcessStep execStep = process.createStep();
        execStep.addUser(taskToSystemId);
        process.getState(STATE_ID_EXEC).getSteps().add(execStep);
        int stepIdx = process.getState(STATE_ID_EXEC).getSteps().size() - 1; // calculate the index of the Step on which the newly added User will appear

        if (getWorkflowDao().updateOrCreate(null, workflow)) {
            // Create WorkflowTask (Resolution)
            WorkflowTask wfTaskFollowup = WorkflowTask.createFollowupFromTo(workflow.getId(), systemId, taskToSystemId);
            wfTaskFollowup.setStep(stepIdx);
            wfTaskFollowup.setAuthorBy(doneByName);
            wfTaskFollowup.setNote(taskContent);
            getWorkflowTaskDao().create(null, wfTaskFollowup);

            // We also have to update the initial WorkflowTask with the ProcBackup for the current document State and Iteration as Monitoring Data will be using it
            workflowTaskUpdateWithProcBackup(workflow);
        } else {
            LogHelper.setEvent("", null, "Can not save Workflow", new LogExt(workflow));
            throw new WorkflowSaveException().setEntity(workflow);
        }
    }


    /**
     * Checks whether user or any of his groups present on the given State and Step of the Process
     *
     * @param workflow
     * @param state
     * @param step
     * @param userGroupsUnitsAndSystemId
     * @return
     */
    public static boolean isNameExistOnStateStep(Workflow workflow, int state, int step, Set<String> userGroupsUnitsAndSystemId) {
        try {
            List<String> namesOnStateStep = workflow.getProcess().getState(state).getStep(step).getNamesAll();
            return CollectionUtils.containsAny(namesOnStateStep, userGroupsUnitsAndSystemId);
        } catch (Exception e) {
            LogHelper.setEvent("id00245", e, e.toString(), new LogExt(workflow));
        }
        return false;
    }


    /**
     * Checks, if Workflow with provided ID exists.<br>
     * <b>IMPORTANT:</b> this method performs check in full access mode
     *
     * @param id
     * @return
     */
    public boolean isWorkflowExists(String id) {
        return getWorkflowDao().find(id, _directoryService.getAclContext(DirectoryConstants.ELDOC_ROBOT).toFullAccessMode()) != null;
    }


    /**
     * Performs update of the WorkflowTask which contains ProcBackup in case Document Process was dynamically updated
     *
     * @param workflow
     * @return
     */
    public boolean workflowTaskUpdateWithProcBackup(Workflow workflow) {
        WorkflowTask wfTaskWithProcBackup = getWorkflowTaskDao().findProcBackup(workflow.getId(), workflow.getState(), workflow.getIteration());
        wfTaskWithProcBackup.setProcess(workflow.getProcess());
        return getWorkflowTaskDao().updateOrCreate(null, wfTaskWithProcBackup);
    }


    /**
     * Logs action when Workflow moved to the next Process State or State restarted
     *
     * @param wfTask
     */
    public void workflowLogStateStartAction(WorkflowTask wfTask) {
        String eventId;
        if (STATE_ID_LIST.contains(wfTask.getState())) {
            // Handling for fixed Process States
            switch (wfTask.getTaskStatus()) {
                case TASK_STATUS_TO_APPROVAL -> eventId = "id00010";
                case TASK_STATUS_TO_SIGN -> eventId = "id00028";
                case TASK_STATUS_TO_EXECUTION -> eventId = "id00061";
                case TASK_STATUS_TO_REPOSITORY -> eventId = "id00224";
                case TASK_STATUS_TO_ARCHIVE -> eventId = "id00023";
                case TASK_STATUS_TO_WORK_AGAIN -> eventId = "id00018";
                default -> throw new ElDocRuntimeException("Unknown WorkflowTask Status: " + wfTask.getTaskStatus());
            }
        } else if (STATE_ID_LIST_DYNAMIC.contains(wfTask.getState())) {
            // Handling for fixed Process States
            switch (wfTask.getTaskStatus()) {
                case TASK_STATUS_TO_WORK -> eventId = "id00096";
                case TASK_STATUS_TO_WORK_AGAIN -> eventId = "id00018";
                case TASK_STATUS_TO_REPOSITORY -> eventId = "id00224";
                case TASK_STATUS_TO_ARCHIVE -> eventId = "id00023";
                default -> throw new ElDocRuntimeException("Unknown WorkflowTask Status: " + wfTask.getTaskStatus());
            }
        } else {
            eventId = ""; // Should not happen
        }
        LogHelper.setEvent(eventId, null, null, new LogExt(wfTask).setLogAccount(wfTask.getAuthor()).setLogAccountBy(wfTask.getAuthorBy()));
    }


    //
    // Workflow Task related methods --------------------------------------------------------------
    //


    /**
     * Creates and initializes WorkflowTask action of initiator
     *
     * @param workflow
     * @param taskStatus (optional) task status to be set to wfTask
     * @param doneByName NULL in case not actin on behalf of another user, otherwise own systemId
     * @return
     *
     */
    public WorkflowTask workflowTaskCreateAndInitForInitiator(Workflow workflow, int taskStatus, String doneByName) {
        WorkflowTask wfTask = WorkflowTask.createFor(workflow, workflow.getAuthor());
        wfTask.setAuthorBy(doneByName);
        wfTask.setTaskStatus(taskStatus);
        wfTask.setState(workflow.getState());
        wfTask.setStep(0);
        wfTask.setIteration(workflow.getIteration());
        wfTask.setStatus(workflow.getStatus());

        // Duplicating process-related data for history log (in case of several iterations, etc.)
        wfTask.setProcess(workflow.getProcess()); // wfTask contains process backup
        if (List.of(TASK_STATUS_TO_APPROVAL, TASK_STATUS_TO_SIGN).contains(wfTask.getTaskStatus()) || STATE_ID_LIST_DYNAMIC.contains(workflow.getState())) {
            if (!workflow.isRecoWF()) {
                wfTask.setNote(workflow.getNote());
                wfTask.setRefItems(workflow.getRefItems());
            }
        }

        return wfTask;
    }


    /**
     * Initializes initial WorkflowTask for the Workflow (usually performed on the Workflow open)<br>
     * It creates either new WorkflowTask on first open OR look-ups the existing WorkflowTask on subsequent open<br>
     * IMPORTANT: you need to refresh Workflow ACL after calling this method
     *
     * @param workflow Workflow which is to be open
     * @param bpmParams BpmParams (can be null)
     * @param aclContext AclContext of the User who opens Workflow
     * @return either created WorkflowTask on first open or loaded WorkflowTask on subsequent open, or null in case there is no available DocResponses for the current phase
     */
    public WorkflowTask workflowTaskCreateAndInitForParticipant(Workflow workflow, BpmParams bpmParams, AclContext aclContext) {
        WorkflowTask wfTask = null;

        if (getWorkflowACL(workflow, aclContext).contains(ACTION_GET_IN)) {
            List<WorkflowTask> wfTaskList = getWorkflowTaskDao().findAll(workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), List.of(TASK_STATUS_DELEGATED_IN), aclContext.getSystemId());
            LOG.trace("Workflow[{}] systemId[{}] executing ACTION_GET_IN logic, DocResponses found: {}", workflow.getId(), aclContext.getSystemId(), wfTaskList.size());

            if (wfTaskList.isEmpty()) {
                // WorkflowTask to be inited, created and saved
                wfTask = WorkflowTask.createFor(workflow, aclContext.getSystemId());
                LOG.trace("Workflow[{}] new WorkflowTask was created", workflow.getId());
            } else {
                // Delegated User on the process who takes workflow for processing for the first time: existing DocResponse to be taken
                // NOTE: Delegated User may have several responses present as several users on the Step can delegate to the same user
                wfTask = wfTaskList.get(0);
                LOG.trace("Workflow[{}] WorkflowTask[{}] with taskStatus={} is to be used", workflow.getId(), wfTask.getId(), wfTask.getTaskStatus());
            }

            // We need to update WorkflowTask only in case it is a new document or current status in not equal InWork
            wfTask.setTaskStatus(TASK_STATUS_INWORK);
            if (bpmParams != null) {
                bpmParams.applyTo(wfTask);
            }

            if (isNotBlank(workflow.getTenantId())) {
                // We must override wfTask.tenantId value as it must use the workflow.tenantId value for cases when workflow was initiated by user from tenantA but executed by user from the tenantB
                wfTask.setTenantId(workflow.getTenantId());
            }

            if (getWorkflowTaskDao().updateOrCreate(null, wfTask)) {
                LogHelper.setEvent("id00120", null, null, new LogExt(wfTask).setLogAccount(wfTask.getAuthor()));
                LOG.trace("Workflow[{}] WorkflowTask[{}] was saved in DB", workflow.getId(), wfTask.getId());
            } else {
                LogHelper.setEvent("id00121", null, "Error saving WorkflowTask", new LogExt(workflow));
                throw new WorkflowTaskSaveException().setEntity(wfTask);
            }
        } else {
            List<WorkflowTask> wfTaskList = getWorkflowTaskDao().findAll(workflow.getId(), workflow.getState(), workflow.getStep(), workflow.getIteration(), List.of(TASK_STATUS_INWORK, TASK_STATUS_DELEGATE_OUT), aclContext.getSystemId());
            LOG.trace("Workflow[{}] systemId[{}] loaded existing DocResponses: {}", workflow.getId(), aclContext.getSystemId(), wfTaskList.size());

            // WorkflowTask to be loaded
            if (!wfTaskList.isEmpty()) {
                if (wfTaskList.size() > 1) {
                    String msgDetail = format("Only one WorkflowTask has to be present instead of: {0}", wfTaskList.size());
                    LOG.warn("Workflow[{}] {}", workflow.getId(), msgDetail);
                    LogHelper.setEvent("id00276", null, msgDetail, new LogExt(workflow));
                }
                wfTask = wfTaskList.get(0);
                LOG.trace("Workflow[{}] WorkflowTask[{}] with taskStatus={} is to be used", workflow.getId(), wfTask.getId(), wfTask.getTaskStatus());
            } else {
                LOG.trace("Workflow[{}] No DocResponses are available for systemId[{}], state={}, step={}, iteration={}",
                        workflow.getId(), aclContext.getSystemId(), workflow.getState(), workflow.getStep(), workflow.getIteration());
            }
        }
        return wfTask;
    }


    /**
     * Workflow Task FollowUp create method
     * Creates Resolution WorkflowTask in DB and also adds assigned person / group / unit to the Workflow Process and saves Workflow in DB
     *
     * @param workflow
     * @param docRespResol
     * @param systemId
     * @return
     */
    public boolean workflowTaskFolloupAdd(Workflow workflow, WorkflowTask docRespResol, String systemId) {
        try {
            String resolTo = docRespResol.getResolutionTo();

            if (!workflow.getProcess().hasState(STATE_ID_EXEC)) { // Check if Execution Process Step exists at all
                return false;
            }

            ProcessState stateExec = workflow.getProcess().getState(STATE_ID_EXEC); // Get Execution Process Step

            if (stateExec.getSteps().isEmpty()) { // If there are no users on the process at all - create Initiator Step
                ProcessStep authorStep = workflow.getProcess().createStep();
                authorStep.addUser(workflow.getAuthor());
                stateExec.getSteps().add(authorStep);
            }

            if (stateExec.getSteps().size() == 1) { // Add first and only Step 1 - Step of Executors
                stateExec.getSteps().add(workflow.getProcess().createStep());
            }

            // in case of edit - we do remove the name first, as it will be added again on the next lines of code
            stateExec.getStep(1).removeName(resolTo);

            if (!getWorkflowTaskDao().updateOrCreate(null, docRespResol)) {
                LogHelper.setEvent("id00284", null, null, new LogExt(workflow));
                return false;
            }

            // Once follow-up action was added - we need to reinit EXEC phase as follow-up actions could be assigned for different steps, e.g.: [0, 1, 2, 3, 4, 5],
            // so we need to make sure that we don't get an empty step, as process will stuck there
            List<WorkflowTask> resolutionList = getWorkflowTaskDao()
                    .getAssignedTasksList(workflow.getId(), STATE_ID_EXEC).stream().sorted(Comparator.comparingInt(WorkflowTask::getStep))
                    .collect(Collectors.toList());
            getBpmEngine().initExecPhaseWithFollowupList(workflow.getId(), workflow.getProcess(), resolutionList);

            boolean saveResult = getWorkflowDao().updateOrCreate(null, workflow);
            if (!saveResult) {
                LogHelper.setEvent("id00285", null, null, new LogExt(workflow));
                workflowTaskFollowupRemove(workflow, docRespResol, systemId);
            }
            return saveResult;

        } catch (Exception e) {
            LogHelper.setEvent("id00272", e, null, new LogExt(workflow));
            return false;
        }
    }


    /**
     * Resolution removal method
     * Deletes WorkflowTask from DB and also removes assigned person / group / unit from Workflow Process and saves Workflow in DB
     *
     * @param workflow
     * @param docRespResol - WorkflowTask with resolution
     * @param systemId
     * @return
     */
    public boolean workflowTaskFollowupRemove(Workflow workflow, WorkflowTask docRespResol, String systemId) {
        try {
            try {
                if (!getWorkflowTaskDao().delete(null, docRespResol)) {
                    LogHelper.setEvent("id00288", null, null, new LogExt(workflow));
                    return false;
                }
            } catch (Exception e) {
                LogHelper.setEvent("id00289", e, null, new LogExt(workflow));
                return false;
            }

            // Once follow-up action was removed - we need to reinit EXEC phase as follow-up actions could be assigned for different steps [0, 1, 2, 3, 4, 5],
            // and in case all resolutions for step 3 were removed - we need to make sure that we don't get an empty step, as process will stuck there
            List<WorkflowTask> resolutionList = getWorkflowTaskDao()
                    .getAssignedTasksList(workflow.getId(), STATE_ID_EXEC).stream().sorted(Comparator.comparingInt(WorkflowTask::getStep))
                    .collect(Collectors.toList());
            getBpmEngine().initExecPhaseWithFollowupList(workflow.getId(), workflow.getProcess(), resolutionList);
            boolean saveResult = getWorkflowDao().updateOrCreate(null, workflow);
            if (!saveResult) {
                LogHelper.setEvent("id00209", null, null, new LogExt(workflow));
            }
            return saveResult;
        } catch (Exception e) {
            LogHelper.setEvent("id00360", e, null, new LogExt(workflow));
            return false;
        }
    }


    //
    // Timer methods ------------------------------------------------------------------------------
    //


    /**
     * Processes all FormDefinitions by applying their retention settings to corresponding RecoDocs
     *
     * @param tenantId
     */
    public void runRetentionTimer(String tenantId) {
        try {
            LOG.debug("WorkflowRetentionTimer -> START");
            AtomicInteger totalBlocked = new AtomicInteger(0);
            AtomicInteger totalDeleted = new AtomicInteger(0);

            List<FormDefinition> formDefList = _formDefService.getAllByStatusAndRetentionAction(List.of(FormDefinition.STATUS_ACTIVE), List.of(FormDefinition.RETENTION_ACTION_BLOCK, FormDefinition.RETENTION_ACTION_DELETE));
            for (FormDefinition formDef : formDefList) {
                LOG.debug("WorkflowRetentionTimer -> FormDefinition[{}] processing START", formDef.getFormId());

                AtomicInteger formDefBlocked = new AtomicInteger(0);
                AtomicInteger formDefDeleted = new AtomicInteger(0);
                WorkflowLazyQuery lazyQuery = new WorkflowLazyQuery();
                lazyQuery.setFirst(-1);
                lazyQuery.setPageSize(-1);

                // Set default filters for currently processed FormDefinition
                lazyQuery.setView(WorkflowLazyQuery.VIEW.ALL);
                lazyQuery.getFilter().put(Workflow._FORM, formDef.getFormId());
                if (formDef.getRetentionAction() == FormDefinition.RETENTION_ACTION_BLOCK) {
                    // Do not perform BLOCK action on already blocked documents
                    lazyQuery.getFilter().put(Workflow._STATUS, new Document("$in", List.of(PROCESS_STATUS_NORMAL, PROCESS_STATUS_SUSPENDED, PROCESS_STATUS_TERMINATED)));
                }

                // Process only documents, created more than Retention Period ago
                Period retPeriod = formDef.getRetentionPeriod() != null ? formDef.getRetentionPeriod() : Period.ofYears(100); // Fallback to 100 years just in case of broken retention settings (must not happen)
                LOG.trace("WorkflowRetentionTimer -> FormDefinition[{}] retention action {}, retention period {}", formDef.getFormId(), (formDef.getRetentionAction() == FormDefinition.RETENTION_ACTION_BLOCK ? "BLOCK" : "DELETE"), retPeriod.toString());
                lazyQuery.getFilter().put(Meta._PATH_META_CREATED, new Document("$lte", ZonedDateTime.now(ZoneOffset.UTC).minus(retPeriod).toInstant()));

                BasicModelProcessor<Workflow> workflowProcessor = new BasicModelProcessor<>() {
                    @Override
                    public void process(Workflow workflow) {
                        if (formDef.getRetentionAction() == FormDefinition.RETENTION_ACTION_BLOCK) {
                            actionBlock(workflow, DirectoryConstants.ELDOC_ROBOT, null, null);
                            LOG.trace("WorkflowRetentionTimer -> BLOCKED: Workflow[{}] | FormDefinition[{}]", workflow.getId(), formDef.getFormId());
                            formDefBlocked.incrementAndGet();
                        } else if (formDef.getRetentionAction() == FormDefinition.RETENTION_ACTION_DELETE) {
                            actionDelete(workflow, DirectoryConstants.ELDOC_ROBOT, null, null);
                            LOG.trace("WorkflowRetentionTimer -> DELETED: Workflow[{}] | FormDefinition[{}]", workflow.getId(), formDef.getFormId());
                            formDefDeleted.incrementAndGet();
                        }
                    }
                };

                getWorkflowDao().processWithCursor(lazyQuery, workflowProcessor, new AclContext(DirectoryConstants.ELDOC_ROBOT, null, null, null, null, null).toFullAccessMode());
                totalBlocked.addAndGet(formDefBlocked.get());
                totalDeleted.addAndGet(formDefDeleted.get());

                String statsMsg = format("FormId[{0}]: {1} documents processed (Blocked: {2}; Deleted: {3})",
                        new Object[]{formDef.getFormId(), formDefBlocked.get() + formDefDeleted.get(), formDefBlocked.get(), formDefDeleted.get()});
                LOG.debug("WorkflowRetentionTimer -> FormDefinition[{}] processing END > {}", formDef.getFormId(), statsMsg);
                if (formDefBlocked.get() + formDefDeleted.get() > 0) {
                    LogHelper.setEvent("id00361", statsMsg);
                }
            }

            String statsMsg = format("Total {0} documents processed (Blocked: {1}; Deleted: {2})", new Object[]{totalBlocked.get() + totalDeleted.get(), totalBlocked.get(), totalDeleted.get()});
            LOG.debug("WorkflowRetentionTimer -> END: {}", statsMsg);
            if (totalBlocked.get() + totalDeleted.get() > 0) {
                LogHelper.setEvent("id00362", statsMsg);
            }
        } catch (Throwable th) {
            LOG.error("Error executing WorkflowRetentionTimer for tenantId={}", tenantId, th);
        }
    }


    //
    // File Attachments and File Manager related methods ------------------------------------------
    //
    public boolean isFilesAllowDelete(Workflow workflow, DbFileRef dbFileRef) {
        try {
            // the first default check: editable and on author step
            if (workflow.getStep() == 0) {
                // then two scenarious are currently possible:
                // scenario 1: file is a template and was attached on document creation (or revision to the tempalte - treated as template)
                // scenario 2: file is regural file attached by document author
                if (workflow.isTemplate(dbFileRef)) {
                    // scenraio 1: template -> get settings from the Process, by default it should allow replacement of template docs (for backward compatability)
                    return workflow.getProcessState().getAttachmentManager().isAllowTemplatesDelete();
                } else {
                    // scenario 2: the file was attached by author -> it is allowed to be deleted from the document
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error(String.valueOf(dbFileRef), e);
        }
        return false;
    }


    public boolean isFilesAllowReset(Workflow workflow, DbFileRef dbFileRef) {
        try {
            // The first default check: editable and on author step and has any temaplates
            if (workflow.getStep() == 0 && !workflow.getTemplates().isEmpty()) {
                // Only file Templates could be resetted. Reset - means delete all revisions of TemplateRef exept initial one
                // Only root revision of the file have to be compared with the templates
                List<DbFileRef> revisionsList = getWorkflowDao().getFilesDao().getFileRevisions(dbFileRef);
                boolean isTemplate = workflow.isTemplate(revisionsList.get(0));
                boolean hasRevisions = revisionsList.size() > 1; // Reset makes sense only in case file TemplateRef has revisions
                return isTemplate && hasRevisions; // This can be omptimized but considering not a big performance kill - left as is for clearity purpose
            }
        } catch (Exception e) {
            LOG.error(String.valueOf(dbFileRef), e);
        }
        return false;
    }


    public boolean isFilesAllowDownload(Workflow workflow, Set<String> workflowAclActions, String systemId) {
        try {
            return ((workflow.isAuthor(systemId) && workflow.getStep() == 0) || workflow.getProcessState().getAttachmentManager().isAllowDownload());
        } catch (Exception e) {
            LogHelper.setEvent("id00297", e, null, new LogExt(workflow));
        }
        return false;
    }


    //
    // Static Methods -----------------------------------------------------------------------------
    //


    /**
     * Ensures presence of default ACL on the Workflow Entity
     *
     * @param workflow
     */
    public static void ensureDefaultAcl(Workflow workflow) {
        if (!workflow.getAcl().getR().contains(DirectoryConstants.ROLE_ADMINDOCR)) {
            workflow.getAcl().getR().add(DirectoryConstants.ROLE_ADMINDOCR);
        }
        if (!workflow.getAcl().getW().contains(DirectoryConstants.ROLE_ADMINDOCRW)) {
            workflow.getAcl().getW().add(DirectoryConstants.ROLE_ADMINDOCRW);
        }
    }


    public static WorkflowService getInstance() {
        return CDI.current().select(WorkflowService.class).get();
    }


    //
    // WorkflowDao Proxy methods ------------------------------------------------------------------
    //
    protected WorkflowDao getWorkflowDao() {
        return _workflowDao;
    }


    public Workflow find(String id, AclContext subjectAclContext) {
        return getWorkflowDao().find(id, subjectAclContext);
    }


    public Workflow findByFileId(String id, AclContext subjectAclContext) {
        return getWorkflowDao().findByFileId(id, subjectAclContext);
    }


    public DbFileRef getFileRootRev(String fileId) {
        DbFileRef requestedFileRef = getWorkflowDao().getFilesDao().createDbFileRef(fileId, null);
        return getWorkflowDao().getFilesDao().getFileRootRev(requestedFileRef);
    }


    public LazyResult<Workflow> findAllLazy(LazyQuery lq, AclContext subjectAclContext) {
        return getWorkflowDao().findAllLazy(lq, subjectAclContext);
    }


    public Workflow findByWebId(String id, AclContext subjectAclContext) {
        return getWorkflowDao().findByWebId(id, subjectAclContext);
    }


    public Workflow findByExtId(String id, AclContext subjectAclContext) {
        return getWorkflowDao().findByExtId(id, subjectAclContext);
    }


    public boolean updateOrCreate(Workflow workflow) {
        return getWorkflowDao().updateOrCreate(null, workflow);
    }


    public void deleteFile(DbFileRef file) {
        getFilesDao().deleteFile(null, file);
    }


    public void deleteFile(Workflow workflow, DbFileRef file) {
        getFilesDao().deleteFile(null, workflow, file);
        updateOrCreate(workflow);
    }


    public FilesDao getFilesDao() {
        return getWorkflowDao().getFilesDao();
    }


    public DbFileDownloadStream getFileDownloadStream(DbFileRef fileRef) {
        return _workflowDao.getFilesDao().getFileDownloadStream(fileRef);
    }


    //
    // WorkflowTaskDao Proxy methods --------------------------------------------------------------
    //
    public WorkflowTaskDao getWorkflowTaskDao() {
        return _workflowTaskDao;
    }


    public WorkflowTask getWorkflowTask(String wfId, Integer state, Integer step, Integer iteration, List<Integer> taskStatusArray, String author, String delegatedFrom, String delegatedTo) {
        return _workflowTaskDao.find(wfId, state, step, iteration, taskStatusArray, author, delegatedFrom, delegatedTo);
    }


    public boolean updateOrCreate(WorkflowTask workflowTask) {
        return _workflowTaskDao.updateOrCreate(null, workflowTask);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    protected ClientSession dbClientStartSession() {
        return _dbClientService.startSession(TenantContext.getCurrentTenantId());
    }


    //
    // Getters ------------------------------------------------------------------------------------
    //
    public BpmEngine getBpmEngine() {
        return _bpmEngine;
    }
}
