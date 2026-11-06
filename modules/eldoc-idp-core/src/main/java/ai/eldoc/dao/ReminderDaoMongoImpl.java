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

import ai.eldoc.dao.mongo.AbstractMongoPojoDao;
import ai.eldoc.model.AclContext;
import ai.eldoc.model.Reminder;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.IndexModel;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.conversions.Bson;
import java.time.Instant;
import java.util.*;
import static com.mongodb.client.model.Filters.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Reminder DAO MongoDB Implementation
 */
@ApplicationScoped
public class ReminderDaoMongoImpl extends AbstractMongoPojoDao<Reminder> implements ReminderDao {

    public static final String COLLECTION_NAME = "reminders";


    /**
     * {@inheritDoc}
     */
    @Override
    public List<Reminder> getByDateRange(Date from, Date to) {
        final List<Reminder> result = new ArrayList<>();
        Bson filter;
        if (from == null && to == null) {
            return result;
        } else if (from != null && to == null) {
            filter = gte(Reminder._REMINDER_AT, from);
        } else if (from == null && to != null) {
            filter = lte(Reminder._REMINDER_AT, to);
        } else {
            filter = and(
                    gte(Reminder._REMINDER_AT, from),
                    lte(Reminder._REMINDER_AT, to)
            );
        }
        getCollection().find(filter).into(result);
        return result;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasOverdue(AclContext aclContext) {
        Bson filter = and(
                in(Reminder._RECIPIENTS, aclContext.toSetSysIdGroupsUnits()),
                eq(Reminder._STATUS, Reminder.STATUS_ACTIVE),
                lte(Reminder._REMINDER_AT, Instant.now())
        );
        return (getCollection().countDocuments(filter, new CountOptions().collation(_collation)) > 0);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAnyByRefId(AclContext aclContext, String refId) {
        if (isBlank(refId)) {
            return false;
        }

        Bson filter = and(
                eq(Reminder._REF_ID, refId),
                or(
                        in(Reminder._RECIPIENTS, aclContext.toSetSysIdGroupsUnits()),
                        eq(Reminder._AUTHOR, aclContext.getSystemId())
                ),
                eq(Reminder._STATUS, Reminder.STATUS_ACTIVE)
        );

        return getCollection().countDocuments(filter, new CountOptions().collation(_collation)) > 0;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<Reminder> findAllByRefId(AclContext aclContext, String refId, boolean authorOwnedOnly) {
        List<Bson> filters = new ArrayList<>();

        if (isNotBlank(refId)) {
            filters.add(eq(Reminder._REF_ID, refId));
        }

        if (authorOwnedOnly) {
            filters.add(eq(Reminder._AUTHOR, aclContext.getSystemId()));
        } else {
            filters.add(or(
                    eq(Reminder._AUTHOR, aclContext.getSystemId()),
                    in(Reminder._RECIPIENTS, aclContext.toSetSysIdGroupsUnits())
            ));
        }

        filters.add(eq(Reminder._STATUS, Reminder.STATUS_ACTIVE));

        return getCollection()
                .find(and(filters))
                .collation(_collation)
                .into(new ArrayList<>());
    }


    //
    // DAO Specific Generic Methods ---------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    public String getCollectionName() {
        return COLLECTION_NAME;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MongoCollection<Reminder> getCollection() {
        return super.getCollection(Reminder.class);
    }


    //
    // DAO Index Housekeeping methods -------------------------------------------------------------
    //


    /**
     * {@inheritDoc}
     */
    @Override
    protected List<IndexModel> getIndexesList() {
        List<IndexModel> indexes = new ArrayList<>();
        indexes.add(indexModelAsc(Reminder._STATUS, indexOptions(null)));
        indexes.add(indexModelAsc(Reminder._REMINDER_AT, indexOptions(null)));
        indexes.add(indexModelAsc(Reminder._AUTHOR, indexOptionsText(null)));
        indexes.add(indexModelAsc(Reminder._REF_ID, indexOptionsText(null)));
        indexes.add(indexModelAsc(Reminder._RECIPIENTS, indexOptionsText(null)));
        indexes.add(indexModelAsc(Reminder._NOTE, indexOptionsText(null))); // Required for sort and filtering to work properly on UI
        return indexes;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void doIndexHousekeep() {
        doIndexHousekeep(getIndexesList());
    }
}
