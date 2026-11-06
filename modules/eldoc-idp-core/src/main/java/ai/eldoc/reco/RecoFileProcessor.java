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

import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.PropertyAwareModel;
import ai.eldoc.model.PropertyConfig;
import ai.eldoc.model.reco.RecoFileMeta;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.ocr.OcrService;
import ai.eldoc.pdf.PageOrientationDetector;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.util.MimeType;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static ai.eldoc.ocr.OcrService.PROP_REQUEST_BLOCK_ID;
import static java.text.MessageFormat.format;


/**
 * Reco File Processor Class contains methods for processing recognition (source) files
 */
public final class RecoFileProcessor implements AutoCloseable, PropertyAwareModel {

    private static final Logger LOG = LoggerFactory.getLogger(RecoFileProcessor.class);

    public static final List<String> SUPPORTED_FILE_TYPES = List.of("jpeg", "jpg", "tiff", "tif", "bmp", "png", "webp", "pdf");

    /**
     * Default size of the PDF page that used to create page for PORTRAIT images after preprocessing
     */
    private static final PDRectangle PAGE_SIZE_FOR_IMAGE_BASED_PAGES_PORTRAIT = PDRectangle.A4;
    /**
     * Default size of the PDF page that used to create page for LANDSCAPE images after preprocessing
     */
    private static final PDRectangle PAGE_SIZE_FOR_IMAGE_BASED_PAGES_LANDSCAPE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    /**
     * Controls DPI, that is used for rendering Source image, used in a preprocessed document<br>
     * Applied only during preprocessing for generating image for embedding to PDF if the source PDF document page was rendered and not extracted
     */
    private static final int PREPROCESSING_RENDERED_SOURCE_IMAGE_DPI = 200;

    private static final String REQUEST_BLOCK_ID_DEFAULT = "RECO_PPRD";

    public enum PreprocessType {
        DEFAULT
    }

    private final List<String> _languages;
    private final ImageProcessor _imageProcessor;
    private PDDocument _sourceDocument;
    private PDDocument _preprocessedDocument;
    private String _sourceFileName;
    private String _preprocessedFileName;
    private OcrService _ocrService;
    private final RecoFileMeta _sourceMeta;
    private final RecoFileMeta _preprocessedMeta;
    private InputStream _fileInputStream;
    private int _pageStart;
    private int _pageEnd;
    private boolean _isPdf;
    private boolean _isForcePreprocessing;
    private boolean _isDebugMode;

