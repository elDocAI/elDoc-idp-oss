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

import ai.eldoc.util.JsonbUtils;
import jakarta.json.bind.annotation.JsonbProperty;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;


/**
 * Report File Meta Model
 */
public final class ReportFileMeta extends FileMeta {

    @Serial
    private static final long serialVersionUID = -4641654027675650264L;

    public static final String _ROWS = "rows";

    private int _rows;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static ReportFileMeta fromJson(String jsonStr) {
        return JsonbUtils.getInstance().fromJson(jsonStr, ReportFileMeta.class);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_ROWS)
    @JsonbProperty(_ROWS)
    public int getRows() {
        return _rows;
    }


    @BsonProperty(_ROWS)
    @JsonbProperty(_ROWS)
    public void setRows(int rows) {
        _rows = rows;
    }


    //
    // Generic methods --------------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 3 * super.hashCode();
        hash = 53 * hash + _rows;
        return hash;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ReportFileMeta other = (ReportFileMeta) obj;
        if (!super.equals(obj)) {
            return false;
        }
        if (_rows != other._rows) {
            return false;
        }
        return true;
    }

}
