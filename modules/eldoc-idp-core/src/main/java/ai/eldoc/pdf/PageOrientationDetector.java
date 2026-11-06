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

package ai.eldoc.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import java.io.*;
import java.util.*;


/**
 * This is an example on how to get some x/y coordinates of text.
 */
public class PageOrientationDetector extends PDFTextStripper {

    private final Map<Integer, Integer> _stats; // Map which contains distribution of direction by number of accurrences

    private static final int STAT_COUNT_THRESHOLD = 250; // number of words to be analized for understanding the page orientation
    private boolean _thresholdReached = false;
    private int _wordsCounter = 0;


    /**
     * Instantiate a new PDFTextStripper object.
     *
     * @throws IOException If there is an error loading the properties.
     */
    public PageOrientationDetector() throws IOException {
        _stats = new HashMap<>();
    }


    /**
     * Returns page orientation calculated based on words orientation in the page.
     * Only angles of 0, 90, 180, or 270 are supported.
     * When returns 90 - page should be rotated 90 in order to get text oriented horizontal
     *
     * @param document
     * @param pageIdx
     * @return
     * @throws IOException
     */
    public int getPageOrientation(PDDocument document, int pageIdx) throws IOException {
        setSortByPosition(true);
        setStartPage(pageIdx + 1);
        setEndPage(pageIdx + 1);

        try (Writer dummy = new OutputStreamWriter(new ByteArrayOutputStream())) {
            writeText(document, dummy);
        }

        if (_stats.isEmpty()) {
            // In case we got nothing - return default value
            return 0;
        }

        return Collections.max(_stats.entrySet(), Map.Entry.comparingByValue()).getKey();
    }


    /**
     * Override the default functionality of PDFTextStripper.
     *
     * @param string
     * @param textPositions
     * @throws java.io.IOException
     */
    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        if (_thresholdReached || textPositions.isEmpty()) {
            // Nothing to do
            return;
        }

        // We do analize only text position of the first character of the word
        Integer direction = Math.round(textPositions.get(0).getDir());

        if (!_stats.containsKey(direction)) {
            _stats.put(direction, 0);
        }
        _stats.put(direction, _stats.get(direction) + 1);

        _wordsCounter++;
        if (_wordsCounter >= STAT_COUNT_THRESHOLD) {
            _thresholdReached = true;
        }
    }

}
