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

import ai.eldoc.model.reco.Rectangle;
import java.io.Serializable;
import java.util.Objects;


/**
 * Model for representing extracted/converted Paddle OCR results for single text block
 */
public class OcrTextBlockResult implements Serializable {

    private static final long serialVersionUID = 2096536318480359877L;

    private int _orientationAngle;
    private String _text;
    private Double _confidence;
    private Rectangle _boundingBox;


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public int getOrientationAngle() {
        return _orientationAngle;
    }


    public void setOrientationAngle(int orientationAngle) {
        _orientationAngle = orientationAngle;
    }


    public String getText() {
        return _text;
    }


    public void setText(String text) {
        _text = text;
    }


    public Double getConfidence() {
        return _confidence;
    }


    public void setConfidence(Double confidence) {
        _confidence = confidence;
    }


    public Rectangle getBoundingBox() {
        return _boundingBox;
    }


    public void setBoundingBox(Rectangle boundingBox) {
        _boundingBox = boundingBox;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + _orientationAngle;
        hash = 97 * hash + Objects.hashCode(_text);
        hash = 97 * hash + Objects.hashCode(_confidence);
        hash = 97 * hash + Objects.hashCode(_boundingBox);
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
        final OcrTextBlockResult other = (OcrTextBlockResult) obj;
        if (_orientationAngle != other._orientationAngle) {
            return false;
        }
        if (!Objects.equals(_text, other._text)) {
            return false;
        }
        if (!Objects.equals(_confidence, other._confidence)) {
            return false;
        }
        return Objects.equals(_boundingBox, other._boundingBox);
    }
}
