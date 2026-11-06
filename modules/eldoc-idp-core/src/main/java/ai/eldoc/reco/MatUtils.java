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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import org.bytedeco.javacpp.BytePointer;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import org.bytedeco.opencv.opencv_core.Mat;


/**
 * Mat <-> BufferedImage helpers (no JavaCV) with alpha & colorspace options.<br>
 * <i>NOTE: This class was ChatGPT 5 generated</i><br><br>
 *
 * Notes on native memory:<br>
 * - BytePointer instances obtained via Mat.data() are *views* of Mat's memory.
 * Do NOT call close()/deallocate() on them. Just restore position(0) after use.<br>
 * - Only Mats explicitly created with new Mat(...) here own memory; they are returned
 * to the caller or released in finally blocks where appropriate.
 */
public final class MatUtils {

    /**
     * Alpha handling when creating BufferedImage from Mat.
     */
    public enum AlphaMode {
        /**
         * Ignore alpha (output TYPE_3BYTE_BGR / TYPE_BYTE_GRAY).
         */
        DROP,
        /**
         * Preserve alpha as straight (non-premultiplied) TYPE_INT_ARGB.
         */
        PRESERVE_STRAIGHT,
        /**
         * Preserve alpha as premultiplied TYPE_INT_ARGB_PRE.
         */
        PRESERVE_PREMULTIPLIED
    }


    //
    // Constructors -------------------------------------------------------------------------------
    //
    private MatUtils() {
    }


    //
    // Mat -> BufferedImage Methods ---------------------------------------------------------------
    //
    /**
     * Fast defaults: drops alpha. Accepts CV_8U 1/3/4-channel Mats.
     */
    public static BufferedImage toBufferedImage(Mat mat) {
        return toBufferedImage(mat, AlphaMode.DROP, null);
    }


    /**
     * Convert Mat to BufferedImage with optional alpha & color conversion.
     *
     * @param mat CV_8U Mat (1/3/4 channels)
     * @param alpha Alpha handling mode
     * @param convertCode Optional cvtColor() code to apply first (e.g., COLOR_RGB2BGR). Null to skip.
     */
    public static BufferedImage toBufferedImage(Mat mat, AlphaMode alpha, Integer convertCode) {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("mat is null/empty");
        }
        if (mat.depth() != CV_8U) {
            throw new IllegalArgumentException("Only CV_8U supported");
        }

