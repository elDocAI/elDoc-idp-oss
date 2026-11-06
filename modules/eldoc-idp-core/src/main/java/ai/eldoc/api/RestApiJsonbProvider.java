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
import ai.eldoc.util.JsonbUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonValue;
import jakarta.json.bind.*;
import jakarta.json.bind.serializer.*;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import java.lang.reflect.Type;


/**
 * REST API JsonB provider
 */
@ApplicationScoped
public class RestApiJsonbProvider {

    @Inject
    private UrlService _urlService;

    private Jsonb _jsonbInstance;
    private JsonbConfig _configForRestApi;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @PostConstruct
    public void init() {
        JsonbSerializer<DbWebRef> dbWebRefSerializer = new JsonbSerializer<>() {
            @Override
            public void serialize(DbWebRef dbWebRef, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
                if (dbWebRef != null) {
                    serializationContext.serialize(_urlService.toDbWebRefCipher(dbWebRef), jsonGenerator);
                } else {
                    serializationContext.serialize(null, jsonGenerator);
                }
            }
        };

        JsonbDeserializer<DbWebRef> dbWebRefDeserializer = new JsonbDeserializer<>() {
            @Override
            public DbWebRef deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
                if (parser.getValue() != null && JsonValue.ValueType.STRING.equals(parser.getValue().getValueType())) {
                    String dbWebRefCipher = parser.getString();
                    return _urlService.fromDbWebRefCipher(dbWebRefCipher);
                }

                // There is no string to be parsed as DbWebRef, meaning the value is null
                return null;
            }
        };

        JsonbDeserializer<String> stringTrimDeserializer = new JsonbDeserializer<>() {
            @Override
            public String deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
                try {
                    String value = parser.getString();
                    return value == null ? null : value.trim();
                } catch (IllegalStateException ex) {
                    // There is no string to be trimmed, meaning the value is null
                    return null;
                }
            }
        };

        _configForRestApi = JsonbUtils.getConfigStandard()
                //.withPropertyVisibilityStrategy(new RestApiVisibilityStrategy()) // can be anebled once all entities support this
                .withDateFormat(JsonbUtils.DATE_FORMAT_WEB, null)
                .withSerializers(dbWebRefSerializer)
                .withDeserializers(dbWebRefDeserializer, stringTrimDeserializer);

        _jsonbInstance = JsonbBuilder.newBuilder().withConfig(_configForRestApi).build();
    }


    //
    // Getters ------------------------------------------------------------------------------------
    //
    public Jsonb get() {
        return _jsonbInstance;
    }


    public JsonbConfig getConfig() {
        return _configForRestApi;
    }
}
