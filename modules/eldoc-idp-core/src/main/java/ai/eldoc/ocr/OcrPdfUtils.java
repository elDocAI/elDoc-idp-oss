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

import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.reco.RecoPdfUtils;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.COSArrayList;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import static org.apache.commons.lang3.StringUtils.isEmpty;


/**
 * OCR-related methods for working with PDF documents
 */
public class OcrPdfUtils {

    private static final Logger LOG = LoggerFactory.getLogger(OcrPdfUtils.class);

    private static final String MC_ELDOC_NAME = "elDoc";
    private static final String PROP_BLOCK_CONF = "blockConf";
    private static final String PROP_SYMB_CONF = "symbConf";
    private static final String PROP_RTL = "rtl";
    private static final String PROP_RTL_PARA = "rtlPara";


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private OcrPdfUtils() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static void addOcrText(PDDocument document, PDPage page, OcrResult ocrResult) throws IOException {

        PDPageContentStream.AppendMode appendMode = RecoPdfUtils.isAccessibilityFixesEnabled() ? PDPageContentStream.AppendMode.APPEND : PDPageContentStream.AppendMode.PREPEND;

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page, appendMode, true)) {
            if (RecoPdfUtils.isAccessibilityFixesEnabled()) {
                RecoPdfUtils.applyAccessibilityFixes(contentStream);
            }

            Map<TrueTypeFont, PDFont> fontsCache = new HashMap<>();

            // Get visual-ordered text from logical-ordered text
            OcrBlock reorderedText = OcrTextUtils.bidiReorderToVisual(ocrResult.getOcrBlock(), !ocrResult.isTextDirectionDetected());
            // Iterate over all OcrWords
            for (OcrText ocrWord : reorderedText.getOcrData()) {

                if (isEmpty(ocrWord.getText())) {
                    // Do not try to add empty text to PDF
                    // IMPORTANT! We still add whitespace-only text blocks, as they may be important for text extraction
                    continue;
                }

                // Load unicode TTF font so we can add text on PDF page in CJK languages as well
                TrueTypeFont ttfFont = PdfUtils.getFont(ocrWord.getText());
                PDFont font = fontsCache.get(ttfFont);
                if (font == null) {
                    font = PDType0Font.load(document, ttfFont, true);
                    fontsCache.put(ttfFont, font);
                }

                addOcrText(
                        contentStream,
                        ocrWord.getRegion().getX(),
                        page.getCropBox().getHeight() - ocrWord.getRegion().getMaxY(),
                        ocrWord.getRegion().getWidth(),
                        font,
                        ocrWord.getFontSizePt(),
                        ocrWord.getText(),
                        ocrWord.getConfidence(),
                        ocrWord.getOcrData().stream().map(OcrSymbol::getConfidence).collect(Collectors.toList()),
                        ocrWord.isRtl(),
                        ocrWord.isRtlPara());
            }
        }
    }


    /**
     * Adds OCR text to the preprocessed PDF file<br>
     * This method performs validation that all glyphs within word are supported by the given font
     * and in case not - replaces unknown (not supported) glyphs with \u2219 glyph
     *
     * @param contentStream
     * @param offsetX
     * @param offsetY
     * @param width
     * @param font
     * @param fontSize
     * @param word
     * @param wordConf
     * @param symbConf
     * @param rtl
     * @param rtlPara
     * @return actually added word to the PDF after sanitizing it for the unknown glyphs
     * @throws IOException
     */
    static String addOcrText(
            PDPageContentStream contentStream,
            double offsetX,
            double offsetY,
            double width,
            PDFont font,
            float fontSize,
            String word,
            int wordConf,
            List<Integer> symbConf,
            boolean rtl,
            boolean rtlPara) throws IOException {

        COSDictionary eldocParams = new COSDictionary();
        eldocParams.setInt(PROP_BLOCK_CONF, wordConf);

        //https://pdfbox.apache.org/docs/2.0.13/javadocs/org/apache/pdfbox/cos/class-use/COSArray.html
        eldocParams.setItem(PROP_SYMB_CONF, COSArrayList.converterToCOSArray(symbConf));

        eldocParams.setBoolean(PROP_RTL, rtl);
        eldocParams.setBoolean(PROP_RTL_PARA, rtlPara);

        // Making sure font supports all given glyphs withing word
        try {
            font.encode(word); // This may throw exciption like: java.lang.IllegalArgumentException: No glyph for U+0719 (ܙ) in font Noto Sans
        } catch (IllegalArgumentException e) {
            // For such cases - let's replace unknown glyph with something known
            StringBuilder sanitizedWord = new StringBuilder(word);
            char unknGlyphReplacer = "\u00B7".charAt(0);
            for (int i = 0; i < word.length(); i++) {
                try {
                    font.encode(Character.toString(word.charAt(i)));
                } catch (IllegalArgumentException e1) {
                    LOG.warn("Replacing unknown glyph due to: {}", e.toString());
                    sanitizedWord.setCharAt(i, unknGlyphReplacer);
                }
            }
            word = sanitizedWord.toString();
        }

        float fontWidth = font.getStringWidth(word) / 1000 * fontSize;
        float scaleX = (float) (width / fontWidth);

        // Scale can be calculated wrong, if fontSize or provided width are incorrect. In this case, treat scale as 1
        if (scaleX == Float.POSITIVE_INFINITY || scaleX <= 0) {
            LOG.debug("Invalid scale {}, resetting to 1", scaleX);
            scaleX = 1;
        } else if (scaleX > 1.5) {
            scaleX = 1;
        }

        contentStream.beginMarkedContent(COSName.getPDFName(MC_ELDOC_NAME), PDPropertyList.create(eldocParams));
        contentStream.beginText();
        contentStream.setTextMatrix(Matrix.getScaleInstance(scaleX, 1));
        contentStream.newLineAtOffset((float) (offsetX / scaleX), (float) offsetY);
        contentStream.setFont(font, fontSize);
        contentStream.showText(word);
        contentStream.endText();
        contentStream.endMarkedContent();

        return word;
    }


    /**
     * Return whole text block confidence
     *
     * @param markedContentProperties
     * @return
     */
    public static Integer getBlockConfidence(COSDictionary markedContentProperties) {
        COSName blockConf = COSName.getPDFName(PROP_BLOCK_CONF);
        if (markedContentProperties.containsKey(blockConf)) {
            return markedContentProperties.getInt(blockConf);
        }
        return null;
    }


    public static List<Integer> getSymbolConfidence(COSDictionary markedContentProperties) {
        COSName symbConf = COSName.getPDFName(PROP_SYMB_CONF);
        if (markedContentProperties.containsKey(symbConf)) {
            return markedContentProperties.getCOSArray(symbConf).toCOSNumberIntegerList();
        }
        return new ArrayList();
    }


    public static boolean isRtl(COSDictionary markedContentProperties) {
        COSName rtl = COSName.getPDFName(PROP_RTL);
        if (markedContentProperties.containsKey(rtl)) {
            return markedContentProperties.getBoolean(rtl, false);
        }
        return false;
    }


    public static boolean isRtlPara(COSDictionary markedContentProperties) {
        COSName rtlPara = COSName.getPDFName(PROP_RTL_PARA);
        if (markedContentProperties.containsKey(rtlPara)) {
            return markedContentProperties.getBoolean(rtlPara, false);
        }
        return false;
    }

}
