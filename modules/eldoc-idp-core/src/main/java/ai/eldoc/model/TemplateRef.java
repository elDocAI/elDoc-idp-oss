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

import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;


/**
 * Template Reference Model<br>
 * Model represents a reference to the template file in the DB with the respective associated settings
 */
public class TemplateRef implements Serializable {

    @Serial
    private static final long serialVersionUID = -7539517832480806673L;

    public static final String _TEMPLATE_FILE_REF = "tfr";
    public static final String _COMPUTE_FORM_FIELDS = "cff";

    private DbFileRef _tmplDbFileRef;
    private boolean _computeFormFields;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    protected TemplateRef() {
    }


    public TemplateRef(DbFileRef dbFileRef) {
        _tmplDbFileRef = dbFileRef;
    }


    public TemplateRef(DbFileRef tmplDbFileRef, boolean computeFormFields) {
        _tmplDbFileRef = tmplDbFileRef;
        _computeFormFields = computeFormFields;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_TEMPLATE_FILE_REF)
    public DbFileRef getDbFileRef() {
        return _tmplDbFileRef;
    }


    @BsonProperty(_TEMPLATE_FILE_REF)
    public void setDbFileRef(DbFileRef dbFileRef) {
        _tmplDbFileRef = dbFileRef;
    }


    /**
     * @return Whether a template requires to compute fields
     */
    @BsonProperty(_COMPUTE_FORM_FIELDS)
    public boolean isComputeFormFields() {
        return _computeFormFields;
    }


    /**
     * @param computeFormFields Whether a template requires to compute fields
     */
    @BsonProperty(_COMPUTE_FORM_FIELDS)
    public void setComputeFormFields(boolean computeFormFields) {
        _computeFormFields = computeFormFields;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return "TemplateRef{" +
                "dbFileRef=" + _tmplDbFileRef +
                ", computeFormFields=" + _computeFormFields +
                '}';
    }


    @Override
    public int hashCode() {
        return Objects.hash(_tmplDbFileRef, _computeFormFields);
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TemplateRef that = (TemplateRef) o;
        return _computeFormFields == that._computeFormFields && Objects.equals(_tmplDbFileRef, that._tmplDbFileRef);
    }

}
