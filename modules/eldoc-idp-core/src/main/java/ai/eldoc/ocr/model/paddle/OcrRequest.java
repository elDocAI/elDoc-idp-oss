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
 * Model for sending OCR request to Paddle OCR API endpoint
 */
public class OcrRequest implements Serializable {

    private static final long serialVersionUID = 6753010707632999561L;

    public static final int FILE_TYPE_PDF = 0;
    public static final int FILE_TYPE_IMAGE = 1;

    public static final String TEXT_DET_LIMIT_TYPE_MAX = "max";
    public static final String TEXT_DET_LIMIT_TYPE_MIN = "min";

    private String _file;
    private int _fileType;
    private Boolean _useDocOrientationClassify;
    private Boolean _useDocUnwarping;
    private Boolean _useTextlineOrientation;
    private String _textDetLimitSideLen;
    private String _textDetLimitType;
    private Double _textDetThresh;
    private Double _textDetBoxThresh;
    private Double _textDetUnclipRatio;
    private Double _textRecScoreThresh;
    private Boolean _returnWordBox;
    private Boolean _visualize;


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getFile() {
        return _file;
    }


    public void setFile(String file) {
        _file = file;
    }


    public int getFileType() {
        return _fileType;
    }


    public void setFileType(int fileType) {
        _fileType = fileType;
    }


    public Boolean getUseDocOrientationClassify() {
        return _useDocOrientationClassify;
    }


    public void setUseDocOrientationClassify(Boolean useDocOrientationClassify) {
        _useDocOrientationClassify = useDocOrientationClassify;
    }


    public Boolean getUseDocUnwarping() {
        return _useDocUnwarping;
    }


    public void setUseDocUnwarping(Boolean useDocUnwarping) {
        _useDocUnwarping = useDocUnwarping;
    }


    public Boolean getUseTextlineOrientation() {
        return _useTextlineOrientation;
    }


    public void setUseTextlineOrientation(Boolean useTextlineOrientation) {
        _useTextlineOrientation = useTextlineOrientation;
    }


    public String getTextDetLimitSideLen() {
        return _textDetLimitSideLen;
    }


    public void setTextDetLimitSideLen(String textDetLimitSideLen) {
        _textDetLimitSideLen = textDetLimitSideLen;
    }


    public String getTextDetLimitType() {
        return _textDetLimitType;
    }


    public void setTextDetLimitType(String textDetLimitType) {
        _textDetLimitType = textDetLimitType;
    }


    public Double getTextDetThresh() {
        return _textDetThresh;
    }


    public void setTextDetThresh(Double textDetThresh) {
        _textDetThresh = textDetThresh;
    }


    public Double getTextDetBoxThresh() {
        return _textDetBoxThresh;
    }


    public void setTextDetBoxThresh(Double textDetBoxThresh) {
        _textDetBoxThresh = textDetBoxThresh;
    }


    public Double getTextDetUnclipRatio() {
        return _textDetUnclipRatio;
    }


    public void setTextDetUnclipRatio(Double textDetUnclipRatio) {
        _textDetUnclipRatio = textDetUnclipRatio;
    }


    public Double getTextRecScoreThresh() {
        return _textRecScoreThresh;
    }


    public void setTextRecScoreThresh(Double textRecScoreThresh) {
        _textRecScoreThresh = textRecScoreThresh;
    }


    public Boolean getReturnWordBox() {
        return _returnWordBox;
    }


    public void setReturnWordBox(Boolean returnWordBox) {
        _returnWordBox = returnWordBox;
    }


    public Boolean getVisualize() {
        return _visualize;
    }


    public void setVisualize(Boolean visualize) {
        _visualize = visualize;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(_file);
        hash = 59 * hash + _fileType;
        hash = 59 * hash + Objects.hashCode(_useDocOrientationClassify);
        hash = 59 * hash + Objects.hashCode(_useDocUnwarping);
        hash = 59 * hash + Objects.hashCode(_useTextlineOrientation);
        hash = 59 * hash + Objects.hashCode(_textDetLimitSideLen);
        hash = 59 * hash + Objects.hashCode(_textDetLimitType);
        hash = 59 * hash + Objects.hashCode(_textDetThresh);
        hash = 59 * hash + Objects.hashCode(_textDetBoxThresh);
        hash = 59 * hash + Objects.hashCode(_textDetUnclipRatio);
        hash = 59 * hash + Objects.hashCode(_textRecScoreThresh);
        hash = 59 * hash + Objects.hashCode(_returnWordBox);
        hash = 59 * hash + Objects.hashCode(_visualize);
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
        final OcrRequest other = (OcrRequest) obj;
        if (_fileType != other._fileType) {
            return false;
        }
        if (!Objects.equals(_file, other._file)) {
            return false;
        }
        if (!Objects.equals(_textDetLimitSideLen, other._textDetLimitSideLen)) {
            return false;
        }
        if (!Objects.equals(_textDetLimitType, other._textDetLimitType)) {
            return false;
        }
        if (!Objects.equals(_useDocOrientationClassify, other._useDocOrientationClassify)) {
            return false;
        }
        if (!Objects.equals(_useDocUnwarping, other._useDocUnwarping)) {
            return false;
        }
        if (!Objects.equals(_useTextlineOrientation, other._useTextlineOrientation)) {
            return false;
        }
        if (!Objects.equals(_textDetThresh, other._textDetThresh)) {
            return false;
        }
        if (!Objects.equals(_textDetBoxThresh, other._textDetBoxThresh)) {
            return false;
        }
        if (!Objects.equals(_textDetUnclipRatio, other._textDetUnclipRatio)) {
            return false;
        }
        if (!Objects.equals(_textRecScoreThresh, other._textRecScoreThresh)) {
            return false;
        }
        if (!Objects.equals(_returnWordBox, other._returnWordBox)) {
            return false;
        }
        return Objects.equals(_visualize, other._visualize);
    }

}
