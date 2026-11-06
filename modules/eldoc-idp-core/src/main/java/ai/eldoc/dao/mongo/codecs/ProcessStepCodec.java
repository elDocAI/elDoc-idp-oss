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

import ai.eldoc.bpm.ProcessStep;
import ai.eldoc.bpm.v3.ProcessStepImpl;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.*;
import org.bson.codecs.configuration.CodecRegistry;


/**
 * Process Step Codec
 */
public class ProcessStepCodec implements Codec<ProcessStep> {

    private CodecRegistry _codecRegistry;


    @Override
    public void encode(BsonWriter writer, ProcessStep value, EncoderContext encoderContext) {
        Codec codec = _codecRegistry.get(value.getClass());
        codec.encode(writer, value, encoderContext);
    }


    @Override
    public ProcessStep decode(BsonReader reader, DecoderContext decoderContext) {
        // Let's assume all implementations are of ProcessImpl for simplicity
        Codec<ProcessStepImpl> codec = _codecRegistry.get(ProcessStepImpl.class);
        return codec.decode(reader, decoderContext);
    }


    @Override
    public Class<ProcessStep> getEncoderClass() {
        return ProcessStep.class;
    }


    public void setCodecRegistry(CodecRegistry codecRegistry) {
        _codecRegistry = codecRegistry;
    }
}
