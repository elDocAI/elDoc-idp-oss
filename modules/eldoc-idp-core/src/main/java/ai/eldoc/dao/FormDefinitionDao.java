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

import ai.eldoc.model.FormDefinition;
import java.util.List;


public interface FormDefinitionDao extends BasicLazyDao<FormDefinition>, FileAwareDao {

    /**
     * Returns all FormDefinition models
     *
     * @return FormDefinition models, or empty list in case none found
     */
    public List<FormDefinition> findAll();


    /**
     * Returns FormDefinition found by formId (webId), or null in case none found (restricted by ACCESS_CREATE)
     *
     * @param systemId of the user (can be {@code null} when {@code excludeCanCreateCheck == true})
     * @param webId webId of the FormDefinition (e.g.: fd1, fd2, frd1), case-insensitive
     * @param skipAccessCreateCheck do not perform check by the ACCESS_CREATE field
     * @return FormDefinition object or null in case nothing found (restricted by the ACCESS_CREATE)
     */
    public FormDefinition findByWebId(String webId, boolean skipAccessCreateCheck, final String systemId);


    /**
     * Returns all FormDefinition models based on the given statuses list and also taking into consideration the ACCESS_CREATE field
     *
     * @param statusList list of statuses
     * @param skipAccessCreateCheck do not perform check by the ACCESS_CREATE field
     * @param systemId of the user (can be {@code null} when {@code skipAccessCreateCheck == true})
     * @return FormDefinition models based on the given statuses list and also taking into consideration the ACCESS_CREATE field, or empty list in case nothing found (restricted by the ACCESS_CREATE)
     */
    public List<FormDefinition> findAllByStatus(List<Integer> statusList, boolean skipAccessCreateCheck, final String systemId);


    /**
     * Returns all FormDefinition models based on the given statuses list and retention actions
     *
     * @param statusList list of statuses
     * @param retentionActionList list of retention actions
     * @return FormDefinition models based on the given statuses list and retention action list (or empty list in case nothing found)
     */
    public List<FormDefinition> findAllByStatusAndRetentionAction(List<Integer> statusList, List<Integer> retentionActionList);

}
