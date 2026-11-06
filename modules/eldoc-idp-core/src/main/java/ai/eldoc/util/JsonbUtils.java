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
package ai.eldoc.util;

import ai.eldoc.model.reco.Rectangle;
import jakarta.json.*;
import jakarta.json.bind.*;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.serializer.*;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.bson.types.ObjectId;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * JSON-B Utils
 */
public class JsonbUtils {

    /**
     * Precised ISO-8601 date-time format which includes milliseconds
     */
    public static final String DATE_FORMAT_PRECISED = "yyyy-MM-dd'T'HH:mm:ss.SSSX";

    /**
     * Mode relaxed ISO-8601 date-time format which omits milliseconds and returns only seconds
     */
    public static final String DATE_FORMAT_WEB = "yyyy-MM-dd'T'HH:mm:ssX";

    /**
     * SimpleDateFormat format pattern Z is for RFC 822 time zone and pattern X is for ISO 8601 (this standard supports single letter time zone names like Z for Zulu)
     */
    public static final SimpleDateFormat DATE_FORMAT_ISO8601 = new SimpleDateFormat(DATE_FORMAT_PRECISED);


    static {
        DATE_FORMAT_ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final static Jsonb _instance = JsonbBuilder.newBuilder().withConfig(getConfigStandard()).build();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Returns global instance with standard config
     *
     * @return
     */
    public static Jsonb getInstance() {
        return _instance;
    }


    /**
     * Creates new instance on each request.To be used for debugging purposes only
     *
     * @param withFormatting
     * @return
     */
    public static Jsonb getInstance(boolean withFormatting) {
        return JsonbBuilder.newBuilder().withConfig(getConfigStandard(withFormatting)).build();
    }


    /**
     * Returns standard config
     *
     * @return
     */
    public static JsonbConfig getConfigStandard() {
        JsonbSerializer<ObjectId> objectIdSerializer = new JsonbSerializer<>() {
            @Override
            public void serialize(ObjectId objectId, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
                if (objectId != null) {
                    serializationContext.serialize(objectId.toHexString(), jsonGenerator);
                } else {
                    serializationContext.serialize(null, jsonGenerator);
                }
            }
        };

        JsonbSerializer<Rectangle> rectangleSerializer = new JsonbSerializer<>() {
            @Override
            public void serialize(Rectangle rect, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
                if (rect != null) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.write(Rectangle._X, rect.getX());
                    jsonGenerator.write(Rectangle._Y, rect.getY());
                    jsonGenerator.write(Rectangle._WIDTH, rect.getWidth());
                    jsonGenerator.write(Rectangle._HEIGHT, rect.getHeight());
                    jsonGenerator.writeEnd();
                } else {
                    serializationContext.serialize(null, jsonGenerator);
                }
            }
        };

        JsonbDeserializer<Rectangle> rectangleDeserializer = new JsonbDeserializer<>() {
            @Override
            public Rectangle deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
                if (!parser.hasNext()) {
                    // There is no object to be parsed as Rectangle, meaning the value is null
                    return null;
                }

                JsonObject rectJson = parser.getObject();
                double x = rectJson.getJsonNumber(Rectangle._X).doubleValue();
                double y = rectJson.getJsonNumber(Rectangle._Y).doubleValue();
                double w = rectJson.getJsonNumber(Rectangle._WIDTH).doubleValue();
                double h = rectJson.getJsonNumber(Rectangle._HEIGHT).doubleValue();
                return new Rectangle(x, y, w, h);
            }
        };

        /*
        JsonbSerializer<Date> dateSerializer = new JsonbSerializer<Date>() {
            @Override
            public void serialize(Date date, JsonGenerator jsonGenerator, SerializationContext serializationContext) {
                if (date != null) {
                    serializationContext.serialize(dateToIsoString(), jsonGenerator);
                } else {
                    serializationContext.serialize(null, jsonGenerator);
                }
            }
        };
         */
        JsonbConfig config = new JsonbConfig()
                .withNullValues(true) // some logic may rely on this behaviour so it is important to have it in place, particurarly RecoFileMeta.toJson()
                .withPropertyOrderStrategy(PropertyOrderStrategy.LEXICOGRAPHICAL)
                .withSerializers(objectIdSerializer, rectangleSerializer)
                .withDeserializers(rectangleDeserializer)
                .withDateFormat(DATE_FORMAT_PRECISED, null);

