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
package ai.eldoc.ui.view;

import ai.eldoc.constants.Pages;
import ai.eldoc.context.*;
import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.data.*;
import ai.eldoc.model.*;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.service.DirectoryService;
import ai.eldoc.util.JsfUtils;
import jakarta.faces.annotation.RequestMap;
import jakarta.faces.annotation.RequestParameterMap;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.event.AjaxBehaviorEvent;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.PrimeFaces;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static ai.eldoc.bpm.BpmConstants.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Documents View Bean
 */
@Named("wfListController")
@ViewScoped
public class WorkflowListController implements Serializable {

    @Serial
    private static final long serialVersionUID = 2580747958791038408L;
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowListController.class);

    private static final String CACHE_KEY_WORKFORMS_INBOX = "__workflows_cache_inbox";
    public static final String URL_PARAM_VIEW = "view";

    private static final Map<String, WorkflowLazyQuery.VIEW> _viewUrlParamToViewIdMap = Map.of(
            "ibx", WorkflowLazyQuery.VIEW.INBOX,
            "rcq", WorkflowLazyQuery.VIEW.RECOQUEUE
    );

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private UserContext _userContext;

    @Inject
    private UserUiContext _userUiContext;

    @Inject
    private PageController _pageController;

    @Inject
    private UIViewRoot _viewRoot;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private FormDefController _formDefController;

    @Inject
    @RequestMap
    private Map<String, Object> _requestMap;

    @Inject
    @RequestParameterMap
    private Map<String, String> _requestParamMap;

    private int _rowsPerPage = 25;
    private String _rowsPerPageTemplate = "25,50,100";
    private WorkflowLazyDataModel _dataProvider = null;
    private String _viewName;
    private List<Workflow> _selectedDocs = new ArrayList<>();
    private final List<Column> _columnsList = new ArrayList<>();
    private SortMeta _sortMeta = null;
    private List<SelectItem> _workflowTitleSI;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void initOnPageLoad() {
        setViewName(getViewNameFromUrlRememberedOrDefault());
        initColumnsAndSort();
        initView();
    }


    private void initView() {
        if (getView() == null) {
            return; // Nothing to do on the current page
        }
        _dataProvider = new WorkflowLazyDataModel(_workflowDao, getView(), _pageController.isModuleIdp(), SecurityUtils.getSubjectAclContext());

        List<Integer> stateFilter = _formDefController.getWorkflowStateSI(_pageController.isModuleIdp()).stream()
                .map(SelectItem::getValue)
                .map(i -> (Integer) i)
                .collect(Collectors.toList());
        _dataProvider.setStateFilter(stateFilter);

        List<Integer> statusFilter = _formDefController.getWorkflowStatusSI().stream()
                .map(SelectItem::getValue)
                .map(i -> (Integer) i)
                .collect(Collectors.toList());
        _dataProvider.setStatusFilter(statusFilter);

        _workflowTitleSI = _formDefController.getSearchDocTitleLabelsSI();
        Stream<String> formFilterStream = _workflowTitleSI.stream()
                .map(SelectItem::getValue)
                .map(String::valueOf);

        _dataProvider.setFormFilter(formFilterStream.collect(Collectors.toList()));

        // Implement customizations based on the view
        switch (getView()) {
            case ALL -> {
                // For All page we do not show Drafts by default
                _dataProvider.setStateFilter(List.of(STATE_ID_APPROVE, STATE_ID_SIGN, STATE_ID_EXEC, STATE_ID_RPSTR, STATE_ID_ARCH));
                _dataProvider.setStatusFilter(List.of(PROCESS_STATUS_NORMAL));
            }

            case INWORK -> {
                _dataProvider.setStatusFilter(List.of(PROCESS_STATUS_NORMAL)); // Also we need to filter only items with status Normal
            }

            case MYDOCS -> {
                // For MyDocs page we do not show Drafts by default
                _dataProvider.setStateFilter(List.of(STATE_ID_APPROVE, STATE_ID_SIGN, STATE_ID_EXEC, STATE_ID_RPSTR, STATE_ID_ARCH));
            }

            case RECOQUEUE -> {
                // For RECOQUEUE we show only active documents by default. We also show documents in REPO and ARCH so users can find documents more easily
                _dataProvider.setStateFilter(List.of(STATE_ID_DRAFT, STATE_ID_EXEC_RECOBOT, STATE_ID_EXEC_RECO_VALIDATION, STATE_ID_EXEC_CONVERSIONBOT, STATE_ID_EXEC_CONV_VALIDATION, STATE_ID_RPSTR, STATE_ID_ARCH));
                _dataProvider.setStatusFilter(List.of(PROCESS_STATUS_NORMAL));
                _dataProvider.setFormFilter(List.of(FormDefinition.FORM_ID_RECO));
            }

            case ADMIN -> {
                _rowsPerPageTemplate = "25,50,100,250";
                if (_pageController.isModuleIdp()) {
                    _dataProvider.setStateFilter(List.of(STATE_ID_DRAFT, STATE_ID_EXEC_RECOBOT, STATE_ID_EXEC_RECO_VALIDATION, STATE_ID_EXEC_CONVERSIONBOT, STATE_ID_EXEC_CONV_VALIDATION, STATE_ID_RPSTR, STATE_ID_ARCH));
                    _dataProvider.setFormFilter(List.of(FormDefinition.FORM_ID_RECO));
                }
            }

            default -> {
            }
        }
    }


    private void initColumnsAndSort() {
        _columnsList.clear();

        Column number = new Column(Workflow._NUMBER, DataType.STRING, _localeContext.getLabel("general.docs.number"));
        Column state = new Column("state", Workflow._STATE, DataType.STRING, _localeContext.getLabel("general.workflow.state"));
        Column status = new Column("status", Workflow._STATUS, DataType.NUMBER, _localeContext.getLabel("general.docs.status"));
        Column title = new Column("name", Workflow._NAME, DataType.STRING, _localeContext.getLabel("general.docs.title"));
        Column subject = new Column(Workflow._TOPIC, DataType.STRING, _localeContext.getLabel("general.docs.subject"));
        Column author = new Column(Workflow._AUTHOR, DataType.STRING, _localeContext.getLabel("general.docs.author"));
        Column dateCreated = new Column("dateCreated", Workflow._DATE_CREATED, DataType.DATE, _localeContext.getLabel("general.docs.dateCreated"));
        Column dateIncome = new Column("dateReceived", Workflow._DATE_RECEIVED, DataType.DATE, _localeContext.getLabel("general.docs.dateIncome"));
        Column dateDue = new Column("dateDue", Workflow._DATE_DUE, DataType.DATE, _localeContext.getLabel("general.docs.dateDue"));
        Column resolText = new Column("taskKFId", WorkflowTask._TASK_KF_ID, DataType.STRING, _localeContext.getLabel("general.docs.prcTskAction")); //"resolText"

        if (getView() == null) {
            /* Default layout */
            _columnsList.add(number);
            _columnsList.add(title);
            _columnsList.add(subject);
            _columnsList.add(state);
            _columnsList.add(author);
            _columnsList.add(dateCreated);
            _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
            LOG.warn("WorkflowListController.getColumns() -> Not initialized for the viewId: {}", _viewRoot.getViewId());
            return;
        }

        switch (getView()) {
            case INBOX:
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(state);
                _columnsList.add(author);
                _columnsList.add(dateIncome);
                _columnsList.add(dateDue);
                _sortMeta = SortMeta.builder().field(dateIncome.getDbField()).order(SortOrder.DESCENDING).build();
                return;

            case PROCESSED:
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(state);
                _columnsList.add(author);
                _columnsList.add(dateCreated);
                _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
                return;

            case INWORK:
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(state);
                _columnsList.add(author);
                _columnsList.add(dateCreated);
                _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
                return;

            case MYDOCS:
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(state);
                _columnsList.add(status);
                _columnsList.add(author);
                _columnsList.add(dateCreated);
                _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
                return;

            case DRAFTS:
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(status);
                _columnsList.add(dateCreated);
                _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
                return;

            case REPOSITORY:
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(author);
                _columnsList.add(dateCreated);
                _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
                return;

            case ARCHIVE:
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(author);
                _columnsList.add(dateCreated);
                _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
                return;

            case ALL:
            case ADMIN:
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(state);
                _columnsList.add(status);
                _columnsList.add(author);
                _columnsList.add(dateCreated);
                _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
                return;

            case RECOQUEUE:
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(state);
                _columnsList.add(status);
                _columnsList.add(author);
                _columnsList.add(dateCreated);
                _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
                return;

            default:
                /* Default layout */
                _columnsList.add(number);
                _columnsList.add(title);
                _columnsList.add(subject);
                _columnsList.add(state);
                _columnsList.add(author);
                _columnsList.add(dateCreated);
                _sortMeta = SortMeta.builder().field(dateCreated.getDbField()).order(SortOrder.DESCENDING).build();
                LOG.warn("WorkflowListController.getColumns() -> Not initialized for the viewId: {}", _viewRoot.getViewId());
                return;
        }
    }


    public void viewNameChnageListener(AjaxBehaviorEvent event) {
        rememberViewNameToSesssionSettings(getViewName()); // Must be called before postConstrcutInit()
        initView(); // Need to reinit dataProvider, etc.
    }


    private String getViewNameFromUrlRememberedOrDefault() {
        if (isNotBlank(_requestParamMap.get(URL_PARAM_VIEW))) {
            String viewNameFromUrl = WorkflowLazyQuery.VIEW.fromStringOrDefault(_requestParamMap.get(URL_PARAM_VIEW).toUpperCase()).name();
            rememberViewNameToSesssionSettings(viewNameFromUrl);
            return viewNameFromUrl;
        } else if (_userUiContext.getWorkflowsPageViewName() != null) {
            return WorkflowLazyQuery.VIEW.fromStringOrDefault(_userUiContext.getWorkflowsPageViewName()).name();
        }

        if (_pageController.isModuleIdp()) {
            // For IDP module active - we return RECOQUE view by default
            return WorkflowLazyQuery.VIEW.RECOQUEUE.name();
        }

        return WorkflowLazyQuery.VIEW.ALL.name();
    }


    private void rememberViewNameToSesssionSettings(String viewName) {
        if (WorkflowLazyQuery.VIEW.ALL.name().equals(viewName)) {
            // No need to remember default view, but also need to remove current selection
            _userUiContext.setWorkflowsPageViewName(null);
        } else {
            // Remember view name in case it was switched from the default selection
            _userUiContext.setWorkflowsPageViewName(viewName);
        }
    }


    public void btnCsvExport() {
        if (_dataProvider.getRowCount() > 0) {
            boolean exportAttachments = (boolean) _requestMap.getOrDefault("csvExportWithAttachments", false);
            PrimeFaces.current().ajax().addCallbackParam("exportUrl",
                    "/api/export"
                            .concat("?id=")
                            .concat(_dataProvider.createCsvExportTask(_selectedDocs, _localeContext.getLangSystemCode(), _userContext.getDateFormat(), _userContext.getTimeFormat(), _userContext.getTimezone(), exportAttachments)));
            JsfUtils.addInfoMessage(_localeContext.getLabel("export.msg.started.title"), _localeContext.getLabel("export.msg.started.details"));
        } else {
            JsfUtils.addInfoMessage(_localeContext.getLabel("general.dataTable.noRecordsFound"));
        }
    }


    //
    // Static methods -----------------------------------------------------------------------------
    //
    public static WorkflowLazyQuery.VIEW getViewIdByUrlParam(String viewUrlParam) {
        if (isBlank(viewUrlParam) || !_viewUrlParamToViewIdMap.containsKey(viewUrlParam)) {
            return WorkflowLazyQuery.VIEW.ALL; // Default behaviour
        }
        return _viewUrlParamToViewIdMap.get(viewUrlParam);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    protected WorkflowLazyQuery.VIEW getView() {
        return getViewByPageName(_viewRoot.getViewId());
    }


    private WorkflowLazyQuery.VIEW getViewByPageName(String pageName) {
        if (isBlank(pageName)) {
            return null;
        }

        switch (pageName) {
            case Pages.PAGE_WORKFLOW_INBOX -> {
                return WorkflowLazyQuery.VIEW.INBOX;
            }

            case Pages.PAGE_WORKFLOW_STATUS -> {
                // This page may display several views based on the selection
                return WorkflowLazyQuery.VIEW.fromStringOrDefault(getViewName());
            }

            default -> {
                return null;
            }
        }
    }


    public String getMenuInboxCount() {
        if (!_requestMap.containsKey(CACHE_KEY_WORKFORMS_INBOX)) {
            WorkflowLazyQuery lazyQuery = new WorkflowLazyQuery();
            // Filters should use $in/$nin with lists to avoid matching corresponding fields with regex
            lazyQuery.setFilters(Map.of(Workflow._FORM, List.of(FormDefinition.FORM_ID_RECO)));
            _requestMap.put(CACHE_KEY_WORKFORMS_INBOX, _workflowDao.viewInboxCount(lazyQuery, SecurityUtils.getSubjectAclContext()));
        }
        long count = (long) _requestMap.get(CACHE_KEY_WORKFORMS_INBOX);
        if (count == 0) {
            return null;
        }
        return count >= 100 ? "99+" : String.valueOf(count);
    }


    public WorkflowLazyDataModel getDataProvider() {
        return _dataProvider;
    }


    public int getRowsPerPage() {
        return _rowsPerPage;
    }


    public void setRowsPerPage(int rowsPerPage) {
        _rowsPerPage = rowsPerPage;
    }


    public String getRowsPerPageTemplate() {
        return _rowsPerPageTemplate;
    }


    public String getViewName() {
        return _viewName;
    }


    public void setViewName(String viewName) {
        _viewName = viewName;
    }


    public List<SelectItem> getViewNameSI() {
        List<SelectItem> result = new ArrayList<>();

        result.add(new SelectItem(WorkflowLazyQuery.VIEW.RECOQUEUE, _localeContext.getLabel("workflow.view.recoQueue")));

        if (_userContext.isAdminDocAny()) {
            result.add(new SelectItem(WorkflowLazyQuery.VIEW.ADMIN, _localeContext.getLabel("workflow.view.admin")));
        }
        return result;
    }


    public Map<String, Column> getColumnsMap() {
        initColumnsAndSort();
        return _columnsList.stream().collect(Collectors.toMap(Column::getDbField, item -> item));
    }


    public SortMeta getDefaultSortBy() {
        return _sortMeta;
    }


    public List<SelectItem> getWorkflowStateSI() {
        return _formDefController.getWorkflowStateSI(_pageController.isModuleIdp());
    }


    public List<SelectItem> getWorkflowStatusSI() {
        return _formDefController.getWorkflowStatusSI();
    }


    public List<SelectItem> getWorkflowTitleSI() {
        return _workflowTitleSI;
    }


    public List<Workflow> getSelectedDocs() {
        return _selectedDocs;
    }


    public void setSelectedDocs(List<Workflow> selectedDocs) {
        _selectedDocs = selectedDocs;
    }


    public boolean isDelegateBatchRendered() {
        return Pages.PAGE_WORKFLOW_INBOX.equals(_viewRoot.getViewId());
    }


    public boolean isViewNameSelectRendered() {
        return Pages.PAGE_WORKFLOW_STATUS.equals(_viewRoot.getViewId());
    }


    public boolean isBtnResumeRendered() {
        return !WorkflowLazyQuery.VIEW.DRAFTS.equals(getView());
    }


    public boolean isBtnSuspendRendered() {
        return !WorkflowLazyQuery.VIEW.DRAFTS.equals(getView());
    }


    public boolean isBtnDeleteBatchRendered() {
        return WorkflowLazyQuery.VIEW.DRAFTS.equals(getView()) || WorkflowLazyQuery.VIEW.ADMIN.equals(getView());
    }


    public boolean isBtnUnBlockRendered() {
        return WorkflowLazyQuery.VIEW.ADMIN.equals(getView());
    }


    public boolean isBtnBlockRendered() {
        return WorkflowLazyQuery.VIEW.ADMIN.equals(getView());
    }


    public boolean isBtnBulkEditRendered() {
        return WorkflowLazyQuery.VIEW.ADMIN.equals(getView()) && _userContext.isAdminDocRW();
    }


    public List<Date> getDateCreatedFilter() {
        return getDataProvider().getDateCreated().toDateRangeList();
    }


    public void setDateCreatedFilter(List<Date> dateCreatedFilter) {
        getDataProvider().getDateCreated().setFromDateRangeList(dateCreatedFilter);
    }


    public List<Date> getDateIncomeFilter() {
        return getDataProvider().getDateIncome().toDateRangeList();
    }


    public void setDateIncomeFilter(List<Date> dateIncomeFilter) {
        getDataProvider().getDateIncome().setFromDateRangeList(dateIncomeFilter);
    }


    public List<Date> getDateDueFilter() {
        return getDataProvider().getDateDue().toDateRangeList();
    }


    public void setDateDueFilter(List<Date> dateDueFilter) {
        getDataProvider().getDateDue().setFromDateRangeList(dateDueFilter);
    }

}
