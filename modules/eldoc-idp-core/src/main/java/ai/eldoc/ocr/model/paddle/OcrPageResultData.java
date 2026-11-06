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
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Paddle OCR results for document page/image
 */
public class OcrPageResultData implements Serializable {

    private static final long serialVersionUID = -3232948650692054230L;

    private static final Logger LOG = LoggerFactory.getLogger(OcrPageResultData.class);

    private static final String _PREPROCESSING_RESULT = "doc_preprocessor_res";
    private static final String _TEXT_DET_POLYGONS = "dt_polys";
    private static final String _TEXT_TYPE = "text_type";
    private static final String _TEXTLINE_ORIENTATION_ANGLES = "textline_orientation_angles";
    private static final String _TEXT = "rec_texts";
    private static final String _TEXT_CONF = "rec_scores";
    private static final String _TEXT_POLYGONS = "rec_polys";
    private static final String _WORD_BOXES = "rec_boxes";
    private static final String _WORD_PARTS = "text_word";
    private static final String _WORD_POLYGONS = "text_word_boxes";

    private OcrPagePreprocessorResult _preprocessingResult;
    private String _textType;
    private List<Integer> _textlineOrientationAngles;

    private List<String> _text;
    private List<Double> _textConf;
    private List<List<List<Integer>>> _textPolygons;
    private List<List<List<Integer>>> _textDetPolygons;

    private List<List<Integer>> _wordBoxes;
    private List<List<String>> _wordParts;
    private List<List<List<Integer>>> _wordPolygons;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void scale(Double scale) {
        if (_textDetPolygons != null) {
            _textDetPolygons = scaleArrayOfPolygons(_textDetPolygons, scale);
        }
        if (_textPolygons != null) {
            _textPolygons = scaleArrayOfPolygons(_textPolygons, scale);
        }
        if (_wordBoxes != null) {
            _wordBoxes = scalePolygon(_wordBoxes, scale);
        }
        if (_wordPolygons != null) {
            _wordPolygons = scaleArrayOfPolygons(_wordPolygons, scale);
        }
    }


    private List<List<List<Integer>>> scaleArrayOfPolygons(List<List<List<Integer>>> polyArray, double scale) {
        return polyArray.stream()
                .map(poly -> scalePolygon(poly, scale))
                .collect(Collectors.toList());
    }


    private List<List<Integer>> scalePolygon(List<List<Integer>> poly, double scale) {
        return poly.stream()
                .map(coord -> scaleCoord(coord, scale))
                .collect(Collectors.toList());
    }


    private List<Integer> scaleCoord(List<Integer> coord, double scale) {
        return coord.stream()
                .map(axisCoord -> (int) (axisCoord * scale))
                .collect(Collectors.toList());
    }


    @JsonbTransient
    public List<OcrTextBlockResult> getResults() {
        List<OcrTextBlockResult> result = new ArrayList<>();

        double avgTextSkew = getAvgTextLineAngle();
        boolean textSkewed = Math.abs(avgTextSkew) > 0.35;
        for (int resultIdx = 0; resultIdx < getText().size(); resultIdx++) {
            List<List<Integer>> resultPoly = getTextPolygons().get(resultIdx);

            Point resultTL = paddleCoordsToPoint(resultPoly.get(0));
            Point resultTR = paddleCoordsToPoint(resultPoly.get(1));
            Point resultBR = paddleCoordsToPoint(resultPoly.get(2));
            Point resultBL = paddleCoordsToPoint(resultPoly.get(3));

            List<String> part = getWordParts().get(resultIdx);
            List<List<Integer>> resultCoords = getWordPolygons().get(resultIdx);
            if (textSkewed) {
                // Text is (potentially) skewed - process text line per-word to build stairlike structure, imitating skew
                for (int partIdx = 0; partIdx < part.size(); partIdx++) {
                    String partText = part.get(partIdx);
                    Rectangle partRect = bboxToRectangle(resultCoords.get(partIdx));

                    OcrTextBlockResult block = new OcrTextBlockResult();
                    block.setText(partText);
                    block.setConfidence(getTextConf().get(resultIdx));
                    block.setOrientationAngle(getTextlineOrientationAngles().get(resultIdx));
                    block.setBoundingBox(computeWordRect(resultTL, resultTR, resultBR, resultBL, partRect.getX(), partRect.getMaxX(), partText.length(), !isCJK(partText)));
                    result.add(block);
                }
            } else {
                if (isBlank(getText().get(resultIdx))) {
                    // Do not include empty text blocks in results
                    LOG.debug("Paddle OCR: Ignoring empty word/block result with idx={}", resultIdx);
                    continue;
                }
                OcrTextBlockResult block = new OcrTextBlockResult();
                block.setText(getText().get(resultIdx));
                block.setConfidence(getTextConf().get(resultIdx));
                block.setOrientationAngle(getTextlineOrientationAngles().get(resultIdx));
                block.setBoundingBox(bboxToRectangle(getWordBoxes().get(resultIdx)));
                result.add(block);
            }
        }

        return result;
    }


