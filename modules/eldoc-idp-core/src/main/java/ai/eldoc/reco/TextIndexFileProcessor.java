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

import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.ai.service.AiService;
import ai.eldoc.model.PropertyAwareModel;
import ai.eldoc.model.PropertyConfig;
import ai.eldoc.ocr.*;
import ai.eldoc.pdf.PdfUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import static java.text.MessageFormat.format;


/**
 * TextIndex file processor
 */
public class TextIndexFileProcessor implements AutoCloseable, PropertyAwareModel {

    private static final Logger LOG = LoggerFactory.getLogger(TextIndexFileProcessor.class);

    public static final List<String> SUPPORTED_FILE_TYPES = List.of("jpeg", "jpg", "tiff", "tif", "bmp", "png", "webp", "pdf");

    public static final String PROP_AI_ENABLED = "aiEnbld";
    public static final String REQUEST_BLOCK_ID_DEFAULT = "TI";

    private final ImageProcessor _imageProcessor;
    private OcrService _ocrService;

    private OcrEngineVLImpl _aiOcrEngine;

    private final List<String> _languages;

    private InputStream _fileInputStream;
    private PDDocument _sourceDocument;
    private int _pageStart;
    private int _pageEnd;
    private boolean _isPdf;

    private AiRequestContext _billingRequestContext;

    private final Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public TextIndexFileProcessor() {
        _properties = new HashMap<>();
        _languages = new ArrayList<>();
        _imageProcessor = new ImageProcessor();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


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
     * @param pageStart Start the page of the file to be loaded (inclusive, 0-based)
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
        _isPdf = PdfUtils.isPdf(fileNameOrPath);

        // Preload file with respective logic
        if (_isPdf) {
            // Read PDF
            _sourceDocument = PdfUtils.loadPDF(fileInputStream);
            _pageEnd = pageEnd == -1 ? _sourceDocument.getNumberOfPages() : pageEnd;
        } else {
            // Preload other image type files
            _fileInputStream = fileInputStream;
            _pageEnd = pageEnd == -1 ? ImageProcessor.getNumberOfPages(_fileInputStream) : pageEnd;
            _fileInputStream.reset(); // Stream should be reset
        }
    }


    /**
     * Performs preprocessing of each page of the provided document and generates a preprocessed file that contains all the pages
     *
     * @return Per-page text content of the file with the number of pages that required OCR and number of pages, for which OCR was performed
     * @throws IOException in case of error, while trying to read the page content
     */
    public Pair<List<String>, Integer> preprocessAndGetText() throws IOException {
        if (isUseAiOcrEngine()) {
            LOG.debug("Using VL OCR engine for text extraction");
        }
        List<String> ocrResult = new ArrayList<>();

        int pagesWithOcrCount = 0;

        // Step 2: Perform actual OCR
        try {
            for (int pageIdx = _pageStart; pageIdx < _pageEnd; pageIdx++) {
                LOG.trace("Page {} processing start", pageIdx + 1);
                Pair<String, Boolean> pageText = preprocessAndGetPageText(pageIdx);
                ocrResult.add(pageText.getLeft());
                if (pageText.getRight()) {
                    pagesWithOcrCount++;
                }
            }
        } finally {
            // Step 3: Bill actually OCRed pages and release the rest of the reserved amount
            LOG.trace("Total processed pages: {}, with OCR: {}", _pageEnd - _pageStart, pagesWithOcrCount);
        }

        return Pair.of(ocrResult, pagesWithOcrCount);
    }


    /**
     * Performs preprocessing of a specified type for the requested page and returns OCR text of the page.<br>
     * <b>IMPORTANT:</b> In case calling this method directly, controlling processed pages indexes should be handled additionally
     * to prevent processing the same page multiple times or processing pages in incorrect order
     *
     * @param pageIdx Index of the page to be processed (0-based)
     * @return Preprocessing result for page ({@link OcrResult} text and flag, whether OCR was performed)
     * @throws IOException in case of error, while trying to read the page content
     */
    private Pair<String, Boolean> preprocessAndGetPageText(int pageIdx) throws IOException {
        String result;
        boolean isMachineGenerated = _isPdf && !getProperty(PropertyConfig.IDP_IGNORE_TEXT_LAYER, false) && PdfUtils.isTextBased(_sourceDocument, pageIdx);
        if (isMachineGenerated && !isUseAiOcrEngine()) {
            // In case this is a PDF, need to do a forcePreprocessing, and this is machine-generated page - process it as is
            result = PdfUtils.getText(_sourceDocument, pageIdx).replaceAll(" +", " ");
        } else {
            // In case this is not PDF or PDF that requires rendering by setting IgnoreTextLayer=TRUE OR, this PDF doesn't contain enough text to be treated as TextBased PDF - process it as images
            LOG.trace("Extracting page {} image", pageIdx + 1);
            if (_isPdf) {
                Pair<BufferedImage, Boolean> docPageData = PdfUtils.renderOrExtractDocPageImage(_sourceDocument, pageIdx, PdfUtils.IMAGE_RENDER_BEST_DPI, true);
                _imageProcessor.loadPage(docPageData.getLeft());
            } else {
                _imageProcessor.loadPage(ImageProcessor.readImageFilePage(_fileInputStream, pageIdx, null));
                _fileInputStream.reset(); // InputStream need to be reset after use
            }

            _imageProcessor.preprocess();

            if (isUseAiOcrEngine()) {
                result = getAiOcrEngine().processImageMarkdown(_imageProcessor.getPages().getFirst(), false);
            } else {
                result = getPageOcrText(_imageProcessor.getPages().getFirst()).getOcrBlock().getText();
            }
        }
        return Pair.of(result, !isMachineGenerated);
    }


    private OcrResult getPageOcrText(BufferedImage pageImgPreprocessed) throws IOException {
        return getOcrService().processImage(pageImgPreprocessed, true);
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
        } catch (Exception e) {
            LOG.error("Error while performing close()", e);
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


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


    @Override
    public Map<String, Object> getProperties() {
        return _properties;
    }


    public boolean isUseAiOcrEngine() {
        Boolean isVlEnabled = getProperty(PROP_AI_ENABLED, false);
        return isVlEnabled && AiService.getInstance().isVisionAvailable();
    }


    private OcrEngineVLImpl getAiOcrEngine() {
        if (_aiOcrEngine == null) {
            _aiOcrEngine = AiService.getInstance().createVLOcrEngine(getBillingRequestContext());
        }
        return _aiOcrEngine;
    }


    public AiRequestContext getBillingRequestContext() {
        return _billingRequestContext;
    }


    public void setBillingRequestContext(AiRequestContext billingRequestContext) {
        _billingRequestContext = billingRequestContext;
    }
}
