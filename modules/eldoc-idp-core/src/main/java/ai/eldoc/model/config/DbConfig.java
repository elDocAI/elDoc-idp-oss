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
package ai.eldoc.model.config;

import java.io.Serial;
import java.io.Serializable;


/**
 * Database Config Model
 */
public final class DbConfig extends Config implements Serializable {

    @Serial
    private static final long serialVersionUID = 2039142057727561692L;

    public static final int DB_VER_7_4_u29 = 7_0004_0029;

    public static final int DB_VER_CURRENT = DB_VER_7_4_u29;

    private int _ver;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public DbConfig() {
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public int getVer() {
        return _ver;
    }


    public void setVer(int ver) {
        _ver = ver;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 89 * hash + _ver;
        hash = 89 * hash + super.hashCode();
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
        if (!super.equals(obj)) {
            return false;
        }
        final DbConfig other = (DbConfig) obj;
        if (_ver != other._ver) {
            return false;
        }
        return true;
    }

}
