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

import ai.eldoc.TenantContext;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.ai.service.AiService;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.AclContext;
import ai.eldoc.model.PropertyAwareModel;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.reco.ImageProcessor;
import ai.eldoc.service.AppConfigService;
import ai.eldoc.service.DirectoryService;
import ai.eldoc.util.I18n;
import org.apache.commons.lang3.Strings;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * OCR Service
 */
public class OcrService implements AutoCloseable, PropertyAwareModel {

    private static final Logger LOG = LoggerFactory.getLogger(OcrService.class);

    public static final String PROP_REQUEST_BLOCK_ID = "ocr.rqBlkId";

    enum MODE {
        OCR,
        LAYOUT_ANALYSIS
    }

    private final OcrEngine _ocrEngine;


    //
    // Constructor & Initialization methods -------------------------------------------------------
    //
    /**
     * Default constructor for initializing OCR Service in OCR mode (for internal usage)
     */
    private OcrService() {
        this(MODE.OCR, Collections.singletonList(getDefaultLanguageCode()));
    }


    /**
     * Default constructor for initializing OCR Service in OCR mode
     *
     * @param languages List of enabled languages for the OCR engine. If null, the default language is used.
     */
    public OcrService(List<String> languages) {
        this(MODE.OCR, languages);
    }


    /**
     * OCR Service constructor that accepts parameter with initialization mode
     *
     * @param engineMode {@link MODE} of the OCR engine (used primarily for Tesseract engine)
     * @param languages List of enabled languages for the OCR engine. If null, the default language is used.
     */
    private OcrService(MODE engineMode, List<String> languages) {
        LOG.debug("OcrService: creating OcrEngine...");
        _ocrEngine = createOcrEngine(engineMode, languages);
        LOG.debug("OcrService: OcrEngine of type '{}' was created", _ocrEngine.getClass().getSimpleName());
    }


