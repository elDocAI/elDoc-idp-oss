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

import ai.eldoc.ai.service.AiService;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;


/**
 * Property Config Model for defining available properties
 */
public class PropertyConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = -2981281101049556431L;


    //
    // Global (App Level) custom properties (adjusted by user on the Configuration page via properties table)
    // NOTE: Must start with app_ prefix
    //
    public static final String APP_WORKFLOW_IGNORE_NOTIFICATIONS = "app_wf_ignoreNotifications";


    //
    // Local properties (specific to some parts of the app)
    //
    public static final String IDP_RECOFORM_TABLE_ANCUNDTBL_USEBOTTOM = "idp_recoform_table_ancundtblusebottom";
    public static final String IDP_RECOFORM_USE_SOURCE = "idp_recoform_use_source";
    public static final String IDP_RECOFORM_CUSTOM_ID = "idp_recoform_custom_id";
    /**
     * Defines the size of artifacts to be removed (e.g., 1x1), default 0x0 what means disabled
     */
    public static final String IDP_RECOFORM_MORPHOLOGY = "idp_recoform_morphology";
    public static final String IDP_ADAPTIVE_THRESHOLD = "idp_adaptive_threshold";
    public static final String IDP_ITERATIVE_THRESHOLD = "idp_iterative_threshold";
    /**
     * Defines value for the custom threshold
     */
    public static final String IDP_THRESHOLD_VALUE = "idp_threshold_value";
    public static final String IDP_RESOLUTION_THRESHOLD = "idp_resolution_threshold";
    public static final String IDP_OCR_MODE_SPARSE_TEXT = "idp_ocr_mode_sparse_text";
    public static final String IDP_RECOFORM_STRICT_LAYOUT = "idp_recoform_strict_layout";
    public static final String IDP_IGNORE_TEXT_LAYER = "idp_ignore_text_layer";
    /**
     * Defines whether this form should be used for keyword classification if no form matched
     */
    public static final String IDP_RECOFORM_KEYWORDS_CLASSIFICATION = "idp_recoform_keywords_classification";
    public static final String IDP_RECOFORM_TABLE_DISABLE_ROW_MERGE = "idp_recoform_table_disable_row_merge";
    public static final String IDP_RECOFORM_TABLE_ENABLE_COLUMNS_DETECTION = "idp_recoform_table_enable_columns_detection";
    /**
     * Maximum number of page images that will be sent in a single AI / LLM request. MUST be validated to not exceed model/backend limits.
     * Defaults to -1, which means to use the model / backend default value
     */
    public static final String IDP_AI_MAX_PAGES_PER_REQUEST = "idp_ai_max_pages_per_request";

    public static final int TYPE_INT = 1;
    //public static final int TYPE_DOUBLE = 2; // support to be added later
    public static final int TYPE_STRING = 3;
    public static final int TYPE_BOOLEAN = 4;
    //public static final int TYPE_DATE = 5; // support to be added later

    public static final Set<PropertyConfig> SYSTEM_PROPERTIES = new HashSet<>();

    static {
        //
        // Global (App Level) custom properties
        //
        SYSTEM_PROPERTIES.add(new PropertyConfig(APP_WORKFLOW_IGNORE_NOTIFICATIONS, TYPE_STRING, "", "List of workflow form Ids (comma separated) for which email notifications are ignored (experimental)"));


        //
        // IDP
        //
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_RECOFORM_TABLE_ANCUNDTBL_USEBOTTOM, TYPE_BOOLEAN, false, "Use bottom left point of anchor for calculating offset"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_RECOFORM_USE_SOURCE, TYPE_BOOLEAN, false, "Switches to use source image instead of preprocessed image for the RecoForm processing"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_RECOFORM_CUSTOM_ID, TYPE_STRING, "", "Id of the custom RecoForm which is loaded as plugin"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_RECOFORM_MORPHOLOGY, TYPE_STRING, "0x0", "Enables artifacts removal and sets size of the artifact to be removed during image pre-processing", "^\\d{1,2}x\\d{1,2}$", "0x0", "99x99"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_ADAPTIVE_THRESHOLD, TYPE_BOOLEAN, false, "Enables adaptive threshold"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_ITERATIVE_THRESHOLD, TYPE_BOOLEAN, false, "Enables iterative threshold"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_THRESHOLD_VALUE, TYPE_INT, 0, "Defines custom thresholding value", null, 0, 255));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_RESOLUTION_THRESHOLD, TYPE_STRING, "2480x3500", "If source image resolution is below defined threshold in at least one dimension document will be sent to validation", "^\\d{1,5}x\\d{1,5}$", "10x10", "99999x99999"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_OCR_MODE_SPARSE_TEXT, TYPE_BOOLEAN, false, "Sets OCR recognition mode to Sparse Text"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_RECOFORM_STRICT_LAYOUT, TYPE_BOOLEAN, false, "Enforces strict template layout of the recoForm"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_IGNORE_TEXT_LAYER, TYPE_BOOLEAN, false, "Enforces document OCR, even if it already has a text layer (relevant to PDF files)"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_RECOFORM_KEYWORDS_CLASSIFICATION, TYPE_BOOLEAN, false, "Enables this form to be used for keywords classification"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_RECOFORM_TABLE_DISABLE_ROW_MERGE, TYPE_BOOLEAN, false, "Disables table rows merge"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_RECOFORM_TABLE_ENABLE_COLUMNS_DETECTION, TYPE_BOOLEAN, false, "Enables table columns detection"));
        SYSTEM_PROPERTIES.add(new PropertyConfig(IDP_AI_MAX_PAGES_PER_REQUEST, TYPE_INT, AiService.REQUEST_IMAGES_COUNT_MAX, "Maximum number of pages to send in a single request to AI/LLM backend", null, 1, AiService.REQUEST_IMAGES_COUNT_MAX));
    }

    private final String _name;
    private final int _type;
    private final Object _defaultValue;
    private final String _description;
    private final String _validator;
    private final Object _minValue;
    private final Object _maxValue;


    //
    // Constructor --------------------------------------------------------------------------------
    //


    /**
     * Property Config constructor
     *
     * @param name name of the property like: "domain.ite.subitem.property", e.g.: "idp.recoform.table.removelines"
     * @param type datatype of the property: INT, STRING, BOOLEAN
     * @param defaultValue default value if any
     * @param description description to be shown on UI for the user
     */
    public PropertyConfig(String name, int type, Object defaultValue, String description) {
        this(name, type, defaultValue, description, null, null, null);
    }


    /**
     * Property Config constructor
     *
     * @param name name of the property like: "domain.ite.subitem.property", e.g.: "idp.recoform.table.removelines"
     * @param type datatype of the property: INT, STRING, BOOLEAN
     * @param defaultValue default value if any
     * @param description description to be shown on UI for the user
     * @param validator planned as a regular expression-based validator
     * @param minValue minimum value
     * @param maxValue maximum value
     */
    public PropertyConfig(String name, int type, Object defaultValue, String description, String validator, Object minValue, Object maxValue) {
        _name = name;
        _type = type;
        _defaultValue = defaultValue;
        _description = description;
        _validator = validator;
        _minValue = minValue;
        _maxValue = maxValue;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Returns Property Config of the systems properties
     *
     * @param propName Name of the property to retrieve
     * @return PropertyConfig or null if not found
     */
    public static PropertyConfig get(String propName) {
        return SYSTEM_PROPERTIES.stream().filter(propertyConfig -> propertyConfig.getName().equals(propName)).findFirst().orElse(null);
    }


    /**
     * Checks whether the name of the property is valid
     *
     * @param propName Name of the property to check
     * @return {@code true} if the property is valid, {@code false} otherwise
     */
    public static boolean isValid(String propName) {
        return SYSTEM_PROPERTIES.stream().anyMatch(propertyConfig -> propertyConfig.getName().equals(propName));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getName() {
        return _name;
    }


    public int getType() {
        return _type;
    }


    public Object getDefaultValue() {
        return _defaultValue;
    }


    public String getDescription() {
        return _description;
    }


    /**
     * @return Regular expression for validating the property
     */
    public String getValidator() {
        return _validator;
    }


    public Object getMinValue() {
        return _minValue;
    }


    public Object getMaxValue() {
        return _maxValue;
    }


    //
    // Generic methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PropertyConfig that = (PropertyConfig) o;
        return _type == that._type && Objects.equals(_name, that._name) && Objects.equals(_defaultValue, that._defaultValue)
                && Objects.equals(_description, that._description) && Objects.equals(_validator, that._validator)
                && Objects.equals(_minValue, that._minValue) && Objects.equals(_maxValue, that._maxValue);
    }


    @Override
    public int hashCode() {
        return Objects.hash(_name, _type, _defaultValue, _description, _validator, _minValue, _maxValue);
    }
}
