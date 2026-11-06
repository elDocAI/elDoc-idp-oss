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
import java.util.List;
import java.util.Objects;


/**
 * Paddle OCR results
 */
public class OcrResponseData implements Serializable {

    private static final long serialVersionUID = 1752746975457446432L;

    private List<OcrPageResult> _ocrResults;
    private Object _dataInfo;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void scale(Double scale) {
        if (_ocrResults != null) {
            for (int pageIdx = 0; pageIdx < _ocrResults.size(); pageIdx++) {
                _ocrResults.get(pageIdx).scale(scale);
            }
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public List<OcrPageResult> getOcrResults() {
        return _ocrResults;
    }


    public void setOcrResults(List<OcrPageResult> ocrResults) {
        _ocrResults = ocrResults;
    }


    public Object getDataInfo() {
        return _dataInfo;
    }


    public void setDataInfo(Object dataInfo) {
        _dataInfo = dataInfo;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(_ocrResults);
        hash = 89 * hash + Objects.hashCode(_dataInfo);
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
        final OcrResponseData other = (OcrResponseData) obj;
        if (!Objects.equals(_ocrResults, other._ocrResults)) {
            return false;
        }
        return Objects.equals(_dataInfo, other._dataInfo);
    }

}
