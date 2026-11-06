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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import ai.eldoc.model.reco.Rectangle;
import static ai.eldoc.model.reco.Rectangle._HEIGHT;
import static ai.eldoc.model.reco.Rectangle._WIDTH;
import static ai.eldoc.model.reco.Rectangle._X;
import static ai.eldoc.model.reco.Rectangle._Y;


/**
 * Rectangle Codec
 */
public class RectangleCodec implements Codec<Rectangle> {

    private static final Logger LOG = Logger.getLogger(RectangleCodec.class.getName());


    @Override
    public void encode(final BsonWriter writer, final Rectangle rect, final EncoderContext encoderContext) {
        writer.writeStartDocument();

        writer.writeDouble(_X, rect.getX());
        writer.writeDouble(_Y, rect.getY());
        writer.writeDouble(_WIDTH, rect.getWidth());
        writer.writeDouble(_HEIGHT, rect.getHeight());

        writer.writeEndDocument();
    }


    @Override
    public Rectangle decode(final BsonReader reader, final DecoderContext decoderContext) {
        
        double x = 0;
        double y = 0;
        double w = 0;
        double h = 0;
        
        reader.readStartDocument();

        while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            String fieldName = reader.readName();
            double value;
            try {
                switch (reader.getCurrentBsonType()) {
                    case INT32:
                        value = reader.readInt32();
                        break;
                    case DOUBLE:
                        value = reader.readDouble();
                        break;
                    default:
                        reader.skipValue();
                        value = 0;
                        LOG.log(Level.WARNING, "Skipping reading value for field [{0}] with type [{1}]", new Object[]{fieldName, reader.getCurrentBsonType()});
                }
                if (_X.equals(fieldName)) {
                    x = value;
                }
                if (_Y.equals(fieldName)) {
                    y = value;
                }
                if (_WIDTH.equals(fieldName)) {
                    w = value;
                }
                if (_HEIGHT.equals(fieldName)) {
                    h = value;
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, fieldName, e);
            }
        }
        reader.readEndDocument();

        return new Rectangle(x, y, w, h);
    }


    @Override
    public Class<Rectangle> getEncoderClass() {
        return Rectangle.class;
    }
}
