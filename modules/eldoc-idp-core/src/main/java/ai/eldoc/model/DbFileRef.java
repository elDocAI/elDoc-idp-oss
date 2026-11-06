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

import jakarta.json.bind.annotation.JsonbProperty;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 * DbFileRef Model represents information of the file stored in the DB
 */
public final class DbFileRef implements Serializable {

    @Serial
    private static final long serialVersionUID = -7532277759170973573L;

    public static final String _ID = "id";
    public static final String _REF = "ref"; // Contains collectionName (without .files suffix for GridFS, e.g.: for "workflowsData.files" it stores value as "workflowsData")
    public static final String _TAGS = "tags";

    private String _id;
    private String _ref;
    private List<String> _tags;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    protected DbFileRef() {
        _tags = new ArrayList<>();
    }


    /**
     * Creates an instance of DbFileRef<br>
     * IMPORTANT: You should use method {@link ai.eldoc.dao.FilesDao#createDbFileRef(java.lang.String, java.util.List)}
     * from the DAO, which has created the file
     *
     * @param collectionName target collection name (usually bucket or GridFS name, without .files suffix for GridFS, e.g.: for "workflowsData.files" it stores value as "workflowsData")
     * @param fileId file id in the target bucket collection
     */
    public DbFileRef(String collectionName, String fileId) {
        this(collectionName, fileId, null);
    }


    /**
     * Creates an instance of DbFileRef<br>
     * IMPORTANT: You should use method {@link ai.eldoc.dao.FilesDao#createDbFileRef(java.lang.String, java.util.List)}
     * from the DAO, which has created the file
     *
     * @param collectionName target collection name (usually bucket or GridFS name, without .files suffix for GridFS, e.g.: for "wfs.files" it stores value as "wfs")
     * @param fileId file id in the target bucket collection
     * @param tags file tags
     */
    public DbFileRef(String collectionName, String fileId, List<String> tags) {
        this();
        _ref = collectionName;
        _id = fileId;
        addTags(tags);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Sets tags by checking the given argument for not NULL and taking only unique tag values.<br>
     * This method also ensures that only non-present tags are being added
     *
     * @param tags List of tags to add
     */
    public void addTags(List<String> tags) {
        if (tags == null) {
            return;
        }
        tags.forEach(newTag -> {
            if (newTag != null && !newTag.isBlank() && !_tags.contains(newTag)) {
                _tags.add(newTag);
            }
        });
    }


    /**
     * Adds tag to the list of tags stored within this model.<br>
     * Checks that tag is not NULL, not BLANK, and not already present in the tag collection
     *
     * @param tag Tag to add
     */
    public void addTag(String tag) {
        if (tag != null && !tag.isBlank() && !_tags.contains(tag)) {
            _tags.add(tag);
        }
    }


    /**
     * Removes tag from the list of tags stored within this model.<br>
     * Checks that tag is not NULL and not BLANK
     *
     * @param tag Tag to remove
     */
    public void removeTag(String tag) {
        if (tag != null && !tag.isBlank()) {
            _tags.removeAll(Collections.singleton(tag));
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_ID)
    @JsonbProperty(_ID)
    public String getId() {
        return _id;
    }


    @BsonProperty(_ID)
    @JsonbProperty(_ID)
    public void setId(String objectId) {
        _id = objectId;
    }


    @BsonProperty(_REF)
    @JsonbProperty(_REF)
    public String getCollection() {
        return _ref;
    }


    @BsonProperty(_REF)
    @JsonbProperty(_REF)
    public void setCollection(String collection) {
        _ref = collection;
    }


    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS)
    public List<String> getTags() {
        return _tags;
    }


    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS)
    public void setTags(List<String> tags) {
        _tags.clear();
        if (tags != null && !tags.isEmpty()) {
            _tags.addAll(tags);
        }
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //


    @Override
    public int hashCode() {
        // IMPORTANT: we intentionally do not calculate hash for the tags on the DbFileRef object
        // See more description in the equals() method below
        return Objects.hash(_id, _ref);
    }


    @Override
    public boolean equals(Object o) {
        // IMPORTANT: we intentionally do not compare tags on the DbFileRef object
        // As we consider DbFileRefs equal when they point to the same file,
        // but tags may not be present in case we constructed DbFileRef with two arguments only (col and id)
        // for the lookup&compare purpose only.
        if (o == null || getClass() != o.getClass()) return false;
        DbFileRef dbFileRef = (DbFileRef) o;
        return Objects.equals(_id, dbFileRef._id)
                && Objects.equals(_ref, dbFileRef._ref);
    }


    @Override
    public String toString() {
        return "DbFileRef{ref=" + _ref + "; id=" + _id + '}';
    }

}
