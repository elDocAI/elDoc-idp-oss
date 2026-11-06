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

import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.util.Objects;

public class IDPConfig extends Config{

    @Serial
    private static final long serialVersionUID = -8123915405792219163L;

    public static final String _AUTO_CLASSIFICATION = "autoClsf";
    public static final String _CLASSIFICATION_DEFAULT_MODEL = "dmClsf";
    public static final String _EXTRACTION_DEFAULT_MODEL = "dmExtr";

    private boolean _autoClassification;
    private String _classificationDefaultModel;
    private String _extractionDefaultModel;


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_AUTO_CLASSIFICATION)
    public boolean isAutoClassification() {
        return _autoClassification;
    }


    @BsonProperty(_AUTO_CLASSIFICATION)
    public void setAutoClassification(boolean autoClassification) {
        _autoClassification = autoClassification;
    }


    @BsonProperty(_CLASSIFICATION_DEFAULT_MODEL)
    public String getClassificationDefaultModel() {
        return _classificationDefaultModel;
    }


    @BsonProperty(_CLASSIFICATION_DEFAULT_MODEL)
    public void setClassificationDefaultModel(String classificationDefaultModel) {
        _classificationDefaultModel = classificationDefaultModel;
    }


    @BsonProperty(_EXTRACTION_DEFAULT_MODEL)
    public String getExtractionDefaultModel() {
        return _extractionDefaultModel;
    }


    @BsonProperty(_EXTRACTION_DEFAULT_MODEL)
    public void setExtractionDefaultModel(String extractionDefaultModel) {
        _extractionDefaultModel = extractionDefaultModel;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        IDPConfig idpConfig = (IDPConfig) o;
        return _autoClassification == idpConfig._autoClassification
                && Objects.equals(_classificationDefaultModel, idpConfig._classificationDefaultModel)
                && Objects.equals(_extractionDefaultModel, idpConfig._extractionDefaultModel);
    }


    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Boolean.hashCode(_autoClassification);
        result = 31 * result + Objects.hashCode(_classificationDefaultModel);
        result = 31 * result + Objects.hashCode(_extractionDefaultModel);
        return result;
    }
}
