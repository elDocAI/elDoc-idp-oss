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

import ai.eldoc.pdf.PdfUtils;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;


/**
 * IDP-specific PDF utilities
 */
public class RecoPdfUtils implements Serializable {

    private static final long serialVersionUID = 311369814072755656L;

    public static final String ELDOC_JVM_OPTION_IDP_PREPROCESS_WITH_ACCESSIBILITY = "ai.eldoc.idp.preprocess.enableAccessibility";

    private static final float TEXT_OPACITY = 0.00001f;

    private static final String DOC_INFO_PROP_PREPROCESSED = "preProc"; // preprocessed by elDoc (machine-generated or image-based)
    private static final String DOC_INFO_PROP_MACHINE_GENERATED = "mGen"; // Machine-Generated PDF
    private static final String DOC_INFO_PROP_INTERNAL_OCR = "intOcr"; // Preprocessed using intrnal OCR


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private RecoPdfUtils() {

    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Adds required properties to indicate it is IDP Preprocessed PDF document
     *
     * @param document
     */
    public static void addPreprocessedProperties(PDDocument document) {
        Map<String, Object> properties = Map.of(
                DOC_INFO_PROP_PREPROCESSED, true
        );
        PdfUtils.addEldocProperties(document, properties);
    }


    /**
     * Adds required properties to indicate it is Debug PDF document
     *
     * @param document
     */
    public static void addDebugProperties(PDDocument document) {
        Map<String, Object> properties = Map.of(
                "debug", true
        //                DOC_INFO_PROP_PREPROCESSED, true,
        //                DOC_INFO_PROP_MACHINE_GENERATED, machineGenerated,
        //                DOC_INFO_PROP_INTERNAL_OCR, internalOcr
        );
        PdfUtils.addEldocProperties(document, properties);
    }


    public static void setPageProperties(PDPage page, boolean machineGenerated, boolean internalOcr) {
        Map<String, Object> properties = Map.of(
                DOC_INFO_PROP_PREPROCESSED, true,
                DOC_INFO_PROP_MACHINE_GENERATED, machineGenerated,
                DOC_INFO_PROP_INTERNAL_OCR, internalOcr
        );
        PdfUtils.addEldocProperties(page, properties);
    }


    public static boolean isMachineGenerated(PDPage page) {
        return PdfUtils.getEldocProperties(page).getBoolean(DOC_INFO_PROP_MACHINE_GENERATED, false);
    }


    public static boolean isAccessibilityFixesEnabled() {
        return Boolean.getBoolean(ELDOC_JVM_OPTION_IDP_PREPROCESS_WITH_ACCESSIBILITY);
    }


    public static void applyAccessibilityFixes(PDPageContentStream contentStream) throws IOException {
        PDExtendedGraphicsState extGS = new PDExtendedGraphicsState();
        extGS.setStrokingAlphaConstant(TEXT_OPACITY);
        extGS.setNonStrokingAlphaConstant(TEXT_OPACITY);
        contentStream.setGraphicsStateParameters(extGS);
    }
}
