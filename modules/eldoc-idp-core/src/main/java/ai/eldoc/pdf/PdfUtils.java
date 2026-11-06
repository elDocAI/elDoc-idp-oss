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
package ai.eldoc.pdf;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.ocr.OcrPdfUtils;
import ai.eldoc.ocr.OcrText;
import ai.eldoc.reco.ImageProcessor;
import ai.eldoc.util.RectangleUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.fontbox.ttf.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.*;
import org.apache.pdfbox.io.RandomAccessStreamCache.StreamCacheCreateFunction;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.*;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * PDF Converter Service
 */
public class PdfUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PdfUtils.class);

    private static final String ELDOC_JVM_OPTION_MAX_MEMORY_BYTES = "ai.eldoc.pdf.maxMemoryBytes";
    private static final String ELDOC_JVM_OPTION_TEXT_PDF_CHARACTERS_THRESHOLD = "ai.eldoc.pdf.textPdfCharThresh"; // Should be an integer value

    /**
     * Maximum memory to be used by PDFBox for document content buffers (on per file basis)<br>
     * <ul>
     * <li>-1 means no limit and all buffers will be stored in main memory;</li>
     * <li>0 means, that main memory will not be used and all buffers will be stored in temp files</li>
     * <li>Other positive values set PDFBox to use a portion of main-memory and additionally temporary file(s) in case the specified portion is exceeded</li>
     * </ul>
     */
    private static final long MAX_PDF_MEMORY_BYTES = Long.getLong(ELDOC_JVM_OPTION_MAX_MEMORY_BYTES, 10 * 1024 * 1024);

    private static final String PDF_PARAM_CREATOR = "elDoc eSignature Platform";
    private static final String PDF_PARAM_PRODUCER = "elDoc eSignature Platform";
    private static final String DOC_INFO_ROOT = "elDoc";
    private static final String DOC_INFO_PROP_VER = "ver";

    public static final int IMAGE_RENDER_DEFAULT_DPI = 72;
    public static final int IMAGE_RENDER_WEB_DPI = 120;
    public static final int IMAGE_RENDER_BEST_DPI = 300;
    private static final int PDF_MIN_SYMBOLS_COUNT = 50;

    /**
     * List of available fonts (ordered)
     */
    private static final List<String> FONTS_AVAIL = List.of(
            "fonts/noto/NotoSans-Regular.ttf", // Most basic font, greek-latin-cyrillic
            "fonts/noto/NotoSansCJKsc-VF.ttf", // Simplified Chinese
            "fonts/noto/NotoSansCJKtc-VF.ttf", // Traditional Chinese
            "fonts/noto/NotoSansCJKjp-VF.ttf", // Japanese
            "fonts/noto/NotoSansCJKkr-VF.ttf", // Korean
            "fonts/noto/NotoSansLiving-Regular.ttf" // Experimental (official, not released) merged font for all non-basic, non-CJK fonts
    );
    /**
     * Already loaded fonts map
     */
    private static final Map<String, TrueTypeFont> FONTS_LOADED = new HashMap<>();


    /**
     * Creates PDF Document
     *
     * @return
     */
    public static PDDocument createPDF() {
        return new PDDocument(getCacheFunction());
    }


    /**
     * Creates PDF Document with properties set, according to the internal system conventions
     *
     * @return
     */
    public static PDDocument createPDFWithProps() {
        PDDocument document = createPDF();
        addElDocProps(document);
        return document;
    }


    /**
     * Adds PDF properties to document according to internal system conventions
     *
     * @param document
     */
    public static void addElDocProps(PDDocument document) {
        PDDocumentInformation info = document.getDocumentInformation();
        info.setCreator(PDF_PARAM_CREATOR);
        info.setProducer(PDF_PARAM_PRODUCER);
        info.setCreationDate(Calendar.getInstance());
        info.setModificationDate(Calendar.getInstance());

        COSDictionary elDocProp = PdfUtils.getEldocProperties(document);
        elDocProp.setString(DOC_INFO_PROP_VER, PdfUtils.class.getPackage().getImplementationVersion());
    }


    /**
     * Creates PDF document from provided images, creating separate page for each image
     *
     * @param images List of images to be added
     * @return {@link PDDocument} document with each of the provided images loaded as separate page
     * @throws IOException
     */
    public static PDDocument createPDFFromImages(List<BufferedImage> images) throws IOException {
        PDDocument document = createPDFWithProps();

        // Add images to the the document as separate pages
        for (BufferedImage pageImg : images) {
            document.addPage(PdfUtils.createPDFPageFromImage(document, pageImg));
        }

        return document;
    }


    /**
     * Creates PDF page
     *
     * @param pageRect
     * @param rotation
     * @return
     */
    public static PDPage createPDFPage(Rectangle pageRect, int rotation) {
        PDRectangle rect = new PDRectangle((float) pageRect.getWidth(), (float) pageRect.getHeight());
        PDPage page = new PDPage(rect);
        page.setCropBox(rect);
        page.setRotation(rotation);
        return page;
    }


    /**
     * Creates PDF page
     *
     * @param pageRect
     * @param rotation
     * @return
     */
    private static PDPage createPDFPage(PDRectangle pageSize, int rotation) {
        PDPage page = new PDPage(pageSize);
        page.setCropBox(pageSize);
        page.setRotation(rotation);
        return page;
    }


    /**
     * Creates PDF Page from provided image<br>
     * Page is not added to provided document, but created page is bound to provided document, as it is
     * used for storing image resources
     *
     * @param document {@link PDDocument} document, where page will be added
     * @param pageImg {@link BufferedImage} image to be added
     * @return {@link PDPage} created page, bound to the provided document
     * @throws IOException
     */
    public static PDPage createPDFPageFromImage(PDDocument document, BufferedImage pageImg) throws IOException {
        PDPage page = createPDFPage(new Rectangle(0, 0, pageImg.getWidth(), pageImg.getHeight()), 0);
        addImage(document, page, pageImg);
        return page;
    }


    /**
     * Creates PDF Page from provided image<br>
     * Page is not added to provided document, but created page is bound to provided document, as it is
     * used for storing image resources
     *
     * @param document {@link PDDocument} document, where page will be added
     * @param pageImg {@link BufferedImage} image to be added
     * @param rect {@link PDRectangle} of the page, that will be created
     * @param centered Whether to put image in the center of the page
     * @param lossy Whether to use lossy (jpeg) or lossless compression for image
     * @return Pair of {@link PDPage} created page, bound to the provided document, and scale, applied for centering image, if requested
     * @throws IOException
     */
    public static Pair<PDPage, Rectangle> createPDFPageFromImage(PDDocument document, BufferedImage pageImg, PDRectangle rect, boolean centered, boolean lossy) throws IOException {
        if (rect == null) {
            rect = PDRectangle.A4;
        }

        PDPage page = createPDFPage(rect, 0);
        Rectangle imageOnPage = addImage(document, page, pageImg, centered, lossy);
        return Pair.of(page, imageOnPage);
    }


    /**
     * Loads PDF from the given source
     *
     * @param bytes
     * @return
     * @throws IOException
     */
    public static PDDocument loadPDF(byte[] bytes) throws IOException {
        return Loader.loadPDF(new RandomAccessReadBuffer(bytes), getCacheFunction());
    }


    /**
     * Loads PDF from the given source
     *
     * @param is
     * @return
     * @throws IOException
     */
    public static PDDocument loadPDF(InputStream is) throws IOException {
        return Loader.loadPDF(new RandomAccessReadBuffer(is), getCacheFunction());
    }


    /**
     * Loads PDF from the given source
     *
     * @param path
     * @return
     * @throws IOException
     */
    public static PDDocument loadPDF(String path) throws IOException {
        return loadPDF(Path.of(path));
    }


    /**
     * Loads PDF from the given source
     *
     * @param path
     * @return
     * @throws IOException
     */
    public static PDDocument loadPDF(Path path) throws IOException {
        return Loader.loadPDF(new RandomAccessReadBufferedFile(path), getCacheFunction());
    }


    /**
     * Saves PDF document to new file in temp directory with provided file name prefix
     *
     * @param document {@link PDDocument} to be saved
     * @param prefix Temporary file name prefix
     * @return
     * @throws IOException
     */
    public static File saveTempPDF(PDDocument document, String prefix) throws IOException {
        File outputFile = Files.createTempFile(prefix, ".pdf").toFile();
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            // Using OutputStream to write file, as using java.io.File (or its String path) causes validation, if file exists,
            // and gives warning from PDFBox for overwriting existing file, while we can be sure target is freshly created temp file
            // There is no other differences under the hood except this check as of PDFBox version 3.0.1
            document.save(os);
        }
        return outputFile;
    }


    /**
     * Extracts attached image from the PDF page, assuming that the PDF page has image for the whole page
     *
     * @param page
     * @param cosName
     * @return
     * @throws IOException
     */
    private static BufferedImage extractImage(PDPage page, COSName cosName) throws IOException {
        PDResources pdResources = page.getResources();
        if (isImageXObject(pdResources, cosName)) {
            PDImageXObject pdImage = (PDImageXObject) getXObject(pdResources, cosName);
            if (pdImage == null) {
                LOG.warn("Image object is null and can not be retrieved from the PDF page by the provided COSName: {}", cosName.getName());
                return null;
            }
            return extractImage(page, pdImage);
        } else {
            LOG.warn("Given object (COSName: {}) is not an image XObject", cosName.getName());
            return null;
        }
    }


    /**
     * Checks, if provided object is {@link PDImageXObject}<br>
     * If provided {@link PDResources} doesn't contain image with requested name - checks child forms (if present), as
     * it may be embedded into forms content stream
     *
     * @param resources
     * @param cosName
     * @return
     * @throws IOException
     */
    private static boolean isImageXObject(PDResources resources, COSName cosName) throws IOException {
        if (resources.isImageXObject(cosName)) {
            return true;
        }
        for (COSName formObjectName : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(formObjectName);
            if (xObject instanceof PDFormXObject pDFormXObject) {
                if (isImageXObject(pDFormXObject.getResources(), cosName)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Extracts {@link PDXObject} from provided resources by name<br>
     * If provided {@link PDResources} doesn't contain resource with requested name - checks child forms (if present), as
     * it may be embedded into forms content stream
     *
     * @param resources
     * @param cosName
     * @return
     * @throws IOException
     */
    private static PDXObject getXObject(PDResources resources, COSName cosName) throws IOException {
        PDXObject object = resources.getXObject(cosName);
        if (object != null) {
            return object;
        }
        for (COSName formObjectName : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(formObjectName);
            if (xObject instanceof PDFormXObject pDFormXObject) {
                object = getXObject(pDFormXObject.getResources(), cosName);
                if (object != null) {
                    return object;
                }
            }
        }
        return null;
    }


    private static BufferedImage extractImage(PDPage page, PDImageXObject pdImage) throws IOException {
        BufferedImage image = extractAsBufferedImage(pdImage);
        if (image == null) {
            return null;
        }

        // Perform image rotation if required
        if (page.getRotation() != 0) {
            image = ImageProcessor.rotateImage(image, page.getRotation());
        }

        return image;
    }


    /**
     * Determines by file name whether it is PDF file or not (checks lowercased name ending with .pdf)
     *
     * @param fileName
     * @return
     */
    public static boolean isPdf(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }


    /**
     * Extracts image from the given PDImage<br>
     *
     * @param pdImage
     * @return
     * @throws IOException
     */
    private static BufferedImage extractAsBufferedImage(PDImage pdImage) throws IOException {
        if (pdImage == null) {
            return null;
        }

        // PDFBox composes masks and returns (A)RGB
        BufferedImage src = pdImage.getImage();
        if (src == null) {
            return null;
        }

        int bpc = pdImage.getBitsPerComponent(); // may be -1, 1, 8, ...
        if (bpc == 1 && src.getTransparency() == java.awt.Transparency.OPAQUE) {
            // Already true 1-bit? keep it.
            if (src.getColorModel().getPixelSize() == 1) {
                return src;
            }

            // Coerce to 1-bit without dithering
            int w = src.getWidth(), h = src.getHeight();
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);

            // Bulk read source pixels
            int[] row = new int[w];
            for (int y = 0; y < h; y++) {
                row = src.getRGB(0, y, w, 1, row, 0, w);
                for (int x = 0; x < w; x++) {
                    // setRGB on TYPE_BYTE_BINARY maps to nearest (black/white), no dithering
                    dst.setRGB(x, y, row[x]);
                }
            }
            return dst;
        }

        return src;
    }


    /**
     * Returns size of the image in page as Rectangle.<br>
     * <b>IMPORTANT:</b> provided name must match image name on the PDF
     *
     * @param page
     * @param name
     * @return
     * @throws IOException
     */
    public static Rectangle getImageSize(PDPage page, String name) throws IOException {
        COSName cosName = COSName.getPDFName(name);
        PDResources pdResources = page.getResources();
        if (isImageXObject(pdResources, cosName)) {
            PDImageXObject image = (PDImageXObject) getXObject(pdResources, cosName);
            return new Rectangle(0, 0, image.getWidth(), image.getHeight());
        }
        return new Rectangle();
    }


    /**
     * Renders PDF Page as image (with defined dpi)
     *
     * @param pdfInputStream
     * @param pageIdx
     * @param dpi
     * @return
     * @throws IOException
     */
    public static BufferedImage renderPageToImage(InputStream pdfInputStream, int pageIdx, int dpi) throws IOException {
        try (PDDocument pdDocument = loadPDF(pdfInputStream)) {
            return renderPageToImage(pdDocument, pageIdx, dpi);
        }
    }


    /**
     * Renders PDF Page as image (with defined dpi)
     *
     * @param pdDocument
     * @param pageIdx
     * @param dpi
     * @return
     * @throws IOException
     */
    public static BufferedImage renderPageToImage(PDDocument pdDocument, int pageIdx, int dpi) throws IOException {
        PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);
        return pdfRenderer.renderImageWithDPI(pageIdx, dpi, ImageType.RGB);
    }


    /**
     * Performs PDF document page rendering (with {@link IMAGE_RENDER_BEST_DPI}) to image or extracts full-page image in case present
     *
     * @param pdDocument
     * @param pageIdx (0-based index)
     * @return
     * @throws IOException
     */
    public static BufferedImage renderOrExtractDocPageImage(PDDocument pdDocument, int pageIdx) throws IOException {
        return renderOrExtractDocPageImage(pdDocument, pageIdx, IMAGE_RENDER_BEST_DPI);
    }


    /**
     * Performs PDF document page rendering (with defined dpi) to image or extracts full-page image in case present
     *
     * @param pdDocument
     * @param pageIdx
     * @param dpi DPI to be used in case no image found for extraction
     * @return
     * @throws IOException
     */
    public static BufferedImage renderOrExtractDocPageImage(PDDocument pdDocument, int pageIdx, int dpi) throws IOException {
        return renderOrExtractDocPageImage(pdDocument, pageIdx, dpi, false).getLeft();
    }


    /**
     * Performs PDF document page rendering (with defined dpi) to image or extracts full-page image in case present
     *
     * @param pdDocument
     * @param pageIdx
     * @param dpi DPI to be used in case no image found for extraction
     * @param forceRenderIfContainsText forces page render if it contains text
     * @return
     * @throws IOException
     */
    public static Pair<BufferedImage, Boolean> renderOrExtractDocPageImage(PDDocument pdDocument, int pageIdx, int dpi, boolean forceRenderIfContainsText) throws IOException {
        PDPage page = pdDocument.getPage(pageIdx);
        ImageStripper imageStripper = new ImageStripper();
        imageStripper.processPage(page);

        // Check if page contains text (lazy)
        boolean containsText = false;
        if (forceRenderIfContainsText) {
            containsText = isNotBlank(getText(pdDocument, pageIdx));
        }

        // Decide to extract image or to render a page
        BufferedImage pageImage;
        boolean isRendered;
        if (imageStripper.getFullPageImageCandidate() != null && imageStripper.getNumberOfImages() == 1 && !(forceRenderIfContainsText && containsText)) {
            // Extract image: we do extract image from PDF only in case we got one single full-page image candidate
            BufferedImage pageImg = extractImage(page, COSName.getPDFName(imageStripper.getFullPageImageCandidate()));
            pageImage = ImageProcessor.flipImage(pageImg, imageStripper.isHorizontalFlipRequired(), imageStripper.isVerticalFlipRequired());
            isRendered = false;
        } else {
            // Render page
            pageImage = renderPageToImage(pdDocument, pageIdx, dpi);
            isRendered = true;
        }
        return Pair.of(pageImage, isRendered);
    }


    public static Map<Rectangle, BufferedImage> renderOrExtractDocPageImage(PDDocument pdDocument, int pageIdx, List<Rectangle> areaList) throws IOException {
        Map<Rectangle, BufferedImage> result = new HashMap<>();
        BufferedImage pageImage = null;

        PDPage page = pdDocument.getPage(pageIdx);
        ImageRegionStripper imageStripper = new ImageRegionStripper();
        areaList.forEach(imageStripper::addRegion);
        imageStripper.processPage(page);

        for (Rectangle area : areaList) {
            if (imageStripper.getRegionImage(area) != null) {
                // Requested area corresponds to image inside PDF page
                BufferedImage areaImg = extractImage(page, imageStripper.getRegionImage(area));
                areaImg = ImageProcessor.flipImage(areaImg, imageStripper.getRegionHorizontalFlipRequired(area), imageStripper.getRegionVerticalFlipRequired(area));
                result.put(area, areaImg);
            } else if (imageStripper.getRegionImageCandidates(area).size() == 1) {
                // Requested area corresponds to image inside PDF page
                BufferedImage areaImg = extractImage(page, imageStripper.getRegionImageCandidates(area).get(0));
                areaImg = ImageProcessor.flipImage(areaImg,
                        imageStripper.isRegionImageCandidateHorizontalFlipRequired(area, 0),
                        imageStripper.isRegionImageCandidateVerticalFlipRequired(area, 0));
                result.put(area, areaImg);
            } else if (imageStripper.getRegionImageCandidates(area).size() > 1) {

                float scale;

                // Perform additional check, if page image will be extracted or rendered
                if (imageStripper.getFullPageImageCandidate() != null && imageStripper.getNumberOfImages() == 1) {
                    // If page image will be extracted - no additional scaling required
                    scale = 1;
                } else {
                    // If page will be rendered - additional scaling should be applied
                    scale = ((float) IMAGE_RENDER_BEST_DPI) / ((float) IMAGE_RENDER_DEFAULT_DPI);
                }

                List<Rectangle> imagesCandidatesInArea = imageStripper.getRegionImageCandidateAreas(area);
                Rectangle mergedImagesArea = RectangleUtils.union(imagesCandidatesInArea).getScaledClone(scale);
                if (pageImage == null) {
                    pageImage = renderOrExtractDocPageImage(pdDocument, pageIdx, IMAGE_RENDER_BEST_DPI);
                }
                result.put(area, ImageProcessor.deepCopy(ImageProcessor.getSubimage(pageImage, mergedImagesArea)));
            } else {
                if (pageImage == null) {
                    pageImage = renderOrExtractDocPageImage(pdDocument, pageIdx, IMAGE_RENDER_BEST_DPI);
                }
                Rectangle pageImageRect = new Rectangle(0, 0, pageImage.getWidth(), pageImage.getHeight());

                double scale;
                // Perform additional check, if page image will be extracted or rendered
                if (imageStripper.getFullPageImageCandidate() != null && imageStripper.getNumberOfImages() == 1) {
                    // If page image will be extracted - no additional scaling required
                    scale = pageImageRect.getWidth() / getPageDimensionRect(pdDocument.getPage(pageIdx)).getWidth();
                } else {
                    // If page will be rendered - additional scaling should be applied
                    scale = ((double) IMAGE_RENDER_BEST_DPI) / ((double) IMAGE_RENDER_DEFAULT_DPI);
                }

                Rectangle requestedArea = area.getScaledClone(scale);

                // Create deep copy of requested area to ensure no collisions will happen in case requested area will be post-processed
                Rectangle croppedArea = pageImageRect.createIntersection(requestedArea);
                result.put(area, ImageProcessor.deepCopy(ImageProcessor.getSubimage(pageImage, croppedArea)));
            }
        }

        return result;
    }


    /**
     * Returns number of pages in PDF document
     *
     * @param pdfInputStream
     * @return
     */
    public static int getNumberOfPages(InputStream pdfInputStream) {
        try (PDDocument pdfDoc = loadPDF(pdfInputStream);) {
            return pdfDoc.getNumberOfPages();
        } catch (InvalidPasswordException e) {
            throw new ElDocRuntimeException("Failed to load pdf, file is encrypted with password", e);
        } catch (IOException e) {
            throw new ElDocRuntimeException("Failed to load pdf", e);
        }
    }


    /**
     * Creates copy of source PDF document. If {@code removeAnnotations} set to true - performs annotations cleanup.
     * <b>WARNING</b>: Annotations keep references to the page they was originally after copy. If source document supposed
     * to have annotations - it is recommended to remove them. Otherwise, document will still be usable, but annotations
     * will be broken.
     *
     * @param sourceDocument
     * @param removeAnnotations Whether to remove annotations from cloned document
     * @return
     */
    public static PDDocument cloneDocument(PDDocument sourceDocument, boolean removeAnnotations) {
        PDDocument clonedDoc = createPDF();
        for (int pageIdx = 0; pageIdx < sourceDocument.getNumberOfPages(); pageIdx++) {
            PDPage pageClone = clonePageByIdx(sourceDocument, pageIdx, removeAnnotations);
            clonedDoc.addPage(pageClone);
        }
        return clonedDoc;
    }


    /**
     * Adds all pages from documentToImport to targetDocument<br>
     * <b>IMPORTANT:</b> this method keeps reference to imported document(COSStream) due to how
     * {@link PDDocument#addPage(org.apache.pdfbox.pdmodel.PDPage)} works. Imported document should
     * be kept open until target document is saved/refreshed or closed
     *
     * @param targetDocument
     * @param documentToImport
     *
     * @see PDDocument#importPage(org.apache.pdfbox.pdmodel.PDPage)
     */
    public static void importDocument(PDDocument targetDocument, PDDocument documentToImport) {
        for (int pageIdx = 0; pageIdx < documentToImport.getNumberOfPages(); pageIdx++) {
            PDPage pageClone = clonePageByIdx(documentToImport, pageIdx, false);
            targetDocument.addPage(pageClone);
        }
    }


    /**
     * Clones PDF page by given page index
     * If source page is supposed to have annotations - set {@code cleanAnnotations} to true, as it retains references to
     * source PDPage, which will be wrong in copied page
     *
     * @param sourceDoc {@link PDDocument}, whose page should be cloned
     * @param pageIdx 0-based index
     * @param cleanAnnotations whether to remove annotations from cloned page to avoid them referencing source page
     * @see https://stackoverflow.com/questions/20084117/pdfbox-how-to-clone-a-page
     * @return Cloned {@link PDPage}
     */
    public static PDPage clonePageByIdx(PDDocument sourceDoc, int pageIdx, boolean cleanAnnotations) {
        // Get Source page
        PDPage sourcePage = sourceDoc.getPage(pageIdx);
        COSDictionary sourcePageDict = sourcePage.getCOSObject();

        // Create page clone and remove annotations as they can be linked to the source page
        COSDictionary clonePageDict = new COSDictionary(sourcePageDict);
        if (cleanAnnotations) {
            clonePageDict.removeItem(COSName.ANNOTS);
        }

        PDPage clonePage = new PDPage(clonePageDict);
        clonePage.getCOSObject().removeItem(COSName.PARENT);

        if (sourcePage.getResources() != null && !sourcePage.getCOSObject().containsKey(COSName.RESOURCES)) {
            clonePage.setResources(sourcePage.getResources());
        }

        // Check, if source page had all boxes defined, and if not - check, if they need to be transferred from Parent dictionary (Pages)
        List.of(COSName.CROP_BOX, COSName.MEDIA_BOX, COSName.TRIM_BOX, COSName.BLEED_BOX, COSName.ART_BOX)
                .forEach(box -> transferParentBoxIfNeeded(sourcePageDict, clonePageDict, box));

        return clonePage;
    }


    /**
     * Transfers values for *Boxes (i.e. CropBox, MediaBox) from source page Parent dictionary ("Pages" element) to target page,
     * if (and only if) it was not directly defined on source page
     *
     * @param sourcePageDict {@link COSDictionary} source page dictionary
     * @param targetPageDict {@link COSDictionary} target page dictionary
     * @param boxName {@link COSName} name of the Box, that should be checked and transferred, if needed
     */
    private static void transferParentBoxIfNeeded(COSDictionary sourcePageDict, COSDictionary targetPageDict, COSName boxName) {
        if (sourcePageDict.getDictionaryObject(boxName) != null) {
            // *Box value already present on source page, so it was transferred
            return;
        }

        // Source page doesn't have *Box value defined directly - check, if there's inherited *Box
        COSDictionary parent = sourcePageDict.getCOSDictionary(COSName.PARENT, COSName.P);
        if (parent == null || !COSName.PAGES.equals(parent.getCOSName(COSName.TYPE))) {
            // Can't find parent dictionary
            return;
        }

        // Parent 'Pages' dictionary found, it may have global *Box defined
        COSBase boxBase = parent.getDictionaryObject(boxName);
        if (boxBase instanceof COSArray) {
            // Global *Box found
            targetPageDict.setItem(boxName, boxBase);
        }
    }


    /**
     * Sets PDF page rotation
     *
     * @param page
     * @param rotationDegree
     */
    public static void setPageRotation(PDPage page, int rotationDegree) {
        page.setRotation(rotationDegree);
    }


    /**
     * Removes existing image from PDF page and adds provided BufferedImage.<br>
     * <b>IMPORTANT:</b> This method assumes, that page doesn't have any other content yet and adjusts CropBox and
     * MediaBox of the page to match provided image
     *
     * @param document
     * @param page
     * @param pageImage
     * @throws java.io.IOException
     */
    public static void replacePageImage(PDDocument document, PDPage page, BufferedImage pageImage, boolean fit) throws IOException {
        // Remove existing image
        PDResources pageResources = page.getResources();
        for (COSName resource : pageResources.getXObjectNames()) {
            if (pageResources.isImageXObject(resource)) {
                ((COSDictionary) pageResources.getCOSObject().getDictionaryObject(COSName.XOBJECT)).removeItem(resource);
            }
        }

        // Add new image
        float width = fit ? page.getCropBox().getWidth() : pageImage.getWidth();
        float height = fit ? page.getCropBox().getHeight() : pageImage.getHeight();
        PDRectangle rect = new PDRectangle(width, height);
        PDImageXObject pdImage = LosslessFactory.createFromImage(document, pageImage);
        page.setCropBox(rect);
        page.setMediaBox(rect);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page, AppendMode.OVERWRITE, false, true)) {
            contentStream.drawImage(pdImage, 0, 0, width, height);
        }
    }


    /**
     * Checks whether PDF page is text-based or image-based
     *
     * @param pdfDocument
     * @param pageIdx page index (0-based)
     * @return
     * @throws IOException
     */
    public static boolean isTextBased(PDDocument pdfDocument, int pageIdx) throws IOException {
        // If page has fonts information - it is text-based, has watermarks or was OCRed
        if (!containsFonts(pdfDocument, pageIdx)) {
            // If document has fonts information - it is text-based, has watermarks or was OCRed
            if (!containsFonts(pdfDocument)) {
                return false;
            }
        }

        int textThresholdToBeComparedWith = getTextPdfCharsThreshold() == -1 ? PDF_MIN_SYMBOLS_COUNT : getTextPdfCharsThreshold();
        if (textThresholdToBeComparedWith == 0) {
            return true; // By having threshold set to 0 effectively disables further checks and in case PDF page or document has fonts - we treat this page as machine-generated as well
        }

        String pageText = getText(pdfDocument, pageIdx);
        // Even if PDF document contains font information, it can be empty for various reasons
        if (isBlank(pageText)) {
            return false;
        }
        // If there is to small amount of text in the document - most probably this is watermark/copyright and document shouldn't be treated as text-based or OCRed
        return pageText.replaceAll("\\s", "").length() >= textThresholdToBeComparedWith;
    }


    /**
     * Returns JVM-system property value of the text-pdf-page detection by amount of text threshold
     *
     * @return -1 in case no system property was defined, otherwise integer value equal or greater 0
     */
    private static int getTextPdfCharsThreshold() {
        String textPdfCharsThresholdSysPropStr = System.getProperty(ELDOC_JVM_OPTION_TEXT_PDF_CHARACTERS_THRESHOLD);
        int textPdfCharsThresholdSysProp = -1;
        if (textPdfCharsThresholdSysPropStr != null) {
            try {
                textPdfCharsThresholdSysProp = Integer.parseInt(textPdfCharsThresholdSysPropStr);
                if (textPdfCharsThresholdSysProp < 0) {
                    throw new NumberFormatException("Only positive integer or zero are allowed here, given value was: " + textPdfCharsThresholdSysProp);
                }
            } catch (NumberFormatException e) {
                LOG.warn("Invalid value for the JVM-property '{}'={}, only positive integer or zero are allowed", ELDOC_JVM_OPTION_TEXT_PDF_CHARACTERS_THRESHOLD, textPdfCharsThresholdSysPropStr);
            }
        }
        return textPdfCharsThresholdSysProp;
    }


    /**
     * Checks, if provided document contains information about used fonts
     *
     * @param pdfDocument
     * @return
     */
    private static boolean containsFonts(PDDocument pdfDocument) {
        if (pdfDocument == null) {
            return false;
        }
        try {
            List<COSObject> docFonts = pdfDocument.getDocument().getObjectsByType(COSName.FONT);
            return !docFonts.isEmpty();
        } catch (Exception ex) {
            LOG.warn("Error checking for fonts in the document");
            return false;
        }
    }


    /**
     * Checks, if specific page contains information about fonts, used on this page.
     * <b>IMPORTANT</b>: This function doesn't work, if fonts defined for sub-elements of the page,
     * i.e. form, defined on the page, and not page itself
     *
     * @param pdfDocument
     * @param pageIdx
     * @return
     */
    private static boolean containsFonts(PDDocument pdfDocument, int pageIdx) {
        PDPage page = pdfDocument.getPage(pageIdx);
        PDResources pageResources = page.getResources();
        return pageResources == null || pageResources.getFontNames().iterator().hasNext();
    }


    /**
     * Extracts and returns all text from the given page index
     *
     * @param pdfDocument
     * @param pageIdx page index (0-based)
     * @return
     * @throws IOException
     */
    public static String getText(PDDocument pdfDocument, int pageIdx) throws IOException {
        PDFTextStripper textStripper = new PDFTextStripper();
        textStripper.setStartPage(pageIdx + 1);
        textStripper.setEndPage(pageIdx + 1);
        return textStripper.getText(pdfDocument);
    }


    /**
     * Extracts and returns text from the document on given page
     *
     * @param pdfDocument
     * @param pageIdx page index (0-based)
     * @return
     * @throws IOException
     */
    public static OcrText getTextWithMeta(PDDocument pdfDocument, int pageIdx) throws IOException {
        RecoTextStripperByArea textStripper = new RecoTextStripperByArea();
        textStripper.addRegion("rect", getPageDimensionRect(pdfDocument.getPage(pageIdx)));
        textStripper.extractRegions(pdfDocument.getPage(pageIdx));
        return textStripper.getTextForRegionWithCoords("rect");
    }


    /**
     * Returns page dimensions in format WxH, e.g.: "2500x3600", "2300x3450"
     *
     * @param pdfPage
     * @return
     */
    public static String getPageDimension(PDPage pdfPage) {
        Rectangle pageDims = getPageDimensionRect(pdfPage);
        return ImageProcessor.formatDimensions((int) pageDims.getWidth(), (int) pageDims.getHeight());
    }


    /**
     * Returns page dimensions as {@link Rectangle}
     *
     * @param pdfPage
     * @return
     */
    public static Rectangle getPageDimensionRect(PDPage pdfPage) {
        int w;
        int h;
        int pageOrientation = pdfPage.getRotation();
        // Crop Box dimensions are extracted befor Page Orientation is applied, so we need to swap dimentions, if needed
        if (pageOrientation == 90 || pageOrientation == 270) {
            w = (int) pdfPage.getCropBox().getHeight();
            h = (int) pdfPage.getCropBox().getWidth();
        } else {
            w = (int) pdfPage.getCropBox().getWidth();
            h = (int) pdfPage.getCropBox().getHeight();
        }
        return new Rectangle(0, 0, w, h);
    }


    public static void addRect(PDDocument document, int pageIdx, Rectangle rectangle, Color rectColor) {
        if (document != null) {
            try {
                PDPage page = document.getPage(pageIdx);
                PDRectangle cropBox = page.getCropBox();

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                    contentStream.addRect((float) rectangle.getX(), (float) (cropBox.getHeight() - rectangle.getMaxY()), (float) rectangle.getWidth(), (float) rectangle.getHeight());
                    contentStream.setStrokingColor(rectColor);
                    contentStream.stroke();
                }
            } catch (Exception ex) {
                LOG.error("Error adding rectangle to the PDF document", ex);
            }
        }
    }


    /**
     * Adds provided image to the PDF document page at (0,0) coordinates
     *
     * @param document
     * @param page
     * @param bi
     * @throws IOException
     */
    public static void addImage(PDDocument document, PDPage page, BufferedImage bi) throws IOException {
        addImage(document, page, bi, false, false);
    }


    /**
     * Adds provided image to the PDF document page.If 'centered' is true, image will be scaled to fit page as much as possible
     * without breaking image proportions, and placed in center of the image. Otherwise image is added in (0, 0) coordinates
     * with original size
     *
     * @param document {@link PDDocument}, in whose page image should be added
     * @param page {@link PDPage}, where image should be added
     * @param bi {@link BufferedImage}, that should be added on the page
     * @param centered
     * @param lossy
     * @return Scale, that was used for fitting/centering image on the page, if requested
     * @throws IOException
     */
    public static Rectangle addImage(PDDocument document, PDPage page, BufferedImage bi, boolean centered, boolean lossy) throws IOException {
        double offsetX = 0;
        double offsetY = 0;
        double width = bi.getWidth();
        double height = bi.getHeight();
        if (centered) {
            // Get image size in PDF points (1/72 inches)
            Rectangle imageRectInPdfPoints = getImageRectInPdfPoints(bi, IMAGE_RENDER_BEST_DPI);

            // Scale image to fit into provided page crop box
            Rectangle pageRect = getPageDimensionRect(page);
            imageRectInPdfPoints.scalePreservingRatio(pageRect);
            width = imageRectInPdfPoints.getWidth();
            height = imageRectInPdfPoints.getHeight();

            // Calculate offsets, that are required to place image in the center of the page, if their proportions does not match
            offsetX = (page.getCropBox().getWidth() - width) / 2;
            offsetY = (page.getCropBox().getHeight() - height) / 2;
        }
        addImage(document, page, bi, offsetX, offsetY, width, height, lossy);
        return new Rectangle(offsetX, offsetY, width, height);
    }


    /**
     * Adds provided image to the PDF document page at provided coordinates and size
     *
     * @param document {@link PDDocument}, in whose page image should be added
     * @param page {@link PDPage}, where image should be added
     * @param bi {@link BufferedImage}, that should be added on the page
     * @param offsetX Offset from the left side of the page to start of the image
     * @param offsetY Offset from the right side of the page to start of the image
     * @param width Width of the image area on the page
     * @param height Height of the image area on the page
     * @throws IOException
     */
    private static void addImage(PDDocument document, PDPage page, BufferedImage bi, double offsetX, double offsetY, double width, double height, boolean lossy) throws IOException {
        PDImageXObject pdImage;
        if (lossy) {
            pdImage = JPEGFactory.createFromImage(document, bi, 0.8f);
        } else {
            pdImage = LosslessFactory.createFromImage(document, bi);
        }
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page, AppendMode.APPEND, true)) {
            contentStream.drawImage(pdImage, (float) offsetX, (float) offsetY, (float) width, (float) height);
        }
    }


    private static Rectangle getImageRectInPdfPoints(BufferedImage bi, int dpi) {
        double imgWidthInPoints = (bi.getWidth() / (double) dpi) * 72;
        double imgHeightInPoints = (bi.getHeight() / (double) dpi) * 72;
        return new Rectangle(0, 0, imgWidthInPoints, imgHeightInPoints);
    }


    public static void addEldocProperties(PDDocument document, Map<String, Object> properties) {
        COSDictionary elDocProp = PdfUtils.getEldocProperties(document);
        // TODO: Consider adding document-level properties to document metadata instead of using custom COSDictionary
        addEldocProperties(elDocProp, properties);
    }


    public static void addEldocProperties(PDPage page, Map<String, Object> properties) {
        COSDictionary elDocProp = PdfUtils.getEldocProperties(page);
        addEldocProperties(elDocProp, properties);
    }


    private static void addEldocProperties(COSDictionary elDocProp, Map<String, Object> properties) {
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            if (property.getValue() instanceof Boolean bValue) {
                elDocProp.setBoolean(property.getKey(), bValue);
            } else {
                elDocProp.setString(property.getKey(), String.valueOf(property.getValue()));
            }
        }
    }


    /**
     * Returns PDF properties (if present) or creates a new instance of properties and stores within document
     *
     * @param document PDF document, where properties should be located
     * @return
     */
    public static COSDictionary getEldocProperties(PDDocument document) {
        return getEldocProperties(document, true);
    }


    /**
     * Returns PDF properties<br>
     * If properties are not present on the document, but their creation was requested - new Properties will
     * be created and appended to the PDF document
     *
     * @param document PDF document, where properties should be located
     * @param create Whether new properties should be created, if provided document doesn't contain elDoc properties
     * @return
     */
    public static COSDictionary getEldocProperties(PDDocument document, boolean create) {
        COSName elDocRootPropName = COSName.getPDFName(DOC_INFO_ROOT);
        PDDocumentInformation info = document.getDocumentInformation();
        COSDictionary elDocProp = info.getCOSObject().getCOSDictionary(elDocRootPropName);
        if (elDocProp == null && create) {
            // elDoc properties not found, creation for missing properties requested - creating new
            elDocProp = new COSDictionary();
            info.getCOSObject().setItem(elDocRootPropName, elDocProp);
        }
        return elDocProp;
    }


    public static COSDictionary getEldocProperties(PDPage page) {
        return getEldocProperties(page, true);
    }


    public static COSDictionary getEldocProperties(PDPage page, boolean create) {
        COSName elDocPagePropName = COSName.getPDFName(DOC_INFO_ROOT);

        COSDictionary pageProps = page.getCOSObject().getCOSDictionary(COSName.PROPERTIES);
        if (pageProps == null) {
            if (create) {
                pageProps = new COSDictionary();
                page.getCOSObject().setItem(COSName.PROPERTIES, PDPropertyList.create(pageProps));
            } else {
                return null;
            }
        }
        COSDictionary eldocProps = pageProps.getCOSDictionary(elDocPagePropName);
        if (eldocProps == null) {
            if (create) {
                eldocProps = new COSDictionary();
                pageProps.setItem(elDocPagePropName, PDPropertyList.create(eldocProps));
            } else {
                return null;
            }
        }
        return eldocProps;
    }


    /**
     * Removes all page properties from all pages in document
     *
     * @param document PDF Document, that needs to be cleaned up
     */
    public static void removePageProperties(PDDocument document) {
        for (PDPage page : document.getPages()) {
            PDResources pageResources = page.getResources();
            pageResources.getPropertiesNames().forEach(prop -> pageResources.getProperties(prop).getCOSObject().clear());
        }
    }


    //
    // PDF-related helper methods
    //
    /**
     * Returns default caching function
     *
     * @return
     */
    private static StreamCacheCreateFunction getCacheFunction() {
        return MemoryUsageSetting.setupMixed(MAX_PDF_MEMORY_BYTES).streamCache;
    }


    /**
     * Returns default Unicode Font
     *
     * @return
     * @throws IOException
     */
    public static TrueTypeFont getFontDefault() throws IOException {
        return getOrLoadFont(FONTS_AVAIL.get(0));
    }


    /**
     * Finds best font for provided text string for use on PDF<br>
     * This method iterates over known fonts and tries to find a font, that will be able to encode
     * all characters in provided String.<br>
     * If such font is not found - best match will be returned (font, that can encode largest amount of
     * characters in provided text)<br>
     * If no font is capable of encoding at least one character - default font will be returned
     *
     * @param text Text to be tested against fonts
     * @return Best matching font for encoding provided text string on PDF or {@link #getFontDefault() },
     * if no font can encode any character in provided text
     * @throws IOException
     */
    public static TrueTypeFont getFont(String text) throws IOException {
        long bestFontCharMatches = 0;
        TrueTypeFont bestFont = null;
        for (String fontName : FONTS_AVAIL) {
            TrueTypeFont font = getOrLoadFont(fontName);
            CmapLookup lookup = font.getUnicodeCmapLookup(false);
            long matchedChars = text.codePoints() // Iterate code points
                    .filter(cp -> lookup.getGlyphId(cp) != 0) // Check, if code point has glyph in font
                    .count(); // Count code points, that has glyphs
            if (matchedChars == text.length()) {
                return font;
            }

            // Font doesn't fully support requested text
            if (matchedChars > bestFontCharMatches) {
                bestFontCharMatches = matchedChars;
                bestFont = font;
            }
        }

        // No font found, that will be able to encode requested text
        if (bestFont != null) {
            // There is partial match, use it to properly print as much text, as possible
            return bestFont;
        }

        // No font matches text at least partially - use default font to be able to print replacement text
        return getFontDefault();
    }


    private static TrueTypeFont getOrLoadFont(String fontName) throws IOException {
        TrueTypeFont font = FONTS_LOADED.get(fontName);
        if (font == null) {
            font = new TTFParser().parse(new RandomAccessReadBuffer(OcrPdfUtils.class.getClassLoader().getResourceAsStream(fontName)));
            FONTS_LOADED.put(fontName, font);
        }
        return font;
    }
}
