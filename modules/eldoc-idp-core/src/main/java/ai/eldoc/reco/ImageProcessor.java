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
package ai.eldoc.reco;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.model.PropertyAwareModel;
import ai.eldoc.model.PropertyConfig;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.ocr.OcrService;
import org.apache.commons.lang3.Range;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_imgproc.Vec3fVector;
import org.bytedeco.opencv.opencv_imgproc.Vec4iVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.imageio.*;
import javax.imageio.spi.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.*;
import java.io.*;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;


/**
 * Image Processing Service for performing image transformation
 */
public class ImageProcessor implements AutoCloseable, PropertyAwareModel {

    private static final Logger LOG = LoggerFactory.getLogger(ImageProcessor.class);

    public static final String ELDOC_JVM_OPTION_MIN_SKEW_DEGREE = "ai.eldoc.idp.minSkewDegree";
    public static final String ELDOC_JVM_OPTION_MAX_SKEW_DEGREE = "ai.eldoc.idp.maxSkewDegree";
    public static final String ELDOC_JVM_OPTION_MAX_ROTATE_DEGREE = "ai.eldoc.idp.maxRotateDegree"; // Defines max rotate degree, supported values: 0, 90, 180, 270

    private static final double MAX_CONFIGURABLE_SKEW_DEGREE = 45;  // Maximum angle that is allowed to be set via configuration for min and max skew degree
    private static final double MAX_SKEW_DEGREE_DEFAULT = 5;        // Maximum deskew angle we would like to adjust
    private static final double MIN_SKEW_DEGREE_DEFAULT = 0.55;     // Minimum deskew angle we would like to adjust

    private static final long IMAGE_AREA_MAX = 200_000_000L;

    private final List<BufferedImage> _imagePages;
    private final List<List<AffineTransform>> _pageTransforms;
    private final List<Integer> _detectedRotation;

    private final Map<String, Object> _properties;

    /**
     * List of properties that are not enabled by default and if defined - require custom pre-processing of the source file
     */
    private static final List<String> CUSTOM_PREPROCESSING_PROPERTIES = Arrays.asList(
            PropertyConfig.IDP_RECOFORM_MORPHOLOGY,
            PropertyConfig.IDP_ADAPTIVE_THRESHOLD,
            PropertyConfig.IDP_ITERATIVE_THRESHOLD,
            PropertyConfig.IDP_THRESHOLD_VALUE,
            PropertyConfig.IDP_IGNORE_TEXT_LAYER
    );

    private static final String IMAGE_FORMAT_NOT_SUPPORTED_MSG = "File format is not supported (no image reader found) or file is broken";


    //
    // Constructors & PreDestroy ------------------------------------------------------------------
    //
    public ImageProcessor() {
        _imagePages = new ArrayList<>();
        _pageTransforms = new ArrayList<>();
        _detectedRotation = new ArrayList<>();
        _properties = new HashMap<>();
    }


    //
    // Converter methods --------------------------------------------------------------------------
    //


    /**
     * Loads a single image to the engine. If there was already image(s) loaded, they will be removed.
     * Images should be in PNG format
     *
     * @param image {@link BufferedImage} image to be loaded
     */
    public void loadPage(BufferedImage image) {
        cleanup();
        _imagePages.add(image);
        _pageTransforms.add(new ArrayList<>());
        _detectedRotation.add(0); // By default, we assume no rotation until we check it
    }


    /**
     * Loads a list of images to the engine, for example, multipage image or images for batch processing.
     * If there was already image(s) loaded, they will be removed. Images should be in PNG format.
     *
     * @param images List of {@link BufferedImage} images to be loaded to the system
     */
    public void loadPages(List<BufferedImage> images) {
        cleanup();
        _imagePages.addAll(images);
        _imagePages.forEach(page -> _pageTransforms.add(new ArrayList<>()));
        _imagePages.forEach(page -> _detectedRotation.add(0));
    }


    /**
     * @return Images from the current processing engine instance
     */
    public List<BufferedImage> getPages() {
        return _imagePages;
    }


    /**
     * @return {@link AffineTransform}s, that was applied by this ImageProcessor to each page.<br>
     * If no layout transformation was performed for the page, then corresponding AffineTransform will
     * contain identity transformation (i.e., transformation that does not change coordinates)
     */
    public List<List<AffineTransform>> getPageTransforms() {
        return _pageTransforms;
    }


    /**
     * @return List of detected page rotations if the corresponding check was already performed.<br>
     * If no such check performed, assumes no rotation presents and such a page will have 0-degree rotation set
     */
    public List<Integer> getPageRotation() {
        return _detectedRotation;
    }


    /**
     * @return Pages dimensions in format WxH, e.g.: ["2500x3600", "2300x3450"]
     */
    public List<String> getPagesDimensions() {
        return _imagePages.stream()
                .map(pageImage -> formatDimensions(pageImage.getWidth(), pageImage.getHeight()))
                .toList();
    }


    public void addPage(BufferedImage image) {
        _imagePages.add(image);
        _pageTransforms.add(new ArrayList<>());
        _detectedRotation.add(0);
    }


    @Override
    public void close() {
    }


    @Override
    public Map<String, Object> getProperties() {
        return _properties;
    }


    public ImageProcessor setProperties(Map<String, Object> properties) {
        _properties.putAll(properties);
        return this;
    }


    /**
     * @param properties Set of properties to be checked if they require custom pre-processing.
     * @return {@code true} if provided properties require custom pre-processing, {@code false} otherwise
     */
    public static boolean isCustomPreprocessingRequired(Map<String, Object> properties) {
        return !Collections.disjoint(CUSTOM_PREPROCESSING_PROPERTIES, properties.keySet());
    }


    //
    // Image processing methods -------------------------------------------------------------------
    //


    /**
     * Performs image pre-processing in the following order:
     * <ol>
     * <li>Fix image, if necessary (image depth and alpha channel)</li>
     * <li>Convert image to grayscale;</li>
     * <li>Fixe image rotation 90, -90, 180 (using Tesseract OSD);</li>
     * <li>Deskew image;</li>
     * <li>Optimize image size to 2480 pixels wide (approximate size of A4 page with 300 DPI);</li>
     * <li>Blur image;</li>
     * <li>Threshold image using simple thresholding, this also removes most of the background;</li>
     * <li>Remove scanning artifacts;</li>
     * </ol>
     */
    public void preprocess() {
        for (int i = 0; i < _imagePages.size(); i++) {
            LOG.trace("Preprocess: [{}] preprocessing START...", i);
            Mat matImage = null;
            try {
                LOG.trace("Preprocess: [{}] Converting to OpenCV Mat...", i);
                List<AffineTransform> pageTransformsList = new ArrayList<>();
                matImage = bufferedImageToMat(_imagePages.get(i));

                LOG.trace("Preprocess: [{}] Fixing colour depth...", i);
                // Check the image depth and fix it, if necessary. For thresholding, images should be 8-bits depth, so if another image type
                // was sent for preprocessing - convert it to 8 BPP and proceed with further preprocessing
                fixImage(matImage);

                LOG.trace("Preprocess: [{}] Grayscaling...", i);
                convertToGrayscale(matImage);

                LOG.trace("Preprocess: [{}] Inverting...", i);
                // Work with inverted image
                invertColor(matImage);

                if (isFixRotationEnabled()) {
                    LOG.trace("Preprocess: [{}] Running orientation detection...", i);
                    int rotationDegree = fixRotation(matImage, _imagePages.get(i), pageTransformsList);
                    _detectedRotation.set(i, rotationDegree);
                }

                if (isDeskewEnabled()) {
                    LOG.trace("Preprocess: [{}] Running deskew...", i);
                    fixSkew(matImage, pageTransformsList);
                }

                LOG.trace("Preprocess: [{}] Size optimization...", i);
                sizeOptimization(matImage);

                LOG.trace("Preprocess: [{}] Blurring image...", i);
                blur(matImage);

                LOG.trace("Preprocess: [{}] Thresholding...", i);
                threshold(matImage); // must come 2nd from the end (before clearArtifacts())

                LOG.trace("Preprocess: [{}] Artifacts cleanup...", i);
                clearArtifacts(matImage); // must come after threshold()

                LOG.trace("Preprocess: [{}] Inverting back...", i);
                // Inverting image back to normal
                invertColor(matImage);

                LOG.trace("Preprocess: [{}] Converting to BufferedImage...", i);
                BufferedImage newImage = matToBufferedImage(matImage);

                // Replace old image with the converted one
                _imagePages.set(i, newImage);
                _pageTransforms.set(i, pageTransformsList);
            } catch (Exception e) {
                LOG.error("Preprocess: Page index: {}", i, e);
                throw new ElDocRuntimeException("Error pre-processing image with index: " + i, e);
            } finally {
                // Release resources for the current Mat image
                CvUtils.close(matImage);
                Pointer.deallocateReferences();
            }
            LOG.trace("Preprocess: [{}] preprocessing END...", i);
        }
    }


    /**
     * Performs image cleanup, preparing image for OCR (improves Tesseract results):<br>
     * <ul>
     * <li>Removes lines in big contours, cleaning tables markup;</li>
     * <li>Removes squares (like OMR or single 'cells', skipped on the previous step due to size;</li>
     * </ul>
     *
     * @param pageImageForOcr {@link BufferedImage} image to be cleaned up
     * @return {@link Mat} cleaned up image, ready for OCR
     */
    public static Mat prepareImageForOcrAndGetMat(BufferedImage pageImageForOcr) {
        Mat result = bufferedImageToMat(pageImageForOcr);
        if (result.type() != CV_8UC1) {
            convertToGrayscale(result);
            thresholdDefault(result, THRESH_OTSU);
        }
        removeBigContours(result);
        removeSquares(result);
        return result;
    }


