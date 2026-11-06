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
package ai.eldoc.ocr;

import java.io.Serializable;
import java.util.Objects;


/**
 * Represents general OCR results with metadata
 */
public class OcrResult implements Serializable {

    private static final long serialVersionUID = -408555623115303272L;

    private OcrBlock _ocrBlock;
    private int _rotation;
    private boolean _textDirectionDetected;


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns OCR text with metadata
     *
     * @return
     */
    public OcrBlock getOcrBlock() {
        return _ocrBlock;
    }


    /**
     * Sets OCR text with metadata
     *
     * @param ocrBlock
     */
    public void setOcrBlock(OcrBlock ocrBlock) {
        _ocrBlock = ocrBlock;
    }


    /**
     * Returns rotation in degrees (0/90/180/270) of this OCR result, detected by OCR engine
     *
     * @return
     */
    public int getRotation() {
        return _rotation;
    }


    /**
     * Sets rotation in degrees (0/90/180/270) of this OCR result
     *
     * @param rotation
     */
    public void setRotation(int rotation) {
        _rotation = rotation;
    }


    /**
     * Returns true, if content of this OcrResult already has proper text direction defined
     *
     * @return
     */
    public boolean isTextDirectionDetected() {
        return _textDirectionDetected;
    }


    /**
     * Defines, if content of this OcrResult already has proper text direction defined
     *
     * @param textDirectionDetected
     */
    public void setTextDirectionDetected(boolean textDirectionDetected) {
        _textDirectionDetected = textDirectionDetected;
    }


    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(_ocrBlock);
        hash = 79 * hash + _rotation;
        hash = 79 * hash + (_textDirectionDetected ? 1 : 0);
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
        final OcrResult other = (OcrResult) obj;
        if (_rotation != other._rotation) {
            return false;
        }
        if (_textDirectionDetected != other._textDirectionDetected) {
            return false;
        }
        return Objects.equals(_ocrBlock, other._ocrBlock);
    }

}
