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
package ai.eldoc.model;

import java.io.Serializable;
import java.util.Objects;


/**
 * Preview Item Model
 */
public class PreviewItem implements Serializable {

    private static final long serialVersionUID = 3955453620658581983L;

    private final DbFile _file;
    private final String _author;
    private final int _previewType;
    private final boolean _allowDownload;
    private final int _dpi;
    private final String _shareId;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * Default constructor
     *
     * @param dbFile sets dbFile to be previewed
     * @param author
     * @param previewType defines preview type
     * @param allowDownload
     * @param dpi for PDF files defines dpi for rendering
     * @param shareId ID of the Share in case file to be opened from the shared link
     */
    public PreviewItem(DbFile dbFile, String author, int previewType, boolean allowDownload, int dpi, String shareId) {
        _file = dbFile;
        _author = author;
        _previewType = previewType;
        _allowDownload = allowDownload;
        _dpi = dpi;
        _shareId = shareId;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public DbFile getFile() {
        return _file;
    }


    public String getAuthor() {
        return _author;
    }


    public int getPreviewType() {
        return _previewType;
    }


    public boolean isAllowDownload() {
        return _allowDownload;
    }


    public int getDpi() {
        return _dpi;
    }


    /**
     * Returns shareId in case file is opened from the shared link
     *
     * @return
     */
    public String getShareId() {
        return _shareId;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return "PreviewItem{" + "_file=" + _file + ", _author=" + _author + ", _previewType=" + _previewType + ", _allowDownload=" + _allowDownload + ", _dpi=" + _dpi + ", _shareId=" + _shareId + '}';
    }


    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(_file);
        hash = 97 * hash + Objects.hashCode(_author);
        hash = 97 * hash + _previewType;
        hash = 97 * hash + (_allowDownload ? 1 : 0);
        hash = 97 * hash + _dpi;
        hash = 97 * hash + Objects.hashCode(_shareId);
        return hash;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PreviewItem other = (PreviewItem) obj;
        if (_previewType != other._previewType) {
            return false;
        }
        if (_allowDownload != other._allowDownload) {
            return false;
        }
        if (_dpi != other._dpi) {
            return false;
        }
        if (!Objects.equals(_author, other._author)) {
            return false;
        }
        if (!Objects.equals(_file, other._file)) {
            return false;
        }
        if (!Objects.equals(_shareId, other._shareId)) {
            return false;
        }
        return true;
    }
}
