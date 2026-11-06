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
import ai.eldoc.dao.FormDefinitionDao;
import ai.eldoc.model.BpmProcessDefinition;
import ai.eldoc.model.FormDefinition;
import ai.eldoc.util.I18n;
import ai.eldoc.util.LabelUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Form Definition Service
 */
@ApplicationScoped
public class FormDefinitionService {

    private static final Logger LOG = LoggerFactory.getLogger(FormDefinitionService.class);

    @Inject
    private FormDefinitionDao _formDefDao;

    @Inject
    private BpmProcessDefinitionService _bpmProcessDefinitionService;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Creates FormDefinition model without saving in DB (in-memory only)
     *
     * @param authorSystemId systemId of the author/initiator
     * @return new FormDefinition model
     */
    public FormDefinition createFormDefinition(String authorSystemId) {
        return new FormDefinition(authorSystemId);
    }


    /**
     * Creates and saves in DB new static FormDefinition for Recognition Workflow (frd1)
     *
     * @param authorSystemId systemId of the author/initiator
     * @return new FormDefinition model
     */
    public FormDefinition createStaticFormDefinitionForRecoDoc(String authorSystemId) {
        //Step 1: check for BpmProcessDefinition for the Recognition Workflow Form
        BpmProcessDefinition bpmProcDef = _bpmProcessDefinitionService.getBpmProcessDefinitionForRecoDoc(authorSystemId);

        //Step 2: create/update Reco FormDefinition
        FormDefinition formDef = _formDefDao.findByWebId(FormDefinition.FORM_ID_RECO, true, null);
        if (formDef == null) {
            // Reco FormDefinition is not present - need to create new
            formDef = createFormDefinition(authorSystemId);
            formDef.setType(FormDefinition.TYPE_STATIC);
            formDef.setFormId(FormDefinition.FORM_ID_RECO);
            for (String langSysCode : I18n.LANG_SYSTEM_CODES_ALL) {
                formDef.setNameByLang(langSysCode, LabelUtils.getLabel("general.recoDoc.name", langSysCode));
            }
        }
        formDef.setProcessIds(Set.of(bpmProcDef.getId()));
        formDef.getSfDef().getDescription().setEnabled(false);
        _formDefDao.updateOrCreate(null, formDef);
        return formDef;
    }


    /**
     * Updates system FormDefinition models<br>
     * IMPORTANT: Executed on each launch
     */
    public void updateFormDefs() {
        updateRecoWFFormNames();
    }


    /**
     * Updates Reco FormDefinition names, if form present
     */
    private void updateRecoWFFormNames() {
        LOG.debug("FormDefinition[{}] names validation", FormDefinition.FORM_ID_RECO);
        boolean updated = false;
        FormDefinition formDef = _formDefDao.findByWebId(FormDefinition.FORM_ID_RECO, true, null);
        if (formDef == null) {
            LOG.debug("FormDefinition[{}] not initialized, nothing to update", FormDefinition.FORM_ID_RECO);
            return;
        }
        for (String langSysCode : I18n.LANG_SYSTEM_CODES_ALL) {
            String systemLabel = LabelUtils.getLabel("general.recoDoc.name", langSysCode);
            if (isNotBlank(systemLabel) && !Objects.equals(formDef.getNameByLang(langSysCode), systemLabel)) {
                formDef.setNameByLang(langSysCode, systemLabel);
                updated = true;
            }
        }
        if (updated) {
            if (!_formDefDao.updateOrCreate(null, formDef)) {
                throw new ElDocRuntimeException("Error updating name for FormDefinition: " + FormDefinition.FORM_ID_RECO);
            }
            LOG.info("FormDefinition[{}] names updated", FormDefinition.FORM_ID_RECO);
        }
    }


    /**
     * Returns all FormDefinition Models by the given status and retention action
     *
     * @param statusList list of statuses
     * @param retentionActionList list of retention actions
     * @return List of FormDefinition Models
     */
    public List<FormDefinition> getAllByStatusAndRetentionAction(List<Integer> statusList, List<Integer> retentionActionList) {
        return _formDefDao.findAllByStatusAndRetentionAction(statusList, retentionActionList);
    }


    /**
     * Returns FormDefinition model from DB
     *
     * @param webId FormDefinition webId
     * @param skipAccessCreateCheck skip check for accessCreate for the given systemId
     * @param systemId systemId of the user
     * @return FormDefinition model or null if not found
     */
    public FormDefinition getByWebId(String webId, boolean skipAccessCreateCheck, String systemId) {
        return _formDefDao.findByWebId(webId, skipAccessCreateCheck, systemId);
    }


    public boolean updateOrCreate(FormDefinition formDef) {
        return _formDefDao.updateOrCreate(null, formDef);
    }

}
