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
 * SHA256 File Meta Model
 */
public abstract class Sha256FileMeta extends FileMeta {

    @Serial
    private static final long serialVersionUID = 1743898545672343618L;


    //
    // Model DB Fields ----------------------------------------------------------------------------
    //
    public static final String _SHA256 = "sha256";


    //
    // Constants ----------------------------------------------------------------------------------
    //
    public static final String _PATH_METADATA_SHA256 = _METADATA + "." + _SHA256;


    //
    // Model Fields -------------------------------------------------------------------------------
    //
    private String _sha256;


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_SHA256)
    @JsonbProperty(_SHA256)
    public String getSha256() {
        return _sha256;
    }


    @BsonProperty(_SHA256)
    @JsonbProperty(_SHA256)
    public void setSha256(String sha256) {
        _sha256 = sha256;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Sha256FileMeta that = (Sha256FileMeta) o;
        return Objects.equals(_sha256, that._sha256);
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _sha256);
    }
}
