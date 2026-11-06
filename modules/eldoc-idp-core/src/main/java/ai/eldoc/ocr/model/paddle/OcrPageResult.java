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

import jakarta.json.bind.annotation.JsonbProperty;
import java.io.Serializable;
import java.util.Objects;


/**
 * Paddle OCR result wrapper for page
 */
public class OcrPageResult implements Serializable {

    private static final long serialVersionUID = 6740742138673476154L;

    private OcrPageResultData _data;
    private String _ocrImage;
    private String _docPreprocessingImage;
    private String _docImage;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void scale(Double scale) {
        _data.scale(scale);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @JsonbProperty("prunedResult")
    public OcrPageResultData getData() {
        return _data;
    }


    @JsonbProperty("prunedResult")
    public void setData(OcrPageResultData data) {
        _data = data;
    }


    public String getOcrImage() {
        return _ocrImage;
    }


    public void setOcrImage(String ocrImage) {
        _ocrImage = ocrImage;
    }


    public String getDocPreprocessingImage() {
        return _docPreprocessingImage;
    }


    public void setDocPreprocessingImage(String docPreprocessingImage) {
        _docPreprocessingImage = docPreprocessingImage;
    }


    public String getDocImage() {
        return _docImage;
    }


    public void setDocImage(String docImage) {
        _docImage = docImage;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 43 * hash + Objects.hashCode(_data);
        hash = 43 * hash + Objects.hashCode(_ocrImage);
        hash = 43 * hash + Objects.hashCode(_docPreprocessingImage);
        hash = 43 * hash + Objects.hashCode(_docImage);
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
        final OcrPageResult other = (OcrPageResult) obj;
        if (!Objects.equals(_ocrImage, other._ocrImage)) {
            return false;
        }
        if (!Objects.equals(_docPreprocessingImage, other._docPreprocessingImage)) {
            return false;
        }
        if (!Objects.equals(_docImage, other._docImage)) {
            return false;
        }
        return Objects.equals(_data, other._data);
    }
}
