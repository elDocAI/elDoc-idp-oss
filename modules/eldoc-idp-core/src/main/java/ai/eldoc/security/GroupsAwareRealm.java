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
package ai.eldoc.security;

import org.apache.shiro.subject.PrincipalCollection;
import java.util.Set;


/**
 * Groups Aware Realm Interface
 */
public interface GroupsAwareRealm {

    /**
     * Returns groups for the given principal (returned value can be a cached value by realm)
     *
     * @param pc
     * @return
     */
    public Set<String> getGroups(final PrincipalCollection pc);

}
