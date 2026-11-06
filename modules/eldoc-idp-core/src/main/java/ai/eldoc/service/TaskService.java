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

import ai.eldoc.data.WorkflowLazyQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Named;
import java.util.*;


/**
 * Class holds tasks of users application wise
 */
@Named
@ApplicationScoped
public class TaskService {

    private final Map<String, WorkflowLazyQuery> _usersExportRequests = new HashMap<>();
    private final Map<String, String> _usersExportDownloadRequests = new HashMap<>();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public String setExportTask(WorkflowLazyQuery workflowLazyQuery) {
        String exportId = UUID.randomUUID().toString();
        _usersExportRequests.put(exportId, workflowLazyQuery);
        return exportId;
    }


    public WorkflowLazyQuery getExportTask(String exportTaskId) {
        return _usersExportRequests.remove(exportTaskId);
    }


    public String setExportDownloadTask(String task) {
        String exportId = UUID.randomUUID().toString();
        _usersExportDownloadRequests.put(exportId, task);
        return exportId;
    }


    public String getExportDownloadTask(String exportTaskId) {
        return _usersExportDownloadRequests.remove(exportTaskId);
    }


    public static TaskService getInstance() {
        return CDI.current().select(TaskService.class).get();
    }
}
