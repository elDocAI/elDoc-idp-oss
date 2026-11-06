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

import ai.eldoc.model.reco.Rectangle;
import jakarta.json.bind.annotation.JsonbProperty;
import org.apache.commons.lang3.SerializationUtils;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serializable;
import java.util.Objects;


/**
 * OcrText Attributes Model
 */
public abstract class OcrTextAttributes implements Serializable {

    private static final long serialVersionUID = 2937919806150723598L;

    public final static String _REGION = "region";
    public final static String _CONFIDENCE = "confidence";
    public final static String _FONT_SIZE_PT = "fontSize";
    public final static String _RTL = "rtl";
    public final static String _RTL_PARA = "rtlPara";

    private Rectangle _region;
    private int _confidence;
    private float _fontSizePt;
    private boolean _rtl;
    private boolean _rtlPara;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public OcrTextAttributes() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Creates a deep clone using Serialization-based cloning approach
     *
     * @return
     */
    @Override
    public OcrTextAttributes clone() {
        return SerializationUtils.clone(this);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public Rectangle getRegion() {
        return _region;
    }


    public void setRegion(Rectangle region) {
        _region = region;
    }


    public int getConfidence() {
        return _confidence;
    }


    public void setConfidence(int confidence) {
        _confidence = confidence;
    }


    @BsonProperty(_FONT_SIZE_PT)
    @JsonbProperty(_FONT_SIZE_PT)
    public float getFontSizePt() {
        return _fontSizePt;
    }


    @BsonProperty(_FONT_SIZE_PT)
    @JsonbProperty(_FONT_SIZE_PT)
    public void setFontSizePt(float fontSizePt) {
        _fontSizePt = fontSizePt;
    }


    public boolean isRtl() {
        return _rtl;
    }


    public void setRtl(boolean rtl) {
        _rtl = rtl;
    }


    public boolean isRtlPara() {
        return _rtlPara;
    }


    public void setRtlPara(boolean rtlPara) {
        _rtlPara = rtlPara;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(_region);
        hash = 41 * hash + _confidence;
        hash = 41 * hash + Float.floatToIntBits(_fontSizePt);
        hash = 11 * hash + (_rtl ? 1 : 0);
        hash = 11 * hash + (_rtlPara ? 1 : 0);
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
        final OcrTextAttributes other = (OcrTextAttributes) obj;
        if (_confidence != other._confidence) {
            return false;
        }
        if (Float.floatToIntBits(_fontSizePt) != Float.floatToIntBits(other._fontSizePt)) {
            return false;
        }
        if (!Objects.equals(_region, other._region)) {
            return false;
        }
        if (!Objects.equals(_rtl, other._rtl)) {
            return false;
        }
        if (!Objects.equals(_rtlPara, other._rtlPara)) {
            return false;
        }
        return true;
    }

}
