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
package ai.eldoc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;


/**
 * Mime Type Utils
 */
public class MimeType {

    private static final Logger LOG = LoggerFactory.getLogger(MimeType.class);

    public static final String DEFAULT = "application/octet-stream";
    public static final String TEXT_PLAIN = "text/plain";
    public static final String TEXT_MARKDOWN = "text/markdown";
    public static final String APPLICATION_PDF = "application/pdf";
    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_TIFF = "image/tiff";
    public static final String IMAGE_WEBP = "image/webp";

    private static final Properties _mimeTypes;


    static {
        _mimeTypes = new Properties();
        try (InputStream inputStream = MimeType.class.getClassLoader().getResourceAsStream("mimeTypes.properties")) {
            _mimeTypes.load(inputStream);
        } catch (Exception e) {
            LOG.error("Cannot load MimeTypes resource bundle", e);
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns Content Type base on the given file extension (extension only, without dot, e.g.: "pdf", "jpg")
     *
     * @param extension
     * @return
     */
    public static String getMimeType(String extension) {
        if (extension == null || extension.isBlank()) {
            return DEFAULT;
        }
        String mimeType = _mimeTypes.getProperty(extension.toLowerCase());
        return mimeType != null ? mimeType : DEFAULT;
    }


    /**
     * Tries to get the mimeType by checking if the given fileName ends with one of the known extensions
     *
     * @param fileName
     * @return
     */
    public static String getMimeTypeSafe(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return DEFAULT;
        }

        String fileNameLowerCase = fileName.toLowerCase();
        for (Map.Entry entry : _mimeTypes.entrySet()) {
            if (fileNameLowerCase.endsWith("." + entry.getKey())) {
                return (String) entry.getValue();
            }
        }

        return DEFAULT;
    }

}
