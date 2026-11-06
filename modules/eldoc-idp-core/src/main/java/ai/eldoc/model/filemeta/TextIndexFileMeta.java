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
package ai.eldoc.model.filemeta;

import ai.eldoc.util.JsonbUtils;
import jakarta.json.bind.annotation.JsonbProperty;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.util.*;


/**
 * TextIndex File Meta Model
 */
public final class TextIndexFileMeta extends FileMeta {

    @Serial
    private static final long serialVersionUID = 372589784494336228L;

    /**
     * Array of the first bytes of each page in the file (i.e., byte offset of pages)
     */
    public static final String _PAGE_OFFSETS = "pgsOfst";

    /**
     * File type: 0 or absent = Workflow related file, 1 - FileManager file
     */
    public static final String _TYPE = TypeAwareFileMeta._TYPE;

    /**
     * ID of the folder where this file is placed
     */
    public static final String _FOLDER_ID = "folderId";

    /**
     * File reference to the Original/Source file
     */
    public static final String _REF_ID = "refId";

    public static final String _PATH_METADATA_REF_ID = _METADATA + "." + _REF_ID;

    public static final int TYPE_TEXT_INDEX = TypeAwareFileMeta.TYPE_TEXT_INDEX;


    //
    // Model Fields -------------------------------------------------------------------------------
    //
    private int _type;
    private String _folderId;
    private String _refId;
    private List<Integer> _pageOffsets;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public TextIndexFileMeta() {
        _pageOffsets = new ArrayList<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static TextIndexFileMeta fromJson(String jsonStr) {
        return JsonbUtils.getInstance().fromJson(jsonStr, TextIndexFileMeta.class);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE)
    public int getType() {
        return _type;
    }


    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE)
    public void setType(int type) {
        _type = type;
    }


    @BsonProperty(_FOLDER_ID)
    @JsonbProperty(_FOLDER_ID)
    public String getFolderId() {
        return _folderId;
    }


    @BsonProperty(_FOLDER_ID)
    @JsonbProperty(_FOLDER_ID)
    public void setFolderId(String folderId) {
        _folderId = folderId;
    }


    @BsonProperty(_PAGE_OFFSETS)
    @JsonbProperty(_PAGE_OFFSETS)
    public List<Integer> getPageOffsets() {
        return _pageOffsets;
    }


    @BsonProperty(_PAGE_OFFSETS)
    @JsonbProperty(_PAGE_OFFSETS)
    public void setPageOffsets(List<Integer> pageOffsets) {
        _pageOffsets = pageOffsets;
    }


    @BsonProperty(_REF_ID)
    @JsonbProperty(_REF_ID)
    public String getRefId() {
        return _refId;
    }


    @BsonProperty(_REF_ID)
    @JsonbProperty(_REF_ID)
    public void setRefId(String refId) {
        _refId = refId;
    }


    //
    // Generic methods --------------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TextIndexFileMeta that = (TextIndexFileMeta) o;
        return _type == that._type
                && Objects.equals(_folderId, that._folderId)
                && Objects.equals(_refId, that._refId)
                && Objects.equals(_pageOffsets, that._pageOffsets);
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _type, _folderId, _refId, _pageOffsets);
    }
}
