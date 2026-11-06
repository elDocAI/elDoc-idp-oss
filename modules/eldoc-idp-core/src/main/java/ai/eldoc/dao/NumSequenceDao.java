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

import ai.eldoc.model.NumSequence;
import java.util.List;


/**
 * NumberSequence DAO
 */
public interface NumSequenceDao extends BasicLazyDao<NumSequence> {

    /**
     * Returns all NumSequence models
     *
     * @return
     */
    public List<NumSequence> findAll();


    /**
     * Finds NumSequence model by name (name serves as counter id as well)
     *
     * @param counterName
     * @return model in case found or null
     */
    public NumSequence findByName(String counterName);


    /**
     * Returns counter next value (in system defined TimeZone) and updates counter model in DB in case requested
     *
     * @param counterName
     * @param updateCounter
     * @return
     */
    public String getValue(String counterName, boolean updateCounter);

}
