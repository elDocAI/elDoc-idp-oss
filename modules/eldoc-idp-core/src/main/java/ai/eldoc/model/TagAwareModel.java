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

import org.apache.commons.lang3.StringUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.jspecify.annotations.Nullable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Interface defines Tag-Aware Model (for TAGs defined by end-users for models)
 */
public interface TagAwareModel {

    String _TAGS = "tags";
    String _TAGS_JSON = "tags";


    //
    // Methods definition -------------------------------------------------------------------------
    //

    /**
     * @return list of assigned tags. **Can be null.**
     */
    @Nullable
    List<String> getTags();


    /**
     * Sets a list of tags to the entity. If the list is empty - clears tags by setting null.
     *
     * @param tags Tags that should be set
     */
    void setTags(@Nullable List<String> tags);


    //
    // Methods default implementations ------------------------------------------------------------
    //


    /**
     * Clears tag from all unsupported characters
     *
     * @param origTag Tag, that should be cleared
     * @return Cleared tag
     */
    @BsonIgnore
    default String clearTag(String origTag) {
        if (origTag == null) {
            return "";
        }
        return origTag.replaceAll("[^0-9a-zA-Z\\-_.]", "").trim();
    }


    /**
     * Clears tags and returns only unique not-blank values
     *
     * @param tags Tags that should be cleared
     * @return Cleared tags with preserved order
     */
    @BsonIgnore
    default LinkedHashSet<String> clearTags(@Nullable List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return tags.stream()
                .map(this::clearTag).filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }


    /**
     * Clears tags and returns only unique not-blank values
     *
     * @param tags Tags that should be cleared
     * @return Cleared tags with preserved order
     */
    @BsonIgnore
    default @Nullable List<String> normalizeTags(@Nullable List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }

        return new ArrayList<>(clearTags(tags));
    }
}
