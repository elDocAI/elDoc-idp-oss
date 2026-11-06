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

import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.PropertyAwareModel;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;


/**
 * OCR Engine Standard Interface
 */
public interface OcrEngine extends PropertyAwareModel {

    static final String ELDOC_JVM_OPTION_OCR_DEBUG_PRINT_RESULTS = "ai.eldoc.ocr.debug.printResults";


    /**
     * OCR Engine type
     */
    public static enum TYPE {
        /**
         * Tesseract OCR engine
         */
        TESS,
        /**
         * Vision Language model-based OCR API (OpenAI API)
         */
        VL,
        /**
         * Paddle OCR API
         */
        PDDL
    }

    /**
     * Width of the space symbol in the font, used for writing on PDF (per point of font size)
     */
    static final float SPACE_WIDTH = 0.278f;


    /**
     * Performs OCR of the given image.<br>
     * <b>IMPORTANT:</b> this method returns RTL-text in logical order
     *
     * @param image image for OCR (IMPORTANT: image is flashed inside the method)
     * @param withPrepareForOcr if set to true - performs whole cycle of image preparation for OCR
     * @return
     * @throws java.io.IOException
     * @throws ai.eldoc.exceptions.TimeoutException in case time out on page processing is reached
     */
    public OcrResult processImage(BufferedImage image, boolean withPrepareForOcr) throws IOException, TimeoutException;


    /**
     * Detects orientation of the image, based on text content and returns values in degrees: 0, 90, 180, 270 (clockwise)
     *
     * @param image Source image to be used for page orientation detection
     * @return 0, 90, 180, 270 page rotation clockwise
     */
    public int getPageOrientation(BufferedImage image);


    /**
     * Lists all languages available for current engine
     *
     * @return
     */
    public List<OcrLang> getAvailableLanguages();


    /**
     * Returns progress (1...100) of the currently active recognition task
     *
     * @return progress indicator (values from 1 to 100) or -1 in case OCR Engine / Progress monitor in wrong state or does not support progress indicator
     */
    public int getProgress();


    /**
     * Indicates, whether this OcrEngine supports page orientation detection
     *
     * @return
     */
    public boolean isPageOrientationDetectionSupported();


    /**
     * Indicates, whether OCR engine requires preprocessed or original image to detect page orientation
     *
     * @return
     */
    public boolean isPageOrientationRequiresPreprocessing();


    public void close();

}
