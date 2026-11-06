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

package ai.eldoc.model.filemeta;

import jakarta.json.bind.annotation.JsonbProperty;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.util.Objects;

/**
 * TextIndexAwareFileMeta
 */
public abstract class TextIndexAwareFileMeta extends TypeAwareFileMeta {

    @Serial
    private static final long serialVersionUID = -5055957342226793999L;


    public static final String _TEXT_INDEX_STATE = "tiStt";
    public static final String _TEXT_INDEX_TYPE = "tiTyp";


    /**
     * Just uploaded a file, not processed
     */
    public static final int TEXT_INDEX_STATE_DEFAULT = 0;

    /**
     * File processing isn't supported (not covered by reco: not image or not PDF)
     */
    public static final int TEXT_INDEX_STATE_NOT_SUPPORTED = 5;

    /**
     * Content already present (machine-generated PDF, office file, text files, etc.)
     */
    public static final int TEXT_INDEX_STATE_CONTENT_PRESENT = 10;

    /**
     * Recognition finished successfully
     */
    public static final int TEXT_INDEX_STATE_READY = 20;

    /**
     * Recognition failed
     */
    public static final int TEXT_INDEX_STATE_PROCESSING_FAILED = 50;

    /**
     * Text index was built using classic OCR (i.e., Tesseract)
     */
    public static final int TEXT_INDEX_TYPE_OCR = 0;

    /**
     * Text index was built using VL LLM
     */
    public static final int TEXT_INDEX_TYPE_VL = 1;

    public static final String _PATH_METADATA_TEXT_INDEX_STATE = _METADATA + "." + _TEXT_INDEX_STATE;

    private int _textIndexState;
    private int _textIndexType;


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_TEXT_INDEX_STATE)
    @JsonbProperty(_TEXT_INDEX_STATE)
    public int getTextIndexState() {
        return _textIndexState;
    }


    @BsonProperty(_TEXT_INDEX_STATE)
    @JsonbProperty(_TEXT_INDEX_STATE)
    public void setTextIndexState(int textIndexState) {
        _textIndexState = textIndexState;
    }


    @BsonProperty(_TEXT_INDEX_TYPE)
    @JsonbProperty(_TEXT_INDEX_TYPE)
    public int getTextIndexType() {
        return _textIndexType;
    }


    @BsonProperty(_TEXT_INDEX_TYPE)
    @JsonbProperty(_TEXT_INDEX_TYPE)
    public void setTextIndexType(int textIndexType) {
        _textIndexType = textIndexType;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TextIndexAwareFileMeta that = (TextIndexAwareFileMeta) o;
        return _textIndexState == that._textIndexState && _textIndexType == that._textIndexType;
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _textIndexState, _textIndexType);
    }
}
