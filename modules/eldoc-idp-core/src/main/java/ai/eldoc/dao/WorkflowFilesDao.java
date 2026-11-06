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

import ai.eldoc.model.DbFile;
import ai.eldoc.model.DbFileRef;
import java.util.List;


/**
 * Workflow Model Files DAO Interface
 */
public interface WorkflowFilesDao extends FilesRevisionDao {


    /**
     * Returns files from the db found by SHA256 hash
     *
     * @param sha256hash hash value to be queried
     * @param limit maximum number of results to be returned
     * @return List of files with a given SHA256 hash
     */
    public List<DbFile> findAllBySha256(String sha256hash, int limit);


    /**
     * @return all Workflow files that are still pending processing
     */
    public List<DbFileRef> findPendingTextIndexProcessing();

}
