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

import ai.eldoc.dao.BasicLazyDao;
import ai.eldoc.model.*;
import ai.eldoc.util.I18n;
import org.primefaces.model.*;
import java.io.Serializable;
import java.util.*;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * MongoDB Lazy Data Model
 * @param <T>
 */
public class MongoLazyDataModel<T extends BasicModel> extends LazyDataModel<T> {

    private static final long serialVersionUID = 4152369405438342758L;

    private final BasicLazyDao<T> _lazyDao;
    private LazyQuery _lazyQueryClone = new LazyQuery();
    private final Map<String, SortMeta> _defaultSortMeta = new HashMap<>(); // Default sortMeta
    private final Map<String, Object> _filters = new HashMap<>(); // Convenient dataholder for binding column filters on the UI
    private final Map<String, Object> _constraintFilters = new HashMap<>();
    private String _lang; // current language of the UI
    private final Set<String> _filterIgnoreEmptyListForKeysMap = new HashSet<>();
    private final Set<String> _l10nFields; // List of field names which has to be localized
    private AclContext _aclContext;
    private String _searchFT;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public MongoLazyDataModel(BasicLazyDao<T> lazyDao) {
        _lazyDao = lazyDao;
        _l10nFields = new HashSet<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public List<T> load(int first, int pageSize, Map<String, SortMeta> sortMeta, Map<String, FilterMeta> filters) {
        // We need to use independant instance of the LazyQuery below in order to avoid ConcurrentModificationException thrown from MongoDB classes due to
        // frequent call of this method (this can be observed during DataTable updates on the RecoQueue page while several files upload)
        LazyQuery lazyQuery = new LazyQuery();
        lazyQuery.setFirst(first);
        lazyQuery.setPageSize(pageSize);

        // Decide on the SortMeta to be used (default has a higher priority)
        Map<String, SortMeta> sortMetaToUse = !_defaultSortMeta.isEmpty() ? _defaultSortMeta : sortMeta;
        if (!sortMetaToUse.isEmpty()) {
            Map.Entry<String, SortMeta> sortFieldMeta = sortMetaToUse.entrySet().iterator().next();
            lazyQuery.setSortFieldAndOrder(localizeIfRequired(sortFieldMeta.getValue().getField()), sortFieldMeta.getValue().getOrder());
        }

        lazyQuery.setFilters(getCombinedFilters(filters));
        lazyQuery.setSearchFT(getSearchFT());
        _lazyQueryClone = lazyQuery.clone();

        LazyResult<T> lazyResult;
        if (_aclContext == null) {
            // Temporary migration workaround
            lazyResult = _lazyDao.findAllLazy(lazyQuery);
        } else {
            lazyResult = _lazyDao.findAllLazy(lazyQuery, _aclContext);
        }

        super.setRowCount(lazyResult.getCount());

        int recalculatedFirst = super.recalculateFirst(first, pageSize, lazyResult.getCount());
        if (recalculatedFirst != first) {
            // Data changed in the way, that requested page is now empty - we need to update 'first' and query db again
            // Page index on UI and pages will be silently updated, as we updated rowCount, but we need to retrieve valid
            // data from DB with new offset ('first')
            // See primefaces issue 1921
            lazyQuery.setFirst(recalculatedFirst);
            if (_aclContext == null) {
                // Temporary migration workaround
                lazyResult = _lazyDao.findAllLazy(lazyQuery);
            } else {
                lazyResult = _lazyDao.findAllLazy(lazyQuery, _aclContext);
            }
            super.setRowCount(lazyResult.getCount());
        }

        return lazyResult.getDatasource();
    }


    @Override
    public int count(Map<String, FilterMeta> filterBy) {
        // Required to be 0 for pagination to properly work with updating rowCount in load() method
        // See discussion and commits in primefaces issue 8062
        return 0;
    }


    /**
     * Combines all filters (Default, preset and UI) into single map
     *
     * @param filters
     * @return
     */
    private Map<String, Object> getCombinedFilters(Map<String, FilterMeta> filters) {
        Map<String, Object> resultFilters = new HashMap<>();

        // NOTE: when using as:
        // <p:column filterBy="#{owner}"
        //   <f:facet name="filter">
        //     <p:autoComplete value="#{crmController.crmOrgLazyDS.filters['owner']}"
        // Filter key "owner" will repeat in both "_filters" and "filters" variables,
        // and basically autoComplete->value property is optional in such case
        // Apply preset filters (lowest-priority)
        if (!_filters.isEmpty()) {
            _filters.entrySet().forEach(filterEntry -> {
                applyFilterValue(resultFilters, localizeIfRequired(filterEntry.getKey()), filterEntry.getValue());
            });
        }

        // Apply UI-filters (middle-priority) with respective handling of FilterMeta object
        if (filters != null) {
            filters.entrySet().forEach(filterEntry -> {
                applyFilterValue(resultFilters, localizeIfRequired(filterEntry.getKey()), filterEntry.getValue().getFilterValue());
            });
        }

        // Apply DefaultFilters (highest-priority - to be kept always)
        resultFilters.putAll(_constraintFilters);

        return resultFilters;
    }


    private void applyFilterValue(Map<String, Object> resultFilters, String filterKey, Object filterValue) {
        if (filterValue != null && filterValue.getClass().isArray()) {
            List<Object> listValue = asList((Object[]) filterValue);
            if (!(listValue.isEmpty() && isFilterIgnoreEmptyListForKey(filterKey))) {
                // in case emptyListAsNull flag set - we do add only not empty List
                resultFilters.put(filterKey, listValue);
            }
        } else if (filterValue instanceof List) {
            if (DateRange.isValid((List) filterValue)) {
                resultFilters.put(filterKey, new DateRange((List) filterValue));
            } else if (!(((List) filterValue).isEmpty() && isFilterIgnoreEmptyListForKey(filterKey))) {
                resultFilters.put(filterKey, filterValue);
            }
        } else if (filterValue != null) {
            resultFilters.put(filterKey, filterValue);
        }
    }


    private String localizeIfRequired(String fieldName) {
        if (_l10nFields.contains(fieldName) && isNotBlank(getLang())) {
            return I18n.getL10nName(fieldName, getLang());
        } else {
            return fieldName;
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    public String getRowKey(T rowData) {
        return rowData.getId();
    }


    @Override
    public T getRowData(String rowKey) {
        return _lazyDao.find(rowKey);
    }


    /**
     * Returns clone of currently used LazyQuery (but still it is not recommended to update filter or sort values)
     *
     * @return
     */
    public LazyQuery getLazyQueryClone() {
        return _lazyQueryClone;
    }


    public String getSearchFT() {
        return _searchFT;
    }


    public void setSearchFT(String searchFT) {
        _searchFT = searchFT;
    }


    public Map<String, SortMeta> getDefaultSortMeta() {
        return _defaultSortMeta;
    }


    /**
     * Convenient dataholder for binding column filters on the UI<br>
     * Also can be used for defining initial filters on page load
     *
     * @return
     */
    public Map<String, Object> getFilters() {
        return _filters;
    }


    /**
     * Convenient dataholder for binding column filters on the UI<br>
     * Also can be used for defining initial filters on page load
     *
     * @param filters
     */
    public void setFilters(Map<String, Object> filters) {
        _filters.clear();
        if (filters != null) {
            _filters.putAll(filters);
        }
    }


    public Map<String, Object> getConstraintFilters() {
        return _constraintFilters;
    }


    public void setConstraintFilters(Map<String, Object> constraintFilters) {
        _constraintFilters.putAll(constraintFilters);
    }


    public void addConstraintFilters(String key, Object value) {
        _constraintFilters.put(key, value);
    }


    /**
     * Sets field name to be ignored for filters if contains empty list.<br>
     * Supports wildcard * for marking all keys
     *
     * @param key
     */
    public void setFilterIgnoreEmptyListForKey(String key) {
        _filterIgnoreEmptyListForKeysMap.add(key);
    }


    /**
     * Removes field name to be ignored for filters if contains empty list.<br>
     * Supports wildcard * for marking all keys
     *
     * @param key
     */
    public void removeFilterIgnoreEmptyListForKey(String key) {
        _filterIgnoreEmptyListForKeysMap.remove(key);
    }


    /**
     * Defines whether empty list for the given key should be ignored or not.<br>
     * IMPORTANT: In case wildcard * was defined - will mark all keys as ignored
     *
     * @param key
     * @return
     */
    public boolean isFilterIgnoreEmptyListForKey(String key) {
        return _filterIgnoreEmptyListForKeysMap.contains("*") || _filterIgnoreEmptyListForKeysMap.contains(key);
    }


    /**
     * Returns current language of the UI
     *
     * @return
     */
    public String getLang() {
        return _lang;
    }


    /**
     * Sets current language of the UI
     *
     * @param lang
     */
    public void setLang(String lang) {
        _lang = lang;
    }


    /**
     * Returns unmodifiable list of field names which has to be localized
     *
     * @return
     */
    public Set<String> getL10nFields() {
        return Collections.unmodifiableSet(_l10nFields);
    }


    /**
     * Adds field name which has to be localized
     *
     * @param fieldName
     */
    public void addL10nField(String fieldName) {
        _l10nFields.add(fieldName);
    }


    public void setAclContext(AclContext aclContext) {
        _aclContext = aclContext;
    }


    /**
     * Functional interface, that can be used to implement modifiers/processors for LazyQuery, providing support to
     * adjust its internal logic/filters before query execution
     */
    public static interface MongoLazyDataModelConsumer extends Serializable {

        /**
         * Modifies/processes provided LazyQuery instance
         *
         * @param lazyQuery {@link LazyQuery}, that should be processed
         */
        void accept(LazyQuery lazyQuery);
    }

}
