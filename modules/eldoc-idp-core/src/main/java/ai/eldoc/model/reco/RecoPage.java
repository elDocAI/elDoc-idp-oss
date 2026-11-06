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
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Model represents Master Form Page
 */
public final class RecoPage implements PropertyAwareModel, Serializable {

    private static final long serialVersionUID = -7470479650939003251L;

    public static final String _PAGE_INDEX = "srcIdx";
    public static final String _FIELDS = "fields";
    public static final String _KEYWORDS = "kwrds";

    private int _srcIdx; //page index in the source file (PDF or tiff), in case file support multipages
    private List<RecoField> _fields;
    private List<String> _keywords;
    private Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * Constructor for using with MongoDB POJO mainly
     */
    protected RecoPage() {
        _fields = new ArrayList<>();
        _keywords = new ArrayList<>();
        _properties = new HashMap<>();
    }


    /**
     * Creates new instance of the RecoPage and sets its index in the RecoForm
     *
     * @param pageIndex
     */
    public RecoPage(int pageIndex) {
        this();
        setPageIndex(pageIndex);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Arranges fields Left-to-Right and Top-to-Bottom based on the Anchor position
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    List<RecoField> getFieldsArrangedByAnchorRect() {
        return getFields().stream()
                .sorted(Comparator.comparingDouble((RecoField rf) -> rf.getAnchorRegion().getY()).thenComparingDouble((RecoField rf) -> rf.getAnchorRegion().getX()))
                .collect(Collectors.toList());
    }


    /**
     * Arranges fields Left-to-Right and Top-to-Bottom based on the Value position
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    List<RecoField> getFieldsArrangedByValueRect() {
        return getFields().stream()
                .sorted(Comparator.comparingDouble((RecoField rf) -> rf.getValueRegion().getY()).thenComparingDouble((RecoField rf) -> rf.getValueRegion().getX()))
                .collect(Collectors.toList());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns index of the page in the source file
     *
     * @return
     */
    @BsonProperty(_PAGE_INDEX)
    @JsonbProperty(_PAGE_INDEX)
    public int getPageIndex() {
        return _srcIdx;
    }


    /**
     * Sets index of the page in the source file
     *
     * @param pageIndex
     */
    @BsonProperty(_PAGE_INDEX)
    @JsonbProperty(_PAGE_INDEX)
    public void setPageIndex(int pageIndex) {
        _srcIdx = pageIndex;
    }


    public List<RecoField> getFields() {
        return _fields;
    }


    public void setFields(List<RecoField> fields) {
        _fields.clear();
        if (fields != null && !fields.isEmpty()) {
            _fields.addAll(fields);
        }
    }


    /**
     * Returns list of keywords
     *
     * @return
     */
    @BsonProperty(_KEYWORDS)
    @JsonbProperty(_KEYWORDS)
    public List<String> getKeywords() {
        return _keywords;
    }


    /**
     * Sets keywords by retrieving only distinct values
     *
     * @param keywords
     */
    @BsonProperty(_KEYWORDS)
    @JsonbProperty(_KEYWORDS)
    public void setKeywords(List<String> keywords) {
        _keywords.clear();
        if (keywords != null && !keywords.isEmpty()) {
            _keywords.addAll(keywords.stream().distinct().collect(Collectors.toList()));
        }
    }


    @Override
    @BsonProperty(_PROPERTIES)
    @JsonbProperty(_PROPERTIES)
    public Map<String, Object> getProperties() {
        return _properties;
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
    // Getters for properties ---------------------------------------------------------------------
    //
    /**
     * Shortcut method for getting access to the RecoPage general properties
     *
     * @return
     */
    @BsonIgnore
    @JsonbTransient
    public PageProperties getProp() {
        return new PageProperties(this);
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + _srcIdx;
        hash = 29 * hash + Objects.hashCode(_fields);
        hash = 29 * hash + Objects.hashCode(_keywords);
        hash = 29 * hash + Objects.hashCode(_properties);
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
        final RecoPage other = (RecoPage) obj;
        if (_srcIdx != other._srcIdx) {
            return false;
        }
        if (!Objects.equals(_fields, other._fields)) {
            return false;
        }
        if (!Objects.equals(_keywords, other._keywords)) {
            return false;
        }
        if (!Objects.equals(_properties, other._properties)) {
            return false;
        }
        return true;
    }

}
