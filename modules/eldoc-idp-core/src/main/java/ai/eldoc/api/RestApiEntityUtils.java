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
package ai.eldoc.api;

import ai.eldoc.annotation.RestApiVisible;
import ai.eldoc.model.BasicModel;
import jakarta.json.bind.annotation.JsonbProperty;
import org.apache.commons.beanutils.PropertyUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * REST API Entity Utils
 */
public class RestApiEntityUtils {

    private static final Logger LOG = Logger.getLogger(RestApiEntityUtils.class.getName());


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns all REST API Readable property names based on defined annotations on the getters
     *
     * @param entityClass
     * @return
     */
    public static List<String> getRestApiReadableFields(Class entityClass) {
        List<String> result = new ArrayList<>();
        Method[] methods = entityClass.getMethods();
        for (Method method : methods) {
            JsonbProperty jsonbPropAnnt = method.getAnnotation(JsonbProperty.class);
            if (method.getAnnotation(RestApiVisible.class) != null && jsonbPropAnnt != null && isNotBlank(jsonbPropAnnt.value())) {
                result.add(jsonbPropAnnt.value());
            }
        }
        return result;
    }


    /**
     * Returns all REST API Writable property names based on defined annotations on the setters
     *
     * @param entityClass
     * @return
     */
    public static List<String> getRestApiWritableFields(Class entityClass) {
        List<String> result = new ArrayList<>();
        Method[] methods = entityClass.getMethods();
        for (Method method : methods) {
            JsonbProperty jsonbPropAnnt = method.getAnnotation(JsonbProperty.class);
            if (method.getAnnotation(RestApiVisible.class) != null
                    && method.getAnnotation(RestApiVisible.class).writable()
                    && jsonbPropAnnt != null
                    && isNotBlank(jsonbPropAnnt.value())) {
                result.add(jsonbPropAnnt.value());
            }
        }
        return result;
    }


    /**
     * Tries to locate Class's writable property name (Setter) based on the REST API Writable Field @RestApiWritable annotation,
     * and if found - calculates property name based on the Setter name
     *
     * @param entityClass
     * @param restApiPropName
     * @return
     */
    public static String getRestModelWritableLocalName(Class entityClass, String restApiPropName) {
        Method[] methods = entityClass.getMethods();
        for (Method method : methods) {
            JsonbProperty jsonbPropAnnt = method.getAnnotation(JsonbProperty.class);
            if (method.getAnnotation(RestApiVisible.class) != null
                    && method.getAnnotation(RestApiVisible.class).writable()
                    && jsonbPropAnnt != null
                    && isNotBlank(jsonbPropAnnt.value())
                    && jsonbPropAnnt.value().equals(restApiPropName)) {
                String methodName = method.getName();
                if (methodName.startsWith("set") && methodName.length() > 5) {
                    return methodName.substring(3, 4).toLowerCase().concat(methodName.substring(4, methodName.length()));
                }
            }
        }
        return restApiPropName;
    }


    /**
     * Sets property value on the given entity
     *
     * @param entity
     * @param keyName
     * @param value
     */
    public static void setPropertyValue(BasicModel entity, String keyName, Object value) {
        String propName = keyName;
        try {
            boolean isWritable = PropertyUtils.isWriteable(entity, propName);

            if (isWritable == false) {
                propName = getRestModelWritableLocalName(entity.getClass(), keyName);
                isWritable = PropertyUtils.isWriteable(entity, propName);
            }

            if (isWritable) {
                PropertyUtils.setSimpleProperty(entity, propName, value);
            }
        } catch (Exception e) {
            String msg = format("{0}[{1}] error setting value via API for ''{2}''=''{3}''", entity.getClass(), entity.getId(), propName, value);
            LOG.log(Level.SEVERE, msg, e);
        }
    }

}