    public void convertToGrayscale() {
        for (int i = 0; i < _imagePages.size(); i++) {
            BufferedImage image = _imagePages.get(i);
            try {
                Mat matImage = bufferedImageToMat(image);
                convertToGrayscale(matImage);

                BufferedImage newImage = matToBufferedImage(matImage);

                // Release resources for the current Mat image
                CvUtils.close(matImage);

                // Replace old image with the converted one
                _imagePages.set(i, newImage);
            } catch (Exception e) {
                LOG.error("Page index: {}", i, e);
                throw new ElDocRuntimeException(e);
            } finally {
                Pointer.deallocateReferences();
            }
        }
    }


    public void threshold() {
        for (int i = 0; i < _imagePages.size(); i++) {
            BufferedImage image = _imagePages.get(i);
            try {
                Mat matImage = bufferedImageToMat(image);
                thresholdDefault(matImage, THRESH_OTSU);

                BufferedImage newImage = matToBufferedImage(matImage);

                // Release resources for the current Mat image
                CvUtils.close(matImage);

                // Replace old image with the converted one
                _imagePages.set(i, newImage);
            } catch (Exception e) {
                LOG.error("Page index: {}", i, e);
                throw new ElDocRuntimeException(e);
            } finally {
                Pointer.deallocateReferences();
            }
        }
    }


    public static Rectangle getBoundingRect(Mat contour) {
        Rect r = null;
        try {
            r = opencv_imgproc.boundingRect(contour);
            return fromOpenCVRect(r);
        } finally {
            CvUtils.close(r);
        }
    }


    //
    // Internal processing methods ----------------------------------------------------------------
    //


    /**
     * Optimizes image size
     *
     * @param image Image to be processed
     */
    private void sizeOptimization(Mat image) {
        // We assume A4 size which will have 2480px width with 300DPI
        int minSide = Math.min(image.cols(), image.rows()); // take the min side
        double wScale = 2480.0 / minSide;

        double maxScale = 1.7;

        // We resize image when it requires resizing (given image smaller, at least for 7%)
        if (wScale > 1.07) {
            // In case it is close to the A4&300DPI - use the calculated scale
            // Otherwise just resize with the maxScale
            double scale = Math.min(wScale, maxScale);
            resize(image, (int) (image.cols() * scale), (int) (image.rows() * scale));
        }
    }


    /**
     * Blurs image
     *
     * @param matImage Image to be blurred
     */
    private static void blur(Mat matImage) {
        Mat result = new Mat();
        try {
            opencv_imgproc.bilateralFilter(matImage, result, 5, 15.0, 35.0);
            result.copyTo(matImage);
        } finally {
            CvUtils.close(result);
        }
    }


    /**
     * Converts color image to grayscale
     *
     * @param image Image to be processed
     */
    protected static void convertToGrayscale(Mat image) {
        if (image.channels() != 1) {
            opencv_imgproc.cvtColor(image, image, COLOR_BGR2GRAY);
        }
    }


    /**
     * Performs image fix:
     * 1. If it has alpha-layer, it has to be removed
     * 2. If it has more than 8 bits per pixel - convert to 8BPP, scaling existing color values
     *
     * @param image Image to be processed
     */
    protected void fixImage(Mat image) {
        removeAlphaChannel(image);
        fixImageColorDepth(image);
    }


    /**
     * Performs image fix: if it has alpha-layer, it will be removed
     *
     * @param image Image to be processed
     */
    protected void removeAlphaChannel(Mat image) {
        if (image.channels() == 4) {
            LOG.trace("Image has alpha-channel (channels={}), removing alpha-channel", image.channels());
            opencv_imgproc.cvtColor(image, image, COLOR_BGRA2BGR);
        }
    }


    /**
     * Performs image fix: if it has more than 8 bits per pixel - convert to 8BPP, scaling existing color values
     *
     * @param image Image to be processed
     */
    protected void fixImageColorDepth(Mat image) {
        if (image.depth() != CV_8U) {
            LOG.trace("Image has wrong depth ({}), converting to CV_8U", image.depth());
            image.convertTo(image, CV_8U, 1.0 / 256.0, 0.0);
        }
    }


    /**
     * Performs a threshold
     *
     * @param image image to be thresholded (IMPORTANT: expects inverted image)
     */
    protected void threshold(Mat image) {
        if (getProperty(PropertyConfig.IDP_THRESHOLD_VALUE, 0) != 0) {
            thresholdDefault(image, THRESH_BINARY, getProperty(PropertyConfig.IDP_THRESHOLD_VALUE, 0));
        } else if (getProperty(PropertyConfig.IDP_ADAPTIVE_THRESHOLD, false)) {
            thresholdAdaptive(image);
        } else if (getProperty(PropertyConfig.IDP_ITERATIVE_THRESHOLD, false)) {
            thresholdIterative(image);
        } else {
            thresholdDefault(image, THRESH_OTSU);
        }
    }


    /**
     * Thresholding the current image with a specified method. Available values are:
     * <pre>
     * THRESH_BINARY = 0;
     * THRESH_BINARY_INV = 1;
     * THRESH_TRUNC = 2;
     * THRESH_TOZERO = 3;
     * THRESH_TOZERO_INV = 4;
     * THRESH_MASK = 7;
     * THRESH_OTSU = 8;
     * </pre>
     *
     * @param image Image to be processed
     * @param thresh Thresholding method to be used
     */
    protected static void thresholdDefault(Mat image, int thresh) {
        thresholdDefault(image, thresh, 127); // 3rd param (127) is basically ignored when default threshold is performed
    }


    /**
     * Thresholding the current image with a specified method. Available values are:
     * THRESH_BINARY = 0;
     * THRESH_BINARY_INV = 1;
     * THRESH_TRUNC = 2;
     * THRESH_TOZERO = 3;
     * THRESH_TOZERO_INV = 4;
     * THRESH_MASK = 7;
     * THRESH_OTSU = 8;
     *
     * @param image Image to be processed
     * @param thresh Thresholding method to be used
     * @param thresholdLevel Custom threshold level to be used
     */
    protected static void thresholdDefault(Mat image, int thresh, int thresholdLevel) {
        opencv_imgproc.threshold(image, image, thresholdLevel, 255.0, thresh);
    }


    /**
     * Performs an adaptive threshold (OpenCV implementation)
     *
     * @param image image for an adaptive threshold (IMPORTANT: expects inverted image)
     */
    protected void thresholdAdaptive(Mat image) {
        opencv_imgproc.adaptiveThreshold(image, image, 255, ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 77, -4);
    }


    /**
     * Performs an iterative threshold (custom implementation)
     *
     * @param image image for an adaptive threshold (IMPORTANT: expects inverted image)
     */
    protected void thresholdIterative(Mat image) {
        double areaThreshold = 0.05;
        double areaAdaptiveThresholding = 0.2;

        Mat thresholdedImage = new Mat();

        // Find thresholded image
        double locatedThreshold = opencv_imgproc.threshold(image, thresholdedImage, 127, 255.0, THRESH_OTSU);
        double initialLocatedThreshold = locatedThreshold;

        // Find image leftovers, removing thresholded part of the image from it
        Mat removedImage = image.clone();
        Mat mask = thresholdedImage.clone();
        morphDilate(mask, 1, 20, 20);
        opencv_core.bitwise_not(mask, mask);
        Scalar averageBgColor = opencv_core.mean(image, mask);
        opencv_core.bitwise_not(mask, mask);
        removedImage.setTo(new Mat(averageBgColor.blue()), mask);

        CvUtils.close(mask);

        // Threshold image rollovers, getting 'second-best threshold level'
        Mat removedThresholded = new Mat();
        locatedThreshold = opencv_imgproc.threshold(removedImage, removedThresholded, 127, 255.0, THRESH_OTSU);
        CvUtils.close(removedImage);

        Mat removedThresholdedDilated = removedThresholded.clone();
        morphDilate(removedThresholdedDilated, 1, 25, 25);
        double thresholdedBackgroundArea = findBiggestElementArea(removedThresholdedDilated);
        CvUtils.close(removedThresholdedDilated);

        // Join thresholded images by first and second threshold levels, getting image, thresholded by first and second global 'best' threshold levels
        // This allows us to get thresholded image that will contain darkest text and more 'pale' in case it exists
        if (thresholdedBackgroundArea < image.size().width() * image.size().height() * 0.3) {
            opencv_core.bitwise_or(thresholdedImage, removedThresholded, thresholdedImage);
        }

        Mat originalResult = thresholdedImage.clone();
        double originalThreshold = locatedThreshold;

        CvUtils.close(removedThresholded);

        // Locate biggest contour on thresholded image
        morphDilate(thresholdedImage, 1, 20, 20);
        double biggestContourArea = findBiggestElementArea(thresholdedImage);

        // If the image contains to large contour after dilation - most probably this is leftovers of the background. Try to locate a threshold level that will remove such a contour
        if (biggestContourArea > image.size().height() * image.size().width() * areaAdaptiveThresholding) {
            double minThreshold = initialLocatedThreshold * 0.3;
            for (double testingThreshold = locatedThreshold; testingThreshold > minThreshold; testingThreshold -= 1) {
                opencv_imgproc.threshold(image, thresholdedImage, testingThreshold, 255.0, THRESH_BINARY);

                morphDilate(thresholdedImage, 1, 20, 20);

                biggestContourArea = findBiggestElementArea(thresholdedImage);
                if (biggestContourArea < image.size().height() * image.size().width() * areaThreshold) {
                    locatedThreshold = testingThreshold;
                    break;
                }
            }
        }
        if (originalThreshold == locatedThreshold) {
            originalResult.copyTo(image);
        } else {
            opencv_imgproc.threshold(image, image, locatedThreshold, 255.0, THRESH_BINARY);
        }
        CvUtils.close(originalResult);
    }


