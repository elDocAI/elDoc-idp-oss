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
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


/**
 * Standard JAX-RS (JSON-B) visibility strategy
 */
public class RestApiVisibilityStrategy implements PropertyVisibilityStrategy {

    /**
     * Returns true in case field is visible for the REST API
     * Two conditions must met:
     * 1) field is annotated with @JsonbProperty
     * 2) field is annotated with @RestApiVisible
     *
     * @param field
     * @return
     */
    @Override
    public boolean isVisible(Field field) {
        return field.getAnnotation(JsonbProperty.class) != null && field.getAnnotation(RestApiVisible.class) != null;
    }


    @Override
    public boolean isVisible(Method method) {
        return method.getAnnotation(JsonbProperty.class) != null && method.getAnnotation(RestApiVisible.class) != null;
    }

}
