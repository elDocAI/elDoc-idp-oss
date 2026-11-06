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

import ai.eldoc.Util;
import ai.eldoc.annotation.I18nSupport;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Custom Filed Model
 */
@I18nSupport(fields = {CustomField._NAME})
public class CustomField implements PropertyAwareModel, Serializable {

    @Serial
    private static final long serialVersionUID = -4274298907789987003L;

    public static final String _ENABLED = "enabled";
    public static final String _FIELD_NAME = "fieldName";
    public static final String _NAME = "name";
    public static final String _FIELDS = "fields";
    public static final String _PREFIX = "prefix";
    public static final String _REQUIRED = "required";
    public static final String _SUB_TYPE = "subType";
    public static final String _TAGS = "tags";
    public static final String _TYPE = "type";

    /**
     * Tags the CustomFields added from RecoForm during the RecoWorkflow recognition process
     */
    public static final String TAG_RECO_FIELD = "RECO_FIELD";

    public static final String TYPE_STRING = "string";
    public static final String TYPE_NUMBER_INTEGER = "numberInteger";
    public static final String TYPE_NUMBER_FRACTIONAL = "number";
    public static final String TYPE_DATETIME = "dateTime";
    /**
     * <b>IMPORTANT:</b> Custom field of Date type must use UTC timezone to avoid date part shift due to timezone offset
     */
    public static final String TYPE_DATE = "date";
    public static final String TYPE_CHECKBOX = "checkbox";
    public static final String TYPE_DOCREF = "docRef";
    public static final String TYPE_TABLE = "table";
    public static final String TYPE_SIGN = "sign";
    public static final String TYPE_NAMEPCKR = "namePckr";
    public static final String TYPE_RECO = "reco";
    public static final String TYPE_RECO_TABLE = "recoTable";
    public static final String TYPE_RECO_OMR = "recoOmr";
    public static final String TYPE_RECO_IMAGE = "recoImage";

    // TODO: Consider adding an id field like (id=UUID) as otherwise there is a major flaw: CF with all equal params including field names (what should not be a case
    // but we do not validate it) will give the same index for usages like List<CustomField>.indexOf(cf), as there is nothing to differentiate them by the equals() method.

