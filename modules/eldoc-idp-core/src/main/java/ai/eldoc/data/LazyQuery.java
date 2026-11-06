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

import ai.eldoc.model.DateRange;
import ai.eldoc.model.directory.DirectoryItem;
import org.apache.commons.lang3.SerializationUtils;
import org.bson.Document;
import org.primefaces.model.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Model for holding lazy query parameters for Lazy DAO
 */
public class LazyQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 6144671623775541164L;
    private static final Logger LOG = LoggerFactory.getLogger(LazyQuery.class);

    protected int _first = 0;
    protected int _pageSize = 1;
    protected final Document _sort = new Document();
    protected final Document _filter = new Document();
    protected String _searchFT;
    private boolean _skipCount;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Sets filters by handling passed values according to the defined rules
     *
     * @param filters
     */
    public void setFilters(Map<String, Object> filters) {
        _filter.clear();
        if (filters != null) {
            filters.forEach(this::addFilter);
        }
    }


    /**
     * Add filter by handling a given value according to the defined rules
     *
     * @param key
     * @param value
     */
    public void addFilter(String key, Object value) {
        try {
            List<String> dbNativeOperators = List.of("$and", "$not", "$nor", "$or", "$nin", "$in", "$gt", "$gte", "$lt", "$lte");
            if (dbNativeOperators.contains(key) || (value instanceof Map valueMap && valueMap.keySet().stream().anyMatch(dbNativeOperators::contains))) {
                // This is already a MongoDB query - put it as is
                // IMPORTANT: This covers cases like in the BpmProcessDefController or FormDefController
                _filter.put(key, value);
            } else if (value instanceof String strValue && isNotBlank(strValue)) {
                Pattern regex = Pattern.compile(Pattern.quote(strValue.trim()), Pattern.CASE_INSENSITIVE); // TODO: this should not be applied for constrainFilters passed from MongoLazyDataModel
                _filter.put(key, regex);
            } else if (value instanceof List listValue) {
                if (!listValue.isEmpty() && listValue.getFirst() instanceof DirectoryItem) {
                    // if value is actually a list of DirectoryItems - convert it to list of theirs idSys
                    _filter.put(key, new Document("$in", listValue.stream().map(di -> ((DirectoryItem) di).getIdSys()).toList()));
                } else {
                    // In all other cases - add list as is
                    _filter.put(key, new Document("$in", listValue));
                }
            } else if (value instanceof Object[] arrValue) {
                _filter.put(key, new Document("$in", Arrays.asList(arrValue)));
            } else if (value instanceof DateRange dtrValue) {
                addFilterDateRange(key, dtrValue);
            } else if (value instanceof Map) {
                // Map value added as is
                _filter.put(key, value);
            } else {
                _filter.put(key, value);
            }
        } catch (Exception e) {
            LOG.error("Error applying filter key={} value={}", key, value, e);
        }
    }


    private void addFilterDateRange(String key, DateRange dateRange) {
        if (dateRange.getDateFrom() != null && dateRange.getDateTo() != null) {
            _filter.put(key, new Document("$gte", dateRange.getDateFrom()).append("$lte", dateRange.getDateTo()));
        } else if (dateRange.getDateFrom() != null) {
            _filter.put(key, new Document("$gte", dateRange.getDateFrom()));
        } else if (dateRange.getDateTo() != null) {
            _filter.put(key, new Document("$lte", dateRange.getDateTo()));
        }
    }


    public void setSortFieldAndOrder(String sortField, SortOrder sortOrder) {
        // prepare UI sort filter
        _sort.clear();
        if (isNotBlank(sortField) && (SortOrder.ASCENDING.equals(sortOrder) || SortOrder.DESCENDING.equals(sortOrder))) {
            _sort.put(sortField, SortOrder.ASCENDING.equals(sortOrder) ? 1 : -1);
        }
    }


    /**
     * Returns a clone of this LazyQuery<br>
     *
     * @return
     */
    public LazyQuery clone() {
        return SerializationUtils.clone(this);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public int getFirst() {
        return _first;
    }


    /**
     * Sets the index of the first document to be retrieved, set it to -1 to get all documents (no lazy)
     *
     * @param first
     * @return
     */
    public LazyQuery setFirst(int first) {
        _first = first;
        return this;
    }


    public int getPageSize() {
        return _pageSize;
    }


    /**
     * Sets the number of documents to be retrieved. To get all documents, set it to -1
     *
     * @param pageSize
     * @return
     */
    public LazyQuery setPageSize(int pageSize) {
        _pageSize = pageSize;
        return this;
    }


    public Document getSort() {
        return _sort;
    }


    /**
     * Sets sorting
     *
     * @param sort
     * @return
     */
    public LazyQuery setSort(Document sort) {
        _sort.clear();
        if (sort != null && !sort.isEmpty()) {
            _sort.putAll(sort);
        }
        return this;
    }


    public Document getFilter() {
        return _filter;
    }


    public LazyQuery setFilter(Document filter) {
        _filter.clear();
        if (filter != null && !filter.isEmpty()) {
            _filter.putAll(filter);
        }
        return this;
    }


    public String getSearchFT() {
        return _searchFT;
    }


    public LazyQuery setSearchFT(String searchFT) {
        _searchFT = searchFT;
        return this;
    }


    public boolean isSkipCount() {
        return _skipCount;
    }


    public LazyQuery setSkipCount(boolean skipCount) {
        _skipCount = skipCount;
        return this;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return "LazyQuery{" +
                "_first=" + _first +
                ", _pageSize=" + _pageSize +
                ", _sort=" + _sort +
                ", _filter=" + _filter +
                ", _searchFT='" + _searchFT + '\'' +
                ", _skipCount=" + _skipCount +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LazyQuery lazyQuery = (LazyQuery) o;
        return _first == lazyQuery._first
                && _pageSize == lazyQuery._pageSize
                && _skipCount == lazyQuery._skipCount
                && Objects.equals(_sort, lazyQuery._sort)
                && Objects.equals(_filter, lazyQuery._filter)
                && Objects.equals(_searchFT, lazyQuery._searchFT);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_first, _pageSize, _sort, _filter, _searchFT, _skipCount);
    }

}