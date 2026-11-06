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

import ai.eldoc.annotation.RestApiVisible;
import ai.eldoc.api.RestApiVisibilityStrategy;
import jakarta.json.bind.annotation.*;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;


/**
 * Model represents Metadata of the document
 */
@JsonbVisibility(RestApiVisibilityStrategy.class) // when private property and public getter with the same name require special handling
public class Meta implements Serializable {

    @Serial
    private static final long serialVersionUID = 8424837062017440815L;

    public static final String _META = "_meta";
    public static final String _CREATED = "_created";
    public static final String _LASTMODIFIED = "_lastmodified";
    public static final String _REVISION = "_rev";
    public static final String _PATH_META_CREATED = _META + "." + _CREATED;
    public static final String _PATH_META_LASTMODIFIED = _META + "." + _LASTMODIFIED;
    public static final String _PATH_META_REVISION = _META + "." + _REVISION;

    private Date _created;
    private Date _lastModified;
    private int _rev;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public Meta() {
        _created = new Date();
        _rev = 0;
    }

    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Returns the last modified date or the created date if the last modified is null.
     *
     * @return Date last modified or created
     */
    @BsonIgnore
    @JsonbTransient
    public Date getLastModifiedOrCreated() {
        return _lastModified != null ? _lastModified : _created;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    @BsonProperty(_CREATED)
    @JsonbProperty(_CREATED)
    @RestApiVisible
    public Date getCreated() {
        return _created;
    }


    @BsonProperty(_CREATED)
    @JsonbProperty(_CREATED)
    public void setCreated(Date created) {
        _created = created;
    }


    @BsonProperty(_LASTMODIFIED)
    @JsonbProperty(_LASTMODIFIED)
    @RestApiVisible
    public Date getLastModified() {
        return _lastModified;
    }


    @BsonProperty(_LASTMODIFIED)
    @JsonbProperty(_LASTMODIFIED)
    public void setLastModified(Date lastModified) {
        _lastModified = lastModified;
    }


    @BsonProperty(_REVISION)
    @JsonbProperty(_REVISION)
    @RestApiVisible
    public int getRev() {
        return _rev;
    }


    @BsonProperty(_REVISION)
    @JsonbProperty(_REVISION)
    public void setRev(int rev) {
        _rev = rev;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Meta meta = (Meta) o;
        return _rev == meta._rev && Objects.equals(_created, meta._created) && Objects.equals(_lastModified, meta._lastModified);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_created, _lastModified, _rev);
    }
}
