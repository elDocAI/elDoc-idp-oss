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

import ai.eldoc.data.DataType;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;


/**
 * Column Model and used in PrimeFaces tables
 */
public class Column implements Serializable {

    @Serial
    private static final long serialVersionUID = 3631009861780714525L;


    public enum Type {
        OUTPUT_TEXT,
        OUTPUT_DATE,
        LINK_ID
    }

    private final String _modelField;
    private final String _dbField;
    private final int _type;
    private final String _header;


    //
    // Constructors -------------------------------------------------------------------------------
    //


    /**
     * Constructor
     *
     * @param modelField column name, always has to be equal dataSource field name (sets the same value for the dsField property)
     * @param type
     * @param header column UI header value
     */
    public Column(String modelField, int type, String header) {
        this(modelField, modelField, type, header);
    }


    /**
     * Constructor
     *
     * @param modelField column name, always has to be equal dataSource field name
     * @param dbField column dataSource field name, may be a localized field name ending with _uk, _ru, etc.
     * @param type
     * @param header column UI header value
     */
    public Column(String modelField, String dbField, int type, String header) {
        _modelField = modelField;
        _dbField = dbField;
        _type = type;
        _header = header;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public boolean isRendered(final Type componentCheck) {
        Type type = Type.OUTPUT_TEXT;
        if (_dbField != null && _dbField.equals(Workflow._NAME)) {
            type = Type.LINK_ID;
        } else if (DataType.DATE == _type) {
            type = Type.OUTPUT_DATE;
        }
        return type == componentCheck;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getModelField() {
        return _modelField;
    }


    public String getDbField() {
        return _dbField;
    }


    public String getHeader() {
        return _header;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Column column = (Column) o;
        return _type == column._type && Objects.equals(_modelField, column._modelField) && Objects.equals(_dbField, column._dbField) && Objects.equals(_header, column._header);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_modelField, _dbField, _type, _header);
    }
}
