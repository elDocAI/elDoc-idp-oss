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
import ai.eldoc.util.RectangleUtils;
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.lang3.SerializationUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Represents text obtained from OCR (generic implementation, usually represents single word)
 */
public class OcrText extends OcrTextAttributes implements Serializable {

    private static final long serialVersionUID = 5679059286403462636L;

    private String _text;
    private List<OcrSymbol> _ocrData;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public OcrText() {
        super();
        _text = "";
        _ocrData = new LinkedList<>();
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
    public OcrText clone() {
        return SerializationUtils.clone(this);
    }


    /**
     * Adds Symbol to the symbols collection and also appends symbol char to the text value as well.<br>
     * In case you need to add symbol without rebuilding the word, use .add() method on the symbols collection instead
     *
     * @param ocrSymbol
     */
    public void addSymbol(OcrSymbol ocrSymbol) {
        _ocrData.add(ocrSymbol);
        _text = new StringBuilder().append(_text).append(ocrSymbol.getChar()).toString();
    }


    /**
     * Adds Symbol to the symbols collection and also appends symbol char to the text value as well.<br>
     * In case you need to add symbol without rebuilding the word, use .add() method on the symbols collection instead
     *
     * @param index
     * @param ocrSymbol
     */
    public void addSymbol(int index, OcrSymbol ocrSymbol) {
        if (index > _ocrData.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + _ocrData.size());
        }
        _ocrData.add(index, ocrSymbol);
        _text = new StringBuilder()
                .append(_text.substring(0, index))
                .append(ocrSymbol.getChar())
                .append(_text.substring(index, _text.length()))
                .toString();
    }


    /**
     * Calculates {@link Rectangle}, that holds all symbols from this text
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public Rectangle getTextArea() {
        List<Rectangle> symbolsList = getOcrData().stream().map(OcrSymbol::getRegion).collect(Collectors.toList());
        return RectangleUtils.union(symbolsList);
    }


    /**
     * Calculates average font size
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public float getAvgFontSize() {
        return (float) _ocrData.stream()
                .filter(symbol -> !Character.isWhitespace(symbol.getChar()))
                .mapToDouble(OcrSymbol::getFontSizePt)
                .average().orElse(0);
    }


    /**
     * Returns new OcrText, that includes only symbols, whose center is inside requested regions
     *
     * @param area
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public OcrText getTextInArea(Rectangle area) {
        OcrText result = new OcrText();
        List<OcrSymbol> symbols = getOcrData().stream()
                .filter(symb -> area.contains(symb.getRegion().getCenterX(), symb.getRegion().getCenterY()))
                .map(OcrSymbol::clone)
                .collect(Collectors.toList());
        symbols = OcrTextUtils.sortSymbols(symbols);
        result.getOcrData().addAll(symbols);
        return result.rebuild().trim();
    }


    /**
     * Returns a OcrText that is a substring of this OcrText. The substring begins at the specified
     * beginIndex and extends to the character at index endIndex - 1. Thus the length of the
     * substring is endIndex-beginIndex.
     *
     * @param start the beginning index, inclusive.
     * @param end the ending index, exclusive
     * @return the specified substring
     */
    public OcrText substring(int start, int end) {
        OcrText result = new OcrText();
        if (start >= _text.length()) {
            return result;
        }
        result.setText(_text.substring(start, end));
        result.getOcrData().addAll(_ocrData.subList(start, end));
        return result;
    }


    /**
     * Returns an OcrText that is a substring of this OcrText. The substring begins with the character
     * at the specified index and extends to the end of this OcrText.
     *
     * @param start the beginning index, inclusive.
     * @return the specified substring
     */
    public OcrText substring(int start) {
        return substring(start, _text.length());
    }


    /**
     * Concatenates the specified OcrText to the end of this OcrText. <br><br>
     * OcrText object is returned that represents a character sequence that is the concatenation
     * of the character sequence represented by this OcrText object and the character sequence
     * represented by the argument OcrText.
     *
     * @param ocrText the OcrText that is concatenated to the end of this OcrText.
     * @return a OcrText that represents the concatenation of this object's characters followed
     * by the OcrText argument's characters.
     */
    public OcrText concat(OcrText ocrText) {
        OcrText result = new OcrText();
        result.setText(_text.concat(ocrText.getText()));
        result.getOcrData().addAll(_ocrData);
        result.getOcrData().addAll(ocrText.getOcrData());
        return result;
    }


    /**
     * Returns a string whose value is this string, with any leading and trailing whitespace removed. <br><br>
     * Let k be the index of the first character in the string whose code is greater than '\u0020',
     * and let m be the index of the last character in the string whose code is greater than '\u0020'. A String
     * object is returned, representing the substring of this string that begins with the character at index k and
     * ends with the character at index m-that is, the result of this.substring(k, m + 1).<br><br>
     * This method may be used to trim whitespace (as defined above) from the beginning and end of a string.
     *
     * @return
     */
    public OcrText trim() {
        String trimmed = getText().trim();
        int trimmedStart = getText().indexOf(trimmed);
        return substring(trimmedStart, trimmedStart + trimmed.length());
    }


    /**
     * Rebuilds text string of this OcrText from it's OcrSymbols list
     *
     * @return
     */
    public OcrText rebuild() {
        // See: https://stackoverflow.com/questions/54447726/how-to-convert-stream-of-characters-into-a-string-in-java-8/54448255#comment112652553_54448255
        setText(getOcrData().stream()
                .map(ocrSymbol -> ocrSymbol.getChar())
                .collect(StringBuilder::new, (builder, c) -> builder.append((char) c), StringBuilder::append)
                .toString());
        return this;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getText() {
        return _text;
    }


    public void setText(String text) {
        _text = text;
    }


    public List<OcrSymbol> getOcrData() {
        return _ocrData;
    }


    public void setOcrData(List<OcrSymbol> ocrData) {
        _ocrData.clear();
        if (ocrData != null && !ocrData.isEmpty()) {
            _ocrData.addAll(ocrData);
        }
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * super.hashCode();
        hash = 89 * hash + Objects.hashCode(_text);
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
        final OcrText other = (OcrText) obj;
        if (!super.equals(other)) {
            return false;
        }
        if (!Objects.equals(_text, other._text)) {
            return false;
        }
        if (!Objects.equals(_ocrData, other._ocrData)) {
            return false;
        }
        return true;
    }

}
