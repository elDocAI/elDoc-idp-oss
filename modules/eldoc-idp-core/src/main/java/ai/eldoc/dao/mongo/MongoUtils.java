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
package ai.eldoc.dao.mongo;

import ai.eldoc.dao.mongo.codecs.*;
import ai.eldoc.exceptions.BadArgumentException;
import ai.eldoc.model.*;
import ai.eldoc.util.JsonbUtils;
import com.mongodb.MongoClientSettings;
import org.bson.*;
import org.bson.codecs.*;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.*;
import org.bson.codecs.pojo.IdGenerator;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import java.util.*;
import static java.text.MessageFormat.format;
import static org.bson.codecs.configuration.CodecRegistries.*;


/**
 * MongoDB Utils
 */
public class MongoUtils {

    /**
     * Defines default to JSON-serialization settings, what includes the following specifics:<br>
     * - ObjectId serialized to regular string in JSON;<br>
     * - Date serialized to ISO-8601 string with milliseconds;<br>
     *
     * See more: {@link https://mongodb.github.io/mongo-java-driver/4.1/bson/extended-json/}
     */
    public final static JsonWriterSettings JSON_SERIALIZER_SETTINGS = JsonWriterSettings.builder()
            .outputMode(JsonMode.RELAXED)
            .objectIdConverter((objectIdValue, writer) -> writer.writeString(objectIdValue.toHexString()))
            .dateTimeConverter((dateValue, writer) -> writer.writeString(JsonbUtils.dateToIsoString(new Date(dateValue))))
            .build();

    private final static CodecRegistry _codecRegistry = initAndGetCodecRegistry();
    private final static CodecRegistry _codecRegistryWithSerializeNulls = initAndGetCodecRegistry(true);


    //
    // Utility Methods ----------------------------------------------------------------------------
    //
    /**
     * Performs a type-safe conversion of the given Java-object to Key-Value Map<br>
     * IMPORTANT: fields with NULL values are ignored
     *
     * @param <C>
     * @param object
     * @param type
     * @return
     * @throws NullPointerException when given object is null
     * @throws BadArgumentException in case class of the given object doesn't match provided class type
     */
    public static <C> Document objectToDocument(Object object, Class<C> type) throws NullPointerException, BadArgumentException {
        if (object == null) {
            throw new NullPointerException("Object argument is null");
        }

        if (!object.getClass().equals(type)) {
            throw new BadArgumentException(format("Object {0} doesn''t match the given Type ''{1}''", object.getClass(), type)); // {0} and {1} already prints the word class: 'class ai.eldoc.fm.model.FileAcl'
        }

        try {
            BsonDocument bsonDoc = new BsonDocument();
            BsonWriter writer = new BsonDocumentWriter(bsonDoc);
            CodecRegistry codecRegistry = getCodecRegistry();
            codecRegistry.get(type).encode(writer, (C) object, EncoderContext.builder().build());
            return new DocumentCodec(codecRegistry).decode(bsonDoc.asBsonReader(), DecoderContext.builder().build());
        } catch (Exception e) {
            throw new IllegalArgumentException(format("Failed to encode object[{0}] of the declared ''{1}'' to Document", object, type), e); // {1} already prints the word class: 'class ai.eldoc.fm.model.FileAcl'
        }
    }


    /**
     * Performs conversion of the Document to the Java-object of the given Type
     *
     * @param <C>
     * @param document
     * @param type
     * @return
     */
    public static <C> C documentToObject(Document document, Class<C> type) {
        if (document == null) {
            throw new NullPointerException("Document argument is null");
        }
        try {
            return getCodecRegistry().get(type).decode(document.toBsonDocument().asBsonReader(), DecoderContext.builder().build());
        } catch (Exception e) {
            throw new IllegalArgumentException(format("Failed to decode Document[{0}] to ''{1}''", document, type, e));
        }
    }


    //
    // Private Methods ----------------------------------------------------------------------------
    //
    private static CodecRegistry initAndGetCodecRegistry() {
        return initAndGetCodecRegistry(false);
    }


