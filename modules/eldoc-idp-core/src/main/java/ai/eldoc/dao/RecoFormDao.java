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

import ai.eldoc.model.reco.RecoForm;
import java.util.List;
import java.util.Set;


/**
 * RecoForm DAO Interface
 */
public interface RecoFormDao extends BasicLazyDao<RecoForm>, FileAwareDao {

    public List<RecoForm> findAll();


    /**
     * Finds RecoForm Model by its name (returns first occurrence in case several RecoForms have the same name)
     *
     * @param name
     * @return
     */
    public RecoForm findByName(String name);


    /**
     * Returns all RecoForms by given status
     *
     * @param statusList
     * @return
     */
    public List<RecoForm> findAllByStatus(List<Integer> statusList);


    /**
     * Returns all tags (unique only) used with RecoForm Models
     *
     * @return
     */
    public Set<String> getTagsDistinct();

}
