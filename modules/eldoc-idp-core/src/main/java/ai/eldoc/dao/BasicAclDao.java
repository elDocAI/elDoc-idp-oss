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

import ai.eldoc.exceptions.AclReadDeniedException;
import ai.eldoc.model.AclContext;
import ai.eldoc.model.BasicAclAwareModel;


/**
 * Interface declares that DAO follows ACL principles
 * @param <T>
 */
public interface BasicAclDao<T extends BasicAclAwareModel> extends BasicLazyDao<T> {

    /**
     * Finds and returns Model Entity by id
     *
     * @param id String representation of ObjectId, or just a string id representation
     * @param aclContext security context under which current action is being performed
     * @return null in case entity not found or Entity object
     * @throws AclReadDeniedException in case entity was found but no Read Permission is available
     */
    public T find(String id, AclContext aclContext) throws AclReadDeniedException;

}
