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

import org.apache.commons.lang3.SerializationUtils;
import org.primefaces.model.SortOrder;
import java.io.Serial;
import java.io.Serializable;


/**
 * Lazy Query Model for usage with Workflows
 */
public final class WorkflowLazyQuery extends LazyQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = -519661002821249680L;


    public enum VIEW {
        ALL,
        INBOX,
        RECOQUEUE,
        DRAFTS,
        MYDOCS,
        INWORK,
        PROCESSED,
        PROCESSED_AND_COMPLETED,
        REPOSITORY,
        ARCHIVE,
        FORREF,
        ADMIN;


        /**
         * Returns VIEW value based on the given string or defaults to VIEW.ALL
         *
         * @param viewName
         * @return
         */
        public static VIEW fromStringOrDefault(String viewName) {
            if (viewName == null) {
                return ALL;
            }
            try {
                return VIEW.valueOf(viewName);
            } catch (Exception e) {
                return ALL; // Fallback to default
            }
        }
    }

    public static final String PARAM_LANG = "__LANG";
    public static final String PARAM_DATE_FORMAT = "__DATE_FORMAT";
    public static final String PARAM_TIME_FORMAT = "__TIME_FORMAT";
    public static final String PARAM_TIMEZONE = "__TIMEZONE";
    public static final String PARAM_EXPORT_ATTACHMENTS = "__EXPORT_ATTACHMENTS";

    private VIEW _view;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Returns LazyQuery for first N documents in Inbox
     *
     * @param pageSize sets the number of documents to be returned starting from the document with index 0
     * @param skipCount if true, skips counting the number of documents in the lazy result
     * @return 1st N documents in Inbox
     */
    public static WorkflowLazyQuery getInboxLQ(int pageSize, boolean skipCount) {
        WorkflowLazyQuery lazyQuery = new WorkflowLazyQuery();
        lazyQuery.setView(VIEW.INBOX);
        lazyQuery.setFirst(0);
        lazyQuery.setPageSize(pageSize);
        lazyQuery.setSkipCount(skipCount);
        return lazyQuery;
    }

    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * Prepares UI sort filter
     *
     * @param sortField
     * @param sortOrder
     */
    @Override
    public void setSortFieldAndOrder(String sortField, SortOrder sortOrder) {
        _sort.clear();
        if (sortField != null && !sortField.isEmpty() && (SortOrder.ASCENDING.equals(sortOrder) || SortOrder.DESCENDING.equals(sortOrder))) {
            int order = (SortOrder.ASCENDING.equals(sortOrder) ? 1 : -1);
            _sort.put(sortField, order);
        }
    }


    public VIEW getView() {
        return _view;
    }


    public WorkflowLazyQuery setView(VIEW view) {
        _view = view;
        return this;
    }


    /**
     * Returns clone of this WorkflowLazyQuery<br>
     *
     * @return
     */
    @Override
    public WorkflowLazyQuery clone() {
        return SerializationUtils.clone(this);
    }

}
