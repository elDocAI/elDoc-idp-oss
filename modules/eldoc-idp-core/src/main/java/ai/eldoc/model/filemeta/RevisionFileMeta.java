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
import java.util.Date;
import java.util.Objects;


/**
 * Revision File-metadata Model
 */
public final class RevisionFileMeta extends TypeAwareFileMeta {

    @Serial
    private static final long serialVersionUID = 7559775152896684918L;

    /**
     * Used in GridFS metadata, indicates to which file this file is revision
     */
    public static final String _REVISION_TO = "rvTo";
    public static final String _PATH_METADATA_REVISION_TO = _METADATA + "." + _REVISION_TO;

    /**
     * Used in GridFS metadata, indicates the root file of the revision chain.
     * Helps to optimize FT Search: when this revision gets a hit in search results, we need to know the root revision file id,
     * as the main document (e.g., Workflow) contains ref to the initial (root) file version only.
     */
    public static final String _REVISION_ROOT = "rvRt";
    public static final String _PATH_METADATA_REVISION_ROOT = _METADATA + "." + _REVISION_ROOT;

    /**
     * Stores ObjectId of the latest revision
     */
    public static final String _REVISION_LAST_ID = "rvLst";
    public static final String _PATH_METADATA_REVISION_LAST_ID = _METADATA + "." + _REVISION_LAST_ID;

    /**
     * Stores Size of the latest revision
     */
    public static final String _REVISION_LAST_SIZE = "rvLstSz";
    public static final String _PATH_METADATA_REVISION_LAST_SIZE = _METADATA + "." + _REVISION_LAST_SIZE;

    /**
     * Stores Upload Date of the latest revision
     */
    public static final String _REVISION_LAST_DATE = "rvLstDt";
    public static final String _PATH_METADATA_REVISION_LAST_DATE = _METADATA + "." + _REVISION_LAST_DATE;

    private String _revisionTo;
    private String _revisionRoot;
    private String _revisionLast;
    private long _revisionLastSize;
    private Date _revisionLastDate;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static RevisionFileMeta fromJson(String jsonStr) {
        return JsonbUtils.getInstance().fromJson(jsonStr, RevisionFileMeta.class);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_REVISION_TO)
    @JsonbProperty(_REVISION_TO)
    public String getRevisionTo() {
        return _revisionTo;
    }


    @BsonProperty(_REVISION_TO)
    @JsonbProperty(_REVISION_TO)
    public void setRevisionTo(String revisionTo) {
        _revisionTo = revisionTo;
    }


    @BsonProperty(_REVISION_ROOT)
    @JsonbProperty(_REVISION_ROOT)
    public String getRevisionRoot() {
        return _revisionRoot;
    }


    @BsonProperty(_REVISION_ROOT)
    @JsonbProperty(_REVISION_ROOT)
    public void setRevisionRoot(String revisionRoot) {
        _revisionRoot = revisionRoot;
    }


    @BsonProperty(_REVISION_LAST_ID)
    @JsonbProperty(_REVISION_LAST_ID)
    public String getLastRevisionId() {
        return _revisionLast;
    }


    @BsonProperty(_REVISION_LAST_ID)
    @JsonbProperty(_REVISION_LAST_ID)
    public void setLastRevisionId(String lstRv) {
        _revisionLast = lstRv;
    }


    @BsonProperty(_REVISION_LAST_SIZE)
    @JsonbProperty(_REVISION_LAST_SIZE)
    public long getLastRevisionSize() {
        return _revisionLastSize;
    }


    @BsonProperty(_REVISION_LAST_SIZE)
    @JsonbProperty(_REVISION_LAST_SIZE)
    public void setLastRevisionSize(long lstRvSz) {
        _revisionLastSize = lstRvSz;
    }


    @BsonProperty(_REVISION_LAST_DATE)
    @JsonbProperty(_REVISION_LAST_DATE)
    public Date getLastRevisionDate() {
        return _revisionLastDate;
    }


    @BsonProperty(_REVISION_LAST_DATE)
    @JsonbProperty(_REVISION_LAST_DATE)
    public void setLastRevisionDate(Date lstRvDt) {
        _revisionLastDate = lstRvDt;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public String toString() {
        return "RevisionFileMeta{" + "_revisionTo=" + _revisionTo + ", _revisionRoot=" + _revisionRoot + ", _revisionLast=" + _revisionLast + ", _revisionLastSize=" + _revisionLastSize + ", _revisionLastDate=" + _revisionLastDate + '}';
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        RevisionFileMeta that = (RevisionFileMeta) o;
        return _revisionLastSize == that._revisionLastSize
                && Objects.equals(_revisionTo, that._revisionTo)
                && Objects.equals(_revisionRoot, that._revisionRoot)
                && Objects.equals(_revisionLast, that._revisionLast)
                && Objects.equals(_revisionLastDate, that._revisionLastDate);
    }


    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(_revisionTo);
        result = 31 * result + Objects.hashCode(_revisionRoot);
        result = 31 * result + Objects.hashCode(_revisionLast);
        result = 31 * result + Long.hashCode(_revisionLastSize);
        result = 31 * result + Objects.hashCode(_revisionLastDate);
        return result;
    }
}
