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
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.ocr.model.paddle.*;
import ai.eldoc.reco.ImageProcessor;
import ai.eldoc.rest.ResponseStatusCodeException;
import ai.eldoc.rest.RestClientException;
import jakarta.ws.rs.core.GenericType;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;


/**
 * Paddle OCR engine
 */
public class OcrEnginePDDLImpl implements OcrEngine, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OcrEnginePDDLImpl.class);

    private static final boolean DEBUG_PRINT_RESULTS = Boolean.getBoolean(ELDOC_JVM_OPTION_OCR_DEBUG_PRINT_RESULTS);

    /**
     * Maximum length of longest image dimensions, as defined in Paddle OCR engine
     */
    private static final int IMG_LONG_DIM_LIMIT = 4000;
    private static final String _LANG_AUTO = "auto";
    public static final String DEFAULT_LANG = _LANG_AUTO;

    private final OcrEnginePDDLRestClientService _restClientService;
    private final Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public OcrEnginePDDLImpl(OcrEnginePDDLRestClientService restClientService) {
        _restClientService = restClientService;
        _properties = new HashMap<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * @inheritDoc
     */
    @Override
    public OcrResult processImage(BufferedImage image, boolean withPrepareForOcr) throws IOException, TimeoutException {
        OcrPageResult ocrResult = performPageOcr(image);

        OcrResult result = new OcrResult();
        result.setTextDirectionDetected(false);
        OcrBlock ocrBlock = new OcrBlock();
        result.setOcrBlock(ocrBlock);

        for (OcrTextBlockResult blockResult : ocrResult.getData().getResults()) {
            // Convert Paddle API results format to elDoc internal OcrText
            List<OcrText> ocrWords = convertTextBlockResult(blockResult);

            // Adding current word to the whole OcrBlock
            ocrWords.forEach(ocrBlock::addWord);
        }
        return result;
    }


    /**
     * Converts provided Paddle OCR response into elDoc internal format.<br>
     * This method also applies ffixes to values, returned by Paddle API, i.e. it can return value for single- or two-char
     * columns of the table as vertical word. In such case, when necessary, single Paddle text block can be converted to
     * more than one elDoc OcrText blocks
     *
     * @param blockResult {@link OcrTextBlockResult} Text block OCR data in Paddle API format
     * @return Text block OCR result in elDoc format
     */
    private List<OcrText> convertTextBlockResult(OcrTextBlockResult blockResult) {
        if (blockResult.getBoundingBox().getHeight() > blockResult.getBoundingBox().getWidth() * 2 && blockResult.getText().length() > 1) {
            // Paddle doesn't return per-word rotation angles, so we must rely on heuristics (height > width * 2)
            List<OcrText> results = new ArrayList<>();

            double charWidth = blockResult.getBoundingBox().getWidth();
            double charHeight = blockResult.getBoundingBox().getHeight() / blockResult.getText().length();
            for (int charIdx = 0; charIdx < blockResult.getText().length(); charIdx++) {
                double x = blockResult.getBoundingBox().getX();
                double y = blockResult.getBoundingBox().getY() + charIdx * charHeight;
                Rectangle charArea = new Rectangle(x, y, charWidth, charHeight);
                OcrTextBlockResult charResult = new OcrTextBlockResult();
                charResult.setBoundingBox(charArea);
                charResult.setConfidence(blockResult.getConfidence());
                charResult.setOrientationAngle(0);
                charResult.setText(blockResult.getText().substring(charIdx, charIdx + 1));
                results.addAll(convertTextBlockResult(charResult));
            }

            return results;
        }

        double charWidth = blockResult.getBoundingBox().getWidth() / blockResult.getText().length();
        double charHeight = blockResult.getBoundingBox().getHeight();
        int wordConf = (int) (blockResult.getConfidence() * 100);
        OcrText ocrWord = new OcrText();
        ocrWord.setConfidence(wordConf);
        ocrWord.setFontSizePt((float) charHeight);
        for (int charIdx = 0; charIdx < blockResult.getText().length(); charIdx++) {
            OcrSymbol ocrSymbol = new OcrSymbol();
            ocrSymbol.setConfidence(wordConf);
            ocrSymbol.setChar(blockResult.getText().charAt(charIdx));

            double x = blockResult.getBoundingBox().getX() + charIdx * charWidth;
            double y = blockResult.getBoundingBox().getY();
            ocrSymbol.setRegion(new Rectangle(x, y, charWidth, charHeight));
            ocrSymbol.setFontSizePt((float) charHeight);

            ocrWord.addSymbol(ocrSymbol);

            if (DEBUG_PRINT_RESULTS) {
                LOG.info("OCR SYMB: {} [vertices: {}]", ocrSymbol.getChar(), ocrSymbol.getRegion());
            }
        }
        ocrWord.setRegion(ocrWord.getTextArea());

        if (DEBUG_PRINT_RESULTS) {
            LOG.info("OCR: {} [conf: {}; vertices: {}]|symbConf---> {}",
                    ocrWord.getText(),
                    ocrWord.getConfidence(),
                    blockResult.getBoundingBox(),
                    wordConf);
        }

        return List.of(ocrWord);
    }


    /**
     * Performs actual image OCR by sending request to Paddle OCR endpoint and parsing response
     *
     * @param bi Image, that should be OCRed
     * @return Paddle OCR response
     */
    private OcrPageResult performPageOcr(BufferedImage bi) {
        OcrRequest request = new OcrRequest();
        request.setFileType(OcrRequest.FILE_TYPE_IMAGE);
        request.setReturnWordBox(true);
        request.setVisualize(false);
        request.setTextDetThresh(0.3);
        request.setUseDocUnwarping(false); // Strips empty space around actual page content - not required

        Double scale = fitSizeAndSetFile(request, bi);

        OcrResponse ocrResponse;
        try {
            ocrResponse = _restClientService.doPost("ocr", request, 200, new GenericType<OcrResponse>() {
            });
        } catch (RestClientException rce) {
            if (rce.getCause() instanceof ResponseStatusCodeException rsce && rsce.getStatusCode() == 500) {
                // TODO: Current Paddle OCR version has bug with `return_word_box` param, see https://github.com/PaddlePaddle/PaddleOCR/issues/15760
                // It throws exception, if `return_word_box == true`, but Paddle failed to detect text on page
                // As workaround - try to re-send request with this flag set to false to validate, that we hit exactly this issue - in this case we should not abort OCR flow
                request.setReturnWordBox(false);
                LOG.debug("Paddle OCR: got Internal Server Error, retrying without `return_word_box`");
                ocrResponse = _restClientService.doPost("ocr", request, 200, new GenericType<OcrResponse>() {
                });
            } else {
                throw rce;
            }
        }

        ocrResponse.scale(1 / scale);

        return ocrResponse.getResult().getOcrResults().get(0);
    }


    /**
     * @inheritDoc
     */
    @Override
    public int getPageOrientation(BufferedImage image) {
        OcrRequest request = new OcrRequest();
        request.setFileType(OcrRequest.FILE_TYPE_IMAGE);
        request.setVisualize(false);
        request.setUseDocUnwarping(false); // Was already performed on preprocessing step
        request.setUseDocOrientationClassify(true);

        Double scale = fitSizeAndSetFile(request, image);

        // TODO: Check, if possible to have separate "doc_preprocessing" pipeline on same host:port (i.e. nginx)
        OcrResponse ocrResponse = _restClientService.doPost("ocr", request, 200, new GenericType<OcrResponse>() {
        });

        ocrResponse.scale(1 / scale);

        OcrPageResult pageProcessingResult = ocrResponse.getResult().getOcrResults().get(0);

        if (pageProcessingResult == null) {
            LOG.warn("Page orientation detection failed");
            return 0;
        }

        OcrPagePreprocessorResult preprocessingResult = pageProcessingResult.getData().getPreprocessingResult();

        if (preprocessingResult == null) {
            LOG.warn("Page orientation detection failed");
            return 0;
        }

        return preprocessingResult.getAngle();
    }


    /**
     * *
     * Validates and adjusts image size, if required, and sets it to provided OcrRequest
     *
     * @param request {@link OcrRequest}, that should hold provided image
     * @param bi Image to be validated and loaded into request
     * @return Scale, that was applied during image analysis, or 1.0, if no scaling required
     */
    private double fitSizeAndSetFile(OcrRequest request, BufferedImage bi) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Pair<BufferedImage, Double> imageData = optimizeSize(bi);
            LOG.trace("Image scaled by: {}", imageData.getRight());
            ImageProcessor.saveToJpg(imageData.getLeft(), baos, 0.9f);
            String base64data = Base64.getEncoder().encodeToString(baos.toByteArray());
            request.setFile(base64data);
            return imageData.getRight();
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Can't read image for OCR", ex);
        }
    }


    /**
     * Checks, if page size is within range, allowed by Paddle OCR engine, and scales it if the image is too big.<br>
     * If no scaling is required, returns the original image and applied scale as 1.0
     *
     * @param input Image, that should be validated
     * @return Resized (if necessary) image and applied scale
     */
    private Pair<BufferedImage, Double> optimizeSize(BufferedImage input) {
        double scale = 1.0 / Math.max(input.getWidth() / (double) IMG_LONG_DIM_LIMIT, input.getHeight() / (double) IMG_LONG_DIM_LIMIT);
        if (scale >= 1) {
            return Pair.of(input, 1.0);
        }

        BufferedImage scaledImage = ImageProcessor.resize(input, scale);
        return Pair.of(scaledImage, scale);
    }


    @Override
    public void close() {
        // Vision Model will be closed by the service, that created it
    }


    @Override
    public List<OcrLang> getAvailableLanguages() {
        return OcrLangUtils.getOcrLanguages(OcrEngine.TYPE.PDDL.name());
    }


    @Override
    public int getProgress() {
        return -1;
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
        return false;
    }


    @Override
    public Map<String, Object> getProperties() {
        return _properties;
    }


    public OcrEnginePDDLImpl setProperties(Map<String, Object> properties) {
        _properties.putAll(properties);
        return this;
    }

}
