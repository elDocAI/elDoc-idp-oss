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

import jakarta.json.bind.annotation.JsonbTransient;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import java.io.Serializable;
import java.util.regex.Pattern;


/**
 * Interface declares Basic Data Model
 */
public interface BasicModel extends Serializable {

    /**
     * Default ID field name used in all Basic Models
     */
    public static final String _ID = "_id";

    /**
     * Foreign Key ID, used for recursions inside the same collection,
     * in order to build parent/child relations: child record contains _id_kf field
     * pointing to the parent record _id field
     */
    public static final String _ID_KF = "_id_kf";

    /**
     * Some models may have an Web- or User-friendly ID to be used in URLs, etc.
     */
    public static final String _ID_WEB = "_id_web";

    /**
     * Some models may have an ID field which holds external ID,
     * useful for data integration/sync, stores PK of the record in the foreign database
     */
    public static final String _ID_EXT = "_id_ext";

    public static final String ID_PATTERN_STR = "^\\p{XDigit}{24}$";
    public static final Pattern ID_PATTERN = Pattern.compile(ID_PATTERN_STR);
    public static final String ID_WEB_PATTERN_STR = "^\\p{XDigit}{2,40}$";
    public static final Pattern ID_WEB_PATTERN = Pattern.compile(ID_WEB_PATTERN_STR);


    public String getId();


    //
    // Default implementations --------------------------------------------------------------------
    //
    /**
     * Returns true in case this model is unsaved in the DB entity (getId() == null)
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    default public boolean isNew() {
        return getId() == null || getId().isEmpty();
    }

}
