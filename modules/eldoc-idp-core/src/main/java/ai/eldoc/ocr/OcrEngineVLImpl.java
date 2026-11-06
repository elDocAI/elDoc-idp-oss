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
import ai.eldoc.ai.AiUtils;
import ai.eldoc.ai.assistants.OcrAssistant;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.ocr.model.vl.OcrTextBlockResult;
import ai.eldoc.reco.ImageProcessor;
import ai.eldoc.util.JsonbUtils;
import ai.eldoc.util.MimeType;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import org.apache.commons.lang3.reflect.TypeLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


/**
 * Vision Language model-based OCR engine
 */
public class OcrEngineVLImpl implements OcrEngine, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OcrEngineVLImpl.class);

    private static final boolean DEBUG_PRINT_RESULTS = Boolean.getBoolean(ELDOC_JVM_OPTION_OCR_DEBUG_PRINT_RESULTS);

    /**
     * For now, assuming 16k tokens, 32x32 pixels per token => 16MP image, a worse case is 4096x4096, so the 4000px longest side is safe
     */
    private static final int IMG_DIMS_MAX = 4000;
    /**
     * Steps for Jpeg compressions to try before resizing (reducing quality harms less than scaling)
     */
    private static final List<Double> IMG_QUALITY_STEPS = List.of(0.9, 0.85, 0.8, 0.75);
    /**
     * Max scale-down iterations to apply before giving up
     */
    private static final int IMG_SCALEDOWN_STEPS_MAX = 4;
    private static final int IMG_SCALEDOWN_STEPS_FORCE_MAX = 20;
    /**
     * Maximum allowed scale factor for image resizing
     */
    private static final double IMG_SCALE_MAX = 0.85;

    /**
     * Scale factor for coordinates in the LLM response.<br>
     * I.e., scale 1000 means that the model returns coordinates as 1/1000 of the width / height of the original image.
     */
    public static final int MODEL_COORDS_SCALE = 1000;

    private static final String _LANG_AUTO = "auto";
    public static final String DEFAULT_LANG = _LANG_AUTO;

    private final OcrAssistant _assistant;
    private final Map<String, Object> _properties;

    private final double _imgFileSizeMax;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public OcrEngineVLImpl(ChatModel visionModel, AiRequestContext requestContext, double imgFileSizeMax) {
        _assistant = AiServices.builder(OcrAssistant.class)
                .chatModel(visionModel)
                .build();

        _properties = new HashMap<>();

        _imgFileSizeMax = imgFileSizeMax;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * @inheritDoc
     */
    @Override
    public OcrResult processImage(BufferedImage image, boolean withPrepareForOcr) throws IOException, TimeoutException {
        String parseResponse = performOcr(image);
        List<OcrTextBlockResult> llmOcrResult = JsonbUtils.getInstance().fromJson(parseResponse, new TypeLiteral<List<OcrTextBlockResult>>() {
        }.getType());

        OcrResult result = new OcrResult();
        result.setTextDirectionDetected(false);
        OcrBlock ocrBlock = new OcrBlock();
        result.setOcrBlock(ocrBlock);

        for (OcrTextBlockResult llmTextBlock : llmOcrResult) {
            Rectangle blockArea = llmTextBlock.scaledBboxToRectangle(image.getWidth(), image.getHeight(), MODEL_COORDS_SCALE);
            double charWidth = blockArea.getWidth() / llmTextBlock.getText().length();
            int confidence = (int) (llmTextBlock.getConfidence() == 0 ? 100 : (llmTextBlock.getConfidence() * 100));
            OcrText ocrWord = new OcrText();
            ocrWord.setRegion(blockArea);
            ocrWord.setConfidence(confidence);
            ocrWord.setFontSizePt((float) blockArea.getHeight());
            for (int charIdx = 0; charIdx < llmTextBlock.getText().length(); charIdx++) {
                OcrSymbol ocrSymbol = new OcrSymbol();
                ocrSymbol.setConfidence(confidence);
                ocrSymbol.setChar(llmTextBlock.getText().charAt(charIdx));

                double x = blockArea.getX() + charIdx * charWidth;
                double y = blockArea.getY();
                double height = blockArea.getHeight();
                ocrSymbol.setRegion(new Rectangle(x, y, charWidth, height));
                ocrSymbol.setFontSizePt((float) height);

                ocrWord.addSymbol(ocrSymbol);

                if (DEBUG_PRINT_RESULTS) {
                    LOG.info("OCR SYMB: {} [vertices: {}]", ocrSymbol.getChar(), ocrSymbol.getRegion());
                }
            }

            // Adding current word to the whole OcrBlock
            ocrBlock.addWord(ocrWord);

            if (DEBUG_PRINT_RESULTS) {
                LOG.info("OCR: {} [conf: {}; vertices: {}]|symbConf---> {}",
                        ocrWord.getText(),
                        ocrWord.getConfidence(),
                        llmTextBlock.getBbox(),
                        confidence);
            }
        }

        return result;
    }


    public String processImageMarkdown(BufferedImage image, boolean withPrepareForOcr) throws TimeoutException {
        return performOcrMarkdown(image);
    }


    /**
     * Performs actual OCR via VL LLM chat request
     *
     * @param bi Image, that should be OCRed
     * @return LLM OCR response
     */
    private String performOcr(BufferedImage bi) {
        ImageContent imageContent;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage image = optimizeSize(bi);
            ImageProcessor.saveToJpg(image, baos, 0.9f);
            String base64data = Base64.getEncoder().encodeToString(baos.toByteArray());
            Image encodedImage = Image.builder()
                    .url("data:image/jpeg;base64," + base64data)
                    .build();
            imageContent = ImageContent.from(encodedImage);
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Error reading image for VL OCR", ex);
        }
        String originalResponse = doOcrPrompted(imageContent, false);

        return cleanupOcrResponse(originalResponse);
    }


    /**
     * Performs actual OCR via VL LLM chat request and returns image text in Markdown format
     *
     * @param bi Image, that should be OCRed
     * @return LLM OCR response in Markdown format
     */
    private String performOcrMarkdown(BufferedImage bi) {
        String filenamePrefix = "eldoc-vl-ocr-" + System.currentTimeMillis() + "-";
        String originalResponse = doOcrPrompted(getImageContent(bi, filenamePrefix, _imgFileSizeMax), true);
        if (LOG.isTraceEnabled()) {
            try {
                Path tiTempFile = Files.createTempFile(filenamePrefix, ".md");
                Files.writeString(tiTempFile, originalResponse);
            } catch (IOException ignored) {
            }
        }

        return AiUtils.removeMarkdownCodeblockFormatting(originalResponse);
    }


    private static String optimizeSizeAndEncode(BufferedImage bi, String tempFileNamePrefix, long imgFileSizeMax, boolean force) {
        double totalAppliedScale = 1;
        byte[] encodedImage = null;
        boolean imageFit = false;
        BufferedImage imageClone = bi; // Work on original image on first iteration
        int attemptsMax = force ? IMG_SCALEDOWN_STEPS_FORCE_MAX : Math.max(IMG_SCALEDOWN_STEPS_MAX, 1);
        for (int resizeAttempt = 0; resizeAttempt < attemptsMax; resizeAttempt++) {
            for (int attempt = 0; attempt < Math.max(IMG_QUALITY_STEPS.size(), 1); attempt++) {
                encodedImage = optimizeSizeAndEncode(imageClone, tempFileNamePrefix + resizeAttempt + "-", attempt);
                long currentLengthBase64 = encodedImage.length * 4L / 3;
                if (currentLengthBase64 < imgFileSizeMax) {
                    LOG.trace("Image compressed to required size, iteration: {}, quality profile: {}, force: {}", resizeAttempt, IMG_QUALITY_STEPS.get(attempt), force);
                    imageFit = true;
                    break;
                }
                LOG.trace("Image for VL OCR size is too big: {}/{} bytes, trying lower quality", encodedImage.length * 4L / 3, imgFileSizeMax);
            }
            if (imageFit) {
                break;
            }
            long currentLengthBase64 = encodedImage.length * 4L / 3;
            double newScale = estimateResizeScale(currentLengthBase64, imgFileSizeMax);
            totalAppliedScale = totalAppliedScale * newScale;
            LOG.trace("Image for VL OCR size is too big: {}/{} bytes, trying scale down by additional: {}", encodedImage.length * 4L / 3, imgFileSizeMax, newScale);
            imageClone = ImageProcessor.resize(bi, totalAppliedScale);
        }
        if (!imageFit) {
            LOG.debug("Image for VL OCR size is too big after scaling by {}%: {}/{} bytes, giving up", (totalAppliedScale * 100), encodedImage.length * 4L / 3, imgFileSizeMax);
        }
        return Base64.getEncoder().encodeToString(encodedImage);
    }


    private static byte[] optimizeSizeAndEncode(BufferedImage image, String tempFileNamePrefix, int iteration) {
        double jpgQuality = IMG_QUALITY_STEPS.get(Math.min(iteration, IMG_QUALITY_STEPS.size() - 1));
        LOG.trace("Image for VL OCR encoded with quality: {}", jpgQuality);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageProcessor.saveToJpg(image, baos, jpgQuality);
            if (LOG.isTraceEnabled()) {
                try {
                    Path tiTempFile = Files.createTempFile(tempFileNamePrefix + jpgQuality + "-", ".jpg");
                    Files.write(tiTempFile, baos.toByteArray());
                } catch (IOException ignored) {
                }
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Error reading image for VL OCR", ex);
        }
    }


    /**
     * Estimate the geometric scale needed to move from currentBase64Length to targetBase64Length.
     * Uses sqrt because image byte size roughly tracks pixel area for the same codec/settings.
     */
    private static double estimateResizeScale(long currentBase64Length, long targetBase64Length) {
        double rawScale = Math.sqrt((double) targetBase64Length / (double) currentBase64Length);
        double estimatedScale = rawScale * 0.92; // scale a bit more than theoretically required to exactly fit and do not scale more than by 75% of previous size
        if (!Double.isFinite(estimatedScale) || estimatedScale <= 0) {
            return IMG_SCALE_MAX;
        }
        return Math.clamp(estimatedScale, IMG_SCALE_MAX, 1);
    }


    /**
     * Performs actual OCR using pre-created AI Assistant, sending image with text prompt (task description) to LLM
     *
     * @param imageContent Image, that should be OCRed
     * @return LLM OCR response
     */
    private String doOcrPrompted(ImageContent imageContent, boolean markdown) {
        if (markdown) {
            Response<AiMessage> response = _assistant.doOcrMarkdown(imageContent);
            return response.content().text();
        }
        return _assistant.doOcr(imageContent).content();
    }


    /**
     * Processes a list of images and generates a list of {@code ImageContent} objects while adhering to the specified size limit.
     * Attempts to optimize image sizes iteratively if the total size exceeds the given limit.
     *
     * @param images List of {@code BufferedImage} objects to process
     * @param filenamePrefix Prefix to use in generating filenames for the resulting {@code ImageContent} objects
     * @param requestSizeLimitMb Maximum allowed combined size of the resulting {@code ImageContent} objects in megabytes
     * @return A list of {@code ImageContent} objects that represents the processed images
     */
    public static List<ImageContent> getImageContent(List<BufferedImage> images, String filenamePrefix, double requestSizeLimitMb) {
        return getImageContent(images, filenamePrefix, requestSizeLimitMb, false);
    }


    /**
     * Processes a list of images and generates a list of {@code ImageContent} objects while adhering to the specified size limit.
     * Attempts to optimize image sizes iteratively if the total size exceeds the given limit.
     *
     * @param images List of {@code BufferedImage} objects to process
     * @param filenamePrefix Prefix to use in generating filenames for the resulting {@code ImageContent} objects
     * @param requestSizeLimitMb Maximum allowed combined size of the resulting {@code ImageContent} objects in megabytes
     * @param force If {@code true}, image will be compressed until it fits into provided size limits, instead of stopping at {@link #IMG_SCALEDOWN_STEPS_MAX} attempts
     * @return A list of {@code ImageContent} objects that represents the processed images
     */
    public static List<ImageContent> getImageContent(List<BufferedImage> images, String filenamePrefix, double requestSizeLimitMb, boolean force) {
        List<ImageContent> imageContents = new ArrayList<>();

        // First pass - try full-size images
        for (int i = 0; i < images.size(); i++) {
            ImageContent content = getImageContent(images.get(i), filenamePrefix + i + "_", requestSizeLimitMb);
            imageContents.add(content);
        }

        int maxIterations = 10;
        double prevIterationSize = 0;
        for (int iteration = 0; iteration < maxIterations; iteration++) {

            double totalSize = imageContents.stream()
                    .mapToDouble(OcrEngineVLImpl::getImageItemSizeMb)
                    .sum();

            if (totalSize <= requestSizeLimitMb) {
                LOG.trace("Image for VL OCR size limit reached: {}/{} MB, iteration: {}", totalSize, requestSizeLimitMb, iteration + 1);
                return imageContents;
            }

            if (iteration > 0 && totalSize >= prevIterationSize * 0.95) {
                LOG.debug("Image for VL OCR size limit reached: {}/{} MB, iteration: {}, no improvement in size", totalSize, requestSizeLimitMb, iteration + 1);
                return imageContents;
            }

            prevIterationSize = totalSize;

            double extraSize = totalSize - requestSizeLimitMb;
            double avgSize = requestSizeLimitMb / imageContents.size();

            List<ImageContent> oversizedImages = imageContents.stream()
                    .filter(content -> getImageItemSizeMb(content) > avgSize)
                    .toList();
            double oversizedImagesTotalSize = oversizedImages.stream()
                    .mapToDouble(OcrEngineVLImpl::getImageItemSizeMb)
                    .sum();
            List<Double> oversizedImagesSizesRatio = oversizedImages.stream()
                    .map(content -> getImageItemSizeMb(content) / oversizedImagesTotalSize)
                    .toList();

            List<ImageContent> newContents = new ArrayList<>();
            for (int imageIdx = 0; imageIdx < imageContents.size(); imageIdx++) {
                ImageContent content = imageContents.get(imageIdx);
                if (getImageItemSizeMb(content) > avgSize) {
                    double targetImageSize = (getImageItemSizeMb(content) - extraSize * oversizedImagesSizesRatio.get(oversizedImages.indexOf(content)) - 0.001);
                    ImageContent trimmedImage = getImageContent(images.get(imageIdx), filenamePrefix, targetImageSize, force);
                    newContents.add(trimmedImage);
                } else {
                    newContents.add(content);
                }
            }
            imageContents = newContents;
        }

        return imageContents;
    }


    public static double getImageItemSizeMb(ImageContent image) {
        return image.image().base64Data().length() / 1_000_000.0;
    }


    public static double getImageItemSizeMb(List<ImageContent> images) {
        return images.stream()
                .mapToDouble(OcrEngineVLImpl::getImageItemSizeMb)
                .sum();
    }


    public static ImageContent getImageContent(BufferedImage bi, String filenamePrefix, double imgFileSizeMaxMb) {
        return getImageContent(bi, filenamePrefix, imgFileSizeMaxMb, false);
    }


    public static ImageContent getImageContent(BufferedImage bi, String filenamePrefix, double imgFileSizeMaxMb, boolean force) {
        LOG.trace("Image for VL OCR size: {}x{}, saved with prefix: {}", bi.getWidth(), bi.getHeight(), filenamePrefix);
        long imgFileSizeMaxBytes = (long) (imgFileSizeMaxMb * 1_000_000);
        BufferedImage image = optimizeSize(bi);
        String base64data = optimizeSizeAndEncode(image, filenamePrefix, imgFileSizeMaxBytes, force);
        return ImageContent.from(base64data, MimeType.IMAGE_JPEG);
    }


    public static double getSmallestImageSize(BufferedImage image) {
        if (image == null) {
            return 0L;
        }

        // First, account for the same preprocessing used before encoding
        int srcWidth = image.getWidth();
        int srcHeight = image.getHeight();

        double initialScale = 1.0 / Math.max(srcWidth / (double) IMG_DIMS_MAX, srcHeight / (double) IMG_DIMS_MAX);
        if (initialScale > 1.0) {
            initialScale = 1.0;
        }

        double widthAfterOptimize = srcWidth * initialScale;
        double heightAfterOptimize = srcHeight * initialScale;

        // Worst case: each resize iteration may shrink by at most #IMG_SCALE_MAX
        double minScaleFromRetries = Math.pow(IMG_SCALE_MAX, IMG_SCALEDOWN_STEPS_MAX);

        double finalWidth = widthAfterOptimize * minScaleFromRetries;
        double finalHeight = heightAfterOptimize * minScaleFromRetries;

        long pixels = Math.max(1L, Math.round(finalWidth * finalHeight));

        // Very rough pessimistic estimate for JPEG size at lowish quality.
        // For OCR/document images this is intentionally conservative.
        double estimatedJpegBytes = Math.max(512.0, pixels * 0.3);

        // Base64 expands data by ~4/3
        return Math.ceil(estimatedJpegBytes * 4.0 / 3.0) / 1_000_000.0;
    }


    /**
     * Checks the provided image and resizes it closer to predefined dimensions (i.e., the longest side to be around 2000 pixels)<br>
     * Recommended step in the case of using LLMs for OCR, as most models are trained on images of a specific size and receiving a bigger image may reduce OCR quality
     *
     * @param input Image that should be checked and resized
     * @return Image of the size within the optimal range or original image, if it is already within the range
     */
    private static BufferedImage optimizeSize(BufferedImage input) {
        double scale = 1.0 / Math.max(input.getWidth() / (double) IMG_DIMS_MAX, input.getHeight() / (double) IMG_DIMS_MAX);
        if (scale > 1) {
            // Do not scale up image
            return input;
        }

        BufferedImage scaledImage = ImageProcessor.resize(input, scale);
        LOG.trace("Image hit max dimension threshold {}. Scaled by: {}", IMG_DIMS_MAX, scale);
        return scaledImage;
    }


    /**
     * Performs additional pre-processing of LLM response to adjust it into elDoc internal convention.<br>
     * This step is used to "clean up" possible known response flaws, like wrapping JSON objects in Markdown code formatting blocks or using "synonym" fields names, instead of re-querying LLM for
     * fixed response structure.
     *
     * @param response Original LLM response
     * @return Cleaned OCR result, ready to be parsed and converted to elDoc internal format
     */
    private String cleanupOcrResponse(String response) {
        String result = AiUtils.removeJsonCodeblockFormatting(response);
        result = handleBboxCoordsField(result);
        result = handleTextContentField(result);
        return result;
    }


    /**
     * Replaces known "not-so-valid" field names for bounding box coords with ones used during parsing
     *
     * @param response LLM response, that should be cleaned up
     * @return Cleaned LLM response
     */
    private String handleBboxCoordsField(String response) {
        return response.replace("\"bbox_2d\"", "\"bbox\"");
    }


    /**
     * Replaces known "not-so-valid" field names for text block text value with ones used during parsing
     *
     * @param response LLM response, that should be cleaned up
     * @return Cleaned LLM response
     */
    private String handleTextContentField(String response) {
        return response.replace("\"text_content\"", "\"text\"");
    }


    /**
     * @inheritDoc
     */
    @Override
    public int getPageOrientation(BufferedImage image) {
        ImageContent imageContent;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageProcessor.saveToJpg(image, baos, 0.9f);
            String base64data = Base64.getEncoder().encodeToString(baos.toByteArray());
            Image encodedImage = Image.builder()
                    .url("data:image/jpeg;base64," + base64data)
                    .build();
            imageContent = ImageContent.from(encodedImage);
        } catch (IOException ex) {
            throw new ElDocRuntimeException("Can't read image for OCR", ex);
        }
        Response<String> orientationResponse = _assistant.getOrientation(imageContent);
        String responseJson = cleanupOcrResponse(orientationResponse.content());
        OrientationDetectionResult orientationResult = JsonbUtils.getInstance().fromJson(responseJson, OrientationDetectionResult.class);
        return switch (orientationResult.getOrientation()) {
            case "NO_ROTATION" -> 0;
            case "ROTATED_RIGHT_90" -> 90;
            case "ROTATED_180" -> 180;
            case "ROTATED_LEFT_90" -> 270;
            default -> -1;
        };
    }


    @Override
    public void close() {
        // Vision Model will be closed by the service that created it
    }


    @Override
    public List<OcrLang> getAvailableLanguages() {
        return OcrLangUtils.getOcrLanguages(OcrEngine.TYPE.VL.name());
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


    public OcrEngineVLImpl setProperties(Map<String, Object> properties) {
        _properties.putAll(properties);
        return this;
    }


    /**
     * @return Smallest scaling factor that can be applied to the provided image after all scaling attempts
     */
    public static double getMinImageScaleForce() {
        return Math.pow(IMG_SCALE_MAX, IMG_SCALEDOWN_STEPS_FORCE_MAX);
    }


    public static final class OrientationDetectionResult implements Serializable {

        @Serial
        private static final long serialVersionUID = 2036291939993513068L;
        private String _orientation;
        private double _confidence;


        public String getOrientation() {
            return _orientation;
        }


        public void setOrientation(String orientation) {
            _orientation = orientation;
        }


        public double getConfidence() {
            return _confidence;
        }


        public void setConfidence(double confidence) {
            _confidence = confidence;
        }

    }

}
