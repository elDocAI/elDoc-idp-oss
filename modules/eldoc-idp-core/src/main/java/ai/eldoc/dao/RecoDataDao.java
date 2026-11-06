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

import ai.eldoc.model.reco.RecoData;
import ai.eldoc.model.reco.RecoFormResult;
import com.mongodb.client.ClientSession;
import java.util.List;


/**
 * Recognition Data Models DAO Interface
 */
public interface RecoDataDao extends BasicLazyDao<RecoData>, FileAwareDao {

    /**
     * Finds all records by the given refId
     *
     * @param refId
     * @return
     */
    public List<RecoData> findAllByRefId(String refId);


    /**
     * Deletes all records by the given refId (including attached files in DB)
     *
     * @param dbClientSession
     * @param refId
     */
    public void deleteAllByRefId(ClientSession dbClientSession, String refId);


    /**
     * Finds RecoData by its attachment id (fileId)
     *
     * @param fileId
     * @return
     */
    public RecoData findByFileId(String fileId);


    public RecoFormResult getRecoFormResult(RecoData recoData);
}
