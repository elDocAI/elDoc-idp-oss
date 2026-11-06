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

import ai.eldoc.dao.DaoUtils;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Database Utils Service<br>
 *
 * Provides utility methods for working with Tenant's and Default databases
 */
@ApplicationScoped
public class DbUtilsService {

    private static final Logger LOG = LoggerFactory.getLogger(DbUtilsService.class);


    /**
     * Executes Index Housekeeping for all collections
     */
    public static void doAllIndexesHousekeeping() {
        LOG.info("Indexes Housekeeping for all collections initiated...");
        DaoUtils.getWorkflowDaoTenant().doIndexHousekeep();
        DaoUtils.getWorkflowTaskDaoTenant().doIndexHousekeep();
        DaoUtils.getFormDefDao().doIndexHousekeep();
        DaoUtils.getLogDao().doIndexHousekeep();
        DaoUtils.getReactionDao().doIndexHousekeep();
        DaoUtils.getReminderDao().doIndexHousekeep();
        DaoUtils.getNumSequenceDao().doIndexHousekeep();
        DaoUtils.getBpmProcessDefinitionDao().doIndexHousekeep();
        DaoUtils.getRecoFormDao().doIndexHousekeep();
        DaoUtils.getCodeSnippetDao().doIndexHousekeep();
        LOG.info("Indexes Housekeeping for all collections done");
    }

}
