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
package ai.eldoc.ocr.model.paddle;

import java.io.Serializable;
import java.util.Objects;


/**
 * OCR Page Preprocessor Result
 */
public class OcrPagePreprocessorResult implements Serializable {

    private static final long serialVersionUID = -2520688883038426158L;

    private Integer _angle;


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Integer getAngle() {
        return _angle;
    }


    public void setAngle(Integer angle) {
        _angle = angle;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(_angle);
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
        final OcrPagePreprocessorResult other = (OcrPagePreprocessorResult) obj;
        return Objects.equals(_angle, other._angle);
    }

}
