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

import ai.eldoc.model.config.Config;
import java.util.List;


/**
 * Config DAO
 */
public interface ConfigDao extends BasicDao<Config> {

    public <T extends Config> List<T> findAllByType(Class<T> typeClass);

    public <T extends Config> T findByType(Class<T> typeClass);

    public <T extends Config> T findByTypeAndId(Class<T> typeClass, String id);

    public <T extends Config> T findByTypeAndKey(Class<T> typeClass, String key);

}
