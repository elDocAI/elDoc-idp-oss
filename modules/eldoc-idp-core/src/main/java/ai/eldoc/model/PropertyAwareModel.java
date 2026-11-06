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

import ai.eldoc.exceptions.BadArgumentException;
import java.util.Map;


/**
 * Interface declares Basic Properties Aware Model which also can store custom properties with custom parameters
 */
public interface PropertyAwareModel {

    /**
     * Name of the key to be used for storing properties in the DB
     */
    public final static String _PROPERTIES = "_properties";


    /**
     * Returns properties map
     *
     * @return
     */
    public Map<String, Object> getProperties();


    /**
     * Checks whether property is available
     *
     * @param propName
     * @return
     */
    default public boolean containsProperty(String propName) {
        return getProperties().containsKey(propName);
    }


    /**
     * Sets property value by its name
     *
     * @param propName
     * @param propValue
     */
    default public void setProperty(String propName, Object propValue) {
        getProperties().put(propName, propValue);
    }


    /**
     * Sets property value by its name (in case property is not present)
     *
     * @param propName
     * @param propValue
     */
    default public void setPropertyIfAbsent(String propName, Object propValue) {
        getProperties().putIfAbsent(propName, propValue);
    }


    /**
     * Returns property by name
     *
     * @param propName
     * @return
     */
    default public Object getProperty(String propName) {
        return getProperties().get(propName);
    }


    /**
     * Returns property value of the type defined by the type of the defaultValue param
     *
     * @param <T>
     * @param propName
     * @param defaultValue has to be not NULL
     * @return
     */
    default public <T> T getProperty(String propName, T defaultValue) {
        return getProperty(getProperties(), propName, defaultValue, defaultValue != null ? (Class<T>) defaultValue.getClass() : null);
    }


    /**
     * Returns property value of the type defined by the type of the defaultValue param
     *
     * @param <T>
     * @param propName
     * @param defaultValue can be NULL (if using NULL - defaultValueType should not be NULL)
     * @param defaultValueType
     * @return
     */
    default public <T> T getProperty(String propName, T defaultValue, Class<T> defaultValueType) {
        return getProperty(getProperties(), propName, defaultValue, defaultValueType);
    }


    /**
     * Removes property value by its name
     *
     * @param propName
     */
    default public void removeProperty(String propName) {
        getProperties().remove(propName);
    }


    /**
     * Returns property value of the type defined by the type of the defaultValue param
     *
     * @param <T>
     * @param propertiesMap
     * @param propName
     * @param defaultValue in case set to NULL - defaultValueType must be provided
     * @param defaultValueType
     * @return
     */
    public static <T> T getProperty(Map<String, Object> propertiesMap, String propName, T defaultValue, Class<T> defaultValueType) {
        if (defaultValue == null && defaultValueType == null) {
            throw new BadArgumentException("defaultValueType must be defined when defaultValue is NULL");
        }

        Object propValue = propertiesMap.get(propName);
        if (propValue == null) {
            return defaultValue;
        }

        // Perform Class check
        if (defaultValue != null && propValue.getClass().equals(defaultValue.getClass())) {
            // Class of the default value and value from DB match - safe to return the value as is
            return (T) propValue;
        } else if (defaultValueType != null && propValue.getClass().equals(defaultValueType)) {
            // Class of the default value type and value from DB match - safe to return the value as is
            return defaultValueType.cast(propValue);
        } else {
            // Types do not match - we need to perform a manual cast
            if (propValue instanceof Number numberPropValue && defaultValue instanceof Number) {
                // This may happen due to the Import/Export and deserialization via Jsonb: in such case int values will become double
                if (defaultValue instanceof Double) {
                    return (T) (Double) numberPropValue.doubleValue();
                } else if (defaultValue instanceof Integer) {
                    return (T) (Integer) numberPropValue.intValue();
                } else if (defaultValue instanceof Long) {
                    return (T) (Long) numberPropValue.longValue();
                } else {
                    return defaultValue;
                }
            } else {
                // The other cases except of Number we currently don't know, and basically they should not occur, as they are not automatically recoverable
                // In case this happens - most probably we have refactored some functionality and value stored in this property already changed its class
                // Thus we simply return default value in order to overwrite outdated value
                return defaultValue;
            }
        }
    }
}
