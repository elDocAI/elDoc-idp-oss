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

import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.ocr.*;
import org.apache.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequenceWithProperties;
import org.apache.pdfbox.contentstream.operator.markedcontent.EndMarkedContentSequence;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;


/**
 * Performs text extraction from PDF to {@link OcrText}, also extracting per-symbol coordinates and confidence levels.<br>
 *
 * <b>IMPORTANT:</b> As original {@link PDFTextStripperByArea} performs TextPositions sorting in much later step,
 * than this TextStripper extracts extra data, it will require duplicating almost all code from PDFTextStripperByArea to repeat
 * behavior of {@link PDFTextStripperByArea#setSortByPosition(boolean) } - therefore it's behaviour is repeated in
 * {@link RecoTextStripperByArea#getTextForRegionWithCoords(java.lang.String) }
 */
public class RecoTextStripperByArea extends PDFTextStripperByArea {

    private static final Logger LOG = LoggerFactory.getLogger(RecoTextStripperByArea.class);

    private COSDictionary _currentProp; // present only when it is not a machine-generated file (preprocessed with OCR by elDoc)
    private Rectangle _lastTextRect;

    private final Map<String, OcrText> _regionOcrText;
    private final Map<String, Rectangle2D> _regionArea;
    private final Map<String, TreeMap<Float, TreeSet<Float>>> _characterListMapping;
    private int _textPosIdx; // holds index of the character in the currently processed block of text


    public RecoTextStripperByArea() throws IOException {
        super();
        _regionOcrText = new HashMap<>();
        _regionArea = new HashMap<>();
        _characterListMapping = new HashMap<>();
        addOperator(new BeginMarkedContentSequenceWithProperties(this));
        addOperator(new EndMarkedContentSequence(this));
        super.setSortByPosition(true);
    }


    @Override
    public void addRegion(String regionName, Rectangle2D rect) {
        _regionArea.put(regionName, rect);
        super.addRegion(regionName, rect);
    }


