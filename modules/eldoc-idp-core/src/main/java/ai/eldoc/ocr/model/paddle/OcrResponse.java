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
 * Paddle OCR API endpoint response body
 */
public class OcrResponse implements Serializable {

    private static final long serialVersionUID = -3956714933846155158L;

    private String _logId;
    private Integer _errorCode;
    private String _errorMsg;
    private OcrResponseData _result;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void scale(Double scale) {
        _result.scale(scale);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getLogId() {
        return _logId;
    }


    public void setLogId(String logId) {
        _logId = logId;
    }


    public Integer getErrorCode() {
        return _errorCode;
    }


    public void setErrorCode(Integer errorCode) {
        _errorCode = errorCode;
    }


    public String getErrorMsg() {
        return _errorMsg;
    }


    public void setErrorMsg(String errorMsg) {
        _errorMsg = errorMsg;
    }


    public OcrResponseData getResult() {
        return _result;
    }


    public void setResult(OcrResponseData result) {
        _result = result;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(_logId);
        hash = 97 * hash + Objects.hashCode(_errorCode);
        hash = 97 * hash + Objects.hashCode(_errorMsg);
        hash = 97 * hash + Objects.hashCode(_result);
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
        final OcrResponse other = (OcrResponse) obj;
        if (!Objects.equals(_logId, other._logId)) {
            return false;
        }
        if (!Objects.equals(_errorMsg, other._errorMsg)) {
            return false;
        }
        if (!Objects.equals(_errorCode, other._errorCode)) {
            return false;
        }
        return Objects.equals(_result, other._result);
    }

}
