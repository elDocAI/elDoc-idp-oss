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
package ai.eldoc.model;

import java.util.Objects;


/**
 * File Template aware model<br>
 * Models which declare this interface provide functionality for working with embedded template files
 */
public interface FileTemplateAwareModel extends FileTemplateAwareBaseModel<TemplateRef> {

    public static final String _TEMPLATES = "_tmplts";


    /**
     * Returns true in case the given DbFileRef is a Template or based on the Template (meaning revision to the template)
     * In other cases - returns false
     *
     * @param dbFileRef root revision of the DbFileRef
     * @return
     */
    @Override
    default public boolean isTemplate(DbFileRef dbFileRef) {
        return getTemplates().stream().anyMatch(template -> Objects.equals(template.getDbFileRef(), dbFileRef));
    }

}