    private static CodecRegistry initAndGetCodecRegistry(boolean serializeNulls) {
        // some links:
        // https://stackoverflow.com/questions/47800882/save-list-of-interface-objects-using-mongo-driver-for-java
        // https://docs.mongodb.com/drivers/java/sync/current/fundamentals/data-formats/pojo-customization

        PojoCodecProvider.Builder pojoBuilder = PojoCodecProvider.builder();
        pojoBuilder.register(getPropertyCodecProviders());
        pojoBuilder.conventions(getConventions(serializeNulls));
        PojoCodecProvider pojoCodecProvider = pojoBuilder.automatic(true).build();

        CodecRegistry pojoCodecRegistry = fromProviders(pojoCodecProvider);

        // Init base codecs registry
        CodecRegistry baseCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);

        // Create codecs for each Process component
        ProcessSubjectCodec pSubjectCodec = new ProcessSubjectCodec();
        ProcessStepCodec pStepCodec = new ProcessStepCodec();
        ProcessStateCodec pStateCodec = new ProcessStateCodec();
        ProcessCodec processCodec = new ProcessCodec();

        // Create registry for Process model
        CodecRegistry processCodecRegistry = fromRegistries(fromCodecs(pSubjectCodec, pStepCodec, pStateCodec, processCodec), baseCodecRegistry);

        // Each process component codec should know about other process components codecs and base codecs
        pSubjectCodec.setCodecRegistry(processCodecRegistry);
        pStepCodec.setCodecRegistry(processCodecRegistry);
        pStateCodec.setCodecRegistry(processCodecRegistry);
        processCodec.setCodecRegistry(processCodecRegistry);

