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

import ai.eldoc.model.PropertyAwareModel;
import ai.eldoc.model.PropertyConfig;
import ai.eldoc.util.JsfUtils;
import jakarta.faces.component.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Property Table component backing bean
 */
@FacesComponent("propertyTable")
public class PropertyTable extends UIInput implements NamingContainer {

    private String _propName;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Adds Property to the PropertyAwareModel entity
     *
     * @param propPrefix
     * @param propertiesModel
     */
    public void actionAdd(String propPrefix, PropertyAwareModel propertiesModel) {
        if (isBlank(getPropName())) {
            return;
        }
        String newProperty = getPropName();
        if (!newProperty.startsWith(propPrefix) || !isPropertyNameValid(newProperty)) {
            // property is not valid
            JsfUtils.addWarnMessage("Property is not valid");
            return;
        }

        propertiesModel.getProperties().put(newProperty, getPropDefaultValue(newProperty));
        JsfUtils.addInfoMessage("Property was added");
    }


    /**
     * Removes Property from the PropertyAwareModel entity
     *
     * @param propName
     * @param propertiesModel
     */
    public void actionRemove(String propName, PropertyAwareModel propertiesModel) {
        propertiesModel.getProperties().remove(propName);
    }


    /**
     * Completes property names
     *
     * @param query
     * @return
     */
    public List<String> completePropName(String query) {
        String propPrefix = (String) UIComponent.getCurrentComponent(JsfUtils.getFacesContext()).getAttributes().get("propPrefix");
        return PropertyConfig.SYSTEM_PROPERTIES.stream()
                .filter(propertyConfig -> (propertyConfig.getName().startsWith(propPrefix) && propertyConfig.getName().contains(query.toLowerCase())))
                .map(PropertyConfig::getName)
                .sorted()
                .collect(Collectors.toList());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * We are showing only properties with the requested prefix
     *
     * @param propAwareModel
     * @param propPrefix
     * @return
     */
    public List<String> getPropList(PropertyAwareModel propAwareModel, String propPrefix) {
        if (propPrefix == null || propPrefix.isBlank()) {
            return new ArrayList<>(propAwareModel.getProperties().keySet());
        }

        return propAwareModel.getProperties()
                .keySet().stream()
                .filter(prop -> prop.startsWith(propPrefix))
                .collect(Collectors.toList());
    }


    /**
     * Checks if a given property name is valid
     *
     * @param propName
     * @return
     */
    public boolean isPropertyNameValid(String propName) {
        return PropertyConfig.isValid(propName);
    }


    /**
     * Returns Property Configuration or null in case no property found with the given name
     *
     * @param propName
     * @return
     */
    public PropertyConfig getPropertyConfig(String propName) {
        return PropertyConfig.get(propName);
    }


    public Object getPropDefaultValue(String propName) {
        PropertyConfig propConfig = getPropertyConfig(propName);
        if (propConfig == null) {
            return ""; // by default, we treat property as string
        }
        return propConfig.getDefaultValue();
    }


    public String getDescription(String propName) {
        PropertyConfig propConfig = getPropertyConfig(propName);
        if (propConfig == null) {
            return null;
        }
        return propConfig.getDescription();
    }


    public String getPropName() {
        return _propName;
    }


    public void setPropName(String propName) {
        _propName = propName;
    }


    @Override
    public String getFamily() {
        return UINamingContainer.COMPONENT_FAMILY;
    }
}
