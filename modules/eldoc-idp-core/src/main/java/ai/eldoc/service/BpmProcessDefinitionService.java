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

import ai.eldoc.bpm.Process;
import ai.eldoc.bpm.ProcessUtils;
import ai.eldoc.bpm.v3.ProcessImpl;
import ai.eldoc.dao.BpmProcessDefinitionDao;
import ai.eldoc.dao.WorkflowTaskDao;
import ai.eldoc.model.BpmProcessDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * BpmProcessDefinition Services provides business logic for SysProcessModels
 */
@ApplicationScoped
public class BpmProcessDefinitionService {

    @Inject
    private BpmProcessDefinitionDao _bpmProcessDefinitionDao;

    @Inject
    private WorkflowTaskDao _workflowTaskDao;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Creates new BpmProcessDefinition model and saves it in DB
     *
     * @param authorSystemId systemId of the author/initiator
     * @return newly created and persisted BpmProcessDefinition
     */
    public BpmProcessDefinition createBpmProcessDefinition(String authorSystemId) {
        BpmProcessDefinition bpmProcDef = new BpmProcessDefinition(authorSystemId);
        _bpmProcessDefinitionDao.create(null, bpmProcDef);
        return bpmProcDef;
    }


    /**
     * Creates duplicate of the given source BpmProcessDefinition model and saves it in the DB
     *
     * @param systemId systemId of the user who performs duplication (will be set as author)
     * @param bpmProcessDefinitionSrc source process definition to duplicate
     * @return duplicated and persisted BpmProcessDefinition
     */
    public BpmProcessDefinition duplicateBpmProcessDefinition(final String systemId, final BpmProcessDefinition bpmProcessDefinitionSrc) {
        if (bpmProcessDefinitionSrc == null) {
            throw new IllegalArgumentException("Source BpmProcessDefinition must not be null");
        }
        // Init the copy with new author and default process
        BpmProcessDefinition bpmProcDef = new BpmProcessDefinition(systemId);
        // Copy name with suffix
        bpmProcDef.setName(isBlank(bpmProcessDefinitionSrc.getName()) ? "New workflow" : bpmProcessDefinitionSrc.getName() + " (copy)");
        // Deep copy process if present
        Process srcProcess = bpmProcessDefinitionSrc.getProcess();
        if (srcProcess != null) {
            try {
                bpmProcDef.setProcess(ProcessImpl.fromJson(srcProcess.toJson()));
            } catch (Exception ex) {
                // Fallback to default process in case of any issue
                bpmProcDef.setProcess(ProcessUtils.createProcess());
            }
        }
        _bpmProcessDefinitionDao.create(null, bpmProcDef);
        return bpmProcDef;
    }


    /**
     * Returns all BpmProcessDefinition Models located by the list of processId
     *
     * @param sysProcessIDList list of process IDs to fetch
     * @return list of BpmProcessDefinition
     */
    public List<BpmProcessDefinition> getAllByIdList(List<String> sysProcessIDList) {
        return _bpmProcessDefinitionDao.findAllByIdList(sysProcessIDList);
    }


    /**
     * Creates in case not present in DB or loads Recognition Document Process
     *
     * @param authorSystemId systemId of the author/initiator for initial creation
     * @return Recognition Document BpmProcessDefinition (created or updated)
     */
    public BpmProcessDefinition getBpmProcessDefinitionForRecoDoc(String authorSystemId) {
        BpmProcessDefinition bpmProcDef = _bpmProcessDefinitionDao.find(BpmProcessDefinition.RECO_PROCESS_ID);
        if (bpmProcDef == null) {
            bpmProcDef = new BpmProcessDefinition(authorSystemId);
            bpmProcDef.setId(BpmProcessDefinition.RECO_PROCESS_ID);
            bpmProcDef.setName("Workflow: Recognition Document");
            _bpmProcessDefinitionDao.create(null, bpmProcDef); // we have to use create() method here as we are trying to insert a new doc with a manually given _id
        }
        bpmProcDef.setProcess(ProcessUtils.createProcessForRecoWorkflow(_workflowTaskDao));
        _bpmProcessDefinitionDao.updateOrCreate(null, bpmProcDef);
        return bpmProcDef;
    }


    public void create(BpmProcessDefinition bpmProcDef) {
        _bpmProcessDefinitionDao.create(null, bpmProcDef);
    }


    public boolean updateOrCreate(BpmProcessDefinition bpmProcDef) {
        return _bpmProcessDefinitionDao.updateOrCreate(null, bpmProcDef);
    }

}
