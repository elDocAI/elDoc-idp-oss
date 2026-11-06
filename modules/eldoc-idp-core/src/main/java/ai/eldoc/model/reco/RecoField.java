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
import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 * Recognition Form Field data model
 */
public class RecoField implements PropertyAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = -8017571145852912446L;

    public static final String _NAME = "name";
    public static final String _VALUE_REGION = "valueRegion";
    public static final String _ANCHOR_REGION = "anchorRegion";
    public static final String _ANCHOR_TEXT = "anchorText";
    public static final String _TYPE = "type";
    public static final String _REQUIRED = "required";
    public static final String _CONFIDENCE_THRESHOLD = "confThreshold";
    public static final String _TAGS = "tags";
    public static final String _COLUMNS = "columns";
    public static final String _OP_MARKS = "opMarks";

    public static int CONF_THRESH_DEFAULT = 75;

    private String _name;
    private Rectangle _valueRegion;
    private Rectangle _anchorRegion;
    private String _anchorText;
    private RecoFieldDataType _type;
    private int _confThreshold;
    private boolean _required;
    private Set<String> _tags;
    private Map<String, Object> _properties;
    private List<RecoField> _columns;
    private List<RecoField> _opMarks;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    protected RecoField() {
        _columns = new ArrayList<>();
        _opMarks = new ArrayList<>();
        _tags = new HashSet<>();
        _properties = new HashMap<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static RecoField createDefault() {
        RecoField field = new RecoField();
        field.setType(RecoFieldDataType.TEXT);
        field.setConfidenceThreshold(CONF_THRESH_DEFAULT);
        field.setName("TextField");
        field.setRequired(true);
        field.setValueRegion(new Rectangle(200, 0, 300, 150));
        field.setAnchorRegion(new Rectangle(0, 0, 200, 75));
        return field;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getName() {
        return _name;
    }


    public void setName(String name) {
        _name = name;
    }


    public Rectangle getValueRegion() {
        return _valueRegion;
    }


    public void setValueRegion(Rectangle valueRegion) {
        _valueRegion = valueRegion;
    }


    public Rectangle getAnchorRegion() {
        return _anchorRegion;
    }


    public void setAnchorRegion(Rectangle anchorRegion) {
        _anchorRegion = anchorRegion;
    }


    public String getAnchorText() {
        return _anchorText;
    }


    public void setAnchorText(String anchorText) {
        _anchorText = anchorText;
    }


    public RecoFieldDataType getType() {
        return _type;
    }


    public void setType(RecoFieldDataType dataType) {
        _type = dataType;
    }


    @BsonProperty(_CONFIDENCE_THRESHOLD)
    @JsonbProperty(_CONFIDENCE_THRESHOLD)
    public int getConfidenceThreshold() {
        return _confThreshold;
    }


    @BsonProperty(_CONFIDENCE_THRESHOLD)
    @JsonbProperty(_CONFIDENCE_THRESHOLD)
    public void setConfidenceThreshold(int confidenceThreshold) {
        _confThreshold = confidenceThreshold;
    }


    public boolean isRequired() {
        return _required;
    }


    public void setRequired(boolean required) {
        _required = required;
    }


    public List<RecoField> getColumns() {
        return _columns;
    }


    public void setColumns(List<RecoField> columns) {
        _columns.clear();
        if (columns != null && !columns.isEmpty()) {
            _columns.addAll(columns);
        }
    }


    public List<RecoField> getOpMarks() {
        return _opMarks;
    }


    public void setOpMarks(List<RecoField> opMarks) {
        _opMarks.clear();
        if (opMarks != null && !opMarks.isEmpty()) {
            _opMarks.addAll(opMarks);
        }
    }


    public Set<String> getTags() {
        return _tags;
    }


    /**
     * Setter for Tags field. Required by UI
     *
     * @param tags RecoField tags
     */
    public void setTags(Set<String> tags) {
        _tags.clear();
        if (tags != null && !tags.isEmpty()) {
            _tags.addAll(tags);
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
    // Ignoring Getters & Setters (which are not serialized to DB) --------------------------------
    //
    @BsonIgnore
    @JsonbTransient
    public boolean isTypeText() {
        return _type == RecoFieldDataType.TEXT;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeOmr() {
        return _type == RecoFieldDataType.OMR;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeTable() {
        return _type == RecoFieldDataType.TABLE;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeImage() {
        return _type == RecoFieldDataType.IMAGE;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeTextExtendable() {
        return _type == RecoFieldDataType.TEXT_EXTENDABLE;
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isExtendable() {
        return isTypeTable() || isTypeTextExtendable();
    }


    //
    // Getters for properties ---------------------------------------------------------------------
    //


    /**
     * Shortcut method for getting access to the RecoField general properties
     *
     * @return {@link FieldProperties} RecoField general properties
     */
    @BsonIgnore
    @JsonbTransient
    public FieldProperties getProp() {
        return new FieldProperties(this);
    }


    @BsonIgnore
    @JsonbTransient
    public TextProperties getTextProp() {
        return new TextProperties(this);
    }


    @BsonIgnore
    @JsonbTransient
    public TableProperties getTableProp() {
        return new TableProperties(this);
    }


    @BsonIgnore
    @JsonbTransient
    public OmrProperties getOmrProp() {
        return new OmrProperties(this);
    }


    @BsonIgnore
    @JsonbTransient
    public TextExtendableProperties getExtendableTextProp() {
        return new TextExtendableProperties(this);
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RecoField recoField = (RecoField) o;
        return _confThreshold == recoField._confThreshold
                && _required == recoField._required
                && Objects.equals(_name, recoField._name)
                && Objects.equals(_valueRegion, recoField._valueRegion)
                && Objects.equals(_anchorRegion, recoField._anchorRegion)
                && Objects.equals(_anchorText, recoField._anchorText)
                && _type == recoField._type
                && Objects.equals(_tags, recoField._tags)
                && Objects.equals(_properties, recoField._properties)
                && Objects.equals(_columns, recoField._columns)
                && Objects.equals(_opMarks, recoField._opMarks);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_name, _valueRegion, _anchorRegion, _anchorText, _type, _confThreshold, _required, _tags, _properties, _columns, _opMarks);
    }
}