    private Rectangle bboxToRectangle(List<Integer> bbox) {
        double x = bbox.get(0);
        double y = bbox.get(1);
        double w = bbox.get(2) - bbox.get(0);
        double h = bbox.get(3) - bbox.get(1);
        return new Rectangle(x, y, w, h);
    }


    private Point paddleCoordsToPoint(List<Integer> coords) {
        return new Point(coords.get(0), coords.get(1));
    }


    private double getAvgTextLineAngle() {
        return getTextPolygons().stream()
                .mapToDouble(this::textLinePolyAngle)
                .average().orElse(0);
    }


    private double textLinePolyAngle(List<List<Integer>> textLinePoly) {
        Point resultTL = paddleCoordsToPoint(textLinePoly.get(0));
        Point resultTR = paddleCoordsToPoint(textLinePoly.get(1));
        Point resultBR = paddleCoordsToPoint(textLinePoly.get(2));
        Point resultBL = paddleCoordsToPoint(textLinePoly.get(3));
        double topAngle = lineAngle(resultTL, resultTR);
        double bottomAngle = lineAngle(resultBL, resultBR);
        return (topAngle + bottomAngle) / 2.0;
    }


    private double lineAngle(Point p1, Point p2) {
        return Math.toDegrees(Math.atan2(p2.getY() - p1.getY(), p2.getX() - p1.getX()));
    }


