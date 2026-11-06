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
package ai.eldoc.dao;

import ai.eldoc.model.Log;
import com.mongodb.client.ClientSession;
import java.time.Instant;
import java.util.List;


/**
 * Logs DAO Interface
 */
public interface LogDao extends BasicLazyDao<Log> {

    public List<Log> findByEntityId(String entId);


    public List<Log> findByEntityRelatedId(String entRltId);


    public int countIdpPages(Instant sinceDate);


    /**
     * Saves event either increasing Count field of existing entry, if there's already one
     * similar for same day, or creates new log
     *
     * @param dbClientSession
     * @param log
     * @return
     */
    public Log createOrIncrementDailyCount(ClientSession dbClientSession, Log log);

}
