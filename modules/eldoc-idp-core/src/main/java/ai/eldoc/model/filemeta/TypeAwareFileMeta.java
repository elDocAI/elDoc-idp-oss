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

import jakarta.json.bind.annotation.JsonbProperty;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.util.Objects;

/**
 * TypeAwareFileMeta
 */
public abstract class TypeAwareFileMeta extends Sha256FileMeta {

    @Serial
    private static final long serialVersionUID = -6896178168100307707L;

    /**
     * File type: 0 or absent = primary file type, XXXXX
     */
    public static final String _TYPE = "type";

    public static final String _PATH_METADATA_TYPE = _METADATA + "." + _TYPE;

    /**
     * File type: Default. Default file type for the target collection/DAO
     */
    public static final int TYPE_DEFAULT = 1;

    /**
     * File type: Revision (for the file stored in the same collection/DAO)
     */
    public static final int TYPE_REVISION = 5;

    /**
     * File type: Text index. Indexed content of the file stored in the same collection/DAO, if the file itself is not text-based (i.e., scanned PDF, images)
     */
    public static final int TYPE_TEXT_INDEX = 10;

    private int _type;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE)
    public int getType() {
        return _type;
    }


    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE)
    public void setType(int type) {
        _type = type;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TypeAwareFileMeta that = (TypeAwareFileMeta) o;
        return _type == that._type;
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _type);
    }
}
