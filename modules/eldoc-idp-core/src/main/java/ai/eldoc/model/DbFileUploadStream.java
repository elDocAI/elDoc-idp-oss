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
package ai.eldoc.model;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


/**
 * DbFile Upload Stream (OutputStream) Model
 */
public class DbFileUploadStream extends OutputStream {

    private final OutputStream _os;
    private final String _ref;
    private final String _fileId;
    private final List<String> _tags;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * Creates DbFile Upload Stream
     *
     * @param os upload stream
     * @param collectionName target collection name (usually bucket or GridFS name)
     * @param fileId file id in the target bucket collection
     * @param tags file tags
     */
    public DbFileUploadStream(OutputStream os, String collectionName, String fileId, List<String> tags) {
        _os = os;
        _tags = tags;
        _ref = collectionName;
        _fileId = fileId;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public void close() throws IOException {
        _os.close();
    }


    @Override
    public void write(int i) throws IOException {
        _os.write(i);
    }


    @Override
    public void write(byte[] bytes) throws IOException {
        _os.write(bytes);
    }


    @Override
    public void write(byte[] bytes, int i, int i1) throws IOException {
        _os.write(bytes, i, i1);
    }


    @Override
    public void flush() throws IOException {
        _os.flush();
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public DbFileRef getDbFileRef() {
        return new DbFileRef(_ref, _fileId, _tags);
    }
}
