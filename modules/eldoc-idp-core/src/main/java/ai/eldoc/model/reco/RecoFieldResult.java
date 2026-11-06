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

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.model.PropertyAwareModel;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Recognition Field data model
 */
public final class RecoFieldResult implements PropertyAwareModel, Serializable {

    private static final long serialVersionUID = -8017571145852912446L;

    /**
     * Confidence level to be set for not located field
     */
    public static final int FIELD_NOT_FOUND_CONFIDENCE = 10;

    public final static String _TYPE = "type";
    public final static String _REGION = "region";
    public final static String _ANCHOR_REGION = "anchorRegion";
    public final static String _NAME = "name";
    public final static String _VALUE = "value";
    public final static String _VALUE_CONF = "valueConf";
    public final static String _TABLE_DATA = "tableData";
    public final static String _OMR_DATA = "omrData";
    public final static String _RECOFORM_PAGE_INDEX = "rfPageIdx";
    public final static String _RECOFORM_FIELD_INDEX = "rfFieldIdx";

    private String _name;
    private String _value;
    private Rectangle _anchorRegion;
    private Rectangle _region;
    private RecoFieldDataType _type;
    private List<Integer> _valueConf;
    private List<List<RecoFieldResult>> _tableData;
    private List<RecoFieldResult> _omrData;
    private int _matchConfidence;
    private int _matchConfidenceBottomAnchor;
    private int _rfPageIdx;
    private int _rfFieldIdx;
    private Map<String, Object> _properties;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    protected RecoFieldResult() {
        _valueConf = new ArrayList<>();
        _tableData = new ArrayList<>();
        _omrData = new ArrayList<>();
        _rfPageIdx = -1;
        _rfFieldIdx = -1;
        _properties = new HashMap<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Inits RecoFieldResult without binding it to specific RecoForm/RecoField
     *
     * @return
     */
    public static RecoFieldResult createFormless() {
        RecoFieldResult recoFieldResult = new RecoFieldResult();
        return recoFieldResult;
    }


    /**
     * Inits RecoFieldResult based on RecoFrom/RecoField
     *
     * @param recoForm
     * @param recoField
     * @return
     */
    public static RecoFieldResult createDefault(RecoForm recoForm, RecoField recoField) {
        RecoFieldResult recoFieldResult = new RecoFieldResult();
        recoFieldResult.setName(recoField.getName());
        recoFieldResult.setRegion(recoField.getValueRegion().clone());
        recoFieldResult.setType(recoField.getType());
        for (int pageIdx = 0; pageIdx < recoForm.getPages().size(); pageIdx++) {
            int fieldIdx = recoForm.getPages().get(pageIdx).getFields().indexOf(recoField);
            if (fieldIdx != -1) {
                recoFieldResult.setRfPageIdx(pageIdx);
                recoFieldResult.setRfFieldIdx(fieldIdx);
                break;
            }
        }
        if (recoFieldResult.getRfPageIdx() == -1 || recoFieldResult.getRfFieldIdx() == -1) {
            throw new ElDocRuntimeException("Can not initialize RecoFieldResult with given RecoForm and RecoField: index of RecoPage&RecoField is not defined");
        }
        return recoFieldResult;
    }


    /**
     * Inits RecoFieldResult, based on RecoForm/RecoField and properly sets all properties,
     * required to indicate, that field was not found (confidence level, values, region, etc)
     *
     * @param recoForm
     * @param recoField
     * @return
     */
    public static RecoFieldResult createNotFound(RecoForm recoForm, RecoField recoField) {
        RecoFieldResult recoFieldResult = createDefault(recoForm, recoField);
        recoFieldResult.setRegion(new Rectangle());
        recoFieldResult.setMatchConfidence(RecoFieldResult.FIELD_NOT_FOUND_CONFIDENCE);

        if (recoField.isTypeTable() && recoField.getColumns().isEmpty()) {
            // Custom logic for tables without columns on RecoForm (to avoid sending document to validation)
            recoFieldResult.getValueConf().clear();
            recoFieldResult.getValueConf().add(99);
        }

        if (recoField.isTypeOmr()) {
            // As each optical mark is treated as separate 'field' - add marks with 'FieldNotFound' confidences to the result
            for (RecoField opMark : recoField.getOpMarks()) {
                RecoFieldResult omrResult = RecoFieldResult.createDefault(recoForm, recoField);
                omrResult.setName(opMark.getName());
                omrResult.setType(opMark.getType());
                omrResult.setValue("F");
                omrResult.getValueConf().add(0);
                omrResult.setRegion(new Rectangle());
                recoFieldResult.getOmrData().add(omrResult);
            }
        }

        return recoFieldResult;
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


    public String getValue() {
        return _value;
    }


    public void setValue(String value) {
        _value = value;
    }


    public Rectangle getRegion() {
        return _region;
    }


    public void setRegion(Rectangle region) {
        _region = region;
    }


    public Rectangle getAnchorRegion() {
        return _anchorRegion;
    }


    public void setAnchorRegion(Rectangle anchorRegion) {
        _anchorRegion = anchorRegion;
    }


    public RecoFieldDataType getType() {
        return _type;
    }


    public void setType(RecoFieldDataType type) {
        _type = type;
    }


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
    private List<Integer> getAllConfidences() {
        if (isTypeTable()) {
            return getTableData().stream()
                    .flatMap(row -> row.stream())
                    .flatMap(cell -> cell.getValueConf().stream())
                    .collect(Collectors.toList());
        } else if (isTypeOmr()) {
            return getOmrData().stream()
                    .flatMap(opMark -> opMark.getValueConf().stream())
                    .collect(Collectors.toList());
        } else {
            return getValueConf();
        }
    }


    @BsonIgnore
    @JsonbTransient
    public int getConfidenceMin() {
        return getAllConfidences().parallelStream().mapToInt(Integer::intValue).min().orElse(0);
    }


    @BsonIgnore
    @JsonbTransient
    public int getConfidenceMax() {
        return getAllConfidences().parallelStream().mapToInt(Integer::intValue).max().orElse(0);
    }


    @BsonIgnore
    @JsonbTransient
    public int getConfidenceAverage() {
        return (int) getAllConfidences().parallelStream().mapToInt(Integer::intValue).average().orElse(0);
    }


    public List<Integer> getValueConf() {
        return _valueConf;
    }


    public void setValueConf(List<Integer> characters) {
        _valueConf.clear();
        if (characters != null && !characters.isEmpty()) {
            _valueConf.addAll(characters);
        }
    }


    @BsonIgnore
    @JsonbTransient
    public int getMatchConfidence() {
        return _matchConfidence;
    }


    @BsonIgnore
    @JsonbTransient
    public void setMatchConfidence(int matchConfidence) {
        _matchConfidence = matchConfidence;
    }


    /**
     * Returns RecoForm Page Index of this field result
     *
     * @return
     */
    public int getRfPageIdx() {
        return _rfPageIdx;
    }


    /**
     * Sets RecoForm Page Index of this field result
     *
     * @param rfPageIdx
     */
    public void setRfPageIdx(int rfPageIdx) {
        _rfPageIdx = rfPageIdx;
    }


    /**
     * Returns table values in case, if the field is of type {@link RecoDataType.TABLE}
     *
     * @return
     */
    public List<List<RecoFieldResult>> getTableData() {
        return _tableData;
    }


    public void setTableData(List<List<RecoFieldResult>> tableData) {
        _tableData.clear();
        if (tableData != null && !tableData.isEmpty()) {
            _tableData.addAll(tableData);
        }
    }


    /**
     * Returns OMR values in case, if the field is of type {@link RecoDataType.OMR}
     *
     * @return
     */
    public List<RecoFieldResult> getOmrData() {
        return _omrData;
    }


    public void setOmrData(List<RecoFieldResult> omrData) {
        _omrData.clear();
        if (omrData != null && !omrData.isEmpty()) {
            _omrData.addAll(omrData);
        }
    }


    @BsonIgnore
    @JsonbTransient
    public int getMatchConfidenceBottomAnchor() {
        return _matchConfidenceBottomAnchor;
    }


    @BsonIgnore
    @JsonbTransient
    public void setMatchConfidenceBottomAnchor(int matchConfidenceBottomAnchor) {
        _matchConfidenceBottomAnchor = matchConfidenceBottomAnchor;
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


    /**
     * Returns RecoForm Field Index on the RecoFrom Page of this field result
     *
     * @return
     */
    public int getRfFieldIdx() {
        return _rfFieldIdx;
    }


    /**
     * Sets RecoForm Field Index on the RecoFrom Page of this field result
     *
     * @param rfFieldIdx
     */
    public void setRfFieldIdx(int rfFieldIdx) {
        _rfFieldIdx = rfFieldIdx;
    }


    @BsonIgnore
    @JsonbTransient
    public TableProperties getTableProp() {
        return new TableProperties(this);
    }


    @BsonIgnore
    @JsonbTransient
    public TextExtendableProperties getExtendableTextProp() {
        return new TextExtendableProperties(this);
    }


    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(_name);
        hash = 71 * hash + Objects.hashCode(_value);
        hash = 71 * hash + Objects.hashCode(_region);
        hash = 71 * hash + Objects.hashCode(_anchorRegion);
        hash = 71 * hash + Objects.hashCode(_type);
        hash = 71 * hash + Objects.hashCode(_valueConf);
        hash = 71 * hash + Objects.hashCode(_tableData);
        hash = 71 * hash + Objects.hashCode(_omrData);
        hash = 71 * hash + Objects.hashCode(_matchConfidence);
        hash = 71 * hash + Objects.hashCode(_matchConfidenceBottomAnchor);
        hash = 71 * hash + Objects.hashCode(_properties);
        hash = 71 * hash + Objects.hashCode(_rfPageIdx);
        hash = 71 * hash + Objects.hashCode(_rfFieldIdx);
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
        final RecoFieldResult other = (RecoFieldResult) obj;
        if (!Objects.equals(_name, other._name)) {
            return false;
        }
        if (!Objects.equals(_value, other._value)) {
            return false;
        }
        if (!Objects.equals(_region, other._region)) {
            return false;
        }
        if (!Objects.equals(_anchorRegion, other._anchorRegion)) {
            return false;
        }
        if (_type != other._type) {
            return false;
        }
        if (!Objects.equals(_valueConf, other._valueConf)) {
            return false;
        }
        if (!Objects.equals(_tableData, other._tableData)) {
            return false;
        }
        if (!Objects.equals(_omrData, other._omrData)) {
            return false;
        }
        if (!Objects.equals(_matchConfidence, other._matchConfidence)) {
            return false;
        }
        if (!Objects.equals(_matchConfidenceBottomAnchor, other._matchConfidenceBottomAnchor)) {
            return false;
        }
        if (!Objects.equals(_properties, other._properties)) {
            return false;
        }
        if (!Objects.equals(_rfPageIdx, other._rfPageIdx)) {
            return false;
        }
        if (!Objects.equals(_rfFieldIdx, other._rfFieldIdx)) {
            return false;
        }
        return true;
    }

}