    private static OcrEngine createOcrEngine(MODE engineMode, List<String> languages) {
        if (isOcrEngineVL()) {
            AclContext robotAcl = DirectoryService.getInstance().getAclContext(DirectoryConstants.ELDOC_RECO_BOT);
            AiRequestContext chatContext = AiRequestContext.forSrcIDP(I18n.LANG_SYSTEM_CODE_EN, TenantContext.getCurrentTenantId(), robotAcl);
            return AiService.getInstance().createVLOcrEngine(chatContext);
        } else if (getOcrEngineType() == OcrEngine.TYPE.PDDL) {
            return new OcrEnginePDDLImpl(OcrEnginePDDLRestClientService.getInstance());
        } else {
            // TessEngine is a default one
            return new OcrEngineTessImpl(engineMode, languages == null ? null : OcrLangUtils.normalizeLangList(OcrEngine.TYPE.TESS.name(), languages));
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Processes PDF Page of the elDoc IDP Preprocessed PDF file
     * This method adds required metadata / properties to the PDF document / page with confidence levels
     *
     * @param document {@link PDDocument} that contains the PDF page to be processed.
     * @param page {@link PDPage} that should be processed
     * @param pageImgPreprocessed page image for OCR (equals to the PDPage embedded image, IMPORTANT: flashed inside the method)
     * @param pageImgOrig {@link BufferedImage}, that was originally submitted for page
     * @param imageOnPageArea Actual image position on the PDF page (after applying required transformations)
     * @return OcrBlock of text that was retrieved from the given image and added to the PDPage
     * @throws IOException in case of any IO error
     * @throws ai.eldoc.exceptions.TimeoutException in case time out on page processing is reached
     */
    public OcrBlock processPDPage(PDDocument document, PDPage page, BufferedImage pageImgPreprocessed, BufferedImage pageImgOrig, Rectangle imageOnPageArea) throws IOException, TimeoutException {
        // Perform image OCR
        OcrResult ocrResult = processImage(pageImgPreprocessed, true);

        applyPageImageScaleOffset(ocrResult, pageImgPreprocessed, imageOnPageArea);

        addOcrResult(document, page, pageImgOrig, ocrResult);

        return ocrResult.getOcrBlock();
    }


    private void applyPageImageScaleOffset(OcrResult ocrResult, BufferedImage pageImgPreprocessed, Rectangle imageOnPageArea) {
        double imgScale = imageOnPageArea.getWidth() / pageImgPreprocessed.getWidth();
        double imgOffsetX = imageOnPageArea.getX();
        double imgOffsetY = imageOnPageArea.getY();

        for (OcrText word : ocrResult.getOcrBlock().getOcrData()) {
            word.setRegion(word.getRegion().getScaledClone(imgScale));
            word.getRegion().translate(imgOffsetX, imgOffsetY);
            word.setFontSizePt((float) (word.getFontSizePt() * imgScale));
            for (OcrSymbol symbol : word.getOcrData()) {
                symbol.setRegion(symbol.getRegion().getScaledClone(imgScale));
                symbol.getRegion().translate(imgOffsetX, imgOffsetY);
                symbol.setFontSizePt((float) (symbol.getFontSizePt() * imgScale));
            }
        }
    }


    /**
     * Adds recognition results (provided with OcrResults) to the PDF page, following the next rules:<br>
     * <ul>
     * <li>If the OCR result is empty (no text recognized) - nothing added to PDF page;</li>
     * <li>If an OCR result contains rotation (it is not 0) - data on the page is removed and rotated pageImage is added to the page;</li>
     * <li><b>IMPORTANT:</b> if an OCR result contains no text - page rotation is ignored, even if set to value, different from 0;</li>
     * </ul>
     *
     * @param document {@link PDDocument}, on which text should be added
     * @param page {@link PDPage}, to which text should be added
     * @param pageImage Full-page image of the PDF page. If OCR detected page rotation, this image will be rotated accordingly
     * @param ocrResult text to be added to PDDocument. RTL-text provided in logical order
     * @throws IOException in case of any IO error
     */
    public static void addOcrResult(PDDocument document, PDPage page, BufferedImage pageImage, OcrResult ocrResult) throws IOException {
        if (ocrResult.getOcrBlock().getOcrData().isEmpty()) {
            // No text was retrieved - nothing to do, we may exit here
            return;
        }

        if (ocrResult.getRotation() != 0) {
            // Replace image already present on the page with rotated as per OCR analysis results
            BufferedImage rotatedPageImg = ImageProcessor.rotateImage(pageImage, 360 - ocrResult.getRotation());
            PdfUtils.replacePageImage(document, page, rotatedPageImg, true);
        }

        OcrPdfUtils.addOcrText(document, page, ocrResult);
    }


    /**
     * Performs OCR of the given image as is, without any image preparations, transformations, etc.
     *
     * @param image Image, that should be OCRed
     * @return {@link OcrResult} with OCR results
     * @throws IOException in case of any IO error
     * @throws TimeoutException in case time out on page processing is reached
     */
    OcrResult processImage(BufferedImage image) throws IOException, TimeoutException {
        return processImage(image, false);
    }


    /**
     * Performs OCR of the given image
     *
     * @param image Image, that should be OCRed
     * @param withPrepareForOcr if set to true, performs image preparation for OCR
     * @return {@link OcrResult} with OCR results
     * @throws IOException in case of any IO error
     * @throws TimeoutException in case time out on page processing is reached
     */
    public OcrResult processImage(BufferedImage image, boolean withPrepareForOcr) throws IOException, TimeoutException {
        return _ocrEngine.processImage(image, withPrepareForOcr);
    }


    /**
     * Detects orientation of the image, based on text content and returns values in degrees: 0, 90, 180, 270 (clockwise)
     *
     * @param image Source image to be used for page orientation detection
     * @return page rotation clockwise (0, 90, 180, or 270)
     */
    public static int getPageOrientation(BufferedImage image) {
        OcrEngine enabledEngine = null;
        try {
            enabledEngine = createOcrEngine(MODE.LAYOUT_ANALYSIS, null);
            if (enabledEngine.isPageOrientationDetectionSupported()) {
                return enabledEngine.getPageOrientation(image);
            }
        } finally {
            if (enabledEngine != null) {
                enabledEngine.close();
            }
        }
        try (OcrEngineTessImpl tessOcrEngine = new OcrEngineTessImpl(MODE.LAYOUT_ANALYSIS, null)) {
            return tessOcrEngine.getPageOrientation(image);
        }
    }


    public static boolean isPageOrientationDetectionRequiresPreprocessing() {
        OcrEngine enabledEngine = null;
        try {
            enabledEngine = createOcrEngine(MODE.LAYOUT_ANALYSIS, null);
            if (enabledEngine.isPageOrientationDetectionSupported()) {
                return enabledEngine.isPageOrientationRequiresPreprocessing();
            }
        } finally {
            if (enabledEngine != null) {
                enabledEngine.close();
            }
        }
        try (OcrEngineTessImpl tessOcrEngine = new OcrEngineTessImpl(MODE.LAYOUT_ANALYSIS, null)) {
            return tessOcrEngine.isPageOrientationRequiresPreprocessing();
        }
    }


    /**
     * Returns true in case provided properties require custom pre-processing (if property is present)
     *
     * @param properties OcrEngine properties, for which check should be performed
     * @return true in case provided properties require custom pre-processing (if property is present)
     */
    public static boolean isCustomPreprocessingRequired(Map<String, Object> properties) {
        if (getOcrEngineType() == OcrEngine.TYPE.TESS) {
            // Only for internal engine we currently have custom preprocessing options required
            return OcrEngineTessImpl.isCustomPreprocessingRequired(properties);
        }
        // For the rest of the engines - we do not have custom preprocessing
        return false;
    }


    @Override
    public void close() {
        _ocrEngine.close();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns default OCR Engine to be used system-wide
     *
     * @return type of the OCR Engine, used by this OCR Service instance
     */
    static OcrEngine.TYPE getOcrEngineType() {
        String ocrTypeForTenantOrDefault = AppConfigService.getInstance().getIdpConfig().getOcrType(TenantContext.getCurrentTenantId());
        if (Strings.CI.equals(ocrTypeForTenantOrDefault, OcrEngine.TYPE.VL.name())) {
            return OcrEngine.TYPE.VL;
        } else if (Strings.CI.equals(ocrTypeForTenantOrDefault, OcrEngine.TYPE.PDDL.name())) {
            return OcrEngine.TYPE.PDDL;
        }
        return OcrEngine.TYPE.TESS;
    }


    public static boolean isOcrEngineVL() {
        return getOcrEngineType() == OcrEngine.TYPE.VL;
    }


    /**
     * Returns progress (1...100) of the currently active recognition task
     *
     * @return progress indicator (values from 1 to 100) or -1 in case OCR Service / Progress monitor in wrong state
     */
    public int getProgress() {
        return _ocrEngine.getProgress();
    }


    /**
     * @return List of OCR Languages available for the current OCR Engine (enabled languages only are returned)
     */
    public static List<OcrLang> getAvailableLanguages() {
        try (OcrService ocrService = new OcrService()) {
            return ocrService.getAvailableLanguagesInner().stream()
                    .filter(OcrLang::isEnabled)
                    .collect(Collectors.toList());
        }
    }


    private List<OcrLang> getAvailableLanguagesInner() {
        return _ocrEngine.getAvailableLanguages();
    }


    public static String getDefaultLanguageCode() {
        return switch (getOcrEngineType()) {
            case PDDL ->
                OcrEnginePDDLImpl.DEFAULT_LANG;
            case VL ->
                OcrEngineVLImpl.DEFAULT_LANG;
            default ->
                OcrEngineTessImpl.DEFAULT_LANG;
        };
    }


    public static OcrLang getOcrLanguageByCode(String langCode) {
        return OcrLangUtils.getOcrLanguageByCode(getOcrEngineType().name(), langCode);
    }


    @Override
    public Map<String, Object> getProperties() {
        return _ocrEngine.getProperties();
    }


    public OcrService setProperties(Map<String, Object> properties) {
        _ocrEngine.getProperties().putAll(properties);
        return this;
    }
}
