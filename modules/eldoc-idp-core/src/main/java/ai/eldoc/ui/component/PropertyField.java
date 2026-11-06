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
package ai.eldoc.ui.component;

import ai.eldoc.model.PropertyConfig;
import ai.eldoc.util.JsfUtils;
import jakarta.faces.component.*;
import jakarta.faces.context.FacesContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static ai.eldoc.model.PropertyConfig.*;
import static java.text.MessageFormat.format;


/**
 * Property Field component backing bean
 */
@FacesComponent("propertyField")
public class PropertyField extends UIInput implements NamingContainer {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyField.class);


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Validator for INT Property
     *
     * @param fc
     * @param component
     * @param value
     */
    public void propIntValidator(FacesContext fc, UIComponent component, Object value) {
        try {
            String propName = (String) component.getAttributes().get("label");
            if ((Integer) value < getPropIntMin(propName) || (Integer) value > getPropIntMax(propName)) {
                String messageTemplate = JsfUtils.getStandardMessageTemplate(fc, "jakarta.faces.validator.LongRangeValidator.NOT_IN_RANGE");
                JsfUtils.postValidationError(fc, component, format(messageTemplate, getPropIntMin(propName), getPropIntMax(propName), propName));
            }
        } catch (Exception e) {
            String msg = format("{1}: Validation error: {0}", e.toString(), (String) component.getAttributes().get("label"));
            LOG.warn(msg, e);
            JsfUtils.postValidationError(fc, component, msg);
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Return MIN value defined for property or Integer.MIN_VALUE otherwise
     *
     * @param propName
     * @return
     */
    public Integer getPropIntMin(String propName) {
        PropertyConfig propConfig = getPropertyConfig(propName);
        if (propConfig == null || propConfig.getMinValue() == null) {
            return Integer.MIN_VALUE; // default
        }
        return (Integer) propConfig.getMinValue();
    }


    /**
     * Return MAX value defined for property or Integer.MAX_VALUE otherwise
     *
     * @param propName
     * @return
     */
    public Integer getPropIntMax(String propName) {
        PropertyConfig propConfig = getPropertyConfig(propName);
        if (propConfig == null || propConfig.getMaxValue() == null) {
            return Integer.MAX_VALUE; // default
        }
        return (Integer) propConfig.getMaxValue();
    }


    /**
     * Returns Property Configuration or null in case no property found with given name
     *
     * @param propName
     * @return
     */
    public PropertyConfig getPropertyConfig(String propName) {
        return PropertyConfig.get(propName);
    }


    /**
     * Checks whether given property has type of STRING
     *
     * @param propName
     * @return
     */
    public boolean isPropTypeString(String propName) {
        PropertyConfig propConfig = getPropertyConfig(propName);
        if (propConfig == null) {
            return true; // by default we treat property as string (for example for custom external properties used by custom modules)
        }
        return propConfig.getType() == TYPE_STRING;
    }


    /**
     * Checks whether given property has type of INT
     *
     * @param propName
     * @return
     */
    public boolean isPropTypeInt(String propName) {
        PropertyConfig propConfig = getPropertyConfig(propName);
        if (propConfig == null) {
            return false; // default
        }
        return propConfig.getType() == TYPE_INT;
    }


    /**
     * Checks whether given property has type of BOOLEAN
     *
     * @param propName
     * @return
     */
    public boolean isPropTypeBoolean(String propName) {
        PropertyConfig propConfig = getPropertyConfig(propName);
        if (propConfig == null) {
            return false; // default
        }
        return propConfig.getType() == TYPE_BOOLEAN;
    }


    @Override
    public String getFamily() {
        return UINamingContainer.COMPONENT_FAMILY;
    }
}
