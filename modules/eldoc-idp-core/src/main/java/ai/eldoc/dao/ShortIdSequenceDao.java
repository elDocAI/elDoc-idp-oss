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


/**
 * ShortId Sequence DAO
 */
public interface ShortIdSequenceDao {


    public String getCollectionName();


    /**
     * Inits ShortID Sequence Counters
     */
    public void initCounters();


    /**
     * Returns uppercased HEX sequence value for Workflow models
     *
     * @return
     */
    public String getNextSequenceHEXforWorkflow();


    /**
     * Returns sequence value for FormDefinition models in format (prefix + integer)<br>
     * Example: in case prefix = "fd", return value will be like "fd14"
     *
     * @param prefix to be used in the generated ID, something like "fd" for dynamic forms
     * @return generated id for FormDefinition models in format like "fd15"
     */
    public String getNextSequenceForFormDefinition(final String prefix);

}
