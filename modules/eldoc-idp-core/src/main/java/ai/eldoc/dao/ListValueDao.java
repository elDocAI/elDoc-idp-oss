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

import ai.eldoc.model.AclContext;
import ai.eldoc.model.ListValue;
import java.util.List;


/**
 * ListValue DAO
 */
public interface ListValueDao extends BasicLazyDao<ListValue> {

    /**
     * Finds all active ListValues by foreign key.In case key is null or empty string - finds
     * ListValue records where foreign key is not set, in such case all 1st level ListValue records
     * are returned only
     *
     * @param idKf - foreign key or null
     * @param aclContext
     * @return
     */
    public List<ListValue> findAllByKF(String idKf, AclContext aclContext);


    /**
     * Finds all ListValue records by foreign key.In case foreign key is null or empty
     * string - finds all ListValue records where foreign key is not set, in such case all 1st level ListValue records
     * are returned only
     *
     * @param idKf - foreign key or null
     * @param searchFT
     * @param lang language 2-letters code like uk for Ukrainian to be used with FT-search
     * @param showDeletedItems
     * @param aclContext
     * @return
     */
    public List<ListValue> findAllByKF(String idKf, String searchFT, String lang, boolean showDeletedItems, AclContext aclContext);


    /**
     * Returns listValue by system type
     *
     * @param systemType
     * @return
     */
    public ListValue findByType(int systemType);


    /**
     * Finds ListValue model by External Id and Foreign Key (at least one of these arguments must be given)
     *
     * @param idExt External ID
     * @param idKf ListValue Foreign key (Parent ListValue ID)
     * @return
     */
    public List<ListValue> findAllByIdExtAndKF(String idExt, String idKf);
}