        // Build full codec registry
        // IMPORTANT: keep default codec registry first and POJO last, as registries are scanned in provided order
        CodecRegistry codecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromCodecs(new RectangleCodec()),
                processCodecRegistry,
                pojoCodecRegistry
        );

        return codecRegistry;
    }


    /**
     * Method to overwrite when custom codecs are required
     *
     * @return custom codecs providers
     */
    private static PropertyCodecProvider[] getPropertyCodecProviders() {
        return new PropertyCodecProvider[0];
    }


    /**
     * Method to overwrite when custom conventions are required
     *
     * @return custom conventions
     */
    private static List<Convention> getConventions(boolean serializeNulls) {
        List<Convention> conventions = new ArrayList<>(); // An empty conventions list
        conventions.add(Conventions.ANNOTATION_CONVENTION);
        conventions.add(Conventions.USE_GETTERS_FOR_SETTERS); // A convention that uses getter methods as setters for collections and maps if there is no setter
        if (serializeNulls) {
            conventions.add(getSerializeNullsConvention());
        }
        conventions.add(getClassDiscriminatorConvention());
        conventions.add(getBasicModelObjectIdConvention()); // Custom convention for _id property
        conventions.add(getBasicFileAwareModelConvention());
        conventions.add(getPropertyAwareModelConvention());
        conventions.add(getCustomFieldAwareModelConvention());
        return conventions;
    }


    /**
     * Class Discriminator Convention which is based on the default class and property convention (CLASS_AND_PROPERTY_CONVENTION)
     * Sets the discriminator key if not set to _t and the discriminator value if not set to the ClassModels simple type name.
     *
     * @return
     */
    private static Convention getClassDiscriminatorConvention() {
        return new Convention() {
            @Override
            public void apply(final ClassModelBuilder<?> classModelBuilder) {
                if (classModelBuilder.getDiscriminatorKey() == null) {
                    classModelBuilder.discriminatorKey("_t");
                }
                if (classModelBuilder.getDiscriminator() == null && classModelBuilder.getType() != null) {
                    classModelBuilder.discriminator(classModelBuilder.getType().getName());
                }
            }
        };
    }


    /**
     * Provides support for the auto-population of the _id property in the POJOs DAO
     *
     * @return
     */
    private static Convention getBasicModelObjectIdConvention() {
        return new Convention() {
            @Override
            public void apply(ClassModelBuilder<?> classModelBuilder) {

                if (classModelBuilder != null && classModelBuilder.getType() != null && BasicModel.class.isAssignableFrom(classModelBuilder.getType())) {

                    classModelBuilder.idPropertyName("id");

                    classModelBuilder.idGenerator(new IdGenerator<String>() {
                        @Override
                        public String generate() {
                            return new ObjectId().toHexString();
                        }


                        @Override
                        public Class<String> getType() {
                            return String.class;
                        }
                    });

                    PropertyModelBuilder<?> propertyModel = classModelBuilder.getProperty("id");
                    if (propertyModel != null) {
                        ((PropertyModelBuilder<String>) propertyModel).codec(new Codec<String>() {

                            @Override
                            public Class<String> getEncoderClass() {
                                return String.class;
                            }


                            @Override
                            public void encode(BsonWriter writer, String value, EncoderContext encoderContext) {
                                writer.writeObjectId(new ObjectId(value));
                            }


                            @Override
                            public String decode(BsonReader reader, DecoderContext decoderContext) {
                                return reader.readObjectId().toHexString();
                            }

                        });
                    }
                }
            }
        };
    }


    /**
     * Convention for handling special field for storing files: "_files" in MongoDB
     * We need this convention in order to workaround the issue when using Conventions.USE_GETTERS_FOR_SETTERS and not using Setters for collection/map
     * properties, but using only Getters and when property name has to be customized.
     * In such situation WriteName property remains default thus it has to be defined in a custom way.
     *
     * @return
     */
    private static Convention getBasicFileAwareModelConvention() {
        return new Convention() {
            @Override
            public void apply(ClassModelBuilder<?> classModelBuilder) {
                if (classModelBuilder != null && classModelBuilder.getType() != null && BasicFileAwareModel.class.isAssignableFrom(classModelBuilder.getType())) {
                    PropertyModelBuilder<?> propertyModel = classModelBuilder.getProperty("files");
                    if (propertyModel != null) {
                        propertyModel.writeName(BasicFileAwareModel._FILES);
                    }
                }
            }
        };
    }


    private static Convention getPropertyAwareModelConvention() {
        return new Convention() {
            @Override
            public void apply(ClassModelBuilder<?> classModelBuilder) {
                if (classModelBuilder != null && classModelBuilder.getType() != null && PropertyAwareModel.class.isAssignableFrom(classModelBuilder.getType())) {
                    PropertyModelBuilder<?> propertyModel = classModelBuilder.getProperty("properties");
                    if (propertyModel != null) {
                        propertyModel.writeName(PropertyAwareModel._PROPERTIES);
                    }
                }
            }
        };
    }


    private static Convention getSerializeNullsConvention() {
        return new Convention() {
            @Override
            public void apply(ClassModelBuilder<?> classModelBuilder) {
                for (PropertyModelBuilder<?> pmb : classModelBuilder.getPropertyModelBuilders()) { // apply to all properties
                    pmb.propertySerialization(new PropertySerialization() {
                        @Override
                        public boolean shouldSerialize(Object t) {
                            return true; // always serialize object even if t == null
                        }
                    });
                }
            }
        };
    }


    private static Convention getCustomFieldAwareModelConvention() {
        return new Convention() {
            @Override
            public void apply(ClassModelBuilder<?> classModelBuilder) {
                if (classModelBuilder != null && classModelBuilder.getType() != null && CustomFieldAwareModel.class.isAssignableFrom(classModelBuilder.getType())) {
                    PropertyModelBuilder<?> propertyModel = classModelBuilder.getProperty("cfDef");
                    if (propertyModel != null) {
                        propertyModel.writeName(CustomFieldAwareModel._CF_DEF);
                    }
                    propertyModel = classModelBuilder.getProperty("cfData");
                    if (propertyModel != null) {
                        propertyModel.writeName(CustomFieldAwareModel._CF_DATA);
                    }
                }
            }
        };
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public static CodecRegistry getCodecRegistry() {
        return getCodecRegistry(false);
    }


    public static CodecRegistry getCodecRegistry(boolean serializeNulls) {
        if (serializeNulls) {
            return _codecRegistryWithSerializeNulls;
        } else {
            return _codecRegistry;
        }
    }
}
