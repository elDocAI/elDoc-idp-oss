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

package ai.eldoc.model.filemeta;

import ai.eldoc.model.TagAwareModel;
import jakarta.json.bind.annotation.JsonbProperty;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.jspecify.annotations.Nullable;
import java.io.Serial;
import java.util.*;

public class WorkflowTaskFileMeta extends Sha256FileMeta implements TagAwareModel {

    @Serial
    private static final long serialVersionUID = -6948195041310926959L;

    private List<String> _tags;


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    @Override
    @Nullable
    @JsonbProperty(_TAGS)
    @BsonProperty(_TAGS)
    public List<String> getTags() {
        return _tags;
    }


    @Override
    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS_JSON)
    public void setTags(@Nullable List<String> tags) {
        _tags = normalizeTags(tags);
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        WorkflowTaskFileMeta that = (WorkflowTaskFileMeta) o;
        return Objects.equals(_tags, that._tags);
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _tags);
    }
}
