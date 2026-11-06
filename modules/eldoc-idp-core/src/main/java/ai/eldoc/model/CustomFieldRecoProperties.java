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
import java.util.*;
import java.util.stream.Collectors;


/**
 * Recognition-related CustomField properties
 */
public class CustomFieldRecoProperties implements Serializable {

    private static final long serialVersionUID = 7930982672703058871L;

    public static final String PROP_RECO_CONF = "recoValueConf"; // Stores value confidencies for the field
    public static final String PROP_RECO_CONF_THRESH = "recoConfThreshold"; // Stores confidence threshold for the field. For the TABLE field stored on the embedded _fields level
    public static final String PROP_RECO_PAGE_IDX = "recoPageIdx"; // Stores page index to which this field related
    public static final String PROP_RECO_IGNORED = "recoIgnored"; // Stores flag, that indicates if field should be ignored

    protected static final List<String> PROP_ALL = Arrays.asList(
            PROP_RECO_CONF,
            PROP_RECO_CONF_THRESH,
            PROP_RECO_PAGE_IDX,
            PROP_RECO_IGNORED
    );

    private final CustomField _customField;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public CustomFieldRecoProperties(CustomField customField) {
        _customField = customField;
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns confidence threshold set for current custom field
     *
     * @return
     */
    public int getConfThreshold() {
        return _customField.getProperty(PROP_RECO_CONF_THRESH, -1);
    }


    /**
     * Sets provided value as new confidence threshold value
     *
     * @param confThreshold
     */
    public void setConfThreshold(int confThreshold) {
        _customField.getProperties().put(PROP_RECO_CONF_THRESH, confThreshold);
    }


    public List<Integer> getConfidenceText() {
        if (CustomField.TYPE_RECO.equals(_customField.getType())) {
            return _customField.getProperty(PROP_RECO_CONF, new ArrayList<>());
        }
        return new ArrayList<>();
    }


    public void setConfidenceText(List<Integer> confidence) {
        if (CustomField.TYPE_RECO.equals(_customField.getType())) {
            _customField.getProperties().put(PROP_RECO_CONF, confidence);
        }
    }


    public List<Integer> getConfidenceImage() {
        if (CustomField.TYPE_RECO_IMAGE.equals(_customField.getType())) {
            return _customField.getProperty(PROP_RECO_CONF, new ArrayList<>());
        }
        return new ArrayList<>();
    }


    public void setConfidenceImage(List<Integer> confidence) {
        if (CustomField.TYPE_RECO_IMAGE.equals(_customField.getType())) {
            _customField.getProperties().put(PROP_RECO_CONF, confidence);
        }
    }


    public List<Integer> getConfidenceOmr() {
        if (CustomField.TYPE_RECO_OMR.equals(_customField.getType())) {
            return _customField.getProperty(PROP_RECO_CONF, new ArrayList<>());
        }
        return new ArrayList<>();
    }


    public void setConfidenceOmr(List<Integer> confidence) {
        if (CustomField.TYPE_RECO_OMR.equals(_customField.getType())) {
            _customField.getProperties().put(PROP_RECO_CONF, confidence);
        }
    }


    public List<List<List<Integer>>> getConfidenceTable() {
        if (CustomField.TYPE_RECO_TABLE.equals(_customField.getType())) {
            return _customField.getProperty(PROP_RECO_CONF, new ArrayList<>());
        }
        return new ArrayList<>();
    }


    public void setConfidenceTable(List<List<List<Integer>>> confidence) {
        if (CustomField.TYPE_RECO_TABLE.equals(_customField.getType())) {
            _customField.getProperties().put(PROP_RECO_CONF, confidence);
        }
    }


    private List<Integer> getAllConfidences() {
        if (CustomField.TYPE_RECO_TABLE.equals(_customField.getType())) {
            return getConfidenceTable().stream()
                    .flatMap(List::stream)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        } else if (CustomField.TYPE_RECO_OMR.equals(_customField.getType())) {
            return getConfidenceOmr();
        } else if (CustomField.TYPE_RECO_IMAGE.equals(_customField.getType())) {
            return getConfidenceImage();
        } else {
            return getConfidenceText();
        }
    }


    /**
     * Checks whether field is empty or not (uses confidences list for checking)
     *
     * @return
     */
    public boolean isEmpty() {
        return getAllConfidences().isEmpty();
    }


    public int getConfidenceMin() {
        return getAllConfidences().parallelStream().mapToInt(Integer::intValue).min().orElse(0);
    }


    public int getPageIdx() {
        return _customField.getProperty(PROP_RECO_PAGE_IDX, -1);
    }


    public void setPageIdx(int pageIdx) {
        _customField.getProperties().put(PROP_RECO_PAGE_IDX, pageIdx);
    }


    public boolean isIgnored() {
        return _customField.getProperty(PROP_RECO_IGNORED, false);
    }


    public void setIgnored(boolean ignored) {
        _customField.getProperties().put(PROP_RECO_IGNORED, ignored);
    }

}
