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
package ai.eldoc.ui.component;

import ai.eldoc.ai.service.AiService;
import ai.eldoc.constants.Pages;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.WorkflowTaskDao;
import ai.eldoc.model.*;
import ai.eldoc.service.LogHelper;
import ai.eldoc.ui.ResolutionManager;
import ai.eldoc.ui.ResolutionManager.RESOL_ACTION;
import ai.eldoc.ui.view.*;
import ai.eldoc.util.JsfUtils;
import jakarta.annotation.PostConstruct;
import jakarta.faces.annotation.ViewMap;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import static ai.eldoc.bpm.BpmConstants.STATE_ID_EXEC;
import static ai.eldoc.bpm.BpmConstants.TASK_STATUS_ASSIGNMENT;
import static ai.eldoc.constants.DirectoryConstants.BPM_PERS_INITIATOR;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Exec Builder Backing Component
 */
@Named
@ViewScoped
public class ExecBuilderBean implements Serializable {

    @Serial
    private static final long serialVersionUID = -1318758546284351555L;

    private static final Logger LOG = LoggerFactory.getLogger(AiService.class);

    public static final String COMPONENT_RENDERED = "uiExecBuilder_rendered";

    @Inject
    private WorkflowTaskDao _workflowTaskDao;

    @Inject
    private UIViewRoot _viewRoot;

    @Inject
    private FacesContext _context;

    @Inject
    @ViewMap
    private Map<String, Object> _viewMap;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private UserContext _userContext;

    @Inject
    private WorkflowController _workflowController;

    @Inject
    private BpmProcessDefinitionController _bpmProcessDefinitionController;

    @Inject
    private PageController _pageController;

    private int _pageMode = 0;
    private WorkflowTask _wfTaskDS;

    private final int PAGE_MODE_DOCUMENT = 1;
    private final int PAGE_MODE_PROCESS = 2;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ExecBuilderBean() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Executes self-init
     */
    @PostConstruct
    void init() {
        switch (_viewRoot.getViewId()) {
            case Pages.PAGE_WORKFLOW:
                _pageMode = PAGE_MODE_DOCUMENT;
                break;
        }
    }


    /**
     * Enables Exec Builder dialog
     *
     * @param ev
     */
    public void enableExecBuilderDialog(jakarta.faces.event.ComponentSystemEvent ev) {
        if (!_context.isPostback()) {
            // it is okay to lauch it only once on initial page load, not on postbacks.
            _viewMap.put(COMPONENT_RENDERED, true);
        }
    }


    public void initDialog(WorkflowTask wfTask) {
        if (wfTask == null) {
            switch (_pageMode) {
                case PAGE_MODE_DOCUMENT:
                    _wfTaskDS = WorkflowTask.createFollowupFromTo(_workflowController.getCurrentWF().getId(), _userContext.getId(), null);
                    break;
                case PAGE_MODE_PROCESS:
                    _wfTaskDS = WorkflowTask.createFollowupFromTo(_bpmProcessDefinitionController.getSysProcessDS().getId(), BPM_PERS_INITIATOR, null);
                    break;
            }
        } else {
            _wfTaskDS = wfTask;
        }
    }


    public void resolToValidator(FacesContext fc, UIComponent component, Object value) {
        try {
            if (isBlank(_wfTaskDS.getResolutionTo())) {
                JsfUtils.postValidationError(fc, component, _localeContext.getLabel("resolution.selectResolTo"));
            }
        } catch (Exception e) {
            LOG.error("Validation error", e);
            JsfUtils.postValidationError(fc, component, "Validation error: " + e.toString());
        }
    }


    public void resolutionRemove(WorkflowTask wfTask) {
        if (!getResolutionManagerBean().resolutionRemove(wfTask)) {
            LogHelper.setEvent("id00210", null, null, new LogExt(_wfTaskDS));
        }
    }


    public void btnOk() {
        if (!getResolutionManagerBean().resolutionAdd(_wfTaskDS)) {
            LogHelper.setEvent("id00213", null, null, new LogExt(_wfTaskDS));
            _pageController.reloadPage();
        }
    }


    public boolean isAllowAdd() {
        return getResolutionManagerBean().getResolutionAcl(RESOL_ACTION.RESOL_ADD, null);
    }


    public boolean isAllowEdit(WorkflowTask wfTask) {
        return getResolutionManagerBean().getResolutionAcl(RESOL_ACTION.RESOL_EDIT, wfTask);
    }


    public boolean isAllowDelete(WorkflowTask wfTask) {
        return getResolutionManagerBean().getResolutionAcl(RESOL_ACTION.RESOL_DELETE, wfTask);
    }


    public boolean isRecoSysProcess() {
        return _wfTaskDS != null && BpmProcessDefinition.isRecoProcess(_wfTaskDS.getParentId());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Method returns names of executors that already assigned to the Follow-up state<br>
     * For the RecoWorkflow process system checks for Exclude names also for the given Step only
     *
     * @return
     */
    public List<String> getExcludeNames() {
        ArrayList<String> result = new ArrayList<>();
        _workflowTaskDao.findAll(getDocumentId(),
                STATE_ID_EXEC,
                isRecoSysProcess() ? _wfTaskDS.getStep() : null,
                null,
                Arrays.asList(TASK_STATUS_ASSIGNMENT),
                null)
                .forEach(wfTask -> result.add(wfTask.getResolutionTo()));
        return result;
    }


    public WorkflowTask getWfTaskDS() {
        return _wfTaskDS;
    }


    private ResolutionManager getResolutionManagerBean() {
        switch (_pageMode) {
            case PAGE_MODE_DOCUMENT:
                return _workflowController;
            case PAGE_MODE_PROCESS:
                return _bpmProcessDefinitionController;
        }
        LOG.error("Page mode '{}' is not supported for viewId: {}", _pageMode, _viewRoot.getId());
        return null;
    }


    private String getDocumentId() {
        switch (_pageMode) {
            case PAGE_MODE_DOCUMENT:
                return _workflowController.getCurrentWF().getId();
            case PAGE_MODE_PROCESS:
                return _bpmProcessDefinitionController.getSysProcessDS().getId();
        }
        LOG.error("Page mode '{}' is not supported for viewId: {}", _pageMode, _viewRoot.getId());
        return "";
    }


    public boolean isViewDocument() {
        return _pageMode == PAGE_MODE_DOCUMENT;
    }


    public boolean isViewProcess() {
        return _pageMode == PAGE_MODE_PROCESS;
    }


    public String getDateDueLabel() {
        return isViewDocument() ? "execution.dateDue" : "execution.dateDueDelta";
    }


    /**
     * Returns list of SelectItems for the Resolution Process Step
     *
     * @return
     */
    public List<SelectItem> getExecStepSI() {
        List<SelectItem> siList = new ArrayList<>();
        Integer maxStep = getResolutionManagerBean().getResolutionList().stream().mapToInt(WorkflowTask::getStep).max().orElse(0);
        for (int i = 1; i <= (maxStep + 1); i++) {
            siList.add(new SelectItem(i, String.valueOf(i)));
        }
        return siList;
    }
}
