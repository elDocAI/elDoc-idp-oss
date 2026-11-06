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

import jakarta.json.bind.annotation.JsonbTransient;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import java.io.Serializable;
import java.util.*;


/**
 * Represents text block containing OcrText (where each OcrText Model represents a word).<br>
 * It may represent as some area of the page as well as whole page
 */
public class OcrBlock implements Serializable {

    private static final long serialVersionUID = 7835801504622724202L;

    private List<OcrText> _ocrData;

    //
    // Constructor --------------------------------------------------------------------------------
    //

    public OcrBlock() {
        _ocrData = new LinkedList<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @BsonIgnore
    @JsonbTransient
    public String getText() {
        return _ocrData.stream().map(OcrWord -> OcrWord.getText())
                .collect(StringBuilder::new, (builder, text) -> builder.append(text), StringBuilder::append)
                .toString();
    }


    public void addWord(OcrText ocrWord) {
        if (ocrWord == null) {
            return;
        }
        _ocrData.add(ocrWord);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public List<OcrText> getOcrData() {
        return _ocrData;
    }


    public void setOcrData(List<OcrText> ocrWordData) {
        _ocrData = ocrWordData;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(_ocrData);
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
        final OcrBlock other = (OcrBlock) obj;
        if (!Objects.equals(_ocrData, other._ocrData)) {
            return false;
        }
        return true;
    }

}