    private static boolean isCJK(String str) {
        int length = str.length();
        for (int i = 0; i < length; i++) {
            char ch = str.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
            if (Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block)
                    || Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS.equals(block)
                    || Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A.equals(block)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Calculates word Rectangle from available data from PaddleOCR results. This method handles:
     * <ul>
     * <li>Skewed text (defined by TL/TR/BR/BL points polygon);</li>
     * <li>Text baseline adjustments (we need chars to end at baseline, and PaddleOCR returns text ending at bottom pixel of char);</li>
     * <li>Extra padding, added by PaddleOCR on left/right of the word to avoid overlapping of words after inner detection loop (not all langs - i.e. CJK doesn't have this padding);</li>
     * </ul>
     *
     * @param TL Top-Left corner of text block, that includes current word
     * @param TR Top-Right corner of text block, that includes current word
     * @param BR Bottom-Right corner of text block, that includes current word
     * @param BL Bottom-Left corner of text block, that includes current word
     * @param wordXMin Leftmost point of word, as reported by PaddleOCR
     * @param wordXMax Rightmost point of word, as reported by PaddleOCR
     * @param totalCharCount Number of characters in word (for padding calculations)
     * @param applyPadding Whether paddings should be added to calculated word coordinates
     * @return
     */
    private static Rectangle computeWordRect(Point TL, Point TR, Point BR, Point BL, double wordXMin, double wordXMax, int totalCharCount, boolean applyPadding) {
        double textXMin = Math.min(TL.x, BL.x);
        double textXMax = Math.max(TR.x, BR.x);
        double textWidth = textXMax - textXMin;

        double tMin = (wordXMin - textXMin) / textWidth;
        double tMax = (wordXMax - textXMin) / textWidth;

        tMin = Math.clamp(tMin, 0.0, 1.0);
        tMax = Math.clamp(tMax, 0.0, 1.0);

        double topYMin = lerp(TL.y, TR.y, tMin);
        double topYMax = lerp(TL.y, TR.y, tMax);

        double bottomYMin = lerp(BL.y, BR.y, tMin);
        double bottomYMax = lerp(BL.y, BR.y, tMax);

        double yMin = Math.min(topYMin, topYMax);
        double yMax = Math.max(bottomYMin, bottomYMax);

        // ---- horizontal padding reconstruction ----
        double pad;
        if (applyPadding) {
            double avgCharWidth = (wordXMax - wordXMin) / Math.max(1, totalCharCount);
            pad = avgCharWidth * 0.35; // Empyrically stable, required due to CTC character truncation from PaddleOCR
        } else {
            pad = 0;
        }

        double xMin = wordXMin - pad;
        double xMax = wordXMax + pad;

        double w = xMax - xMin;
        double h = yMax - yMin;

        float yAdj = 0.8941f; // Returned Y coord is last pixel of character, not baseline, so needs adjustments
        double hAdjusted = h * yAdj; // height after adjustment

        return new Rectangle(xMin, yMin, w, hAdjusted);
    }


    static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @JsonbProperty(_PREPROCESSING_RESULT)
    public OcrPagePreprocessorResult getPreprocessingResult() {
        return _preprocessingResult;
    }


    @JsonbProperty(_PREPROCESSING_RESULT)
    public void setPreprocessingResult(OcrPagePreprocessorResult preprocessingResult) {
        _preprocessingResult = preprocessingResult;
    }


    /**
     * Polygon boxes of detected text. Each box in list is array of points (tl, tr, br, bl)
     *
     * @return
     */
    @JsonbProperty(_TEXT_DET_POLYGONS)
    public List<List<List<Integer>>> getTextDetPolygons() {
        return _textDetPolygons;
    }


    @JsonbProperty(_TEXT_DET_POLYGONS)
    public void setTextDetPolygons(List<List<List<Integer>>> textDetPolygons) {
        _textDetPolygons = textDetPolygons;
    }


    @JsonbProperty(_TEXT_TYPE)
    public String getTextType() {
        return _textType;
    }


    @JsonbProperty(_TEXT_TYPE)
    public void setTextType(String textType) {
        _textType = textType;
    }


    @JsonbProperty(_TEXTLINE_ORIENTATION_ANGLES)
    public List<Integer> getTextlineOrientationAngles() {
        return _textlineOrientationAngles;
    }


    @JsonbProperty(_TEXTLINE_ORIENTATION_ANGLES)
    public void setTextlineOrientationAngles(List<Integer> textlineOrientationAngles) {
        _textlineOrientationAngles = textlineOrientationAngles;
    }


    @JsonbProperty(_TEXT)
    public List<String> getText() {
        return _text;
    }


    @JsonbProperty(_TEXT)
    public void setText(List<String> text) {
        _text = text;
    }


    @JsonbProperty(_TEXT_CONF)
    public List<Double> getTextConf() {
        return _textConf;
    }


    @JsonbProperty(_TEXT_CONF)
    public void setTextConf(List<Double> textConf) {
        _textConf = textConf;
    }


    /**
     * List of text detection polygon boxes, filtered by confidence scores. Each box in list is array of points (tl, tr, br, bl)
     *
     * @return
     */
    @JsonbProperty(_TEXT_POLYGONS)
    public List<List<List<Integer>>> getTextPolygons() {
        return _textPolygons;
    }


    @JsonbProperty(_TEXT_POLYGONS)
    public void setTextPolygons(List<List<List<Integer>>> textPolygons) {
        _textPolygons = textPolygons;
    }


    /**
     * List of rectangular bounding boxes of detected words<br>
     * <b>IMPORTANT</b>: returned bounding boxes are not rotated
     *
     * @return
     */
    @JsonbProperty(_WORD_BOXES)
    public List<List<Integer>> getWordBoxes() {
        return _wordBoxes;
    }


    @JsonbProperty(_WORD_BOXES)
    public void setWordBoxes(List<List<Integer>> wordBoxes) {
        _wordBoxes = wordBoxes;
    }


    @JsonbProperty(_WORD_PARTS)
    public List<List<String>> getWordParts() {
        return _wordParts;
    }


    @JsonbProperty(_WORD_PARTS)
    public void setWordParts(List<List<String>> wordParts) {
        _wordParts = wordParts;
    }


    /**
     * When return_word_box is set to True, returns a list of bounding box coordinates for each recognized character.
     *
     * @return
     */
    @JsonbProperty(_WORD_POLYGONS)
    public List<List<List<Integer>>> getWordPolygons() {
        return _wordPolygons;
    }


    @JsonbProperty(_WORD_POLYGONS)
    public void setWordPolygons(List<List<List<Integer>>> wordPolygons) {
        _wordPolygons = wordPolygons;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 17 * hash + Objects.hashCode(_preprocessingResult);
        hash = 17 * hash + Objects.hashCode(_textDetPolygons);
        hash = 17 * hash + Objects.hashCode(_textType);
        hash = 17 * hash + Objects.hashCode(_textlineOrientationAngles);
        hash = 17 * hash + Objects.hashCode(_text);
        hash = 17 * hash + Objects.hashCode(_textConf);
        hash = 17 * hash + Objects.hashCode(_textPolygons);
        hash = 17 * hash + Objects.hashCode(_wordParts);
        hash = 17 * hash + Objects.hashCode(_wordPolygons);
        hash = 17 * hash + Objects.hashCode(_wordBoxes);
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
        final OcrPageResultData other = (OcrPageResultData) obj;
        if (!Objects.equals(_preprocessingResult, other._preprocessingResult)) {
            return false;
        }
        if (!Objects.equals(_textType, other._textType)) {
            return false;
        }
        if (!Objects.equals(_textDetPolygons, other._textDetPolygons)) {
            return false;
        }
        if (!Objects.equals(_textlineOrientationAngles, other._textlineOrientationAngles)) {
            return false;
        }
        if (!Objects.equals(_text, other._text)) {
            return false;
        }
        if (!Objects.equals(_textConf, other._textConf)) {
            return false;
        }
        if (!Objects.equals(_wordParts, other._wordParts)) {
            return false;
        }
        if (!Objects.equals(_textPolygons, other._textPolygons)) {
            return false;
        }
        if (!Objects.equals(_wordPolygons, other._wordPolygons)) {
            return false;
        }
        return Objects.equals(_wordBoxes, other._wordBoxes);
    }
}
