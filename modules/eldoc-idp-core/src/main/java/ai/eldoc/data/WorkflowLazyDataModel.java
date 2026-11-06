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
package ai.eldoc.data;

import ai.eldoc.bpm.BpmConstants;
import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.model.*;
import ai.eldoc.service.TaskService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.primefaces.model.*;
import java.io.Serial;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static ai.eldoc.bpm.BpmConstants.*;
import static java.util.function.Predicate.not;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Lazy DataSource for working with Workflow models
 */
public class WorkflowLazyDataModel extends LazyDataModel<Workflow> {

    @Serial
    private static final long serialVersionUID = -3347028679318151494L;
    private static final Logger LOG = Logger.getLogger(WorkflowLazyDataModel.class.getName());

    private final WorkflowDao _workflowDao;
    private final WorkflowLazyQuery.VIEW _view;
    private boolean _isIdpMod;
    private LazyResult<Workflow> _lazyResult;
    private WorkflowLazyQuery _lazyQueryClone = new WorkflowLazyQuery();
    private final Map<String, Object> _constraintFilters = new HashMap<>();
    private final AclContext _aclContext;

    private String _searchDocNum = null;
    private List<String> _formFilter;
    private List<Integer> _stateFilter;
    private List<Integer> _statusFilter;
    private String _searchDocTopic = null;
    private DateRange _dateCreated;
    private DateRange _dateIncome;
    private DateRange _dateDue;
    private String _searchFT;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public WorkflowLazyDataModel(WorkflowDao workflowDao, WorkflowLazyQuery.VIEW view, boolean idpModule, AclContext aclContext) {
        _workflowDao = workflowDao;
        _view = view;
        _isIdpMod = idpModule;
        _aclContext = aclContext;
        _dateCreated = new DateRange();
        _dateIncome = new DateRange();
        _dateDue = new DateRange();
        _lazyQueryClone.setView(_view);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Load defined set of document from DB (using lazy load)
     *
     * @param first
     * @param pageSize
     * @param sortMeta
     * @param filters
     * @return
     */
    @Override
    public List<Workflow> load(int first, int pageSize, Map<String, SortMeta> sortMeta, Map<String, FilterMeta> filters) {
        // We need to use independant instance of the LazyQuery below in order to avoid ConcurrentModificationException thrown from MongoDB classes due to
        // frequent DataTable updates particurarly on the RecoQueue page while several files upload, as this method will modify filters several times
        final WorkflowLazyQuery lazyQuery = new WorkflowLazyQuery();
        lazyQuery.setFirst(first);
        lazyQuery.setPageSize(pageSize);
        if (!sortMeta.isEmpty()) {
            Map.Entry<String, SortMeta> sortFieldMeta = sortMeta.entrySet().iterator().next();
            lazyQuery.setSortFieldAndOrder(sortFieldMeta.getValue().getField(), sortFieldMeta.getValue().getOrder());
        }
        lazyQuery.setFilters(getFilterCriteria(filters));
        lazyQuery.setView(_view);
        lazyQuery.setSearchFT(getSearchFT());
        _lazyQueryClone = lazyQuery.clone();

        _lazyResult = _workflowDao.findAllLazy(lazyQuery, _aclContext);
        super.setRowCount(_lazyResult.getCount());

        int recalculatedFirst = super.recalculateFirst(first, pageSize, _lazyResult.getCount());
        if (recalculatedFirst != first) {
            // Data changed in the way, that requested page is now empty - we need to update 'first' and query db again
            // Page index on UI and pages will be silently updated, as we updated rowCount, but we need to retrieve valid
            // data from DB with new offset ('first')
            // See primefaces issue 1921
            lazyQuery.setFirst(recalculatedFirst);
            _lazyResult = _workflowDao.findAllLazy(lazyQuery, _aclContext);
            super.setRowCount(_lazyResult.getCount());
        }

        return _lazyResult.getDatasource();
    }


    @Override
    public int count(Map<String, FilterMeta> filterBy) {
        // Required to be 0 for pagination to properly work with updating rowCount in load() method
        // See discussion and commits in primefaces issue 8062
        return 0;
    }


    /**
     * Prepares criteria document for performing search
     *
     * @return document with search criteria
     */
    private Map<String, Object> getFilterCriteria(Map<String, FilterMeta> filters) {
        Map<String, Object> resultFilter = new HashMap<>();

        if (filters.get(Workflow._TOPIC) != null) {
            String docTopic = (String) filters.get(Workflow._TOPIC).getFilterValue();
            if (isNotBlank(docTopic)) {
                resultFilter.put(Workflow._TOPIC, Pattern.compile(Pattern.quote(docTopic.trim()), Pattern.CASE_INSENSITIVE));
            }
        }

        if (filters.get(Workflow._NUMBER) != null) {
            String docNum = (String) filters.get(Workflow._NUMBER).getFilterValue();
            if (isNotBlank(docNum)) {
                resultFilter.put(Workflow._NUMBER, Pattern.compile(Pattern.quote(docNum.trim()), Pattern.CASE_INSENSITIVE));
            }
        }

        // in case search filter is not defined - skip this condition at all
        if (getFormFilter() != null) {
            resultFilter.put(Workflow._FORM, new Document("$in", getFormFilter()));
        }

        // Workflow._STATE filter handlig
        List<Integer> statesFilterList = getStateFilter();
        if (WorkflowLazyQuery.VIEW.ALL.equals(_view) && statesFilterList.contains(STATE_ID_DRAFT)) {
            // On the View: ALL when Draft State selected we have to show Drafts only of the current user
            statesFilterList = getStateFilter().stream()
                    .filter(selectedState -> selectedState != STATE_ID_DRAFT)
                    .collect(Collectors.toCollection(ArrayList::new));
            resultFilter.put("$or", List.of(
                    new Document(Workflow._STATE, new Document("$in", statesFilterList)),
                    new Document("$and", List.of(
                            new Document(Workflow._STATE, STATE_ID_DRAFT),
                            new Document(Workflow._AUTHOR, _aclContext.getSystemId()))
                    )));
        } else if ((WorkflowLazyQuery.VIEW.INBOX.equals(_view) && _isIdpMod) || WorkflowLazyQuery.VIEW.RECOQUEUE.equals(_view) || (WorkflowLazyQuery.VIEW.ADMIN.equals(_view) && _isIdpMod)) {
            statesFilterList = getStateFilter().stream()
                    .filter(not(BpmConstants::isVirtualState))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (statesFilterList.size() != getStateFilter().size()) {
                // States filter contains virtual states
                List<Document> virtualStatesFilter = new ArrayList<>();
                virtualStatesFilter.add(new Document(Workflow._STATE, new Document("$in", statesFilterList)));
                getStateFilter().stream()
                        .filter(BpmConstants::isVirtualState)
                        .map(virtualState -> {
                            return new Document("$and", List.of(
                                    new Document(Workflow._STATE, getStateByVirtualState(virtualState)),
                                    new Document(Workflow._STEP, getStepByVirtualState(virtualState)))
                            );
                        })
                        .forEach(virtualStatesFilter::add);
                resultFilter.put("$or", virtualStatesFilter);
            } else {
                resultFilter.put(Workflow._STATE, new Document("$in", statesFilterList));
            }
        } else {
            resultFilter.put(Workflow._STATE, new Document("$in", statesFilterList));
        }

        resultFilter.put(Workflow._STATUS, new Document("$in", getStatusFilter()));

        resultFilter.put(Workflow._DATE_CREATED, _dateCreated);

        resultFilter.put(Workflow._DATE_RECEIVED, _dateIncome);

        resultFilter.put(Workflow._DATE_DUE, _dateDue);

        // Apply ConstraintFilters (highest-priority - to be kept always and override any other filters with the same keys)
        resultFilter.putAll(_constraintFilters);

        return resultFilter;
    }


    /**
     * Returns ID of the submitted job for preparing CSV Export file<br/>
     *
     * @param lang Language to be used during export
     * @param dateFormat date format to be used during export
     * @param timeFormat time format to be used during export
     * @param withAttachments
     * @return
     */
    public String createCsvExportTask(String lang, String dateFormat, String timeFormat, String timeZone, boolean withAttachments) {
        return createCsvExportTask(new ArrayList<>(), lang, dateFormat, timeFormat, timeZone, withAttachments);
    }


    /**
     * Returns ID of the submitted job for preparing CSV Export file<br>
     *
     * @param workflowsList List of Workflows. If not empty, export task will contain workflows only from this list
     * @param lang Language to be used during export
     * @param dateFormat date format to be used during export
     * @param timeFormat time format to be used during export
     * @param withAttachments
     * @return
     */
    public String createCsvExportTask(List<Workflow> workflowsList, String lang, String dateFormat, String timeFormat, String timeZone, boolean withAttachments) {
        WorkflowLazyQuery csvExportLQ = getLazyQueryClone();

        // Set localization parameters
        csvExportLQ.getFilter().put(WorkflowLazyQuery.PARAM_LANG, lang);
        csvExportLQ.getFilter().put(WorkflowLazyQuery.PARAM_DATE_FORMAT, dateFormat);
        csvExportLQ.getFilter().put(WorkflowLazyQuery.PARAM_TIME_FORMAT, timeFormat);
        csvExportLQ.getFilter().put(WorkflowLazyQuery.PARAM_TIMEZONE, timeZone);
        csvExportLQ.getFilter().put(WorkflowLazyQuery.PARAM_EXPORT_ATTACHMENTS, withAttachments);

        if (workflowsList != null && !workflowsList.isEmpty()) {
            List<ObjectId> docIds = workflowsList.stream()
                    .map(workflow -> new ObjectId(workflow.getId()))
                    .collect(Collectors.toList());
            csvExportLQ.getFilter().put(Workflow._ID, new Document("$in", docIds));
            csvExportLQ.setPageSize(workflowsList.size()); // IMPORTANT: we use page size attribute to store the total number of documents to be exported
        } else {
            csvExportLQ.setPageSize(getRowCount()); // IMPORTANT: we use page size attribute to store the total number of documents to be exported
        }

        return TaskService.getInstance().setExportTask(csvExportLQ);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    public Workflow getRowData(String rowKey) {
        return _workflowDao.find(rowKey, _aclContext);
    }


    @Override
    public String getRowKey(Workflow rowData) {
        return rowData.getId();
    }


    /**
     * Returns clone of currently used LazyQuery
     *
     * @return
     */
    public WorkflowLazyQuery getLazyQueryClone() {
        return _lazyQueryClone;
    }


    public String getSearchDocNum() {
        return _searchDocNum;
    }


    public void setSearchDocNum(String searchDocNum) {
        _searchDocNum = searchDocNum;
    }


    public List<String> getFormFilter() {
        return _formFilter;
    }


    public void setFormFilter(List<String> formFilter) {
        _formFilter = formFilter;
    }


    public List<Integer> getStateFilter() {
        return _stateFilter;
    }


    public void setStateFilter(List<Integer> stateFilter) {
        _stateFilter = stateFilter;
    }


    public List<Integer> getStatusFilter() {
        return _statusFilter;
    }


    public void setStatusFilter(List<Integer> statusFilter) {
        _statusFilter = statusFilter;
    }


    public String getSearchDocTopic() {
        return _searchDocTopic;
    }


    public void setSearchDocTopic(String searchDocTopic) {
        _searchDocTopic = searchDocTopic;
    }


    public DateRange getDateCreated() {
        return _dateCreated;
    }


    public void setDateCreated(DateRange dateCreated) {
        _dateCreated = dateCreated;
    }


    public DateRange getDateIncome() {
        return _dateIncome;
    }


    public void setDateIncome(DateRange dateIncome) {
        _dateIncome = dateIncome;
    }


    public DateRange getDateDue() {
        return _dateDue;
    }


    public void setDateDue(DateRange dateDue) {
        _dateDue = dateDue;
    }


    /**
     * Filter constraints which have the highest priority among any other user-defined filters
     *
     * @return
     */
    public Map<String, Object> getConstraintFilters() {
        return _constraintFilters;
    }


    /**
     * Sets filter constraints which have the highest priority among any other user-defined filters
     *
     * @param constraintFilters
     */
    public void setConstraintFilters(Map<String, Object> constraintFilters) {
        _constraintFilters.putAll(constraintFilters);
    }


    /**
     * Adds filter constraints which have the highest priority among any other user-defined filters
     *
     * @param key
     * @param value
     */
    public void addConstraintFilters(String key, Object value) {
        _constraintFilters.put(key, value);
    }


    public String getSearchFT() {
        return _searchFT;
    }


    public void setSearchFT(String searchFT) {
        _searchFT = searchFT;
    }

}
