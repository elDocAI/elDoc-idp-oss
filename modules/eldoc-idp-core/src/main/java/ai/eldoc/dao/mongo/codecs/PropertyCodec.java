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

package ai.eldoc.dao.mongo.codecs;

import org.bson.*;
import org.bson.codecs.*;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This Code is left in the code for reference only
 */
public class PropertyCodec implements Codec<PropertyCodec.Property> {

    private static final Logger LOG = Logger.getLogger(PropertyCodec.class.getName());


    @Override
    public void encode(final BsonWriter writer, final Property prop, final EncoderContext encoderContext) {
        writer.writeStartDocument();

        writer.writeName(Property._NAME);
        writer.writeString(prop.getName());

        writer.writeName(Property._VALUE);
        if (prop.getValue() instanceof String) {
            writer.writeString((String) prop.getValue());
        } else if (prop.getValue() instanceof Integer) {
            writer.writeInt32((int) prop.getValue());
        } else if (prop.getValue() instanceof Double) {
            writer.writeDouble((double) prop.getValue());
        } else if (prop.getValue() instanceof Date) {
            writer.writeDateTime(((Date) prop.getValue()).getTime());
        } else if (prop.getValue() instanceof Boolean) {
            writer.writeBoolean((Boolean) prop.getValue());
        } else {
            writer.writeNull();
        }

        writer.writeEndDocument();
    }


    @Override
    public Property decode(final BsonReader reader, final DecoderContext decoderContext) {
        String name = null;
        Object value = null;

        reader.readStartDocument();

        while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            String fieldName = reader.readName();
            try {
                switch (reader.getCurrentBsonType()) {
                    case INT32:
                        value = reader.readInt32();
                        break;
                    case DOUBLE:
                        value = reader.readDouble();
                        break;
                    case DATE_TIME:
                        value = new Date(reader.readDateTime());
                        break;
                    case STRING:
                        value = reader.readString();
                        break;
                    case BOOLEAN:
                        value = reader.readBoolean();
                        break;
                    case NULL:
                        value = null;
                        break;
                    default:
                        reader.skipValue();
                        value = null;
                        LOG.log(Level.WARNING, "Skipping reading value for field [{0}] with type [{1}]", new Object[]{fieldName, reader.getCurrentBsonType()});
                }

                if (Property._NAME.equals(fieldName)) {
                    name = String.valueOf(value);
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, fieldName, e);
            }
        }
        reader.readEndDocument();

        return new Property(name, value);
    }


    @Override
    public Class<Property> getEncoderClass() {
        return Property.class;
    }


    public static class Property {

        public static String _NAME = "name";
        public static String _VALUE = "value";


        private Property(String name, Object value) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        private String getName() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }


        private Object getValue() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

}
