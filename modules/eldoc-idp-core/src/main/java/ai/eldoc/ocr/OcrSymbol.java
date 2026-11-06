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

import org.apache.commons.lang3.SerializationUtils;
import java.io.Serializable;
import java.util.Objects;


/**
 * Represents single character on document with its characteristics (region, confidence level, alternate symbols, font size)
 */
public class OcrSymbol extends OcrTextAttributes implements Serializable {

    private static final long serialVersionUID = -5443878098125957839L;

    public final static String _CHAR = "char";

    private char _char;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public OcrSymbol() {
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
    public OcrSymbol clone() {
        return SerializationUtils.clone(this);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public char getChar() {
        return _char;
    }


    public void setChar(char newChar) {
        _char = newChar;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * super.hashCode();
        hash = 41 * hash + Objects.hashCode(_char);
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
        final OcrSymbol other = (OcrSymbol) obj;
        if (!super.equals(other)) {
            return false;
        }
        if (_char != other._char) {
            return false;
        }
        return true;
    }

}
