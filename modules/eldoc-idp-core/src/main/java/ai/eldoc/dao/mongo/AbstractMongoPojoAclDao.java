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
package ai.eldoc.dao.mongo;

import ai.eldoc.dao.BasicAclDao;
import ai.eldoc.exceptions.AclMissingException;
import ai.eldoc.exceptions.AclReadDeniedException;
import ai.eldoc.model.*;
import org.bson.conversions.Bson;


/**
 * Abstract Mongo POJO (ACL Enabled) DAO
 * @param <T> type of the entity for which this DAO is created
 */
public abstract class AbstractMongoPojoAclDao<T extends BasicMetaAwareModel & BasicAclAwareModel> extends AbstractMongoPojoDao<T> implements BasicAclDao<T> {

    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Custom ACL isReadable check implementation per specific DAO
     * when there is a need to check for custom fields, etc.
     *
     * @param entity guaranteed not null
     * @param aclContext guaranteed not null
     * @return
     */
    protected boolean aclIsReadableCustomCheck(T entity, AclContext aclContext) {
        return false; // default implementation have to return false
    }


    /**
     * Custom ACL isWritable check implementation per specific DAO
     * when there is a need to check for custom fields, etc.
     *
     * @param entity guaranteed not null
     * @param aclContext guaranteed not null
     * @return
     */
    protected boolean aclIsWritableCustomCheck(T entity, AclContext aclContext) {
        return false; // default implementation have to return false
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public T find(String id, AclContext aclContext) throws AclReadDeniedException {
        if (aclContext == null) {
            throw new AclMissingException();
        }
        T entry = super.find(id);
        if (entry != null && !(entry.getAcl().isReadable(aclContext) || aclIsReadableCustomCheck(entry, aclContext))) {
            throw new AclReadDeniedException(entry.getClass().getSimpleName(), id, aclContext.getSystemId());
        }
        return entry;
    }


    public T find(Bson filter, AclContext aclContext) throws AclReadDeniedException {
        if (aclContext == null) {
            throw new AclMissingException();
        }
        T entry = super.find(filter);
        if (entry != null && !(entry.getAcl().isReadable(aclContext) || aclIsReadableCustomCheck(entry, aclContext))) {
            throw new AclReadDeniedException(entry.getClass().getSimpleName(), entry.getId(), aclContext.getSystemId());
        }
        return entry;
    }

}
