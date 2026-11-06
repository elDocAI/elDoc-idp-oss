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
import ai.eldoc.model.Reminder;
import java.util.Date;
import java.util.List;


/**
 * Reminder DAO
 */
public interface ReminderDao extends BasicLazyDao<Reminder> {


    /**
     * Finds and returns list of reminder models where reminder date belongs to date range: [from:to)
     *
     * @param from From Date - can be null for avoiding limiting the filter by From
     * @param to To Date - can be null for avoiding limiting the filter by To
     * @return
     */
    public List<Reminder> getByDateRange(Date from, Date to);


    /**
     * Checks whether a given user has any overdue reminders set for his name or for any of his groups
     *
     * @param aclContext - AclContext of the user to check
     * @return true if user has any overdue reminders, false otherwise
     */
    public boolean hasOverdue(AclContext aclContext);


    /**
     * Finds and returns reminders by given refId and given user
     *
     * @param aclContext - AclContext of the user to check
     * @param refId - ref item Id
     * @param authorOwnedOnly - true search only by author field, false - searches by "reminder to" field EXCLUDING current user in author field
     * @return List of reminders
     */
    public List<Reminder> findAllByRefId(AclContext aclContext, String refId, boolean authorOwnedOnly);


    /**
     * Checks whether given user has any active reminders set for his name or for any of his groups and for the given refId
     *
     * @param aclContext
     * @param refId - document Id
     * @return
     */
    public boolean hasAnyByRefId(AclContext aclContext, String refId);

}
