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

import ai.eldoc.model.BasicModel;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.model.*;
import java.util.*;


/**
 * Simple Lazy Data Model implementation for wrapping List collection
 * and uses reflection to generate Getter name for accessing values by field names
 * @param <T>
 */
public class SimpleLazyDataModel<T extends BasicModel> extends LazyDataModel<T> {

    private static final long serialVersionUID = 3702064786623806447L;

    private final List<T> _dataSource;
    private final Map<String, SortMeta> _defaultSortMeta = new HashMap<>(); // Default sortMeta
    private final Map<String, Object> _filters = new HashMap<>();


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public SimpleLazyDataModel(List<T> dataSource) {
        _dataSource = dataSource;
        setRowCount(_dataSource.size());
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public T getRowData(String rowKey) {
        return _dataSource.stream().filter(model -> model.getId().equals(rowKey)).findFirst().orElse(null);
    }


    @Override
    public String getRowKey(T model) {
        return model.getId();
    }


    @Override
    public List<T> load(int first, int pageSize, Map<String, SortMeta> sortMeta, Map<String, FilterMeta> filters) {
        List<T> data;

        // filter
        Map<String, FilterMeta> combinedFilters = getCombinedFilters(filters);
        if (!combinedFilters.isEmpty()) {
            List<T> filteredData = new ArrayList<>();
            for (T model : _dataSource) {
                boolean match = false;

                for (Map.Entry<String, FilterMeta> filter : combinedFilters.entrySet()) {
                    try {
                        if (filter.getKey() == null || filter.getValue() == null) {
                            match = true;
                            continue; // Not valid filter, mark entry as matched and continue with other filters (if any)
                        }

                        if (!PropertyUtils.isReadable(model, filter.getKey())) {
                            break; // Property is not readable or not present - break here, no need to proceed with filters
                        }

                        String fieldValue = String.valueOf(PropertyUtils.getSimpleProperty(model, filter.getKey()));

                        switch (filter.getValue().getMatchMode()) {
                            case STARTS_WITH:
                                match = StringUtils.startsWithIgnoreCase(fieldValue, String.valueOf(filter.getValue().getFilterValue()));
                                break;

                            case EQUALS:
                                match = StringUtils.equalsIgnoreCase(fieldValue, String.valueOf(filter.getValue().getFilterValue()));
                                break;

                            case ENDS_WITH:
                                match = StringUtils.endsWithIgnoreCase(fieldValue, String.valueOf(filter.getValue().getFilterValue()));
                                break;

                            case CONTAINS:
                            default:
                                match = StringUtils.containsIgnoreCase(fieldValue, String.valueOf(filter.getValue().getFilterValue()));
                        }
                    } catch (Exception e) {
                        match = false;
                    }
                }

                if (match) {
                    filteredData.add(model);
                }
            }
            data = filteredData;
        } else {
            data = _dataSource;
        }

        super.setRowCount(data.size());
        // Data changed in the way, that requested page is now empty - we need to update 'first' and query db again
        // Page index on UI and pages will be silently updated, as we updated rowCount, but we need to retrieve valid
        // data from DB with new offset ('first')
        // See primefaces issue 1921
        first = super.recalculateFirst(first, pageSize, data.size());

        // sort
        Map<String, SortMeta> sortMetaToUse = !_defaultSortMeta.isEmpty() ? _defaultSortMeta : sortMeta;
        if (sortMetaToUse != null && !sortMetaToUse.isEmpty()) {
            Map.Entry<String, SortMeta> sortFieldMeta = sortMetaToUse.entrySet().iterator().next();
            if (sortFieldMeta.getValue() != null && sortFieldMeta.getValue().getField() != null && sortFieldMeta.getValue().getOrder() != null) {
                Collections.sort(data, new SimpleComparator(sortFieldMeta.getValue().getField(), sortFieldMeta.getValue().getOrder()));
            }
        }

        // paginate
        if (getRowCount() > pageSize) {
            int last = first + pageSize < getRowCount() ? first + pageSize : getRowCount();
            return data.subList(first, last);
        } else {
            return data;
        }
    }


    @Override
    public int count(Map<String, FilterMeta> filterBy) {
        return 0;
    }


    private Map<String, FilterMeta> getCombinedFilters(Map<String, FilterMeta> filters) {
        Map<String, FilterMeta> resultFilters = new HashMap<>();

        // Apply preset filters (lowest-priority)
        getFilters().entrySet().stream()
                .filter(filter -> filter.getValue() != null)
                .map(filter -> {
                    return FilterMeta.builder()
                            .field(filter.getKey())
                            .filterValue(filter.getValue())
                            .matchMode(MatchMode.CONTAINS)
                            .build();
                })
                .forEach(filter -> resultFilters.put(filter.getField(), filter));

        // Apply UI-filters (middle-priority) with respective handling of FilterMeta object
        if (filters != null) {
            resultFilters.putAll(filters);
        }

        return resultFilters;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Map<String, SortMeta> getDefaultSortMeta() {
        return _defaultSortMeta;
    }


    public Map<String, Object> getFilters() {
        return _filters;
    }


    //
    // Inner classes ------------------------------------------------------------------------------
    //
    private class SimpleComparator implements Comparator<T> {

        private final String _sortField;
        private final SortOrder _sortOrder;


        public SimpleComparator(String sortField, SortOrder sortOrder) {
            _sortField = sortField;
            _sortOrder = sortOrder;
        }


        @Override
        public int compare(T c1, T c2) {
            try {
                if (!(PropertyUtils.isReadable(c1, _sortField) && PropertyUtils.isReadable(c2, _sortField))) {
                    return 0;
                }

                Object value1 = PropertyUtils.getSimpleProperty(c1, _sortField);
                Object value2 = PropertyUtils.getSimpleProperty(c2, _sortField);
                if (!(value1 instanceof Comparable && value2 instanceof Comparable)) {
                    return 0;
                }

                int compareRslt = ((Comparable) value1).compareTo(value2);
                return SortOrder.ASCENDING.equals(_sortOrder) ? compareRslt : -1 * compareRslt;
            } catch (Exception e) {
                return 0;
            }
        }
    }

}
