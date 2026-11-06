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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.PropertyConfig;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.reco.CvUtils;
import ai.eldoc.reco.ImageProcessor;
import ai.eldoc.service.AppConfigService;
import ai.eldoc.util.FileUtils;
import org.bytedeco.javacpp.*;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.tesseract.*;
import org.bytedeco.tesseract.global.tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static java.text.MessageFormat.format;
import static org.bytedeco.tesseract.global.tesseract.*;


/**
 * OCR Engine - Tesseract Implementation
 */
public final class OcrEngineTessImpl implements OcrEngine, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OcrEngineTessImpl.class);

    private static final String ELDOC_JVM_OPTION_OCR_PAGE_TIMEOUT_SEC = "ai.eldoc.ocr.page.timeoutSec";
    private static final int TIMEOUT_PER_PAGE_SEC = Integer.getInteger(ELDOC_JVM_OPTION_OCR_PAGE_TIMEOUT_SEC, 300);
    private static final boolean DEBUG_PRINT_RESULTS = Boolean.getBoolean(ELDOC_JVM_OPTION_OCR_DEBUG_PRINT_RESULTS);

    /**
     * 0 PSM_OSD_ONLY Orientation and script detection (OSD) only. <br>
     * 1 PSM_AUTO_OSD Automatic page segmentation with OSD. <br>
     * 2 PSM_AUTO_ONLY Automatic page segmentation, but no OSD, or OCR. <br>
     * 3 PSM_AUTO Fully automatic page segmentation, but no OSD. (Default) <br>
     * 4 PSM_SINGLE_COLUMN Assume a single column of text of variable sizes. <br>
     * 5 PSM_SINGLE_BLOCK_VERT_TEXT Assume a single uniform block of vertically aligned text. <br>
     * 6 PSM_SINGLE_BLOCK Assume a single uniform block of text. <br>
     * 7 PSM_SINGLE_LINE Treat the image as a single text line. <br>
     * 8 PSM_SINGLE_WORD Treat the image as a single word. <br>
     * 9 PSM_CIRCLE_WORD Treat the image as a single word in a circle. <br>
     * 10 PSM_SINGLE_CHAR Treat the image as a single character. <br>
     * 11 PSM_SPARSE_TEXT Sparse text. Find as much text as possible in no particular order. <br>
     * 12 PSM_SPARSE_TEXT_OSD Sparse text with OSD. <br>
     * 13 PSM_RAW_LINE Raw line. Treat the image as a single text line, bypassing hacks that are Tesseract-specific. <br>
     */
    private static final int DEFAULT_PSM_MODE = tesseract.PSM_AUTO;

    private static final String _LANG_ENG = "eng";
    private static final String _LANG_OSD = "osd";
    public static final String DEFAULT_LANG = _LANG_ENG;

    private static final List<String> CUSTOM_PREPROCESSING_PROPERTIES = Arrays.asList(PropertyConfig.IDP_OCR_MODE_SPARSE_TEXT);

    private TessBaseAPI _tessEngine;
    private ETEXT_DESC _tessMonitor;
    private String _tessDataPath;
    private final Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    OcrEngineTessImpl(OcrService.MODE engineMode, List<String> languages) {
        _properties = new HashMap<>();

        LOG.trace("Initializing OCR TessEngine with mode: {}", engineMode.name());

        if (engineMode == null) {
            throw new ElDocRuntimeException("OCR TessEngine mode can not be null");
        }

        _tessDataPath = AppConfigService.getInstance().getIdpConfig().getTessDataPath();
        if (!FileUtils.isFolderValid(_tessDataPath)) {
            throw new ElDocRuntimeException("OCR TessEngine tessData path [" + _tessDataPath + "] is not valid");
        }

        // Create Tess Base API
        _tessEngine = new TessBaseAPI();

        // Init Tess Base API
        switch (engineMode) {

            case LAYOUT_ANALYSIS:
                initModeLayoutAnalysis();
                break;

            case OCR:
            default:
                // This is default
                if (languages == null || languages.isEmpty()) {
                    initModeOcr(Arrays.asList(DEFAULT_LANG));
                } else {
                    initModeOcr(languages);
                }
                break;
        }
    }


    /**
     * Inits OCR Service in OCR mode
     */
    private void initModeOcr(List<String> languages) {
        int initCode = _tessEngine.Init(_tessDataPath, getLangsAsString(languages));
        if (initCode != 0) {
            throw new ElDocRuntimeException("Error initializing OCR TessEngine, initCode=" + initCode);
        }
        _tessEngine.SetPageSegMode(DEFAULT_PSM_MODE);
    }


    /**
     * Inits OCR Service in Layout Analysis mode
     */
    private void initModeLayoutAnalysis() {
        int initCode = _tessEngine.Init(_tessDataPath, _LANG_OSD);
        if (initCode != 0) {
            throw new ElDocRuntimeException("Error initializing OCR TessEngine, initCode=" + initCode);
        }

        _tessEngine.SetPageSegMode(tesseract.PSM_OSD_ONLY);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns distinct list of enabled languages joined via "+" (optimized for OCR Service) in format: eng+chi_tra
     *
     * @return
     */
    private String getLangsAsString(List<String> langs) {
        return String.join("+", langs.stream().distinct().collect(Collectors.toList()));
    }


    /**
     * @inheritDoc
     */
    @Override
    public OcrResult processImage(BufferedImage image, boolean withPrepareForOcr) throws TimeoutException {
        OcrResult result = new OcrResult();
        result.setTextDirectionDetected(true);
        OcrBlock ocrBlock = new OcrBlock();
        result.setOcrBlock(ocrBlock);
        try {
            // Method try-finally block

            // If OCR Service has IDP_OCR_MODE_SPARCE_TEXT set to true - switch to PSM_SPARCE_TEXT, otherwise - keep previously defined mode
            // Doesn't reset mode to default
            if (getProperty(PropertyConfig.IDP_OCR_MODE_SPARSE_TEXT, false)) {
                _tessEngine.SetPageSegMode(tesseract.PSM_SPARSE_TEXT);
            }

            // Get page image
            Mat pageImageForOcr = null;
            try {
                // Set image for Tesseract engine
                if (withPrepareForOcr) {
                    pageImageForOcr = ImageProcessor.prepareImageForOcrAndGetMat(image);
                } else {
                    pageImageForOcr = ImageProcessor.bufferedImageToMat(image);
                }
                _tessEngine.SetImage(pageImageForOcr.data().asBuffer(), pageImageForOcr.size().width(), pageImageForOcr.size().height(), pageImageForOcr.channels(), (int) pageImageForOcr.step1());
            } finally {
                CvUtils.close(pageImageForOcr);
            }

            // Perform recognition logic
            try {
                // Recognition & TessMonitor try-finally block

                _tessMonitor = tesseract.TessMonitorCreate();
                tesseract.TessMonitorSetDeadlineMSecs(_tessMonitor, TIMEOUT_PER_PAGE_SEC * 1000);

                Instant start = Instant.now();

                // Perform recognition
                _tessEngine.Recognize(_tessMonitor);

                long timeElapsed = Duration.between(start, Instant.now()).getSeconds();
                String timeElapsedStr = format("{0}m {1}s", (timeElapsed / 60), String.format("%02d", (timeElapsed % 60)));
                LOG.trace("Time taken for performing OCR: [{}]", timeElapsedStr);

                int progressOnEnd = tesseract.TessMonitorGetProgress(_tessMonitor);

                // Check whether recognition process was interrupted by timeout or not (based on progress value after end processing)
                if (progressOnEnd > 0 && progressOnEnd < 100) {
                    LOG.warn("Page processing by OCR TessEngine was interrupted due to timeout at progress [{}%]. "
                            + "Current timeout per page set to [{}] seconds. "
                            + "Consider adjusting JVM property '{}' in order to set a custom timeout value",
                            progressOnEnd, TIMEOUT_PER_PAGE_SEC, ELDOC_JVM_OPTION_OCR_PAGE_TIMEOUT_SEC
                    );
                    throw new TimeoutException(format("Page OCR timed out. Elapsed OCR time: [{0}]", timeElapsedStr));
                }

                // Positional variables
                IntPointer baseLineY1 = new IntPointer(1);
                IntPointer baseLineX1 = new IntPointer(1);
                IntPointer baseLineY2 = new IntPointer(1);
                IntPointer baseLineX2 = new IntPointer(1);

                IntPointer boundingBoxTop = new IntPointer(1);
                IntPointer boundingBoxLeft = new IntPointer(1);
                IntPointer boundingBoxBottom = new IntPointer(1);
                IntPointer boundingBoxRight = new IntPointer(1);

                // Text properties
                BoolPointer isBold = new BoolPointer(1);
                BoolPointer isItalic = new BoolPointer(1);
                BoolPointer isUnderlined = new BoolPointer(1);
                BoolPointer isMonospace = new BoolPointer(1);
                BoolPointer isSerif = new BoolPointer(1);
                BoolPointer isSmallCaps = new BoolPointer(1);
                IntPointer pointSize = new IntPointer(1);
                IntPointer fontId = new IntPointer(1);

                // Result iterators
                ResultIterator resultWordIterator = null;
                ResultIterator resultSymbolIterator = null;

                try {
                    // Result iterators
                    resultWordIterator = _tessEngine.GetIterator();
                    resultSymbolIterator = _tessEngine.GetIterator();

                    // Retrieve recognition results
                    resultWordIterator.Begin();
                    resultSymbolIterator.Begin();

                    BytePointer resultWordPointer = resultWordIterator.GetUTF8Text(RIL_WORD);
                    while (resultWordPointer != null) {
                        // Process OCR on per-word basis

                        boolean rtl = resultWordIterator.WordDirection() == DIR_RIGHT_TO_LEFT;
                        boolean rtlPara = !resultWordIterator.ParagraphIsLtr();
                        String word = resultWordPointer.getString(StandardCharsets.UTF_8);

                        // Tesseract adds RTL/LTR control characters (codes 8206 (\u200E) and 8207 (\u200F)), which we need to remove from the
                        // resulting text as we handle it on our own by using rtl/rtlPara flags
                        word = word.replace("\u200E", "");
                        word = word.replace("\u200F", "");

                        // As per: https://tesseract-ocr.github.io/tessapi/4.0.0/a02346.html#a22f65a94be27435440673d7c750808fb
                        // char * tesseract::ResultIterator::GetUTF8Text ( PageIteratorLevel level) const
                        // Returns the null terminated UTF-8 encoded text string for the current object at the given level. Use delete [] to free after use.
                        CvUtils.close(resultWordPointer);

                        // https://github.com/tesseract-ocr/tesseract/issues/1074
                        // Returns the font attributes of the current word. If iterating at a higher level object than words, eg textlines, then this will return the
                        // attributes of the first word in that textline. The actual return value is a string representing a font name. It points  to an internal table and SHOULD NOT BE DELETED.
                        // Lifespan is the same as the iterator itself, ie rendered invalid by various members of TessBaseAPI, including Init, SetImage, End or deleting the TessBaseAPI.
                        // Pointsize is returned in printers points (1/72 inch.)
                        // const char* LTRResultIterator::WordFontAttributes(bool* is_bold, bool* is_italic, bool* is_underlined, bool* is_monospace, bool* is_serif, bool* is_smallcaps, int* pointsize, int* font_id)
                        resultWordIterator.WordFontAttributes(isBold, isItalic, isUnderlined, isMonospace, isSerif, isSmallCaps, pointSize, fontId);
                        resultWordIterator.Baseline(RIL_WORD, baseLineX1, baseLineY1, baseLineX2, baseLineY2);
                        resultWordIterator.BoundingBox(RIL_WORD, boundingBoxLeft, boundingBoxTop, boundingBoxRight, boundingBoxBottom);
                        float confWord = resultWordIterator.Confidence(RIL_WORD);

                        // Adjustment for baseline (as for further processing in PDFbox particurarly it needs baseline for adding text)
                        // TODO: Consider moving baseLine Y var to a separate field (in OcrTextAttributes)
                        int baselineAdjY = boundingBoxBottom.get() - baseLineY2.get();

                        // OcrWord data model
                        OcrText ocrWord = new OcrText();
                        ocrWord.setText(word);
                        ocrWord.setFontSizePt(pointSize.get() * 0.96f);
                        ocrWord.setRtl(rtl);
                        ocrWord.setRtlPara(rtlPara);
                        ocrWord.setRegion(getRectangleFromBoundingBoxCoords(
                                boundingBoxLeft.get(),
                                boundingBoxTop.get(),
                                boundingBoxRight.get(),
                                boundingBoxBottom.get(),
                                baselineAdjY
                        ));

                        ocrWord.setConfidence((int) confWord);

                        // Retrieve per-symbol confidencies
                        // Iterate over each letter in the retrieved word
                        // If current word has space before it - it was already processed and we start from index 1
                        for (int i = 0; i < word.length(); i++) {

                            IntPointer symbolBoundingBoxTop = null;
                            IntPointer symbolBoundingBoxLeft = null;
                            IntPointer symbolBoundingBoxBottom = null;
                            IntPointer symbolBoundingBoxRight = null;
                            BytePointer resultSymbolPointer = null;
                            // Extract symbol from the Result Symbol Iterator
                            try {
                                // Retrieve symbol value
                                resultSymbolPointer = resultSymbolIterator.GetUTF8Text(RIL_SYMBOL);
                                if (resultSymbolPointer == null) {
                                    LOG.warn("OCR TessEngine: resultSymbolPointer is null (word={}; i={}). This should not happen. Please contact support in case you see this message", word, i);
                                    break; // no need to continue
                                }

                                String symbol = resultSymbolPointer.getString(StandardCharsets.UTF_8);

                                // Init Symbol bounding box variables
                                symbolBoundingBoxTop = new IntPointer(1);
                                symbolBoundingBoxLeft = new IntPointer(1);
                                symbolBoundingBoxBottom = new IntPointer(1);
                                symbolBoundingBoxRight = new IntPointer(1);
                                resultSymbolIterator.BoundingBox(RIL_SYMBOL, symbolBoundingBoxLeft, symbolBoundingBoxTop, symbolBoundingBoxRight, symbolBoundingBoxBottom);

                                // Collect symbol info
                                OcrSymbol ocrSymbol = new OcrSymbol();
                                ocrSymbol.setChar(symbol.charAt(0));
                                ocrSymbol.setConfidence((int) resultSymbolIterator.Confidence(RIL_SYMBOL));
                                ocrSymbol.setFontSizePt(ocrWord.getFontSizePt());
                                ocrSymbol.setRtl(rtl);
                                ocrSymbol.setRegion(getRectangleFromBoundingBoxCoords(
                                        symbolBoundingBoxLeft.get(),
                                        symbolBoundingBoxTop.get(),
                                        symbolBoundingBoxRight.get(),
                                        symbolBoundingBoxBottom.get(),
                                        baselineAdjY
                                ));

                                ocrWord.getOcrData().add(ocrSymbol);

                                if (word.codePointAt(i) != symbol.codePointAt(0)) {
                                    LOG.warn("Word({}|{}[{}]) <-> Symbol({}[{}]) iterators sync error. Please contact support in case you see this message",
                                            word,
                                            word.charAt(i),
                                            word.codePointAt(i),
                                            symbol.charAt(0),
                                            symbol.codePointAt(0));
                                }

                                resultSymbolIterator.Next(RIL_SYMBOL);
                            } finally {
                                CvUtils.close(resultSymbolPointer);
                                CvUtils.close(symbolBoundingBoxTop);
                                CvUtils.close(symbolBoundingBoxLeft);
                                CvUtils.close(symbolBoundingBoxBottom);
                                CvUtils.close(symbolBoundingBoxRight);
                            }
                        }

                        // Check and add space before current word, if needed
                        int blanksCount = resultWordIterator.BlanksBeforeWord();
                        if (blanksCount > 0) {
                            // Calculate width of the space in font size of the first symbol in processed word
                            int spaceWidth = Math.round(SPACE_WIDTH * ocrWord.getFontSizePt());

                            OcrSymbol spaceSymbol = new OcrSymbol();
                            spaceSymbol.setChar(' ');
                            spaceSymbol.setFontSizePt(ocrWord.getFontSizePt());
                            spaceSymbol.setConfidence(100);

                            spaceSymbol.setRegion(new Rectangle(
                                    ocrWord.getRegion().getX() - spaceWidth,
                                    ocrWord.getRegion().getY(),
                                    spaceWidth,
                                    ocrWord.getRegion().getHeight()
                            ));

                            // Add space symbol at the beginning of processed word
                            ocrWord.addSymbol(0, spaceSymbol);

                            // Because we are inserting first symbol in front of the word - we need to
                            // adjust word X position with the respective offset and whole width of the word
                            ocrWord.getRegion().setX(ocrWord.getRegion().getX() - spaceWidth);
                            ocrWord.getRegion().setWidth(ocrWord.getRegion().getWidth()+ spaceWidth);
                        }

                        // Adding current word to the whole OcrBlock
                        ocrBlock.addWord(ocrWord);

                        if (DEBUG_PRINT_RESULTS) {
                            LOG.info(format(
                                    "OCR: {0} [conf: {1}]|symbConf---> {2}",
                                    ocrWord.getText(),
                                    ocrWord.getConfidence(),
                                    ocrWord.getOcrData().stream().map(OcrSymbol::getConfidence).collect(Collectors.toList())
                            ));
                        }

                        resultWordIterator.Next(RIL_WORD);
                        resultWordPointer = resultWordIterator.GetUTF8Text(RIL_WORD);
                    }
                } finally {
                    // Deallocate result iterators
                    close(resultWordIterator);
                    close(resultSymbolIterator);

                    // Deallocate variables
                    CvUtils.close(baseLineY1);
                    CvUtils.close(baseLineX1);
                    CvUtils.close(baseLineY2);
                    CvUtils.close(baseLineX2);
                    CvUtils.close(boundingBoxTop);
                    CvUtils.close(boundingBoxLeft);
                    CvUtils.close(boundingBoxBottom);
                    CvUtils.close(boundingBoxRight);
                    CvUtils.close(isBold);
                    CvUtils.close(isItalic);
                    CvUtils.close(isUnderlined);
                    CvUtils.close(isMonospace);
                    CvUtils.close(isSerif);
                    CvUtils.close(isSmallCaps);
                    CvUtils.close(pointSize);
                    CvUtils.close(fontId);
                }
            } finally {
                // Recognition & TessMonitor try-finally block
                close(_tessMonitor);
            }
        } finally {
            // Method try-finally block
        }
        return result;
    }


    /**
     * Converts Tesseract BoundingBox coordinates into Rectangle<br>
     * <b>IMPORTANT</b> uses .abs() for height as test on files Sample5_cn.png, Sample8_cn.png, Sample9_en.png, Sample10_en.png shows that sometimes
     * height comes as negative value. As such Math.abs() used here as a temporary workaround until this case is fully troubleshooted
     *
     * @param boundingBoxLeft
     * @param boundingBoxTop
     * @param boundingBoxRight
     * @param boundingBoxBottom
     * @param baselineAdjY
     * @return
     */
    private Rectangle getRectangleFromBoundingBoxCoords(int boundingBoxLeft, int boundingBoxTop, int boundingBoxRight, int boundingBoxBottom, int baselineAdjY) {
        return new Rectangle(
                boundingBoxLeft,
                boundingBoxTop,
                boundingBoxRight - boundingBoxLeft,
                Math.abs(boundingBoxBottom - boundingBoxTop - baselineAdjY)
        );
    }


    /**
     * @inheritDoc
     */
    @Override
    public int getProgress() {
        if (_tessMonitor == null) {
            return -1;
        }
        return tesseract.TessMonitorGetProgress(_tessMonitor);
    }


    /**
     * @inheritDoc
     */
    @Override
    public int getPageOrientation(BufferedImage image) {
        Mat imageMat = null;
        IntPointer orientation = null;
        FloatPointer orientationConf = null;
        FloatPointer scriptConf = null;
        PointerPointer scriptName = null;
        try {
            imageMat = ImageProcessor.bufferedImageToMat(image);
            orientation = new IntPointer(1);
            orientationConf = new FloatPointer(1);
            scriptConf = new FloatPointer(1);
            scriptName = new PointerPointer();

            _tessEngine.SetImage(imageMat.data().asBuffer(), imageMat.size().width(), imageMat.size().height(), imageMat.channels(), (int) imageMat.step1());
            boolean detectionSuccessfull = _tessEngine.DetectOrientationScript(orientation, orientationConf, scriptName, scriptConf);

            return orientation.get();
        } finally {
            CvUtils.close(orientation);
            CvUtils.close(orientationConf);
            CvUtils.close(scriptConf);
            CvUtils.close(scriptName);
            CvUtils.close(imageMat);
        }
    }


    @Override
    public void close() {
        close(_tessEngine);
    }


    public static void close(TessBaseAPI tessEngine) {
        if (tessEngine != null) {
            tessEngine.End();
            tessEngine.deallocate();
            tessEngine.close();
            tessEngine = null;
        }
    }


    public static void close(ETEXT_DESC tessMonitor) {
        if (tessMonitor != null) {
            tessMonitor.deallocate();
            tessMonitor.close();
            tessMonitor = null;
        }
    }


    public static void close(PageIterator pageIterator) {
        if (pageIterator != null) {
            pageIterator.deallocate();
            pageIterator.close();
            pageIterator = null;
        }
    }


    public static void close(ResultIterator ri) {
        if (ri != null) {
            ri.deallocate();
            ri.close();
            ri = null;
        }
    }


    public static void close(StringVector vector) {
        if (vector != null && !vector.isNull()) {
            while (!vector.empty()) {
                // pop_back() - Returns the last object and removes it
                CvUtils.close(vector.pop_back());
            }
            vector.deallocate();
            vector.close();
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns true in case provided properties require custom pre-processing (if property is present)
     *
     * @param properties
     * @return
     */
    public static boolean isCustomPreprocessingRequired(Map<String, Object> properties) {
        return Collections.disjoint(CUSTOM_PREPROCESSING_PROPERTIES, properties.keySet()) == false;
    }


    /**
     * @inheritDoc
     */
    @Override
    public List<OcrLang> getAvailableLanguages() {
        List<OcrLang> langsList = new ArrayList<>();
        StringVector languages = new StringVector();
        try {
            _tessEngine.GetAvailableLanguagesAsVector(languages);
            while (!languages.empty()) {
                langsList.add(OcrLangUtils.getOcrLanguageByCode(OcrEngine.TYPE.TESS.name(), languages.pop_back().getString()));
            }
        } finally {
            close(languages);
        }
        OcrLangUtils.sort(langsList);
        return langsList;
    }


    /**
     * @inheritDoc
     */
    @Override
    public boolean isPageOrientationDetectionSupported() {
        return true;
    }


    /**
     * @inheritDoc
     */
    @Override
    public boolean isPageOrientationRequiresPreprocessing() {
        return true;
    }


    @Override
    public Map<String, Object> getProperties() {
        return _properties;
    }


    public OcrEngineTessImpl setProperties(Map<String, Object> properties) {
        _properties.putAll(properties);
        return this;
    }
}
