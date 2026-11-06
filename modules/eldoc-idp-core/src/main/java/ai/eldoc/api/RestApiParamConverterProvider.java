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

import ai.eldoc.model.DbWebRef;
import ai.eldoc.service.UrlService;
import jakarta.inject.Inject;
import jakarta.ws.rs.ext.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;


/**
 * JAX-RS Param Converter Provider for JAR-RS
 */
@Provider
public class RestApiParamConverterProvider implements ParamConverterProvider {

    @Inject
    private UrlService _urlService;

    @Inject
    private RestApiJsonbProvider _restApiJsonbProvider;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {

        //
        // DbWebRef param converter
        //
        ParamConverter<DbWebRef> dbWebRefConverter = new ParamConverter<>() {
            @Override
            public DbWebRef fromString(String dbWebRefCipher) {
                return _urlService.fromDbWebRefCipher(dbWebRefCipher);
            }


            @Override
            public String toString(DbWebRef dbWebRef) {
                return _urlService.toDbWebRefCipher(dbWebRef);
            }
        };

        //
        // Param converters selector
        //
        if (rawType.equals(DbWebRef.class)) {
            return (ParamConverter<T>) dbWebRefConverter;
        } else {
            return null;
        }
    }

}