    @Override
    protected void processTextPosition(TextPosition textPos) {

        // Step 1: get confidencies for the processing text
        List<Integer> confList = null; // list of per-symbol confidence levels
        Integer blockConf = 0;
        boolean rtl;
        boolean rtlPara;
        if (_currentProp != null) {
            confList = OcrPdfUtils.getSymbolConfidence(_currentProp); // retrieves confidencies list from PDF properties
            if (confList == null || confList.isEmpty()) {
                // fall back to block confidence in case confidencies list is not present due to some reason
                blockConf = OcrPdfUtils.getBlockConfidence(_currentProp);
                confList = null; // reset it to null in case it came empty
            }
            rtl = OcrPdfUtils.isRtl(_currentProp);
            rtlPara = OcrPdfUtils.isRtlPara(_currentProp);
        } else {
            rtl = false;
            rtlPara = false;
        }

        // In case this is macine-generated PDF - OCR properties will be absent, thus 100% confidence to be used
        // Otherwise use confidence levels from OCR properties added by OCR service during file pre-processing
        int confidence = _currentProp == null ? 100 : confList == null ? blockConf == null ? 100 : blockConf : confList.get(_textPosIdx);

        // Step 2: get text coordinates
        double x = textPos.getX();
        double extraHeight = textPos.getY() < textPos.getHeightDir() ? textPos.getHeightDir() - textPos.getY() : 0; // handle cases when OCR returned unusual postions
        double y = textPos.getY() - textPos.getHeightDir() + extraHeight;
        double w = textPos.getWidthDirAdj();
        double h = textPos.getHeightDir() - extraHeight;
        double centerX = x + w / 2;
        double centerY = y + h / 2;

        boolean showCharacter = true;

        if (getSuppressDuplicateOverlappingText()) {
            showCharacter = false;
            String textCharacter = textPos.getUnicode();
            float textX = textPos.getX();
            float textY = textPos.getY();
            TreeMap<Float, TreeSet<Float>> sameTextCharacters = _characterListMapping.get(textCharacter);
            if (sameTextCharacters == null) {
                sameTextCharacters = new TreeMap<>();
                _characterListMapping.put(textCharacter, sameTextCharacters);
            }

            boolean suppressCharacter = false;
            float tolerance = (textPos.getWidth() / textCharacter.length()) / 3.0f;

            SortedMap<Float, TreeSet<Float>> xMatches = sameTextCharacters.subMap(textX - tolerance, textX + tolerance);
            for (TreeSet<Float> xMatch : xMatches.values()) {
                SortedSet<Float> yMatches = xMatch.subSet(textY - tolerance, textY + tolerance);
                if (!yMatches.isEmpty()) {
                    suppressCharacter = true;
                    break;
                }
            }

            if (!suppressCharacter) {
                TreeSet<Float> ySet = sameTextCharacters.get(textX);
                if (ySet == null) {
                    ySet = new TreeSet<>();
                    sameTextCharacters.put(textX, ySet);
                }
                ySet.add(textY);
                showCharacter = true;
            }
        }

        if (!showCharacter) {
            // Do not add character to region text
            super.processTextPosition(textPos);
            return;
        }

        // Create Rectangle for the current text (which is last processed text)
        _lastTextRect = new Rectangle(x, y, w, h);

        // Check if any of defined regions contain current text
        _regionArea.forEach((key, rect) -> {
            if (rect.contains(centerX, centerY)) {
                // Retrieve OcrText model (and create in case it was not present)
                OcrText regionText = _regionOcrText.get(key);
                if (regionText == null) {
                    regionText = new OcrText();
                    _regionOcrText.put(key, regionText);
                }

                // Add current text (usually it is a single character, but cases happen when two or more character are comes as well)
                regionText.setText(regionText.getText().concat(textPos.getUnicode()));

                // Add per-character data
                for (int i = 0; i < textPos.getUnicode().length(); i++) {
                    OcrSymbol symbol = new OcrSymbol();
                    symbol.setChar(textPos.getUnicode().charAt(i));
                    symbol.setConfidence(confidence);
                    symbol.setRtl(rtl);
                    symbol.setRtlPara(rtlPara);
                    symbol.setFontSizePt(textPos.getXScale());
                    symbol.setRegion(new Rectangle(x, y, w, h));
                    regionText.getOcrData().add(symbol);
                }
            }
        });

        _textPosIdx++;

        super.processTextPosition(textPos);
    }


    @Override
    public void beginMarkedContentSequence(COSName tag, COSDictionary properties) {
        // Store current properties of current MarkedContent block for use during text/per-character analysis
        _currentProp = properties;
        _textPosIdx = 0;
    }


    @Override
    public void endMarkedContentSequence() {
        if (_lastTextRect == null) {
            return;
        }

        _regionArea.forEach((key, rect) -> {
            if (rect.contains(_lastTextRect.getCenterX(), _lastTextRect.getCenterY())) {
                OcrText regionText = _regionOcrText.get(key);
                if (regionText == null) {
                    regionText = new OcrText();
                    _regionOcrText.put(key, regionText);
                }
            }
        });

        _currentProp = null;
    }


    /**
     * Returns {@link OcrText} for requested region, if present. This also performs sorting of extracted
     * region text in 'natural' ('reading') order
     *
     * @param region
     * @return
     */
    public OcrText getTextForRegionWithCoords(String region) {
        OcrText processedResult = _regionOcrText.containsKey(region) ? _regionOcrText.get(region) : new OcrText();
        if (super.getSortByPosition()) {
            List<OcrSymbol> sortedText = OcrTextUtils.sortSymbols(processedResult.getOcrData());
            OcrText result = new OcrText();
            result.getOcrData().addAll(sortedText);
            return result.rebuild().trim();
        } else {
            return processedResult.rebuild().trim();
        }
    }

}
