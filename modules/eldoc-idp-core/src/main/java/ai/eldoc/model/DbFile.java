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

import ai.eldoc.model.filemeta.FileMeta;
import ai.eldoc.util.FileUtils;
import ai.eldoc.util.JsonbUtils;
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.util.*;


/**
 * DbFile Model Represents Database File and holds all information about file available in the DB
 */
public class DbFile implements Serializable, BasicModel {

    private static final long serialVersionUID = -9054332452748782357L;
    private static final Logger LOG = LoggerFactory.getLogger(DbFile.class);

    public static final String _FILENAME = "filename";
    public static final String _LENGTH = "length";
    public static final String _UPLOAD_DATE = "uploadDate";
    /**
     * Size of the chunks used to store file
     */
    public static final String _CHUNK_SIZE = "chunkSize";
    public static final String _METADATA = FileMeta._METADATA;
    public static final String _LABEL_FILE_NOT_FOUND = "dbFile.notFound";

    private final DbFileRef _dbFileRef;
    private final String _fileName;
    private final long _length;
    private final int _chunkSize;
    private final Date _uploadDate;
    private final Map<String, Object> _metadata;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public DbFile(DbFileRef dbFileRef, String fileName, long length, int chunkSize, Date uploadDate, Map<String, Object> metadata) {
        _dbFileRef = dbFileRef;
        _fileName = fileName;
        _length = length;
        _chunkSize = chunkSize;
        _uploadDate = uploadDate;
        _metadata = metadata;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Creates instance for the not-found DbFile item<br>
     * This should NEVER happen, as in case this happens - means some inconsistency has happened in the DB<br>
     * Meaning: some document still contain DbFileRef to the DbFile, but actual DbFile already gone from the DB.
     *
     * @param dbFileRef
     * @return
     */
    public static DbFile asNotFound(DbFileRef dbFileRef) {
        return new DbFile(dbFileRef, _LABEL_FILE_NOT_FOUND, 0, 0, new Date(0), new HashMap<>());
    }


    /**
     * Returns true in case file was not found in the DB
     *
     * @return
     */
    @JsonbTransient
    public boolean isFileNotFound() {
        return _LABEL_FILE_NOT_FOUND.equals(getFileName());
    }


    /**
     * Returns ContentType of the file by trying to retrieve it from MetaData. In case not present in Metadata - tries to detect content-type based on the file name.
     * Falls-back to default content-type in case was not able to detect it
     *
     * @return
     */
    public String getContentType() {
        String contentType = null;
        if (getMetadata() != null) {
            if (getMetadata().containsKey(FileMeta._CONTENT_TYPE)) {
                if (StringUtils.isNotBlank((String) getMetadata().get(FileMeta._CONTENT_TYPE))) {
                    contentType = (String) getMetadata().get(FileMeta._CONTENT_TYPE);
                } else {
                    LOG.debug("DbFile[{}] MetaData[{}] property is blank", _dbFileRef, FileMeta._CONTENT_TYPE);
                }
            } else {
                LOG.warn("DbFile[{}] MetaData doesn't contain '{}' property", _dbFileRef, FileMeta._CONTENT_TYPE);
            }
        } else {
            LOG.warn("DbFile[{}] doesn't contain metadata", _dbFileRef);
        }

        if (contentType == null) {
            LOG.debug("DbFile[{}] cannot retrieve content-type from metadata. Using file name to determine content type", _dbFileRef);
            contentType = FileUtils.getContentType(getFileName());
        }

        return contentType;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @Override
    public String getId() {
        if (getDbFileRef() == null) {
            return null;
        }
        return getDbFileRef().getId();
    }


    public DbFileRef getDbFileRef() {
        return _dbFileRef;
    }


    public String getFileName() {
        return _fileName;
    }


    public long getLength() {
        return _length;
    }


    public int getChunkSize() {
        return _chunkSize;
    }


    public Date getUploadDate() {
        return _uploadDate;
    }


    /**
     * Returns Metadata Map
     *
     * @return
     */
    public Map<String, Object> getMetadata() {
        return _metadata;
    }


    /**
     * Returns JSON-representation of the Metadata Map Object
     *
     * @return
     */
    public String getMetadataAsJson() {
        if (_metadata == null) {
            return JsonbUtils.getInstance().toJson(new HashMap<String, Object>());
        } else {
            return JsonbUtils.getInstance().toJson(_metadata);
        }
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return "DbFile{" + _dbFileRef + "; fileName=" + _fileName + '}';
    }


    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(_dbFileRef);
        hash = 97 * hash + Objects.hashCode(_fileName);
        hash = 97 * hash + (int) (_length ^ (_length >>> 32));
        hash = 97 * hash + _chunkSize;
        hash = 97 * hash + Objects.hashCode(_uploadDate);
        hash = 97 * hash + Objects.hashCode(_metadata);
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
        final DbFile other = (DbFile) obj;
        if (_length != other._length) {
            return false;
        }
        if (_chunkSize != other._chunkSize) {
            return false;
        }
        if (!Objects.equals(_dbFileRef, other._dbFileRef)) {
            return false;
        }
        if (!Objects.equals(_fileName, other._fileName)) {
            return false;
        }
        if (!Objects.equals(_uploadDate, other._uploadDate)) {
            return false;
        }
        if (!Objects.equals(_metadata, other._metadata)) {
            return false;
        }
        return true;
    }

}