    private String _type;                                       // "string" | "number" | "checkbox" | "dateTime" etc...
    private String _subType;                                    // subType of the field
    private boolean _required;                                  // true | false
    private String _prefix;                                     // prefix for custom fields in target document
    private String _fieldName;                                  // fieldName for custom field in target document (optional - for customization)
    private boolean _enabled;                                   // flag that field is enabled or disabled
    private final Set<String> _tags;                            // field tags
    private final Map<String, String> _name;                    // field UI name
    private final List<CustomField> _fields;                    // field to be used for table column names, etc.
    private final Map<String, Object> _properties;              // properties field


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public CustomField() {
        _type = TYPE_STRING;
        _subType = "";
        _prefix = "cf";
        _fieldName = "";
        _enabled = true;
        _name = new HashMap<>();
        _tags = new HashSet<>();
        _fields = new ArrayList<>();
        _properties = new HashMap<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public static CustomField createDefault() {
        CustomField cf = new CustomField();
        return cf;
    }


    /**
     * Returns field name that will be used in the document structure
     * For backward compatibility this method can not be changed, or in case of changing it - changes has to be
     * implemented super careful as based on this logic field names were calculated and stored in the documents before
     *
     * @param cfIndex
     * @return
     */
    public String getDSFieldName(int cfIndex) {
        if (isNotBlank(_fieldName)) {
            return _fieldName;
        } else if (isNotBlank(_prefix)) {
            return (_prefix + cfIndex);
        } else {
            // default value for the field name
            return ("cf" + cfIndex);
        }
    }


    /**
     * Adds embedded Custom Field
     */
    public void addField() {
        getFields().add(new CustomField());
    }


    /**
     * Removes embedded CustomField by the given index
     *
     * @param fieldIndex
     */
    public void deleteField(int fieldIndex) {
        getFields().remove(fieldIndex);
    }


    /**
     * Finds first field of this Custom Field, that has all of the provided tags set
     *
     * @param tags
     * @return
     */
    public CustomField getFieldByTagsFirst(Set<String> tags) {
        return getFields().stream().filter(customField -> Util.hasIntersection(customField.getTags(), tags)).findFirst().orElse(null);
    }


    /**
     * Finds all fields, that have all of the provided tags set
     *
     * @param tags
     * @return
     */
    public List<CustomField> getFieldsByTags(Set<String> tags) {
        return getFields().stream().filter(customField -> Util.hasIntersection(customField.getTags(), tags)).collect(Collectors.toList());
    }


    /**
     * Sets field label (name) for the specific language
     *
     * @param lang lang code used in the system
     * @param name CustomField label (name)
     */
    public void setName(String lang, String name) {
        _name.put(lang, name);
    }


    //
    // Virtual Getters ----------------------------------------------------------------------------
    //


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeCheckBox() {
        return TYPE_CHECKBOX.equals(_type);
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeTable() {
        return TYPE_TABLE.equals(_type);
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeReco() {
        return TYPE_RECO.equals(_type);
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeRecoTable() {
        return TYPE_RECO_TABLE.equals(_type);
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeRecoOmr() {
        return TYPE_RECO_OMR.equals(_type);
    }


    @BsonIgnore
    @JsonbTransient
    public boolean isTypeRecoImage() {
        return TYPE_RECO_IMAGE.equals(_type);
    }


    @BsonIgnore
    @JsonbTransient
    public CustomFieldRecoProperties getRecoProp() {
        return new CustomFieldRecoProperties(this);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE)
    public String getType() {
        return _type;
    }


    @BsonProperty(_TYPE)
    @JsonbProperty(_TYPE)
    public void setType(String type) {
        _type = type;
    }


    @BsonProperty(_REQUIRED)
    @JsonbProperty(_REQUIRED)
    public boolean isRequired() {
        return _required;
    }


    @BsonProperty(_REQUIRED)
    @JsonbProperty(_REQUIRED)
    public void setRequired(boolean required) {
        _required = required;
    }


    @BsonProperty(_PREFIX)
    @JsonbProperty(_PREFIX)
    public String getPrefix() {
        return _prefix;
    }


    @BsonProperty(_PREFIX)
    @JsonbProperty(_PREFIX)
    public void setPrefix(String prefix) {
        _prefix = prefix;
    }


    @BsonProperty(_FIELD_NAME)
    @JsonbProperty(_FIELD_NAME)
    public String getFieldName() {
        return _fieldName;
    }


    @BsonProperty(_FIELD_NAME)
    @JsonbProperty(_FIELD_NAME)
    public void setFieldName(String fieldName) {
        _fieldName = fieldName;
    }


    @BsonProperty(_NAME)
    @JsonbProperty(_NAME)
    public Map<String, String> getName() {
        return _name;
    }


    @BsonProperty(_NAME)
    @JsonbProperty(_NAME)
    public void setName(Map<String, String> name) {
        _name.clear();
        _name.putAll(MapUtils.emptyIfNull(name));
    }


    @BsonProperty(_ENABLED)
    @JsonbProperty(_ENABLED)
    public boolean isEnabled() {
        return _enabled;
    }


    @BsonProperty(_ENABLED)
    @JsonbProperty(_ENABLED)
    public void setEnabled(boolean enabled) {
        _enabled = enabled;
    }


    @BsonProperty(_SUB_TYPE)
    @JsonbProperty(_SUB_TYPE)
    public String getSubType() {
        return _subType;
    }


    @BsonProperty(_SUB_TYPE)
    @JsonbProperty(_SUB_TYPE)
    public void setSubType(String subType) {
        _subType = subType;
    }


    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS)
    public Set<String> getTags() {
        return _tags;
    }


    @BsonProperty(_TAGS)
    @JsonbProperty(_TAGS)
    public void setTags(Set<String> tags) {
        _tags.clear();
        _tags.addAll(CollectionUtils.emptyIfNull(tags));
    }


    @BsonProperty(_FIELDS)
    @JsonbProperty(_FIELDS)
    public List<CustomField> getFields() {
        return _fields;
    }


    @BsonProperty(_FIELDS)
    @JsonbProperty(_FIELDS)
    public void setFields(List<CustomField> fields) {
        _fields.clear();
        _fields.addAll(CollectionUtils.emptyIfNull(fields));
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
        _properties.putAll(MapUtils.emptyIfNull(properties));
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        return Objects.hash(_type,
                _subType,
                _required,
                _prefix,
                _fieldName,
                _enabled,
                _tags,
                _name,
                _fields,
                _properties);
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CustomField that = (CustomField) o;
        return _required == that._required
                && _enabled == that._enabled
                && Objects.equals(_type, that._type)
                && Objects.equals(_subType, that._subType)
                && Objects.equals(_prefix, that._prefix)
                && Objects.equals(_fieldName, that._fieldName)
                && Objects.equals(_tags, that._tags)
                && Objects.equals(_name, that._name)
                && Objects.equals(_fields, that._fields)
                && Objects.equals(_properties, that._properties);
    }
}
