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

import ai.eldoc.dao.mongo.MongoUtils;
import ai.eldoc.util.JsonbUtils;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;


/**
 * File Metadata Model<br>
 * Used for storing file metadata within files in Bucket collection (GridFS)
 */
public abstract class FileMeta implements Serializable {

    @Serial
    private static final long serialVersionUID = -6716851548899327006L;


    //
    // Model DB Fields ----------------------------------------------------------------------------
    //

    /**
     * Used in GridFS, this field stores file metadata
     */
    public static final String _METADATA = "metadata";

    /**
     * Standard field for storing contentType inside metadata
     */
    public static final String _CONTENT_TYPE = "contentType";


    //
    // Constants ----------------------------------------------------------------------------------
    //
    public static final String _LENGTH_TARGET = "_lengthTarget";
    public static final String _PATH_METADATA_LENGTH_TARGET = _METADATA + "." + _LENGTH_TARGET;


    //
    // Model Fields -------------------------------------------------------------------------------
    //
    private String _contentType;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @BsonIgnore
    @JsonbTransient
    public String toJson() {
        // We need to have here null values serialized so in case vlaue changes from not-null to null it will be properly updated in the GridFsFile.metadata
        return JsonbUtils.getInstance().toJson(this);
    }


    /**
     * Serializes current object to Map to be used as metadata stored within file<br>
     * IMPORTANT: fields with NULL values are ignored
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public Map<String, Object> toMetadata() {
        // We can not use as below:
        // return JsonbUtils.getInstance().fromJson(this.toJson(), HashMap.class);
        // because it loses types, e.g. using the line above for the following model:
        // Model{ int pageCount = 1}
        // gives HashMap{key=pageCount; value=BigDecimal(1)}
        return MongoUtils.objectToDocument(this, this.getClass());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_CONTENT_TYPE)
    @JsonbProperty(_CONTENT_TYPE)
    public String getContentType() {
        return _contentType;
    }


    @BsonProperty(_CONTENT_TYPE)
    @JsonbProperty(_CONTENT_TYPE)
    public void setContentType(String contentType) {
        _contentType = contentType;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FileMeta fileMeta = (FileMeta) o;
        return Objects.equals(_contentType, fileMeta._contentType);
    }


    @Override
    public int hashCode() {
        return Objects.hashCode(_contentType);
    }
}