    private final Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFileProcessor(RecoFileMeta recoFileMeta) {
        _properties = new HashMap<>();
        _languages = new ArrayList<>();
        _sourceMeta = recoFileMeta; // Source file meta
        _preprocessedMeta = new RecoFileMeta(); // Init preprocessed file meta
        _imageProcessor = new ImageProcessor();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public RecoFileProcessor setProperties(Map<String, Object> properties) {
        _properties.putAll(properties);
        _imageProcessor.setProperties(properties);
        return this;
    }


    public static boolean isCustomPreprocessingRequired(Map<String, Object> properties) {
        return ImageProcessor.isCustomPreprocessingRequired(properties) || OcrService.isCustomPreprocessingRequired(properties);
    }


    /**
     * Loads file content assuming it is either of PDF or another image format
     *
     * @param fileNameOrPath Name of the file to be loaded
     * @param fileInputStream Input stream of the file to be loaded
     * @throws java.io.IOException in case of error, while trying to read the file content
     */
    public void loadFile(String fileNameOrPath, InputStream fileInputStream) throws IOException {
        loadFile(fileNameOrPath, fileInputStream, 0, -1);
    }


    /**
     * Loads file content assuming it is either of PDF or another image format
     *
     * @param fileNameOrPath Name of the file to be loaded
     * @param fileInputStream Input stream of the file to be loaded
     * @param pageStart First page of the file to be loaded (inclusive, 0-based)
     * @param pageEnd End page of the file to be loaded (exclusive, 0-based), value of -1 will process pages till the end page in the given input document
     * @throws java.io.IOException in case of error, while trying to read the file content
     */
    public void loadFile(String fileNameOrPath, InputStream fileInputStream, int pageStart, int pageEnd) throws IOException {
        // Check whether we support the file type
        if (!isFileSupported(fileNameOrPath)) {
            LOG.error("File type not supported: {}", fileNameOrPath);
            throw new IllegalArgumentException(format("File type not supported: {0}", fileNameOrPath));
        }

        _pageStart = pageStart;
        setSourceFileName(FilenameUtils.getName(fileNameOrPath));
        _isPdf = PdfUtils.isPdf(fileNameOrPath);

        // Preload file with respective logic
        if (_isPdf) {
            // Read PDF
            _sourceDocument = PdfUtils.loadPDF(fileInputStream);
        } else {
            // Preload other image type files
            _fileInputStream = fileInputStream;
        }
        _pageEnd = pageEnd == -1 ? _sourceMeta.getPageCount() : pageEnd;
    }


    public static int getNumberOfPages(String fileNameOrPath, InputStream fileInputStream) throws IOException {
        // Check whether we support the file type
        if (!isFileSupported(fileNameOrPath)) {
            LOG.error("File type not supported: {}", fileNameOrPath);
            throw new IllegalArgumentException(format("File type not supported: {0}", fileNameOrPath));
        }

        if (PdfUtils.isPdf(fileNameOrPath)) {
            // Process file as PDF document
            try (PDDocument document = PdfUtils.loadPDF(fileInputStream)) {
                return document.getNumberOfPages();
            }
        } else {
            // Process file as image
            try {
                return ImageProcessor.getNumberOfPages(fileInputStream);
            } catch (Exception ex) {
                LOG.error("Can not open image file or read number of pages from the file: {}", fileNameOrPath);
                throw new IllegalArgumentException(format("Can not open image file or read number of pages from the file: {0}", fileNameOrPath));
            }
        }
    }


    /**
     * Performs default preprocessing of each page of the provided document and generates a preprocessed file that contains all the pages
     *
     * @throws IOException in case of error, while trying to read the page content
     * @throws TimeoutException if file preprocessing timed out
     */
    public void preprocess() throws IOException, TimeoutException {
        preprocess(PreprocessType.DEFAULT);
    }


    /**
     * Performs preprocessing of each page of the provided document and generates a preprocessed file that contains all the pages
     *
     * @param preprocessType Type of the preprocessing to be performed
     * @throws IOException in case of error, while trying to read the page content
     * @throws TimeoutException if file preprocessing timed out
     */
    private void preprocess(PreprocessType preprocessType) throws IOException, TimeoutException {
        for (int pageIdx = _pageStart; pageIdx < _pageEnd; pageIdx++) {
            preprocessAndAddPage(pageIdx, preprocessType);
        }
        getPreprocessedMeta().setPageCount(_pageEnd - _pageStart);
        getPreprocessedMeta().setProperty(RecoFileMeta.IDP_AUTO_CLASSIFICATION, getSourceMeta().getProperty(RecoFileMeta.IDP_AUTO_CLASSIFICATION, false));
    }


    /**
     * Performs preprocessing of a specified type for the requested page and adds it to the current preprocessed document.<br>
     * <b>IMPORTANT:</b> In case calling this method directly, controlling processed pages indexes should be handled additionally
     * to prevent processing the same page multiple times or processing pages in incorrect order
     *
     * @param pageIdx Index of the page to be preprocessed and OCRed, if needed
     * @param preprocessType Type of the preprocessing to be performed
     * @throws IOException in case of error, while trying to read the page content
     * @throws TimeoutException if file preprocessing timed out
     */
    public void preprocessAndAddPage(int pageIdx, PreprocessType preprocessType) throws IOException, TimeoutException {
        PDPage preprocessedPage;
        String sourcePageDimensions;
        initPreprocessedFileIfNeeded();
        if (_isPdf && !getProperty(PropertyConfig.IDP_IGNORE_TEXT_LAYER, false) && PdfUtils.isTextBased(_sourceDocument, pageIdx)) {
            // In case this is a PDF, need to do a forcePreprocessing, and this is machine-generated page - process it as is
            preprocessedPage = PdfUtils.clonePageByIdx(_sourceDocument, pageIdx, true);
            int detectedPageOrientation = new PageOrientationDetector().getPageOrientation(_sourceDocument, pageIdx);
            PdfUtils.setPageRotation(preprocessedPage, detectedPageOrientation);
            RecoPdfUtils.setPageProperties(preprocessedPage, true, false);
            sourcePageDimensions = PdfUtils.getPageDimension(preprocessedPage);
        } else {
            // In case this is not PDF or PDF that requires rendering by setting IgnoreTextLayer=TRUE OR, this PDF doesn't contain enough text to be treated as TextBased PDF - process it as images
            boolean isRenderedPdfPage = false;
            if (_isPdf) {
                Pair<BufferedImage, Boolean> docPageData = PdfUtils.renderOrExtractDocPageImage(_sourceDocument, pageIdx, PdfUtils.IMAGE_RENDER_BEST_DPI, true);
                _imageProcessor.loadPage(docPageData.getLeft());
                isRenderedPdfPage = docPageData.getRight();
            } else {
                _imageProcessor.loadPage(ImageProcessor.readImageFilePage(_fileInputStream, pageIdx, null));
                _fileInputStream.reset(); // InputStream needs to be reset after use
            }
            BufferedImage originalPageImage = _imageProcessor.getPages().getFirst();
            sourcePageDimensions = _imageProcessor.getPagesDimensions().getFirst();

            _imageProcessor.preprocess();

            BufferedImage preprocessedPageImage = _imageProcessor.getPages().getFirst();

            BufferedImage pageImageToEmbed;
            if (_isDebugMode) {
                pageImageToEmbed = preprocessedPageImage;
            } else {
                double transformsScale = 1;
                List<AffineTransform> pageTransform = _imageProcessor.getPageTransforms().getFirst();
                if (isRenderedPdfPage) {
                    originalPageImage = PdfUtils.renderOrExtractDocPageImage(_sourceDocument, pageIdx, PREPROCESSING_RENDERED_SOURCE_IMAGE_DPI, true).getLeft();
                    // Transforms include rotating around center of the image, which is changed due to rendering with other DPI
                    transformsScale = PREPROCESSING_RENDERED_SOURCE_IMAGE_DPI / (double) PdfUtils.IMAGE_RENDER_BEST_DPI;
                }
                pageImageToEmbed = ImageProcessor.transform(originalPageImage, pageTransform, transformsScale);
            }

            Rectangle targetPageRect = null;
            if (_isPdf) {
                targetPageRect = PdfUtils.getPageDimensionRect(_sourceDocument.getPage(pageIdx));
                int detectedPageRotation = _imageProcessor.getPageRotation().getFirst();
                if (detectedPageRotation % 180 != 0) {
                    // Image was rotated 90 or 270 degree due to page orientation, detected by OCR engine - rotate initial page rect correspondingly
                    targetPageRect = new Rectangle(0, 0, targetPageRect.getHeight(), targetPageRect.getWidth());
                }
            }

            // Add preprocessed images and text layer to the preprocessed document
            preprocessedPage = createPreprocessedPage(getPreprocessedFile(), targetPageRect, preprocessedPageImage, pageImageToEmbed, true, isRenderedPdfPage);
            RecoPdfUtils.setPageProperties(preprocessedPage, false, true);
        }
        // Add page to the target pre-processed document
        addPreprocessedPageAndUpdateMeta(preprocessedPage, sourcePageDimensions);
    }


    /**
     * Checks if a preprocessed document was already initialized, and if not - initializes it
     */
    void initPreprocessedFileIfNeeded() {
        if (getPreprocessedFile() == null) {
            _preprocessedDocument = PdfUtils.createPDFWithProps();
            RecoPdfUtils.addPreprocessedProperties(_preprocessedDocument);
            // Set properties used for preprocessing
            getPreprocessedMeta().setLangs(getLanguages());
            getPreprocessedMeta().setForcePreprop(isForcePreprocessing());
        }
    }


    /**
     * Adds the provided preprocessed page to a preprocessed document and updates source and preprocessed file meta
     *
     * @param preprocessedPage Page, that should be added to the preprocessed document
     * @param sourcePageDimensions Dimensions of the source page that was used for preprocessing
     */
    void addPreprocessedPageAndUpdateMeta(PDPage preprocessedPage, String sourcePageDimensions) {
        getPreprocessedFile().addPage(preprocessedPage);
        getSourceMeta().getPagesDimensions().add(sourcePageDimensions);
        getPreprocessedMeta().setPageCount(getPreprocessedMeta().getPageCount() + 1);
        getPreprocessedMeta().getPagesDimensions().add(PdfUtils.getPageDimension(preprocessedPage));
    }


    public void addDocumentToSource(PDDocument documentToBeAdded, RecoFileMeta documentToBeAddedMeta) {
        _preprocessedDocument = PdfUtils.cloneDocument(_sourceDocument, false);
        PdfUtils.importDocument(_preprocessedDocument, documentToBeAdded);

        _preprocessedMeta.setPageCount(_sourceMeta.getPageCount() + documentToBeAdded.getNumberOfPages());
        _preprocessedMeta.getPagesDimensions().addAll(_sourceMeta.getPagesDimensions());
        _preprocessedMeta.getPagesDimensions().addAll(documentToBeAddedMeta.getPagesDimensions());

    }


    /**
     * Creates {@link PDDocument} from provided images and adds required IDP-related properties
     *
     * @param images List of images to be converted to PDF
     * @throws IOException in case of error, while trying to read the page content
     * @throws TimeoutException if file preprocessing timed out
     */
    public void wrapCustomProcessorFileImage(List<BufferedImage> images) throws IOException, TimeoutException {
        _imageProcessor.loadPages(images);
        _preprocessedDocument = PdfUtils.createPDFFromImages(images);
        RecoPdfUtils.addPreprocessedProperties(_preprocessedDocument);
        // Update Preprocessed File Meta
        _preprocessedMeta.setPagesDimensions(_imageProcessor.getPagesDimensions());
        _preprocessedMeta.setPageCount(_imageProcessor.getPages().size());

        // Update preprocessed document pages to include proper metadata
        for (int pageIdx = 0; pageIdx < _preprocessedDocument.getNumberOfPages(); pageIdx++) {
            RecoPdfUtils.setPageProperties(_preprocessedDocument.getPage(pageIdx), false, false);
        }
    }


    /**
     * Creates a preprocessed PDF-page (from the given page image) which is to be added to the target preprocessed PDF-file
     *
     * @param document Document, into which the page will be added
     * @param targetPageRect Target size of the new page
     * @param pageImgPreproc {@link BufferedImage} with page image, preprocessed for OCR
     * @param pageImgOrig {@link BufferedImage}, that was originally submitted for page
     * @param withOcr If {@code true} - performs page OCR and adds recognized text to the page
     * @param lossy Whether to use Lossless or Lossy compression when adding image to the PDF page
     * @return Created PDF page
     * @throws IOException in case of error, while trying to read the page content
     * @throws TimeoutException if file preprocessing timed out
     */
    PDPage createPreprocessedPage(PDDocument document, Rectangle targetPageRect, BufferedImage pageImgPreproc, BufferedImage pageImgOrig, boolean withOcr, boolean lossy) throws IOException, TimeoutException {
        // Create preprocessed page which is to be added to the target preprocessed document
        PDRectangle newPageRect;
        if (targetPageRect != null) {
            newPageRect = new PDRectangle((float) targetPageRect.getX(), (float) targetPageRect.getY(), (float) targetPageRect.getWidth(), (float) targetPageRect.getHeight());
        } else if (pageImgOrig.getWidth() > pageImgOrig.getHeight()) {
            newPageRect = PAGE_SIZE_FOR_IMAGE_BASED_PAGES_LANDSCAPE;
        } else {
            newPageRect = PAGE_SIZE_FOR_IMAGE_BASED_PAGES_PORTRAIT;
        }
        Pair<PDPage, Rectangle> newPageData = PdfUtils.createPDFPageFromImage(document, pageImgOrig, newPageRect, true, lossy);
        PDPage page = newPageData.getLeft();
        RecoPdfUtils.setPageProperties(page, false, withOcr);

        // Add text-layer to the page
        if (withOcr) {
            performPageOcr(document, page, pageImgPreproc, pageImgOrig, newPageData.getRight());
        }

        return page;
    }


    /**
     * Perform page OCR
     *
     * @param document {@link PDDocument} to which the page belongs
     * @param page {@link PDPage} to be OCR-ed
     * @param pageImgPreproc {@link BufferedImage} with page image, preprocessed for OCR
     * @param pageImgOrig {@link BufferedImage}, that was originally submitted for page
     * @param imageOnPageArea Actual area of the image on the page (after applying scaling/offset to fit page size)
     * @throws IOException in case of error, while trying to OCR the page content
     * @throws TimeoutException if file preprocessing timed out
     */
    private void performPageOcr(PDDocument document, PDPage page, BufferedImage pageImgPreproc, BufferedImage pageImgOrig, Rectangle imageOnPageArea) throws IOException, TimeoutException {
        getOcrService().processPDPage(document, page, pageImgPreproc, pageImgOrig, imageOnPageArea);
    }


    private OcrService getOcrService() {
        if (_ocrService == null) {
            // Do lazy init of the OCR Service
            _ocrService = new OcrService(getLanguages());
            _ocrService.setProperties(getProperties());
        }
        return _ocrService;
    }


    @Override
    public void close() {
        try {
            if (_ocrService != null) {
                _ocrService.close();
                _ocrService = null;
            }
            _imageProcessor.close();
            if (_sourceDocument != null) {
                _sourceDocument.close();
            }
            if (_preprocessedDocument != null) {
                _preprocessedDocument.close();
            }
        } catch (Exception e) {
            LOG.error("Error while performing close()", e);
        }
    }


    /**
     * Returns progress of the OCR processing
     *
     * @return Progress of the OCR processing, value of -1 means that OCR is not initialized yet
     */
    public int getOcrProgress() {
        if (_ocrService == null) {
            return -1;
        }
        return getOcrService().getProgress();
    }


    /**
     * Makes a check based on the file extension on whether the file is supported
     *
     * @param fileNameOrPath Name of the file to be checked
     * @return {@code true} if file is supported, {@code false} otherwise
     */
    public static boolean isFileSupported(String fileNameOrPath) {
        String ext = FilenameUtils.getExtension(fileNameOrPath.toLowerCase());
        return SUPPORTED_FILE_TYPES.contains(ext);
    }


    /**
     * Checks, if requested page number is within valid pages range
     *
     * @param pageIdx Page index to be checked
     * @return {@code true} if page index is within requested pages range, {@code false} otherwise
     */
    public boolean isValidPageIdx(int pageIdx) {
        return pageIdx >= _pageStart && pageIdx < _pageEnd;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns inline reference to the preprocessed document<br>
     * IMPORTANT: this reference will be invalidated once {@link #close} is called
     *
     * @return Preprocessed document
     */
    public PDDocument getPreprocessedFile() {
        return _preprocessedDocument;
    }


    public int getPagesToPreprocessCount() {
        return _pageEnd - _pageStart;
    }


    public byte[] getPreprocessedFileAsByteArray() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            _preprocessedDocument.save(out);
            return out.toByteArray();
        }
    }


    public void setSourceFileName(String sourceFileName) {
        _sourceFileName = sourceFileName;
        if (!PdfUtils.isPdf(sourceFileName)) {
            _preprocessedFileName = sourceFileName.concat(".pdf");
        } else {
            _preprocessedFileName = sourceFileName;
        }
    }


    public String getPreprocessedFileName() {
        return _preprocessedFileName;
    }


    public String getPreprocessedFileContentType() {
        return MimeType.APPLICATION_PDF;
    }


    public RecoFileMeta getSourceMeta() {
        return _sourceMeta;
    }


    public RecoFileMeta getPreprocessedMeta() {
        return _preprocessedMeta;
    }


    public List<String> getLanguages() {
        return _languages;
    }


    public void setLanguages(List<String> languages) {
        _languages.clear();
        if (languages == null || languages.isEmpty()) {
            _languages.add(OcrService.getDefaultLanguageCode());
            LOG.trace("No indexing languages defined, using '{}' by default", OcrService.getDefaultLanguageCode());
        } else {
            _languages.addAll(languages);
        }
    }


    private boolean isForcePreprocessing() {
        return _isForcePreprocessing;
    }


    /**
     * Set force-preprocessing properties and flag
     *
     * @param forcePreprocessing Whether to force preprocessing of the file, even if it is machine-generated
     */
    public void setForcePreprocessing(boolean forcePreprocessing) {
        if (forcePreprocessing) {
            _isForcePreprocessing = true;
            getProperties().put(PropertyConfig.IDP_OCR_MODE_SPARSE_TEXT, true);
            getProperties().put(PropertyConfig.IDP_IGNORE_TEXT_LAYER, true);
            _imageProcessor.getProperties().putAll(getProperties());
        } else {
            _isForcePreprocessing = false;
        }
    }


    public void setRequestBlockId(String requestBlockId) {
        setProperty(PROP_REQUEST_BLOCK_ID, requestBlockId);
    }


    /**
     * Enables debug mode for preprocessing. In debug mode, RecoFileProcessor: <br>
     * <ul>
     * <li>Embeds preprocessed, black&white images, as sent to OCR engine for pages that used OCR</li>
     * </ul>
     *
     * @param debugMode {@code true} to enable debug mode, {@code false} otherwise
     */
    public void setDebugMode(boolean debugMode) {
        _isDebugMode = debugMode;
    }


    @Override
    public Map<String, Object> getProperties() {
        return _properties;
    }
}