    private long findBiggestElementArea(Mat image) {
        int biggestElementArea = 0;

        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int labelsCount = opencv_imgproc.connectedComponentsWithStats(image, labels, stats, centroids);

        IntIndexer idx = stats.createIndexer();

        for (int i = 1; i < labelsCount; i++) {
            double area = idx.get(i, CC_STAT_AREA);
            biggestElementArea = (int) Math.max(biggestElementArea, area);
        }

        CvUtils.close(labels);
        CvUtils.close(stats);
        CvUtils.close(centroids);

        return biggestElementArea;
    }


    /**
     * Performs image cleanup from scanning artifacts. It first groups all not empty areas on the image to 'contours',
     * calculates its area, and if it's too small - fills it with black color, meaning this area will be empty on the resulting image.
     *
     * @param image image for clearing artifacts (IMPORTANT: expects inverted image)
     */
    protected void clearArtifacts(Mat image) {
        if (!isClearingArtifactsRequired()) {
            return;
        }

        String morphologyPropValue = getProperty(PropertyConfig.IDP_RECOFORM_MORPHOLOGY, "0x0");
        String[] sizes = morphologyPropValue.split("x");
        int width = Integer.parseInt(sizes[0]);
        int height = Integer.parseInt(sizes[1]);

        Point kernelCenter = null;
        Size kernelSize = null;
        Mat kernel = null;
        try {
            kernelSize = new Size(width, height);
            kernelCenter = new Point(-1, -1);
            kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, kernelSize);
            opencv_imgproc.morphologyEx(image, image, MORPH_CLOSE, kernel, kernelCenter, 1, opencv_core.BORDER_ISOLATED, AbstractScalar.BLACK);
            opencv_imgproc.morphologyEx(image, image, MORPH_OPEN, kernel, kernelCenter, 1, opencv_core.BORDER_ISOLATED, AbstractScalar.BLACK);
        } finally {
            CvUtils.close(kernelSize);
            CvUtils.close(kernel);
            CvUtils.close(kernelCenter);
        }
    }


    /**
     * @return {@code true} if potential artifacts detection and removing are required due to ImageProcessor settings, or {@code false} otherwise
     */
    private boolean isClearingArtifactsRequired() {
        String morphologyPropValue = getProperty(PropertyConfig.IDP_RECOFORM_MORPHOLOGY, "0x0");

        if ("0x0".equals(morphologyPropValue)) {
            return false;
        }

        PropertyConfig morphProp = PropertyConfig.get(PropertyConfig.IDP_RECOFORM_MORPHOLOGY);
        if (morphProp == null) {
            LOG.warn("Property config is not defined for name: {}", PropertyConfig.IDP_RECOFORM_MORPHOLOGY);
            return false;
        }

        if (!morphologyPropValue.matches(morphProp.getValidator())) {
            LOG.warn("Property value [{}] is not valid (matching pattern: {})", morphologyPropValue, morphProp.getValidator());
            return false;
        }

        return true;
    }


    /**
     * Inverts colors on the image (all channels)
     *
     * @param image Image to be processed
     */
    private static void invertColor(Mat image) {
        opencv_core.bitwise_not(image, image);
    }


    /**
     * Tries to find and remove big contours that have a low fill ratio, mainly for increasing OCR quality.
     * This method locates all 'connected components', using flood fill algorithm, which gives 'blocks' of interconnected pixels of the same color, and characteristics of such blocks.
     * Then, it calculates the average fill ratio as (number_of_pixels_of_current_colour) / (width * height).
     * If there are no blocks, which fill ratio differs too much from average ('anomalies') - the algorithm falls back to default lines removal.
     * If such 'anomalies' are found, it removes them, painting out.
     *
     * @param image Image to be processed
     */
    public static void removeBigContours(Mat image) {
        Mat labels = null;
        Mat stats = null;
        Mat centroids = null;
        IntIndexer idx = null;
        try {
            // To find Connected Components, we need to work on inverted image
            LOG.trace("Removing Big Contours starting...");
            labels = new Mat();
            stats = new Mat();
            centroids = new Mat();
            invertColor(image);
            int labelsCount = opencv_imgproc.connectedComponentsWithStats(image, labels, stats, centroids);
            invertColor(image);
            LOG.trace("Removing Big Contours: connected components count: {}", labelsCount);

            List<Double> sizeStatistics = new ArrayList<>();
            List<Double> filledAreaStatistics = new ArrayList<>();

            idx = stats.createIndexer();

            // Calculate ratio of the filled pixels of each connected component area
            // Counted as (number of black pixels) / (total number of pixels)
            for (int i = 1; i < labelsCount; i++) {
                double w = idx.get(i, CC_STAT_WIDTH);
                double h = idx.get(i, CC_STAT_HEIGHT);
                double area = idx.get(i, CC_STAT_AREA);

                if (w * h > 300) {
                    sizeStatistics.add(w * h);
                    filledAreaStatistics.add(area / (w * h));
                }
            }

            double averageSize = getMedian(sizeStatistics);
            double averageFilledArea = getMedian(filledAreaStatistics);
            LOG.trace("Removing Big Contours: averageSize={}, averageFilledArea={}", averageSize, averageFilledArea);

            // Collect candidate components first
            List<Integer> candidates = new ArrayList<>();
            for (int i = 1; i < labelsCount; i++) {
                double w = idx.get(i, CC_STAT_WIDTH);
                double h = idx.get(i, CC_STAT_HEIGHT);
                double area = idx.get(i, CC_STAT_AREA);

                // If current connected component size is too big and relatively hollow -> candidate for line removal
                if ((w * h > averageSize) && (area / (w * h)) < averageFilledArea) {
                    candidates.add(i);
                }
            }
            LOG.trace("Removing Big Contours: components to process: {}", candidates.size());

            if (!candidates.isEmpty()) {
                Mat combinedMask = null;
                try {
                    // Build a single mask of all candidate components using ROIs to avoid repeated full-image scans
                    combinedMask = new Mat(image.size(), CV_8UC1);
                    combinedMask.setTo(new Mat(AbstractScalar.BLACK));

                    for (Integer labelId : candidates) {
                        double w = idx.get(labelId, CC_STAT_WIDTH);
                        double h = idx.get(labelId, CC_STAT_HEIGHT);
                        double left = idx.get(labelId, CC_STAT_LEFT);
                        double top = idx.get(labelId, CC_STAT_TOP);

                        Rect roiRect = null;
                        Mat labelsRoi = null;
                        Mat maskRoi = null;
                        Mat lookupColour = null;
                        Scalar lookupScalar = null;
                        try {
                            roiRect = new Rect((int) left, (int) top, (int) w, (int) h);
                            labelsRoi = new Mat(labels, roiRect);
                            maskRoi = new Mat(combinedMask, roiRect);
                            lookupScalar = new Scalar(labelId);
                            lookupColour = new Mat(lookupScalar);
                            opencv_core.compare(labelsRoi, lookupColour, maskRoi, opencv_core.CMP_EQ);
                        } finally {
                            CvUtils.close(lookupColour);
                            CvUtils.close(lookupScalar);
                            CvUtils.close(maskRoi);
                            CvUtils.close(labelsRoi);
                            CvUtils.close(roiRect);
                        }
                    }

                    // Keep behavior consistent with previous logic
                    invertColor(combinedMask);
                    houghRemoveLinesP(combinedMask);
                    // 'Paint' mask on original image
                    opencv_core.bitwise_or(image, combinedMask, image);
                } finally {
                    CvUtils.close(combinedMask);
                }
            }
        } finally {
            CvUtils.close(centroids);
            CvUtils.close(labels);
            CvUtils.close(stats);
            CvUtils.close(idx);
            LOG.trace("Removing Big Contours completed");
        }
    }


    private static double getMedian(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        if (values.size() == 1) {
            return values.getFirst();
        }
        List<Double> localList = new ArrayList<>(values);
        Collections.sort(localList);
        if (localList.size() % 2 == 0) {
            return (localList.get(localList.size() / 2 - 1) + localList.get(localList.size() / 2)) / 2;
        } else {
            return localList.get(localList.size() / 2);
        }
    }


    private static void houghRemoveLinesP(Mat image) {
        List<Line2D> lineList = findLines(image, 5);
        image.setTo(new Mat(AbstractScalar.BLACK));
        lineList.forEach(line -> {
            Point tl = null;
            Point br = null;
            try {
                tl = new Point((int) line.getX1(), (int) line.getY1());
                br = new Point((int) line.getX2(), (int) line.getY2());
                opencv_imgproc.line(image, tl, br, AbstractScalar.WHITE, 5, LINE_4, 0);
            } finally {
                CvUtils.close(tl);
                CvUtils.close(br);
            }
        });
    }


    /**
     * Finds lines on the image, using the probabilistic Hough lines algorithm.<br>
     * This algorithm finds overlapping lines if the image has 'thick' lines on it (they will have different angles and/or length).<br>
     * <b>IMPORTANT:</b> image should be binary (B/W) and inverted
     *
     * @param image Image to be processed
     * @param maxLineGap Maximum allowed a gap between 2 lines fragment to treat them as parts of the same line
     * @return List of lines found on the image
     */
    private static List<Line2D> findLines(Mat image, int maxLineGap) {
        List<Line2D> result = new ArrayList<>();

        int minSideLen = Math.min(image.size().width(), image.size().height());
        int minLineLength = minSideLen / 10;

        Vec4iVector lines = null;
        Mat imageCopy = null;
        Scalar4i[] linesScalar = null;
        try {
            lines = new Vec4iVector();
            imageCopy = image.clone();
            if (image.type() != CV_8UC1) {
                // Currently, a system assumes that the input image was already preprocessed at this step. In case, it was
                // preprocessed, saved to PDF and then loaded (PREPROCESSED files for non-machine-generated files)
                // PDFBOX ignores a type of the images and loads it as BGR, even if it is B/W. Trying to convert it
                // back to grayscale fixes this issue and sets proper type for the image, leaving it 'as is',
                // without modifying image data
                convertToGrayscale(imageCopy);
            }
            invertColor(imageCopy);
            opencv_imgproc.HoughLinesP(imageCopy, lines, 0.5, CV_PI / 540, 20, minLineLength, maxLineGap);
            linesScalar = lines.get();
            for (Scalar4i line : linesScalar) {
                result.add(new Line2D.Float(line.get(0), line.get(1), line.get(2), line.get(3)));
            }
        } finally {
            CvUtils.close(lines);
            CvUtils.close(imageCopy);
            if (linesScalar != null) {
                for (Scalar4i line : linesScalar) {
                    CvUtils.close(line);
                }
            }
        }

        return result;
    }


    protected static void removeSquares(Mat image) {
        MatVector contours = null;
        Mat hierarchy = null;
        Mat tmpImage = null;
        try {
            contours = new MatVector();
            hierarchy = new Mat();
            tmpImage = image.clone();
            morphErode(tmpImage, 2, 2);

            opencv_imgproc.findContours(tmpImage, contours, hierarchy, opencv_imgproc.RETR_LIST, opencv_imgproc.CHAIN_APPROX_SIMPLE);

            SummaryStatistics widthStats = new SummaryStatistics();
            SummaryStatistics heightStats = new SummaryStatistics();
            for (Mat contour : contours.get()) {
                Rectangle boundingRect = getBoundingRect(contour);
                widthStats.addValue(boundingRect.getWidth());
                heightStats.addValue(boundingRect.getHeight());
            }

            double widthAvg = widthStats.getMean();
            double heightAvg = heightStats.getMean();

            for (int i = 0; i < contours.size(); i++) {
                Mat contour = contours.get(i);
                Rectangle boundingRect = getBoundingRect(contour);
                double perimeter = opencv_imgproc.arcLength(contour, true);
                Mat approxCurve = new Mat();
                opencv_imgproc.approxPolyDP(contour, approxCurve, perimeter * 0.03, true);
                if (approxCurve.total() == 4
                        && boundingRect.getWidth() * 2 > widthAvg && boundingRect.getHeight() > heightAvg * 2
                        && boundingRect.getWidth() > boundingRect.getHeight() * 0.8 && boundingRect.getWidth() < boundingRect.getHeight() * 1.2) {
                    opencv_imgproc.drawContours(image, contours, i, AbstractScalar.WHITE, 5, LINE_4, null, -1, null);
                }

                CvUtils.close(approxCurve);
            }
        } finally {
            CvUtils.close(tmpImage);
            CvUtils.close(contours);
            CvUtils.close(hierarchy);
        }
    }


    /**
     * Detects image rotation (0, 90, 180, 270 clockwise) using Tesseract OSD and adjusts image rotation if required
     *
     * @param image image to be processed (IMPORTANT: expects source inverted image, not thresholded)
     * @param originalImage Original image (without any preprocessing)
     * @param transformsList {@link AffineTransform}, that is used to track layout transformations, applied to image
     */
    private int fixRotation(Mat image, BufferedImage originalImage, List<AffineTransform> transformsList) {
        Mat imageClone = null;
        try {

            int rotationDegree;
            if (OcrService.isPageOrientationDetectionRequiresPreprocessing()) {
                LOG.trace("Page orientation detection requires preprocessing...");
                imageClone = image.clone();

                // because threshold is required here but not implemented yet in scope of the preprocess() method
                LOG.trace("Page orientation detection preprocessing: thresholding...");
                threshold(imageClone);

                // As source image comes inverted, we invert cloned image back before passing to OCR
                LOG.trace("Page orientation detection preprocessing: inverting color...");
                invertColor(imageClone);

                // prepare image for orientation and script detection
                LOG.trace("Page orientation detection preprocessing: preparing for OSD...");
                prepareImageForOsd(imageClone);

                // Get page orientation
                LOG.trace("Page orientation detection preprocessing completed, performing page orientation detection...");
                rotationDegree = OcrService.getPageOrientation(matToBufferedImage(imageClone));
            } else {
                LOG.trace("Page orientation detection does not require preprocessing, performing page orientation detection...");
                rotationDegree = OcrService.getPageOrientation(originalImage);
            }

            // Rotate page in case required
            LOG.trace("Page orientation: detected rotation degree: {} ", rotationDegree);
            if (rotationDegree != 0) {
                if (Math.abs(rotationDegree) <= getMaxRotateDegree()) {
                    fixRotation(image, rotationDegree);
                    if (transformsList != null) {
                        // Rotating around (0,0) coords by default - need to first move image, so that it will be located entirely on screen
                        AffineTransform transform = new AffineTransform();
                        switch (rotationDegree) {
                            case 90 -> transform.translate(0, image.size().height());
                            case 270 -> transform.translate(image.size().width(), 0);
                            case 180 -> transform.translate(image.size().width(), image.size().height());
                        }
                        transform.rotate(Math.toRadians(-rotationDegree));
                        transformsList.add(transform);
                    }
                } else {
                    LOG.debug("Page rotation fix is not applied as detected rotation degree '{}' is bigger than max allowed rotation degree '{}'", rotationDegree, getMaxRotateDegree());
                }
            }
            LOG.trace("Page orientation detection completed, detected and normalized rotation degree: {} ", rotationDegree);
            return rotationDegree;
        } finally {
            CvUtils.close(imageClone);
        }
    }


    /**
     * Adjusts page rotation based on the given angle. Currently, supports angles: 90, 180, 270 degrees
     *
     * @param image {@link Mat} image, whose rotation should be fixed
     * @param rotateAngle Current rotation of the page
     */
    private static void fixRotation(Mat image, int rotateAngle) {
        int rotationDirection = switch (rotateAngle) {
            case 90 -> opencv_core.ROTATE_90_COUNTERCLOCKWISE;
            case 270 -> opencv_core.ROTATE_90_CLOCKWISE;
            case 180 -> opencv_core.ROTATE_180;
            default -> -1;
        };
        if (rotationDirection != -1) {
            opencv_core.rotate(image, image, rotationDirection);
        }
    }


    /**
     * Detects image skew and adjusts it required<br>
     * IMPORTANT: given b/w image will be converted into grayscale {@link #rotateImage(Mat, double)}
     *
     * @param image image to be processed (IMPORTANT: expects source inverted image, not thresholded)
     * @param transformsList {@link AffineTransform}, that is used to track layout transformations, applied to image
     */
    private void fixSkew(Mat image, List<AffineTransform> transformsList) {
        Mat imageClone = null;
        try {
            imageClone = image.clone();

            // because threshold is required here but not implemented yet in scope of the preprocess() method
            threshold(imageClone);

            // Get page skew
            double pageSkew = getImageSkew(imageClone);

            // Check if the detected angle is supported for deskew
            if (getAllowedSkewDegree().contains(Math.abs(pageSkew))) {
                // Rotate image to unwarp it and crop to area, found on previous step
                // IMPORTANT: This method converts B/W images back to grayscale due to interpolation, performed during rotation
                rotateImage(image, pageSkew);
                if (transformsList != null) {
                    AffineTransform transform = new AffineTransform();
                    // Rotate specified degrees around center
                    transform.rotate(0 - Math.toRadians(pageSkew), image.size().width() / 2.0, image.size().height() / 2.0);
                    transformsList.add(transform);
                }
            }
        } finally {
            CvUtils.close(imageClone);
        }
    }


    /**
     * Prepares image for Orientation and script detection.<br>
     * Steps performed:<br>
     * 1. Removes lines from image;<br>
     *
     * @param image {@code Mat} input image
     */
    private void prepareImageForOsd(Mat image) {
        removeBigContours(image);
    }


    /**
     * Finds an image skew angle in degrees, normalized to the range (-45, 45].
     * A positive return value means the image content is rotated clockwise
     * relatively to the horizontal axis (caller should rotate by 0-angle to deskew).
     *
     * @param skewedInputMat source image. Not modified. Ideally grayscale; color input will be converted internally.
     * @return skew angle in degrees, or 0.0 if no suitable contours were found
     */
    protected double getImageSkew(Mat skewedInputMat) {
        final double borderWidth = 0.02;  // fraction to crop from EACH side (so 2*borderWidth total per axis)
        final double sizeCeiling = 0.25;  // max contour area as fraction of cropped image
        final double minRatio = 3.0;   // min width/height (or inverse) ratio to consider

        if (skewedInputMat == null || skewedInputMat.empty()) {
            LOG.trace("Empty input, returning 0 degrees");
            return 0.0;
        }

        double weightedAngleSum = 0.0;
        double totalWeight = 0.0;

        Mat croppedMat = null;
        Mat workingMat = null;
        Mat hierarchy = null;
        MatVector contours = null;
        Rect rectCrop = null;

        try {
            final double imgScale = skewedInputMat.cols() / 2000.0;

            // For a symmetric border crop, width/height must be (1 - 2*borderWidth) of each axis
            rectCrop = toOpenCVRect(new Rectangle(
                    skewedInputMat.cols() * borderWidth,
                    skewedInputMat.rows() * borderWidth,
                    skewedInputMat.cols() * (1.0 - 2.0 * borderWidth),
                    skewedInputMat.rows() * (1.0 - 2.0 * borderWidth)));

            croppedMat = new Mat(skewedInputMat, rectCrop);

            // findContours requires an 8-bit single-channel binary image.
            // Convert to grayscale if needed, then threshold (Otsu + invert so text
            // becomes foreground), then dilate to merge characters into blobs.
            workingMat = new Mat();
            croppedMat.copyTo(workingMat);

            // Merge nearby pixels into continuous text-line blobs
            morphDilate(workingMat, (int) (12 * imgScale), 2, 1);

            contours = new MatVector();
            hierarchy = new Mat();
            opencv_imgproc.findContours(workingMat, contours, hierarchy, opencv_imgproc.RETR_TREE, opencv_imgproc.CHAIN_APPROX_SIMPLE);

            final double maxArea = sizeCeiling * workingMat.cols() * workingMat.rows();

            for (long i = 0; i < contours.size(); i++) {
                Mat contour = contours.get(i);
                RotatedRect r = null;
                try {
                    r = opencv_imgproc.minAreaRect(contour);

                    double rw = r.size().width();
                    double rh = r.size().height();

                    // Guard against zero-size degenerate rects (avoids NaN/Infinity in ratio)
                    if (rw <= 0.0 || rh <= 0.0) {
                        continue;
                    }

                    double ratio = rw / rh;
                    double rectArea = rw * rh;

                    // Must be reasonably sized and clearly non-square (i.e., looks like a text line)
                    if (rectArea >= maxArea) continue;
                    if (ratio < minRatio && ratio > 1.0 / minRatio) continue;

                    // Angle normalization to (-45, 45].
                    // Modern OpenCV (4.5.1+) returns an angle in [0, 90]. Older versions returned (-90, 0]. We fold both conventions into (-45, 45].
                    double a = r.angle();
                    // Collapse to (-90, 90] first — handles either convention and stray -0
                    a = ((a + 90.0) % 180.0) - 90.0;
                    // Then fold into (-45, 45]
                    if (a > 45.0) a -= 90.0;
                    if (a <= -45.0) a += 90.0;

                    // Weight by contour area so long text lines dominate short noise.
                    // Cached — opencv_imgproc.contourArea is non-trivial, don't call twice.
                    double weight = opencv_imgproc.contourArea(contour);
                    weightedAngleSum += a * weight;
                    totalWeight += weight;
                } finally {
                    CvUtils.close(r);
                }
            }
        } finally {
            CvUtils.close(rectCrop);
            CvUtils.close(contours);
            CvUtils.close(hierarchy);
            CvUtils.close(workingMat);
            CvUtils.close(croppedMat);
        }

        if (totalWeight == 0.0) {
            LOG.trace("No contours found for skew detection, returning 0 degrees");
            return 0.0;
        }
        return weightedAngleSum / totalWeight;
    }


    /**
     * Rotates image by a specified degree.<br>
     * <b>WARNING</b>: This method 'crops' corners<br>
     * <b>IMPORTANT</b>: This method converts B/W images back to grayscale due to interpolation, performed during rotation
     *
     * @param image {@link Mat} image to be rotated
     * @param angle {@code double} rotation angle
     */
    private void rotateImage(Mat image, double angle) {
        Point2f center = null;
        Mat rotImage = null;
        try {
            center = new Point2f((float) (image.size().width() / 2.0), (float) (image.size().height() / 2.0));
            rotImage = opencv_imgproc.getRotationMatrix2D(center, angle, 1.0);
            opencv_imgproc.warpAffine(image, image, rotImage, image.size(), INTER_CUBIC, BORDER_CONSTANT, AbstractScalar.BLACK);
        } finally {
            CvUtils.close(rotImage);
            CvUtils.close(center);
        }
    }


    /**
     * Performs search of circles on the image
     *
     * @param image MUST be a pre-processed image (from the pre-processed RecoFile)
     * @return List of circles found on the image
     */
    public static List<Rectangle> findCircles(BufferedImage image) {
        Mat imageMat = null;
        try {
            imageMat = bufferedImageToMat(image);
            return findCircles(imageMat);
        } finally {
            CvUtils.close(imageMat);
        }
    }


    /**
     * Locates circles on the provided image, using Hough transform (HOUGH_GRADIENT method).
     * Image has to be inverted for this method to work
     *
     * @param image MUST be a pre-processed image (from the pre-processed RecoFile)
     * @return List of the rectangles, each of which contains a located circle. The center of the returned rectangle is
     * the center of the located circle, and the circle itself is inscribed into the returned rectangle
     */
    private static List<Rectangle> findCircles(Mat image) {
        double minDist = Math.min(image.size().width(), image.size().height()) / 5.0;

        opencv_core.bitwise_not(image, image);
        // HoughCircles works with greyscaled images, thus requires 8-bit single-channel (CV_8UC1) image
        // Binary images give a much worse result. Blurring converts binary images back to greyscale and
        // also removes sharp edges of the circles, allowing HoughCircles to give better results
        opencv_imgproc.GaussianBlur(image, image, new Size(5, 5), 1);

        Vec3fVector circles = null;
        try {
            circles = new Vec3fVector();

            opencv_imgproc.HoughCircles(
                    image, // Source image
                    circles, // Vector of circles
                    CV_HOUGH_GRADIENT, // Hough algorithm
                    1.0, // Inverse ratio of 'accumulator'. With 1.0 it has the same resolution, as the input image
                    minDist, // Minimum distance between centers of the circle
                    100, // Higher threshold for Canny algorithm
                    45, // Accumulator threshold for circle's center. The smaller value passed - the more false-positive circles will be found
                    10, // Minimum radius
                    Math.min(image.size().width(), image.size().height()) / 2); // Maximum radius

            List<Rectangle> circleList = new ArrayList<>();
            for (int i = 0; i < circles.size(); i++) {
                Point3f circleCoords = circles.get(i);

                double r = circleCoords.get(2);
                double x = circleCoords.get(0) - r;
                double y = circleCoords.get(1) - r;

                Rectangle circle = new Rectangle(x, y, r * 2, r * 2);
                circleList.add(circle);
                CvUtils.close(circleCoords);
            }
            return circleList;
        } finally {
            CvUtils.close(circles);
        }
    }


    /**
     * Performs search of squares on the image
     *
     * @param image MUST be a pre-processed image (from the pre-processed RecoFile)
     * @return List of squares found on the image
     */
    public static List<Rectangle> findSquares(BufferedImage image) {
        Mat imageMat = null;
        try {
            imageMat = bufferedImageToMat(image);
            return findSquares(imageMat);
        } finally {
            CvUtils.close(imageMat);
        }
    }


    /**
     * Performs search of squares on the image
     *
     * @param image MUST be a pre-processed image (from the pre-processed RecoFile)
     * @return List of squares found on the image
     */
    private static List<Rectangle> findSquares(Mat image) {
        List<Rectangle> squares = new ArrayList<>();

        opencv_core.bitwise_not(image, image);

        // Calculate average contours size
        List<Rectangle> contoursList = findContours(image);

        if (contoursList.isEmpty()) {
            // Input image is blank and therefore contains no squares
            return new ArrayList<>();
        }

        double avgWidth = contoursList.stream().mapToDouble(Rectangle::getWidth).average().orElse(0);
        double avgHeight = contoursList.stream().mapToDouble(Rectangle::getHeight).average().orElse(0);

        int dilateSize = (int) ((avgWidth + avgHeight) / 2);

        Mat horizontalLines = null;
        Mat verticalLines = null;
        Mat squaresMat = null;
        MatVector contours = null;
        Mat hierarchy = null;
        try {
            // Find horizontal lines
            horizontalLines = image.clone();
            morphErode(horizontalLines, dilateSize, 1);
            morphDilate(horizontalLines, 1, dilateSize, 1);

            // Find vertical lines
            verticalLines = image.clone();
            morphErode(verticalLines, 1, dilateSize);
            morphDilate(verticalLines, 1, 1, dilateSize);

            squaresMat = new Mat();
            opencv_core.bitwise_or(horizontalLines, verticalLines, squaresMat);

            contours = new MatVector();
            hierarchy = new Mat();
            opencv_imgproc.findContours(squaresMat, contours, hierarchy, opencv_imgproc.RETR_LIST, opencv_imgproc.CHAIN_APPROX_SIMPLE);
            for (Mat contour : contours.get()) {
                Rectangle contourRect = getBoundingRect(contour);

                // Get contour approximation
                double perimeter = opencv_imgproc.arcLength(contour, true);
                Mat approxCurve = new Mat();
                opencv_imgproc.approxPolyDP(contour, approxCurve, perimeter * 0.02, true);

                if (approxCurve.total() == 4
                        && (contourRect.getWidth() > avgWidth * 0.5)
                        && (contourRect.getHeight() > avgHeight * 0.5)
                        && contourRect.getWidth() < contourRect.getHeight() * 1.2 && contourRect.getWidth() > contourRect.getHeight() * 0.8) {
                    squares.add(contourRect);
                }
                CvUtils.close(approxCurve);
            }
        } finally {
            CvUtils.close(horizontalLines);
            CvUtils.close(verticalLines);
            CvUtils.close(squaresMat);
            CvUtils.close(hierarchy);
            CvUtils.close(contours);
        }

        // Remove rectangles that have intersection with others. Most likely, they are false-positive
        Iterator<Rectangle> it = squares.iterator();
        while (it.hasNext()) {
            Rectangle currentSquare = it.next();
            boolean hasIntersection = squares.stream()
                    .filter(square -> !square.equals(currentSquare))
                    .anyMatch(square -> square.intersects(currentSquare));
            if (hasIntersection) {
                it.remove();
            }
        }

        return squares;
    }


    //
    // Internal image conversion methods ----------------------------------------------------------
    //


    /**
     * Converts OpenCV Mat to BufferedImage
     *
     * @param mat OpenCV Mat to convert
     * @return BufferedImage representation of the provided OpenCV Mat
     */
    public static BufferedImage matToBufferedImage(Mat mat) {
        return MatUtils.toBufferedImage(mat);
    }


    /**
     * Converts BufferedImage to OpenCV Mat
     *
     * @param image BufferedImage to convert
     * @return OpenCV Mat representation of the provided BufferedImage
     */
    public static Mat bufferedImageToMat(BufferedImage image) {
        return MatUtils.toMat(image, false);
    }


    public static void saveToPng(BufferedImage image, Path filePath) throws IOException {
        ImageIO.write(image, "png", filePath.toFile());
    }


    /**
     * Writes image to the provided OutputStream in JPEG format, applying specified JPG Compression Quality.<br>
     * A compression quality setting of 0.0 is most generically interpreted as "high compression is important",
     * while a setting of 1.0 is most generically interpreted as "high image quality is important."<br>
     *
     * @param image {@link BufferedImage} to be written
     * @param os {@link OutputStream}, where image should be written
     * @param quality JPEG Quality modifier
     */
    public static void saveToJpg(BufferedImage image, OutputStream os, double quality) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("image is null");
        }

        // JPEG baseline limits: max 65 535 per dimension
        int w = image.getWidth();
        int h = image.getHeight();
        if (w > 65535 || h > 65535) {
            throw new IllegalArgumentException("Image too large for JPEG baseline: " + w + "x" + h);
        }

        ImageWriter writer = null;
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(new NonClosingOutputStream(os))) {
            Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("JPEG");
            if (!it.hasNext()) {
                throw new IllegalStateException("No JPEG ImageWriter found");
            }

            writer = it.next();
            writer.setOutput(ios);

            // Detect grayscale BEFORE alpha flattening so we can keep 1-channel later
            boolean wasGray = image.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY;

            // 1) Strip alpha (JPEG can't encode alpha) if present (flatten on white)
            if (image.getColorModel().hasAlpha()) {
                image = stripAlphaIfPresent(image);
            }

            if (wasGray) {
                // 2) Keep true grayscale as BYTE_GRAY (smaller files): if it’s truly grayscale, encode as 1-channel for smaller files
                if (image.getType() != BufferedImage.TYPE_BYTE_GRAY) {
                    BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
                    Graphics2D g = gray.createGraphics();
                    g.drawImage(image, 0, 0, null);
                    g.dispose();
                    image = gray;
                }
            } else {
                // 3) Otherwise ensure 3x8-bit sRGB (handles CMYK/YCbCrK/indexed/16-bit/etc)
                if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
                    BufferedImage bgr = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g = bgr.createGraphics();
                    g.drawImage(image, 0, 0, null); // color-managed conversion to sRGB
                    g.dispose();
                    image = bgr;
                }
            }

            ImageWriteParam p = writer.getDefaultWriteParam();

            // Compression quality [0..1]
            if (!Double.isNaN(quality) && p.canWriteCompressed()) {
                double q = Math.clamp(quality, 0.0, 1.0);
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality((float) q);
            }

            // Optional: progressive JPEG
            // p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            // Optional: optimize Huffman tables (supported by JDK writer)
            if (p instanceof javax.imageio.plugins.jpeg.JPEGImageWriteParam jp) {
                jp.setOptimizeHuffmanTables(true);
            }

            // We’re not editing metadata -> pass null
            writer.write(null, new IIOImage(image, null, null), p);
        } catch (IOException ex) {
            if (writer != null) {
                try {
                    writer.abort();
                } catch (Throwable ignore) {
                }
            }
            LOG.error("Error saving JPEG", ex);
            throw ex; // keep IOException semantics
        } finally {
            if (writer != null) {
                writer.dispose();
            }
        }
    }


    /**
     * Removes the alpha channel from the given {@code BufferedImage} if it exists. If the source image does not have an alpha channel,
     * the original image is returned.
     * The resulting image will have the alpha channel replaced with an opaque background (white by default).
     *
     * @param src the source {@code BufferedImage} to process; if {@code null}, returns {@code null}
     * @return a {@code BufferedImage} without an alpha channel, or the original image if no alpha channel is present
     */
    private static BufferedImage stripAlphaIfPresent(BufferedImage src) {
        if (src == null || !src.getColorModel().hasAlpha()) {
            return src;
        }

        // Preserve gray if the source is gray(+alpha)
        boolean grayish = src.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY
                || src.getRaster().getNumBands() == 2; // gray + alpha

        int type = grayish ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;

        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), type);
        Graphics2D g = dst.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(Color.WHITE); // transparent -> white (or your chosen bg)
        g.fillRect(0, 0, dst.getWidth(), dst.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }


    /**
     * Returns a number of pages (images) in the image file, mostly relevant for TIFF files
     *
     * @param fileInputStream InputStream with the file content
     * @return Number of pages in the image file
     * @throws IOException In case of I/O error while reading provided InputStream
     */
    public static int getNumberOfPages(InputStream fileInputStream) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new NonClosingInputStream(fileInputStream))) {

            // Get the reader
            Iterator<ImageReader> imgReaders = ImageIO.getImageReaders(iis);

            if (!imgReaders.hasNext()) {
                throw new IllegalArgumentException(IMAGE_FORMAT_NOT_SUPPORTED_MSG);
            }

            ImageReader reader = imgReaders.next();
            try {
                // ignoreMetadata=true for speed; set seekForwardOnly=true if you only read forward
                reader.setInput(iis, /*seekForwardOnly*/ false, /*ignoreMetadata*/ true);
                return reader.getNumImages(true);
            } finally {
                reader.dispose();
            }
        }
    }


    /**
     * Reads the requested page index from the InputStream and returns its content. Also sets the total pages count to the totalPagesCount parameter
     *
     * @param fileInputStream image file stream
     * @param pageIndex pages index to be read
     * @param totalPagesCount return the total pages count found in the file
     * @return return read BudfferedImage data
     */
    public static BufferedImage readImageFilePage(InputStream fileInputStream, int pageIndex, AtomicInteger totalPagesCount) {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must be >= 0");
        }

        if (totalPagesCount == null) {
            totalPagesCount = new AtomicInteger(-1);
        }

        try (ImageInputStream iis = ImageIO.createImageInputStream(new NonClosingInputStream(fileInputStream))) {
            Iterator<ImageReader> imgReaders = ImageIO.getImageReaders(iis); // Get the readers for the image input stream
            if (!imgReaders.hasNext()) {
                throw new IllegalArgumentException(IMAGE_FORMAT_NOT_SUPPORTED_MSG);
            }

            ImageReader reader = imgReaders.next(); // Get Reader
            try {
                // ignoreMetadata=true for speed; set seekForwardOnly=true if you only read forward
                reader.setInput(iis, /*seekForwardOnly*/ false, /*ignoreMetadata*/ true);

                int total = reader.getNumImages(true);
                totalPagesCount.set(total);

                if (pageIndex >= total) {
                    throw new IndexOutOfBoundsException("Failed to read image: pageIndex[" + pageIndex + "] >= totalImages[" + total + "]");
                }

                ImageReadParam param = reader.getDefaultReadParam();
                // Safety check: preflight huge images
                int w = reader.getWidth(pageIndex);
                int h = reader.getHeight(pageIndex);
                if ((long) w * h > 200_000_000L) {
                    throw new IllegalArgumentException("Failed to read image: image too large [" + w + "x" + h + "]");
                }

                return reader.read(pageIndex, param);
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException iax) {
            LOG.error("Failed to read image page[{}]: {}", pageIndex, iax.getMessage());
            throw iax;
        } catch (IOException e) {
            LOG.error("Failed to read image page[{}]: {}", pageIndex, e.getMessage());
            throw new RuntimeException(e);
        }
    }


    /**
     * Rotates image clockwise
     *
     * @param image image to rotate
     * @param rotationAngle angle in degrees (90, 180, 270 angles are supported)
     * @return rotated image
     */
    public static BufferedImage rotateImage(BufferedImage image, int rotationAngle) {
        if (rotationAngle == 0) {
            // Nothing to do
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        AffineTransform at = new AffineTransform();

        switch (rotationAngle) {
            case 90 -> at.translate(height, 0);
            case 270 -> at.translate(0, width);
            case 180 -> at.translate(width, height);
            default -> {
                return image;
            }
        }
        at.rotate(Math.toRadians(rotationAngle)); // As we use 90/180/270-degree rotation - it automatically uses optimized algorithm and does not use approximation
        AffineTransformOp ato = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return ato.filter(image, null);
    }


    /**
     * Performs horizontal and vertical flip of the image, if requested
     *
     * @param image Source image
     * @param flipHorizontal Whether a horizontal flip is required
     * @param flipVertical Whether vertical flip is required
     * @return Flipped image if horizontal or vertical flip is required, or original image otherwise
     */
    public static BufferedImage flipImage(BufferedImage image, boolean flipHorizontal, boolean flipVertical) {
        if (!flipHorizontal && !flipVertical) {
            return image;
        }
        AffineTransform at = new AffineTransform();
        at.scale(flipHorizontal ? -1 : 1, flipVertical ? -1 : 1);
        at.translate(flipHorizontal ? -image.getWidth() : 0, flipVertical ? -image.getHeight() : 0);
        AffineTransformOp ato = new AffineTransformOp(at, INTER_CUBIC);
        return ato.filter(image, null);
    }


    /**
     * Creates a new BufferedImage from the provided image, copying it with the provided transformation applied<br>
     * <b>IMPORTANT:</b> This method currently scales moving image too - i.e., if one of the transforms was supposed
     * to move an image by 100 pixels and a provided scale is 0.5, then the provided image will be moved 50 pixels
     *
     * @param image {@link BufferedImage} source image
     * @param transform {@link AffineTransform} image transformation that should be applied to image copy
     * @param transformScale Scale of the provided input image to the original image that was used to build a transform list
     * @return {@link BufferedImage} copied image with provided AffineTransform, applied to it
     */
    public static BufferedImage transform(BufferedImage image, List<AffineTransform> transform, double transformScale) {
        if (transform == null || transform.isEmpty()) {
            return image;
        }
        for (AffineTransform at : transform) {
            if (transformScale != 1) {
                // Provided image differs in scale from the one, that was used to build transforms - we need to properly adjust "translate" component
                // i.e., if image was 100x100 and transform "rotates image around center" (deskew) - it rotates around [50, 50], but for image of size 50x50 we need to rotate
                // around point [25, 25] - so we apply same scale (1/2) to "translate" component of affine transform matrix, that technically affects rotation center in this
                // example
                at = new AffineTransform(at.getScaleX(), at.getShearY(), at.getShearX(), at.getScaleY(), at.getTranslateX() * transformScale, at.getTranslateY() * transformScale);
            }
            AffineTransformOp ato = new AffineTransformOp(at, INTER_CUBIC);
            BufferedImage bi = new BufferedImage(image.getColorModel(), image.getRaster().createCompatibleWritableRaster(), image.isAlphaPremultiplied(), null);
            if ((at.getType() & AffineTransform.TYPE_MASK_SCALE) != 0 || (at.getType() & AffineTransform.TYPE_QUADRANT_ROTATION) != 0) {
                // Apply transform into new image
                image = ato.filter(image, null);
            } else {
                // Apply transform in-place
                ato.filter(image, bi);
                image = bi;
            }
        }
        return image;
    }


    //
    // Static helper methods ----------------------------------------------------------------------
    //


    /**
     * @param w Width of the image
     * @param h Height of the image
     * @return String representation of provided image dimensions in format "WIDTHxHEIGHT"
     */
    public static String formatDimensions(int w, int h) {
        return MessageFormat.format("{0,number,#}x{1,number,#}", w, h);
    }


    /**
     * Returns Rectangle, representing image/page with dimensions, as specified by {@link #formatDimensions(int, int)}.
     * The returned rectangle will have X and Y coordinates set to 0.
     *
     * @param formattedDimensions String representation of the image/page dimensions, as returned by {@link #formatDimensions(int, int)}
     * @return Rectangle representing image/page dimensions
     */
    public static Rectangle getDimensionsFromString(String formattedDimensions) {
        String[] sizes = formattedDimensions.split("x");
        int w = Integer.parseInt(sizes[0]);
        int h = Integer.parseInt(sizes[1]);
        return new Rectangle(0, 0, w, h);
    }


    /**
     * Checks, if image/page with dimensions, specified according to {@link #formatDimensions(int, int)}, exceeds a provided resolution
     * threshold and therefore source image require downscaling
     *
     * @param dimensions Image/page dimensions
     * @param threshold Resolution threshold
     * @return True if downscaling is required, false otherwise
     */
    public static boolean isDownscalingRequiredByResolutionThreshold(String dimensions, double threshold) {
        Rectangle pageRect = ImageProcessor.getDimensionsFromString(dimensions);
        return pageRect.getArea() >= threshold;
    }


    public static double getPageScaleToFitResolution(String dimensions, double resolution) {
        Rectangle pageRect = ImageProcessor.getDimensionsFromString(dimensions);
        // Scale is applied to both width and height, so to reduce actual resolution, for example, 4 times, we need to reduce both width and height 2 times
        // Reduce scale by 1% to ensure we fit into quota
        return Math.sqrt(resolution / pageRect.getArea()) - 0.01;
    }


    /**
     * Creates a copy (independent) of the provided image
     *
     * @param source Source image to be copied
     * @return Deep copy of the source image with offset added
     */
    public static BufferedImage deepCopy(BufferedImage source) {
        return deepCopyWithOffset(source, 0, 0, 0, 0);
    }


    /**
     * Creates copy (independent) of the provided image, adding requested offsets on all sides
     *
     * @param source Source image to be copied
     * @param left Offset from the left side
     * @param right Offset from the right side
     * @param top Offset from top
     * @param bottom Offset from bottom
     * @return Deep copy of the source image with offset added
     */
    public static BufferedImage deepCopyWithOffset(BufferedImage source, int left, int right, int top, int bottom) {
        left = Math.max(left, 0);
        right = Math.max(right, 0);
        top = Math.max(top, 0);
        bottom = Math.max(bottom, 0);
        int newWidth = source.getWidth() + left + right;
        int newHeight = source.getHeight() + top + bottom;

        ColorModel cm = source.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = source.getRaster().createCompatibleWritableRaster(newWidth, newHeight);
        BufferedImage copy = new BufferedImage(cm, raster, isAlphaPremultiplied, null);
        Graphics2D g2d = copy.createGraphics();
        g2d.drawImage(source, left, top, null);
        g2d.dispose();
        return copy;
    }


    public static BufferedImage getSubimage(BufferedImage image, Rectangle area) {
        return image.getSubimage((int) area.getX(), (int) area.getY(), (int) area.getWidth(), (int) area.getHeight());
    }


    /**
     * Resizes provided image to specified dimensions
     *
     * @param image Image to be resized
     * @param newWidth New width of the image
     * @param newHeight New height of the image
     */
    private static void resize(Mat image, int newWidth, int newHeight) {
        Size newSize = null;
        try {
            newSize = new Size(newWidth, newHeight);
            opencv_imgproc.resize(image, image, newSize);
        } finally {
            CvUtils.close(newSize);
        }
    }


    /**
     * Resizes the provided image to a specified scale and returns a new BufferedImage
     *
     * @param image Image to be resized
     * @param scale Scale factor for resizing
     * @return Resized BufferedImage
     */
    public static BufferedImage resize(BufferedImage image, double scale) {
        // TODO: Consider replacing with AffineTransform for stability and cleanup. Should be properly tested due to rounding approach used here for new size
        long newWidthLong = Math.max(1L, Math.round(image.getWidth() * scale));
        long newHeightLong = Math.max(1L, Math.round(image.getHeight() * scale));

        if (newWidthLong > Integer.MAX_VALUE || newHeightLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Resized image dimensions are too large for requested scale '" + scale + "': " + newWidthLong + "x" + newHeightLong);
        }

        return resize(image, (int) newWidthLong, (int) newHeightLong);
    }


    /**
     * Resizes the provided image to specified dimensions and returns a new BufferedImage
     *
     * @param image Image to be resized
     * @param newWidth New width of the image
     * @param newHeight New height of the image
     * @return Resized BufferedImage
     */
    public static BufferedImage resize(BufferedImage image, int newWidth, int newHeight) {
        ColorModel cm = image.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = image.getRaster().createCompatibleWritableRaster(newWidth, newHeight);
        BufferedImage copy = new BufferedImage(cm, raster, isAlphaPremultiplied, null);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(image, 0, 0, newWidth, newHeight, null);
        graphics.dispose();
        return copy;
    }


    /**
     * Creates new BufferedImage, drawing first, and then second provided image on it<br>
     * <b>IMPORTANT:</b> resulting image color model is taken from the first image
     *
     * @param first First image to be drawn
     * @param second Second image to be drawn on top of the first one
     * @return Merged BufferedImage
     */
    public static BufferedImage merge(BufferedImage first, BufferedImage second) {
        int width = Math.max(first.getWidth(), second.getWidth());
        int height = Math.max(first.getHeight(), second.getHeight());
        ColorModel cm = first.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = first.getRaster().createCompatibleWritableRaster(width, height);
        BufferedImage mergeResult = new BufferedImage(cm, raster, isAlphaPremultiplied, null);
        Graphics2D graphics = mergeResult.createGraphics();
        graphics.drawImage(first, 0, 0, first.getWidth(), first.getHeight(), null);
        graphics.drawImage(second, 0, 0, second.getWidth(), second.getHeight(), null);
        graphics.dispose();
        return mergeResult;
    }


    private static void morphErode(Mat image, int kernelW, int kernelH) {
        Size kernelSize = null;
        Point kernelCenter = null;
        Mat kernel = null;
        try {
            kernelSize = new Size(kernelW, kernelH);
            kernelCenter = new Point(-1, -1);
            kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, kernelSize);
            opencv_imgproc.erode(image, image, kernel, kernelCenter, 1, opencv_core.BORDER_CONSTANT, AbstractScalar.BLACK);
        } finally {
            CvUtils.close(kernelSize);
            CvUtils.close(kernelCenter);
            CvUtils.close(kernel);
        }
    }


    /**
     * Dilates the image in place using a rectangular structuring element.
     *
     * @param image image to dilate (modified in place)
     * @param iterations number of dilation iterations
     * @param kernelW kernel width (prefer odd values for symmetric anchoring)
     * @param kernelH kernel height (prefer odd values for symmetric anchoring)
     */
    private static void morphDilate(Mat image, int iterations, int kernelW, int kernelH) {
        Size kernelSize = null;
        Point kernelCenter = null;
        Mat kernel = null;
        try {
            kernelSize = new Size(kernelW, kernelH);
            kernelCenter = new Point(-1, -1);  // (-1, -1) → OpenCV uses kernel center
            kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, kernelSize);

            // Use OpenCV's default border handling for morphology. It's designed correctly
            // for dilation (effectively -infinity on the border) and avoids edge artifacts.
            opencv_imgproc.dilate(image, image, kernel, kernelCenter, iterations,
                    opencv_core.BORDER_CONSTANT,
                    opencv_imgproc.morphologyDefaultBorderValue());
        } finally {
            CvUtils.close(kernel);           // getStructuringElement returns a real Mat — must close
            CvUtils.close(kernelCenter);     // harmless; Point is lightweight
            CvUtils.close(kernelSize);       // harmless; Size is lightweight
        }
    }


    /**
     * Locates contours on the image
     *
     * @param image Image to be processed
     * @return List of detected contours as rectangles
     */
    private static List<Rectangle> findContours(Mat image) {
        List<Rectangle> contoursList = new ArrayList<>();
        MatVector pageContours = null;
        Mat hierarchy = null;
        try {
            pageContours = new MatVector();
            hierarchy = new Mat();
            opencv_imgproc.findContours(image, pageContours, hierarchy, opencv_imgproc.RETR_LIST, opencv_imgproc.CHAIN_APPROX_SIMPLE);
            Arrays.stream(pageContours.get())
                    .map(ImageProcessor::getBoundingRect)
                    .forEach(contoursList::add);
        } finally {
            CvUtils.close(hierarchy);
            CvUtils.close(pageContours);
        }

        return contoursList;
    }


    /**
     * Returns the number of non-white pixels on the provided image
     *
     * @param bi Image to be processed
     * @return Number of non-white pixels
     */
    public static int countNonWhitePixelsNative(BufferedImage bi) {
        int nonWhitePixelsCount = 0;
        for (int i = 0; i < bi.getWidth(); i++) {
            for (int j = 0; j < bi.getHeight(); j++) {
                Color color = new Color(bi.getRGB(i, j));
                if (!color.equals(Color.WHITE)) {
                    nonWhitePixelsCount++;
                }
            }
        }
        return nonWhitePixelsCount;
    }


    private static Rectangle fromOpenCVRect(Rect r) {
        return new Rectangle(r.x(), r.y(), r.width(), r.height());
    }


    private static Rect toOpenCVRect(Rectangle r) {
        return new Rect((int) r.getX(), (int) r.getY(), (int) r.getWidth(), (int) r.getHeight());
    }


    protected static boolean isDeskewEnabled() {
        return getMaxSkewDegree() != 0;
    }


    protected static Range<Double> getAllowedSkewDegree() {
        double min = getMinSkewDegree();
        double max = getMaxSkewDegree();
        if (min >= max) {
            return Range.of(MIN_SKEW_DEGREE_DEFAULT, MAX_SKEW_DEGREE_DEFAULT);
        }
        return Range.of(min, max);
    }


    protected static double getMaxSkewDegree() {
        String configuredDegreeStr = System.getProperty(ELDOC_JVM_OPTION_MAX_SKEW_DEGREE);
        if (isBlank(configuredDegreeStr)) {
            return MAX_SKEW_DEGREE_DEFAULT;
        }
        try {
            double configuredDegree = Double.parseDouble(configuredDegreeStr);
            configuredDegree = Math.abs(configuredDegree);
            if (configuredDegree > MAX_CONFIGURABLE_SKEW_DEGREE) {
                return MAX_SKEW_DEGREE_DEFAULT;
            }
            return configuredDegree;
        } catch (Exception e) {
            LOG.warn("Error parsing string '{}' as Double for param: {}, using default value of {} instead. Error: {}", configuredDegreeStr, ELDOC_JVM_OPTION_MAX_SKEW_DEGREE, MAX_SKEW_DEGREE_DEFAULT, e.getMessage());
            return MAX_SKEW_DEGREE_DEFAULT;
        }
    }


    protected static double getMinSkewDegree() {
        String configuredDegreeStr = System.getProperty(ELDOC_JVM_OPTION_MIN_SKEW_DEGREE);
        if (isBlank(configuredDegreeStr)) {
            return MIN_SKEW_DEGREE_DEFAULT;
        }
        try {
            double configuredDegree = Double.parseDouble(configuredDegreeStr);
            configuredDegree = Math.abs(configuredDegree);
            if (configuredDegree > MAX_CONFIGURABLE_SKEW_DEGREE) {
                return MIN_SKEW_DEGREE_DEFAULT;
            }
            return configuredDegree;
        } catch (Exception e) {
            LOG.warn("Error parsing string '{}' as Double for param: {}, using default value of {} instead. Error: {}", configuredDegreeStr, ELDOC_JVM_OPTION_MIN_SKEW_DEGREE, MIN_SKEW_DEGREE_DEFAULT, e.getMessage());
            return MIN_SKEW_DEGREE_DEFAULT;
        }
    }


    /**
     * In case a system-wide property is set to 0, it basically disables the page rotation fixing algorithm
     *
     * @return Whether page rotation fixing is enabled
     */
    protected static boolean isFixRotationEnabled() {
        return getMaxRotateDegree() != 0;
    }


    /**
     * Returns max rotation degree which can be applied to the pages to fix rotation
     *
     * @return system-wide defined property (in case any defined) or 270 by default
     */
    protected static int getMaxRotateDegree() {
        String configuredDegreeStr = System.getProperty(ELDOC_JVM_OPTION_MAX_ROTATE_DEGREE);
        return switch (configuredDegreeStr) {
            case "0" -> 0;
            case "90" -> 90;
            case "180" -> 180;
            case null, default -> 270; // Default
        };
    }


    private void cleanup() {
        _imagePages.clear();
        _pageTransforms.clear();
        _detectedRotation.clear();
    }


    /**
     * Initializes image codecs
     */
    public static void init() {
        bootstrapImageIO();
        reorderImageIOCodecs();
    }


    /**
     * Make sure everything is actually registered first
     */
    private static void bootstrapImageIO() {
        // Discover classpath SPIs
        ImageIO.scanForPlugins();
        // Disk cache helps with big images and multipage TIFFs
        ImageIO.setUseCache(true);
        // Optional: ImageIO.setCacheDirectory(new File("/var/tmp"));
    }


    /**
     * Explicitly sets TwelveMonkeys image reader/writer to have higher priority than JAI (which indirectly included by com.github.jai-imageio:jai-imageio-core)
     */
    private static void reorderImageIOCodecs() {
        LOG.debug("Reordering ImageIO plugins...");
        IIORegistry registry = IIORegistry.getDefaultInstance();

        ImageReaderSpi jaiReaderProvider = lookupProviderByName(registry, com.github.jaiimageio.impl.plugins.tiff.TIFFImageReaderSpi.class.getName());
        ImageReaderSpi tmReaderProvider = lookupProviderByName(registry, com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi.class.getName());
        ImageWriterSpi jaiWriterProvider = lookupProviderByName(registry, com.github.jaiimageio.impl.plugins.tiff.TIFFImageWriterSpi.class.getName());
        ImageWriterSpi tmWriterProvider = lookupProviderByName(registry, com.twelvemonkeys.imageio.plugins.tiff.TIFFImageWriterSpi.class.getName());

        if (jaiReaderProvider != null && tmReaderProvider != null) {
            LOG.trace("Changing ImageIO TIFF Reader plugins priority...");
            registry.setOrdering(ImageReaderSpi.class, tmReaderProvider, jaiReaderProvider);
        }
        if (jaiWriterProvider != null && tmWriterProvider != null) {
            LOG.trace("Changing ImageIO TIFF Writer plugins priority...");
            registry.setOrdering(ImageWriterSpi.class, tmWriterProvider, jaiWriterProvider);
        }
        LOG.debug("Reordering ImageIO plugins done");
    }


    private static <T> T lookupProviderByName(final ServiceRegistry registry, final String providerClassName) {
        try {
            return (T) registry.getServiceProviderByClass(Class.forName(providerClassName));
        } catch (ClassNotFoundException ignore) {
            return null;
        }
    }


    //
    // Inner Classes ------------------------------------------------------------------------------
    //

    /**
     * Minimal wrapper: do not close the underlying stream when close() is called.
     */
    private static final class NonClosingInputStream extends FilterInputStream {

        NonClosingInputStream(InputStream in) {
            super(in);
        }


        @Override
        public void close() {
            /* no-op: caller owns the underlying stream */
        }
    }


    /**
     * Prevent closing the caller's OutputStream when the ImageOutputStream is closed.
     */
    private static final class NonClosingOutputStream extends FilterOutputStream {

        NonClosingOutputStream(OutputStream out) {
            super(out);
        }


        @Override
        public void close() {
            /* no-op; caller owns 'out' */
        }


        @Override
        public void flush() throws IOException {
            out.flush();
        }
    }

}