        return config;
    }


    /**
     * Returns standard confing with some modifications
     *
     * @param withFormatting
     * @return
     */
    public static JsonbConfig getConfigStandard(boolean withFormatting) {
        return getConfigStandard().withFormatting(withFormatting);
    }


    /**
     * Formats Date into ISO-8601 date string, such as '2011-12-03T10:15:30.123Z'.
     *
     * @param date
     * @return
     */
    public static String dateToIsoString(Date date) {
        //return ZonedDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        return DATE_FORMAT_ISO8601.format(date);
    }


    /**
     * Formats Instant as ISO-8601 date string, such as '2026-03-10T20:38:56.589498200Z'.<br>
     * Hint: Use <code>Instant.now().truncatedTo(ChronoUnit.MILLIS)</code> in order to truncate instant to milliseconds.
     *
     * @param instant
     * @return
     */
    public static String instantToIsoString(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }


    /**
     * Returns Json Array Builder
     *
     * @return
     */
    public static JsonArrayBuilder createArrayBuilder() {
        return Json.createArrayBuilder();
    }


    /**
     * Returns Null Aware Json Object Builder<br>
     * Returned instance ignores null values and do not set them as JsonValue.NULL
     *
     * @return
     */
    public static JsonObjectBuilder createObjectBuilder() {
        return new NullAwareJsonObjectBuilder(Json.createObjectBuilder(), false);
    }


    /**
     * Returns Null Aware Json Object Builder
     *
     * @param setNullValuesAsJsonNull defines whether null values should be set as JsonValue.NULL or not
     * @return
     */
    public static JsonObjectBuilder createObjectBuilder(boolean setNullValuesAsJsonNull) {
        return new NullAwareJsonObjectBuilder(Json.createObjectBuilder(), setNullValuesAsJsonNull);
    }


    /**
     * Returns Null Aware Json Object Builder
     *
     * @param map
     * @param serializeNulls defines whether null values should be serialized or not
     * @return
     */
    public static JsonObjectBuilder createObjectBuilder(Map<String, Object> map, boolean serializeNulls) {
        return new NullAwareJsonObjectBuilder(Json.createObjectBuilder(map), serializeNulls);
    }


    /**
     * Returns string representation of provided JSON value.<br/>
     * If the value of the type {@link String} - it is returned as is. Otherwise - JSON representation
     * of the value is returned.
     *
     * @param value
     * @return
     */
    public static String getValueAsString(JsonValue value) {
        if (value instanceof JsonString) {
            return ((JsonString) value).getString();
        }
        return value != null ? value.toString() : null;
    }


    //
    // Inner Classes ------------------------------------------------------------------------------
    //
    /**
     * Null Aware Json Object Builder Wrapper Class
     */
    public static class NullAwareJsonObjectBuilder implements JsonObjectBuilder {

        private final JsonObjectBuilder _builder;
        private final boolean _serializeNulls;


        //
        // Constructor ----------------------------------------------------------------------------
        //
        private NullAwareJsonObjectBuilder(JsonObjectBuilder builder, boolean serializeNulls) {
            _builder = builder;
            _serializeNulls = serializeNulls;
        }


        //
        // Methods overrides ----------------------------------------------------------------------
        //
        @Override
        public JsonObjectBuilder add(String name, JsonValue value) {
            if (name == null) {
                return _builder; // Nothing to do
            }
            if (value == null && !_serializeNulls) {
                return _builder; // Nothing to do
            }

            return _builder.add(name, (value == null) ? JsonValue.NULL : value);
        }


        @Override
        public JsonObjectBuilder add(String name, String value) {
            if (name == null) {
                return _builder; // Nothing to do
            }

            if (value == null) {
                if (_serializeNulls) {
                    return addNull(name);
                } else {
                    return _builder;
                }
            }

            return _builder.add(name, value);
        }


        @Override
        public JsonObjectBuilder add(String name, BigInteger value) {
            if (name == null) {
                return _builder; // Nothing to do
            }

            if (value == null) {
                if (_serializeNulls) {
                    return addNull(name);
                } else {
                    return _builder;
                }
            }

            return _builder.add(name, value);
        }


        @Override
        public JsonObjectBuilder add(String name, BigDecimal value) {
            if (name == null) {
                return _builder; // Nothing to do
            }

            if (value == null) {
                if (_serializeNulls) {
                    return addNull(name);
                } else {
                    return _builder;
                }
            }

            return _builder.add(name, value);
        }


        @Override
        public JsonObjectBuilder add(String name, int i) {
            if (name == null) {
                return _builder; // Nothing to do
            }
            return _builder.add(name, i);
        }


        @Override
        public JsonObjectBuilder add(String name, long l) {
            if (name == null) {
                return _builder; // Nothing to do
            }
            return _builder.add(name, l);
        }


        @Override
        public JsonObjectBuilder add(String name, double d) {
            if (name == null) {
                return _builder; // Nothing to do
            }
            return _builder.add(name, d);
        }


        @Override
        public JsonObjectBuilder add(String name, boolean bln) {
            if (name == null) {
                return _builder; // Nothing to do
            }
            return _builder.add(name, bln);
        }


        @Override
        public JsonObjectBuilder add(String name, JsonObjectBuilder value) {
            if (name == null) {
                return _builder; // Nothing to do
            }

            if (value == null) {
                if (_serializeNulls) {
                    return addNull(name);
                } else {
                    return _builder;
                }
            }

            return _builder.add(name, value);
        }


        @Override
        public JsonObjectBuilder add(String name, JsonArrayBuilder value) {
            if (name == null) {
                return _builder; // Nothing to do
            }

            if (value == null) {
                if (_serializeNulls) {
                    return addNull(name);
                } else {
                    return _builder;
                }
            }

            return _builder.add(name, value);
        }


        @Override
        public JsonObjectBuilder addNull(String name) {
            if (name == null) {
                return _builder; // Nothing to do
            }
            return _builder.add(name, JsonValue.NULL);
        }


        @Override
        public JsonObject build() {
            return _builder.build();
        }


        @Override
        public JsonObjectBuilder remove(String name) {
            if (name == null) {
                return _builder; // Nothing to do
            }
            return _builder.remove(name);
        }


        @Override
        public JsonObjectBuilder addAll(JsonObjectBuilder builder) {
            if (builder == null) {
                return _builder; // Nothing to do
            }
            return _builder.addAll(builder);
        }

    }

}
