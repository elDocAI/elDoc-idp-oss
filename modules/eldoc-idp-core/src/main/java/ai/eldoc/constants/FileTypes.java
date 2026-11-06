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
package ai.eldoc.constants;

import java.util.Map;
import java.util.stream.Stream;


/**
 * File Types Constants
 */
public final class FileTypes {

    public static final int OFFICE = 1;
    public static final int OFFICE_TEXT = 11;
    public static final int OFFICE_SPREADSHEET = 12;
    public static final int OFFICE_PRESENTATION = 13;
    public static final int OFFICE_DRAWING = 14;
    public static final int IMAGE = 2;
    public static final int VIDEO = 3;
    public static final int AUDIO = 4;
    public static final int PLAIN_TEXT = 5;

    /**
     * All supported by CollaboraOnline Writer 25.04 extensions (based on /hosting/discovery)
     */
    public static final String[] OFFICE_TEXT_FILE_EXTENSIONS = {
            ".sxw", ".odt", ".fodt", ".stw", ".ott", ".doc", ".dot", ".docx", ".docm", ".dotx", ".dotm", ".wpd", ".pdb", ".hwp",
            ".wps", ".wri", ".lrf", ".mw", ".rtf", ".txt", ".fb2", ".cwk", ".pages", ".abw", ".602", ".sxg", ".odm", ".otm", ".oth"
    };

    /**
     * All supported by CollaboraOnline Calc 25.04 extensions (based on /hosting/discovery)
     */
    public static final String[] OFFICE_SPREADSHEET_FILE_EXTENSIONS = {
            ".sxc", ".ods", ".fods", ".stc", ".ots", ".xls", ".xla", ".xltx", ".xltm", ".xlsx", ".xlsb", ".xlsm", ".dif", ".slk",
            ".csv", ".tsv", ".dbf", ".wk1", ".wks", ".xlr", ".123", ".qpw", ".wb1", ".wq1", ".wq2", ".gnumeric", ".numbers"
    };

    /**
     * All supported by CollaboraOnline Impress 25.04 extensions (based on /hosting/discovery)
     */
    public static final String[] OFFICE_PRESENTATION_FILE_EXTENSIONS = {
            ".sxi", ".odp", ".fodp", ".sti", ".otp", ".ppt", ".pot", ".pptx", ".pptm", ".potx", ".potm", ".ppsx", ".cgm", ".key"
    };

    /**
     * All supported by CollaboraOnline Draw 25.04 extensions (based on /hosting/discovery), except: "bmp", "svg", "png", "gif", "tiff", "jpg", "jpeg"
     */
    public static final String[] OFFICE_DRAW_FILE_EXTENSIONS = {
            ".sxd", ".odg", ".fodg", ".std", ".otg", ".dxf", ".emf", ".wmf", ".cdr", ".vsd", ".vsdx", ".vss", ".pub", ".p65", ".wpg", ".fh", ".pdf"
    };

    /**
     * Supported plain-text file extensions. TXT is not included, as it is supported by CollaboraOnline Writer
     */
    public static final String[] PLAIN_TEXT_FILE_EXTENSIONS = {".json", ".md"};

    /**
     * All supported by CollaboraOnline Draw 25.04 extensions (based on /hosting/discovery)
     */
    public static final String[] OFFICE_FILE_EXTENSIONS = Stream.of(
            OFFICE_TEXT_FILE_EXTENSIONS,
            OFFICE_SPREADSHEET_FILE_EXTENSIONS,
            OFFICE_PRESENTATION_FILE_EXTENSIONS,
            OFFICE_DRAW_FILE_EXTENSIONS
    ).flatMap(Stream::of).toArray(String[]::new);

    /**
     * All supported image files extensions
     */
    public static final String[] IMAGE_FILE_EXTENSIONS = {
            ".jpeg", ".jpg", ".jpe", ".jif", ".jfif", ".jfi", ".gif", ".png", ".svg", ".bmp", ".dib", ".ico", ".tif", ".tiff", ".webp"
    };

    /**
     * TIFF file extensions
     */
    public static final String[] IMAGE_TIFF_FILE_EXTENSIONS = {
            ".tif", ".tiff"
    };

    public static final Map<Integer, String> MSOFFICE_FILE_EXT_BY_FILE_TYPE_MAP = Map.of(
            OFFICE_TEXT, ".docx",
            OFFICE_SPREADSHEET, ".xlsx",
            OFFICE_DRAWING, ".vsdx",
            OFFICE_PRESENTATION, ".pptx");

    public static final Map<Integer, String> OPENDOCUMENT_FILE_EXT_BY_FILE_TYPE_MAP = Map.of(
            OFFICE_TEXT, ".odt",
            OFFICE_SPREADSHEET, ".ods",
            OFFICE_DRAWING, ".odg",
            OFFICE_PRESENTATION, ".odp");

    /**
     * All video files extensions
     */
    public static final String[] VIDEO_FILE_EXTENSIONS = {".mp4", ".webm", ".ogv", ".mov", ".avi", ".wmv", ".avchd", ".flv", ".mkv", ".vob", ".mpeg", ".mpg", ".mpv", ".3gp"};

    /**
     * Video files extensions that provide online preview support
     */
    public static final String[] VIDEO_PREVIEW_FILE_EXTENSIONS = {".mp4", ".webm", ".ogv"};


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Returns file type (Office, Image, other to be added) code based on file extension
     *
     * @param fileName Name of the file
     * @return file type code, or 0 "zero" in case the file type cannot be determined
     */
    public static int getFileType(String fileName) {
        if (fileName == null) {
            return 0;
        }
        String fileNameLC = fileName.toLowerCase();
        for (String officeExt : OFFICE_FILE_EXTENSIONS) {
            if (fileNameLC.endsWith(officeExt)) {
                return OFFICE;
            }
        }
        for (String imageExt : IMAGE_FILE_EXTENSIONS) {
            if (fileNameLC.endsWith(imageExt)) {
                return IMAGE;
            }
        }
        for (String videoExt : VIDEO_FILE_EXTENSIONS) {
            if (fileNameLC.toLowerCase().endsWith(videoExt)) {
                return VIDEO;
            }
        }
        for (String plainTextExt : PLAIN_TEXT_FILE_EXTENSIONS) {
            if (fileNameLC.toLowerCase().endsWith(plainTextExt)) {
                return PLAIN_TEXT;
            }
        }
        return 0;
    }


    public static boolean isOfficeSpreadsheetFile(String fileName) {
        String fileNameLC = fileName.toLowerCase();
        for (String officeExt : OFFICE_SPREADSHEET_FILE_EXTENSIONS) {
            if (fileNameLC.endsWith(officeExt)) {
                return true;
            }
        }
        return false;
    }


    public static boolean isOfficeWriterFile(String fileName) {
        String fileNameLC = fileName.toLowerCase();
        for (String officeExt : OFFICE_TEXT_FILE_EXTENSIONS) {
            if (fileNameLC.endsWith(officeExt)) {
                return true;
            }
        }
        return false;
    }


    public static boolean isVideoPreviewAvailable(String fileName) {
        String fileNameLC = fileName.toLowerCase();
        for (String imageExt : VIDEO_PREVIEW_FILE_EXTENSIONS) {
            if (fileNameLC.endsWith(imageExt)) {
                return true;
            }
        }
        return false;
    }


    public static boolean isTiffFile(String fileName) {
        String fileNameLC = fileName.toLowerCase();
        for (String imageExt : IMAGE_TIFF_FILE_EXTENSIONS) {
            if (fileNameLC.endsWith(imageExt)) {
                return true;
            }
        }
        return false;
    }

}