        Mat src = mat;
        try (Mat converted = (convertCode != null ? new Mat() : null)) {
            if (convertCode != null) {
                cvtColor(mat, converted, convertCode);
                src = converted;
            }

            final int w = src.cols(), h = src.rows(), ch = src.channels();

            if (ch == 1) {
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
                copyMatToBufferedImageBytes(src, img, 1);
                return img;
            }
            if (ch == 3) {
                // OpenCV default is BGR
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
                copyMatToBufferedImageBytes(src, img, 3);
                return img;
            }
            if (ch == 4) {
                switch (alpha) {
                    case DROP -> {
                        try (Mat bgr = new Mat()) {
                            cvtColor(src, bgr, COLOR_BGRA2BGR);
                            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
                            copyMatToBufferedImageBytes(bgr, img, 3);
                            return img;
                        }
                    }
                    case PRESERVE_STRAIGHT -> {
                        // BGRA (straight) -> ARGB (straight)
                        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                        packBGRAtoARGB(src, ((DataBufferInt) img.getRaster().getDataBuffer()).getData(), w, h, false);
                        return img;
                    }
                    case PRESERVE_PREMULTIPLIED -> {
                        // BGRA (straight) -> ARGB premultiplied
                        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
                        packBGRAtoARGB(src, ((DataBufferInt) img.getRaster().getDataBuffer()).getData(), w, h, true);
                        return img;
                    }
                }
            }
            throw new IllegalArgumentException("Unsupported channels: " + ch);
        }
    }


    //
    // BufferedImage -> Mat Methods ---------------------------------------------------------------
    //
    /**
     * Defaults: keep alpha if present; returns CV_8UC4 for alpha images, else CV_8UC3/1.
     *
     * @param src
     * @return
     */
    public static Mat toMat(BufferedImage src) {
        return toMat(src, true);
    }


    /**
     * Convert BufferedImage to Mat (CV_8U), with optional alpha.
     *
     * @param src
     * @param includeAlpha if true and source has alpha, produce CV_8UC4 (BGRA straight alpha).
     * Premultiplied inputs are un-premultiplied back to straight alpha.
     *
     * @return
     */
    public static Mat toMat(BufferedImage src, boolean includeAlpha) {
        if (src == null) {
            throw new IllegalArgumentException("src is null");
        }

        final int w = src.getWidth();
        final int h = src.getHeight();
        final boolean hasAlpha = src.getColorModel().hasAlpha();
        final boolean wantAlpha = includeAlpha && hasAlpha;

        int type = src.getType();
        BufferedImage img = src;

        // -------- Normalize to a predictable layout BEFORE reading pixels --------
        if (wantAlpha) {
            // Prefer INT_ARGB unless already an alpha-capable type
            if (type != BufferedImage.TYPE_4BYTE_ABGR
                    && type != BufferedImage.TYPE_4BYTE_ABGR_PRE
                    && type != BufferedImage.TYPE_INT_ARGB
                    && type != BufferedImage.TYPE_INT_ARGB_PRE) {
                BufferedImage converted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = converted.createGraphics();
                g.drawImage(src, 0, 0, null);
                g.dispose();
                img = converted;
                type = img.getType();
            }
        } else {
            // No alpha desired -> prefer single-channel for indexed/binary, else BGR
            if (type == BufferedImage.TYPE_BYTE_GRAY) {
                // OK
            } else if (type == BufferedImage.TYPE_BYTE_BINARY || type == BufferedImage.TYPE_BYTE_INDEXED) {
                BufferedImage converted = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
                Graphics2D g = converted.createGraphics();
                g.drawImage(src, 0, 0, null);
                g.dispose();
                img = converted;
                type = img.getType();
            } else if (type != BufferedImage.TYPE_3BYTE_BGR) {
                BufferedImage converted = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g = converted.createGraphics();
                g.drawImage(src, 0, 0, null);
                g.dispose();
                img = converted;
                type = img.getType();
            }
        }

        // ---------------------- Now convert normalized image ----------------------
        if (!wantAlpha) {
            if (type == BufferedImage.TYPE_BYTE_GRAY) {
                Mat m = new Mat(h, w, CV_8UC1);
                copyGrayToMat(img, m);
                return m;
            }
            Mat m = new Mat(h, w, CV_8UC3); // TYPE_3BYTE_BGR
            copyBGRToMat(img, m);
            return m;
        }

        // wantAlpha == true : produce CV_8UC4 BGRA (straight alpha)
        if (type == BufferedImage.TYPE_4BYTE_ABGR || type == BufferedImage.TYPE_4BYTE_ABGR_PRE) {
            Mat m = new Mat(h, w, CV_8UC4);
            copyABGRToMat(img, m, type == BufferedImage.TYPE_4BYTE_ABGR_PRE);
            return m;
        }

        // TYPE_INT_ARGB / TYPE_INT_ARGB_PRE
        if (type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_ARGB_PRE) {
            Mat m = new Mat(h, w, CV_8UC4);
            copyARGBIntToMat(img, m, type == BufferedImage.TYPE_INT_ARGB_PRE);
            return m;
        }

        throw new IllegalArgumentException("Unsupported BufferedImage type after normalize: " + type);
    }


    //
    // Internal Methods ---------------------------------------------------------------------------
    //
    /**
     * Fast copy for 1/3-channel Mats into newly-created tightly-packed BufferedImages (handles Mat stride).
     * NOTE: src is a view pointer to Mat memory; DO NOT close it.
     */
    private static void copyMatToBufferedImageBytes(Mat mat, BufferedImage img, int channels) {
        byte[] target = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        final int w = mat.cols(), h = mat.rows();
        final int rowBytes = w * channels;
        final int step = (int) mat.step();
        final BytePointer src = mat.data(); // view – do not close

        if (step == rowBytes) {
            src.get(target, 0, rowBytes * h);
        } else {
            byte[] row = new byte[rowBytes];
            for (int y = 0; y < h; y++) {
                src.position((long) y * step).get(row, 0, rowBytes);
                System.arraycopy(row, 0, target, y * rowBytes, rowBytes);
            }
            src.position(0);
        }
    }


    /**
     * Stream BGRA (OpenCV) into ARGB int[] without allocating a full image-sized byte buffer.
     * Handles arbitrary row stride (step) and preserves/produces straight or premultiplied as requested.
     */
    private static void packBGRAtoARGB(Mat bgra, int[] argb, int w, int h, boolean premultiply) {
        if (bgra == null || bgra.empty()) {
            throw new IllegalArgumentException("bgra Mat is null/empty");
        }
        if (bgra.channels() != 4 || bgra.depth() != CV_8U) {
            throw new IllegalArgumentException("Expected CV_8UC4 Mat");
        }
        if (argb == null || argb.length < w * h) {
            throw new IllegalArgumentException("argb buffer too small: need " + (w * h));
        }

        final int step = (int) bgra.step();          // bytes per input row (may include padding)
        final BytePointer src = bgra.data();         // view into Mat memory — do NOT close
        final byte[] row = new byte[w * 4];          // small, fixed-size row buffer

        int di = 0; // destination index into argb[]
        for (int y = 0; y < h; y++) {
            // Read exactly w*4 bytes for this logical row starting at y*step
            src.position((long) y * step).get(row, 0, row.length);

            // Convert BGRA -> ARGB
            for (int p = 0; p < row.length; p += 4) {
                int B = row[p] & 0xFF;
                int G = row[p + 1] & 0xFF;
                int R = row[p + 2] & 0xFF;
                int A = row[p + 3] & 0xFF;

                if (premultiply) {
                    // produce ARGB_PRE (R,G,B premultiplied by A) with rounding
                    R = (R * A + 127) / 255;
                    G = (G * A + 127) / 255;
                    B = (B * A + 127) / 255;
                }
                argb[di++] = (A << 24) | (R << 16) | (G << 8) | B;
            }
        }
        // Restore pointer position for safety
        src.position(0);
    }


    //
    // BufferedImage -> Mat helpers (stride-safe) -------------------------------------------------
    //
    private static void copyGrayToMat(BufferedImage img, Mat dst /* CV_8UC1 */) {
        final int w = img.getWidth(), h = img.getHeight();
        final WritableRaster r = img.getRaster();
        final DataBuffer db = r.getDataBuffer();
        final int dstStep = (int) dst.step();

        // Fast path for 1 byte/pixel, band offset 0
        SampleModel sm = r.getSampleModel();
        if (db instanceof DataBufferByte && sm instanceof ComponentSampleModel csm) {
            byte[] data = ((DataBufferByte) db).getData();
            int base = ((DataBufferByte) db).getOffset();
            int scan = csm.getScanlineStride();
            int pixel = csm.getPixelStride();
            int[] bo = csm.getBandOffsets();
            int smTx = r.getSampleModelTranslateX();
            int smTy = r.getSampleModelTranslateY();

            // fast path: tightly packed, one band at offset 0
            if (pixel == 1 && bo.length == 1 && bo[0] == 0) {
                int start = base + (-smTy) * scan + (-smTx) * pixel; // band offset 0
                BytePointer dstPtr = dst.data(); // view – do not close
                if (scan == w) {
                    dstPtr.put(data, start, w * h);
                } else {
                    int srcPos = start;
                    for (int y = 0; y < h; y++, srcPos += scan) {
                        dstPtr.position((long) y * dstStep).put(data, srcPos, w);
                    }
                    dstPtr.position(0);
                }
                return;
            }
        }

        // Generic, safe fallback
        byte[] row = new byte[w];
        BytePointer dstPtr = dst.data(); // view – do not close
        for (int y = 0; y < h; y++) {
            r.getDataElements(0, y, w, 1, row);
            dstPtr.position((long) y * dstStep).put(row);
        }
        dstPtr.position(0);
    }


    private static void copyBGRToMat(BufferedImage src, Mat dst /* CV_8UC3 */) {
        final int w = src.getWidth(), h = src.getHeight();

        // Enforce predictable layout (defensive normalisation).
        if (src.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            BufferedImage converted = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = converted.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
            src = converted;
        }

        final WritableRaster r = src.getRaster();
        final DataBuffer db = r.getDataBuffer();
        final int dstStep = (int) dst.step();
        final BytePointer dstPtr = dst.data(); // view – do not close

        // We normalized callers to TYPE_3BYTE_BGR before getting here (when no alpha),
        // so bytes on each scanline are in B,G,R order.
        if (db instanceof DataBufferByte && r.getSampleModel() instanceof ComponentSampleModel csm) {
            byte[] data = ((DataBufferByte) db).getData();
            int base = ((DataBufferByte) db).getOffset();   // bank offset
            int scan = csm.getScanlineStride();             // bytes per row in the backing buffer
            int pixel = csm.getPixelStride();               // bytes per pixel (expect 3)
            int smTx = r.getSampleModelTranslateX();        // sample-model translate
            int smTy = r.getSampleModelTranslateY();
            int[] bo = csm.getBandOffsets();                // typically {2,1,0} for TYPE_3BYTE_BGR
            int bOff = (bo != null && bo.length >= 3) ? bo[2] : 0; // first byte of a pixel = B

            if (pixel == 3) {
                // Compute the starting index in the backing array for raster coordinate (0,0)
                // Formula from Raster: use (x - smTx), (y - smTy)
                int start = base + (-smTy) * scan + (-smTx) * pixel + bOff;

                // If each row of the subimage is laid out tightly (no padding), we can do one big copy.
                if (scan == w * 3) {
                    dstPtr.put(data, start, w * h * 3);
                    dstPtr.position(0);
                    return;
                }

                // Otherwise copy row-by-row using the computed start.
                int srcPos = start;
                for (int y = 0; y < h; y++, srcPos += scan) {
                    dstPtr.position((long) y * dstStep).put(data, srcPos, w * 3);
                }
                dstPtr.position(0);
                return;
            }
            // Fallthrough to generic path if pixel stride is unexpected
        }

        // Generic, safe fallback (rare for TYPE_3BYTE_BGR, but bulletproof)
        byte[] row = new byte[w * 3];
        for (int y = 0; y < h; y++) {
            r.getDataElements(0, y, w, 1, row);        // bytes are in storage order (B,G,R)
            dstPtr.position((long) y * dstStep).put(row);
        }
        dstPtr.position(0);
    }


    private static void copyABGRToMat(BufferedImage img, Mat dst, boolean premultiplied) {
        final int w = img.getWidth(), h = img.getHeight();
        final WritableRaster r = img.getRaster();
        final DataBuffer db = r.getDataBuffer();
        final SampleModel sm = r.getSampleModel();
        final int dstStep = (int) dst.step();
        final BytePointer dstPtr = dst.data(); // view – do not close

        if (db instanceof DataBufferByte && sm instanceof ComponentSampleModel csm) {
            byte[] data = ((DataBufferByte) db).getData();
            int base = ((DataBufferByte) db).getOffset();
            int scan = csm.getScanlineStride();
            int pixel = csm.getPixelStride();             // usually 4
            int[] bo = csm.getBandOffsets();             // for TYPE_4BYTE_ABGR: [0,1,2,3] = A,B,G,R
            int smTx = r.getSampleModelTranslateX();
            int smTy = r.getSampleModelTranslateY();

            byte[] row = new byte[w * 4];
            int siRow = base + (-smTy) * scan + (-smTx) * pixel;
            for (int y = 0; y < h; y++, siRow += scan) {
                int si = siRow;
                int di = 0;
                for (int x = 0; x < w; x++, si += pixel, di += 4) {
                    int A = data[si + bo[0]] & 0xFF;
                    int B = data[si + bo[1]] & 0xFF;
                    int G = data[si + bo[2]] & 0xFF;
                    int R = data[si + bo[3]] & 0xFF;
                    if (premultiplied) {
                        if (A == 0) {
                            R = G = B = 0;
                        } else {
                            R = (R * 255 + (A >> 1)) / A;
                            G = (G * 255 + (A >> 1)) / A;
                            B = (B * 255 + (A >> 1)) / A;
                        }
                    }
                    row[di] = (byte) B; // BGRA for OpenCV
                    row[di + 1] = (byte) G;
                    row[di + 2] = (byte) R;
                    row[di + 3] = (byte) A;
                }
                dstPtr.position((long) y * dstStep).put(row);
            }
            dstPtr.position(0);
            return;
        }

        // Generic fallback (already translation-safe)
        byte[] rowIn = new byte[w * 4];
        byte[] rowOut = new byte[w * 4];
        for (int y = 0; y < h; y++) {
            r.getDataElements(0, y, w, 1, rowIn);
            for (int i = 0, j = 0; i < w; i++, j += 4) {
                int A = rowIn[j] & 0xFF;
                int B = rowIn[j + 1] & 0xFF;
                int G = rowIn[j + 2] & 0xFF;
                int R = rowIn[j + 3] & 0xFF;
                if (premultiplied) {
                    if (A == 0) {
                        R = G = B = 0;
                    } else {
                        R = (R * 255 + (A >> 1)) / A;
                        G = (G * 255 + (A >> 1)) / A;
                        B = (B * 255 + (A >> 1)) / A;
                    }
                }
                rowOut[j] = (byte) B;
                rowOut[j + 1] = (byte) G;
                rowOut[j + 2] = (byte) R;
                rowOut[j + 3] = (byte) A;
            }
            dst.data().position((long) y * dstStep).put(rowOut);
        }
        dst.data().position(0);
    }


    private static void copyARGBIntToMat(BufferedImage img, Mat dst, boolean premultiplied) {
        final int w = img.getWidth(), h = img.getHeight();
        final WritableRaster r = img.getRaster();
        final DataBuffer db = r.getDataBuffer();
        final SampleModel sm = r.getSampleModel();
        final int dstStep = (int) dst.step();
        final BytePointer dstPtr = dst.data(); // view – do not close

        if (db instanceof DataBufferInt && sm instanceof SinglePixelPackedSampleModel sppm) {
            int[] data = ((DataBufferInt) db).getData();
            int scan = sppm.getScanlineStride();  // in INTs
            int base = ((DataBufferInt) db).getOffset();
            int smTx = r.getSampleModelTranslateX();
            int smTy = r.getSampleModelTranslateY();

            byte[] row = new byte[w * 4];
            int siRow = base + (-smTy) * scan + (-smTx);
            for (int y = 0; y < h; y++, siRow += scan) {
                int si = siRow;
                int di = 0;
                for (int x = 0; x < w; x++, si++, di += 4) {
                    int p = data[si];
                    int A = (p >>> 24) & 0xFF, R = (p >>> 16) & 0xFF, G = (p >>> 8) & 0xFF, B = p & 0xFF;
                    if (premultiplied) {
                        if (A == 0) {
                            R = G = B = 0;
                        } else {
                            R = (R * 255 + (A >> 1)) / A;
                            G = (G * 255 + (A >> 1)) / A;
                            B = (B * 255 + (A >> 1)) / A;
                        }
                    }
                    row[di] = (byte) B;
                    row[di + 1] = (byte) G;
                    row[di + 2] = (byte) R;
                    row[di + 3] = (byte) A;
                }
                dstPtr.position((long) y * dstStep).put(row);
            }
            dstPtr.position(0);
            return;
        }

        // Generic fallback (translation-safe)
        int[] argb = new int[w];
        byte[] row = new byte[w * 4];
        for (int y = 0; y < h; y++) {
            r.getDataElements(0, y, w, 1, argb);
            int di = 0;
            for (int i = 0; i < w; i++, di += 4) {
                int p = argb[i];
                int A = (p >>> 24) & 0xFF, R = (p >>> 16) & 0xFF, G = (p >>> 8) & 0xFF, B = p & 0xFF;
                if (premultiplied) {
                    if (A == 0) {
                        R = G = B = 0;
                    } else {
                        R = (R * 255 + (A >> 1)) / A;
                        G = (G * 255 + (A >> 1)) / A;
                        B = (B * 255 + (A >> 1)) / A;
                    }
                }
                row[di] = (byte) B;
                row[di + 1] = (byte) G;
                row[di + 2] = (byte) R;
                row[di + 3] = (byte) A;
            }
            dst.data().position((long) y * dstStep).put(row);
        }
        dst.data().position(0);
    }

}
