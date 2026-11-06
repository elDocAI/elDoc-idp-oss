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

import ai.eldoc.bpm.ProcessSubject;
import ai.eldoc.bpm.v3.ProcessSubjectImpl;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.*;
import org.bson.codecs.configuration.CodecRegistry;


/**
 * Process Subject Codec
 */
public class ProcessSubjectCodec implements Codec<ProcessSubject> {

    private CodecRegistry _codecRegistry;


    @Override
    public void encode(BsonWriter writer, ProcessSubject value, EncoderContext encoderContext) {
        Codec codec = _codecRegistry.get(value.getClass());
        codec.encode(writer, value, encoderContext);
    }


    @Override
    public ProcessSubject decode(BsonReader reader, DecoderContext decoderContext) {
        // Let's assume all implementations are of ProcessImpl for simplicity
        Codec<ProcessSubjectImpl> codec = _codecRegistry.get(ProcessSubjectImpl.class);
        return codec.decode(reader, decoderContext);
    }


    @Override
    public Class<ProcessSubject> getEncoderClass() {
        return ProcessSubject.class;
    }


    public void setCodecRegistry(CodecRegistry codecRegistry) {
        _codecRegistry = codecRegistry;
    }

}
