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

import ai.eldoc.model.BpmProcessDefinition;
import java.util.List;

/**
 * BPMProcessDefinition DAO
 */
public interface BpmProcessDefinitionDao extends BasicLazyDao<BpmProcessDefinition> {

    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Finds and returns list with all BpmProcessDefinition
     */
    public List<BpmProcessDefinition> findAll();


    /**
     * Finds list of BPM Process Definitions by the given IDs
     */
    public List<BpmProcessDefinition> findAllByIdList(List<String> sysProcessIdList);

}
