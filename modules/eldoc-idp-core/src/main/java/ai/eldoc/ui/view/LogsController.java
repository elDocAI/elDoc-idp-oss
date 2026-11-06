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
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.LogDao;
import ai.eldoc.data.MongoLazyDataModel;
import ai.eldoc.model.DateRange;
import ai.eldoc.model.Log;
import ai.eldoc.service.LogHelper;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.ViewScoped;
import java.io.Serializable;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Logs View Controller
 */
@Named
@ViewScoped
public class LogsController implements Serializable {

    private static final long serialVersionUID = -7297820522234237228L;

    @Inject
    private LogDao _logsDao;

    @Inject
    private UserContext _userContext;

    private MongoLazyDataModel<Log> _logsLazyDS;


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public MongoLazyDataModel<Log> getLogsLazyDS(String workflowId) {
        if (_logsLazyDS == null) {
            _logsLazyDS = new MongoLazyDataModel<>(_logsDao);
            _logsLazyDS.getFilters().put(Log._SEVERITY, List.of(LogHelper.WARNING, LogHelper.SUCCESS, LogHelper.INFO, LogHelper.ERROR));
            _logsLazyDS.setFilterIgnoreEmptyListForKey(Log._SYSTEM_ID);

            if (isBlank(workflowId)) {
                // For case when Logs viewed on the logs page set defult filter for the today only
                DateRange dateRange = new DateRange();
                dateRange.setDateFrom(new Date(), ZoneId.of(_userContext.getTimezone()));
                dateRange.setDateTo(new Date(), ZoneId.of(_userContext.getTimezone()));
                _logsLazyDS.getFilters().put(Log._TIMESTAMP, dateRange.toDateRangeList());
            } else {
                // For case when Logs viewed on the Workflow page
                // Filters should use $in/$nin with lists to avoid matching corresponding fields with regex
                _logsLazyDS.setConstraintFilters(Map.of(Log._ENTITY_RELATED_ID, List.of(workflowId)));
            }
        }
        return _logsLazyDS;
    }


    public MongoLazyDataModel<Log> getLogsLazyDS() {
        return _logsLazyDS;
    }


    public List<SelectItem> getLogLevelSI() {
        return List.of(LogHelper.SUCCESS, LogHelper.INFO, LogHelper.WARNING, LogHelper.ERROR).stream()
                .map(logLevel -> {
                    return new SelectItem(logLevel, Log.getSeverityText(logLevel));
                })
                .collect(Collectors.toList());
    }


    public String getLink(Log log) {
        if (isBlank(log.getEntityType())) {
            // Do not show links in case Entity Type is unknown
            return null;
        }

        if (log.getEntityType().startsWith(Log.ENTITY_TYPE_WORKFLOW)) {
            return Pages.PAGE_WORKFLOW + "?id=" + log.getEntityRelatedId();
        } else if (Log.ENTITY_TYPE_WORKFLOW_TASK.equals(log.getEntityType()) && isNotBlank(log.getEntityRelatedId())) {
            return Pages.PAGE_WORKFLOW + "?id=" + log.getEntityRelatedId();
        } else if (Log.ENTITY_TYPE_FORMDEF.equals(log.getEntityType())) {
            return Pages.PAGE_ADMIN_FORM + "?id=" + log.getEntityId();
        }

        return null;
    }


    /**
     * Defines whether Stack Trace available for review via UI (by adding respective css-class)
     *
     * @param logType
     * @return
     */
    public String getSeverityClass(int logType) {
        if (LogHelper.ERROR != logType) {
            return null;
        }
        return _userContext.isAdminSysAny() ? "showLogDetails" : null;
    }


    public String getLogText(Log log) {
        if ("id00409".equals(log.getEventId())) {
            return format(log.getEvent(), log.getProperty(Log.PROP_COUNT, 0));
        }

        return log.getEvent();
    }

}
