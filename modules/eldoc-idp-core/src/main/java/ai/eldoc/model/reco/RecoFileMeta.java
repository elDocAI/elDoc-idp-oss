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
package ai.eldoc.model.reco;

import ai.eldoc.model.PropertyAwareModel;
import ai.eldoc.model.TagAwareModel;
import ai.eldoc.model.filemeta.FileMeta;
import ai.eldoc.model.filemeta.TypeAwareFileMeta;
import ai.eldoc.util.JsonbUtils;
import jakarta.json.bind.annotation.JsonbProperty;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.jspecify.annotations.Nullable;
import java.io.Serial;
import java.util.*;


/**
 * Reco File metadata
 */
public class RecoFileMeta extends FileMeta implements PropertyAwareModel, TagAwareModel {

    @Serial
    private static final long serialVersionUID = -6132834163990572524L;

    public static final String IDP_AUTO_CLASSIFICATION = "idp_auto_classification";

    public static final String _FORCE_PREPROCESSING = "forcePrep";
    public static final String _LANGS = "langs";
    public static final String _PAGE_DIMENSIONS = "pageDmns";
    public static final String _PAGE_COUNT = "pageCnt";
    public static final String _PAGE_IDX_OFFSET = "pageIdxOffset";
    public static final String _TYPE = TypeAwareFileMeta._TYPE;

    public static final int TYPE_DEFAULT = TypeAwareFileMeta.TYPE_DEFAULT;

    private boolean _forcePreprop; // TODO: To be stored in properties as set of separate advanced IDP properties
    private List<String> _langs;
    private List<String> _pageDimList; // holds list with pages dimensions in format WxH, e.g.: ["2600x3500","2600x3500"]
    private int _type;
    private int _pageCount;
    private int _pageIdxOffset;
    private List<String> _tags;
    private Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFileMeta() {
        _langs = new ArrayList<>();
        _pageDimList = new ArrayList<>();
        _properties = new TreeMap<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static RecoFileMeta fromJson(String jsonStr) {
        return JsonbUtils.getInstance().fromJson(jsonStr, RecoFileMeta.class);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_FORCE_PREPROCESSING)
    @JsonbProperty(_FORCE_PREPROCESSING)
    public boolean isForcePreprop() {
        return _forcePreprop;
    }


    @BsonProperty(_FORCE_PREPROCESSING)
    @JsonbProperty(_FORCE_PREPROCESSING)
    public RecoFileMeta setForcePreprop(boolean forcePreprop) {
        _forcePreprop = forcePreprop;
        return this;
    }


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


    public List<String> getLangs() {
        return _langs;
    }


    public RecoFileMeta setLangs(List<String> langs) {
        _langs.clear();
        if (langs != null && !langs.isEmpty()) {
            _langs.addAll(new LinkedHashSet<>(langs));
        }
        return this;
    }


    @BsonProperty(_PAGE_DIMENSIONS)
    @JsonbProperty(_PAGE_DIMENSIONS)
    public List<String> getPagesDimensions() {
        return _pageDimList;
    }


    @BsonProperty(_PAGE_DIMENSIONS)
    @JsonbProperty(_PAGE_DIMENSIONS)
    public RecoFileMeta setPagesDimensions(List<String> pageDimList) {
        _pageDimList.clear();
        if (pageDimList != null && !pageDimList.isEmpty()) {
            _pageDimList.addAll(pageDimList);
        }
        return this;
    }


    @BsonProperty(_PAGE_COUNT)
    @JsonbProperty(_PAGE_COUNT)
    public int getPageCount() {
        return _pageCount;
    }


    @BsonProperty(_PAGE_COUNT)
    @JsonbProperty(_PAGE_COUNT)
    public void setPageCount(int pageCount) {
        _pageCount = pageCount;
    }


    @BsonProperty(_PAGE_IDX_OFFSET)
    @JsonbProperty(_PAGE_IDX_OFFSET)
    public int getPageIdxOffset() {
        return _pageIdxOffset;
    }


    @BsonProperty(_PAGE_IDX_OFFSET)
    @JsonbProperty(_PAGE_IDX_OFFSET)
    public void setPageIdxOffset(int pageIdxOffset) {
        _pageIdxOffset = pageIdxOffset;
    }


    @BsonProperty(_PROPERTIES)
    @JsonbProperty(_PROPERTIES)
    @Override
    public Map<String, Object> getProperties() {
        return _properties;
    }


    @Override
    @Nullable
    @JsonbProperty(_TAGS)
    @BsonProperty(_TAGS)
    public List<String> getTags() {
        return _tags;
    }


    @Override
    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS_JSON)
    public void setTags(@Nullable List<String> tags) {
        _tags = normalizeTags(tags);
    }


    @BsonProperty(_PROPERTIES)
    @JsonbProperty(_PROPERTIES)
    public void setProperties(Map<String, Object> properties) {
        _properties.clear();
        if (properties != null && !properties.isEmpty()) {
            _properties.putAll(properties);
        }
    }


    //
    // Generic methods --------------------------------------------------------------------------------
    //


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RecoFileMeta that = (RecoFileMeta) o;
        return _forcePreprop == that._forcePreprop
                && _type == that._type
                && _pageCount == that._pageCount
                && _pageIdxOffset == that._pageIdxOffset
                && Objects.equals(_langs, that._langs)
                && Objects.equals(_pageDimList, that._pageDimList)
                && Objects.equals(_tags, that._tags)
                && Objects.equals(_properties, that._properties);
    }


    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _forcePreprop, _langs, _pageDimList, _type, _pageCount, _pageIdxOffset, _tags, _properties);
    }
}
