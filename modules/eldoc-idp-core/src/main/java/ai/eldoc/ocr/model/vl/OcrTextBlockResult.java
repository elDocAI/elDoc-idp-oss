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
package ai.eldoc.ocr.model.vl;

import ai.eldoc.model.reco.Rectangle;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;


/**
 * Model for representing extracted/converted VL LLM OCR results for single text block
 */
public class OcrTextBlockResult implements Serializable {

    private static final long serialVersionUID = 2516694101272244910L;

    private int[] _bbox;
    private double _confidence;
    private String _text;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public Rectangle scaledBboxToRectangle(double imgWidth, double imgHeight, int coordsScale) {
        double x = ((double) _bbox[0]) * ((double) imgWidth / coordsScale);
        double y = ((double) _bbox[1]) * ((double) imgHeight / coordsScale);
        double width = ((double) _bbox[2]) * ((double) imgWidth / coordsScale) - x;
        double height = ((double) _bbox[3]) * ((double) imgHeight / coordsScale) - y;
        return new Rectangle(x, y, width, height);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public int[] getBbox() {
        return _bbox;
    }


    public void setBbox(int[] bbox) {
        _bbox = bbox;
    }


    public double getConfidence() {
        return _confidence;
    }


    public void setConfidence(double confidence) {
        _confidence = confidence;
    }


    public String getText() {
        return _text;
    }


    public void setText(String text) {
        _text = text;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Arrays.hashCode(_bbox);
        hash = 71 * hash + Objects.hashCode(_confidence);
        hash = 71 * hash + Objects.hashCode(_text);
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
        if (!Objects.equals(_text, other._text)) {
            return false;
        }
        if (!Objects.equals(_confidence, other._confidence)) {
            return false;
        }
        return Arrays.equals(_bbox, other._bbox);
    }

}
