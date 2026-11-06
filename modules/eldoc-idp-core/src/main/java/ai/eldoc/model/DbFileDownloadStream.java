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

import ai.eldoc.ElDocRuntimeException;
import java.io.IOException;
import java.io.InputStream;


/**
 * DbFile Download Stream (InputStream) Model
 */
public class DbFileDownloadStream extends InputStream {

    private final InputStream _is;
    private final DbFile _dbFile;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public DbFileDownloadStream(InputStream is, DbFile dbFile) {
        _is = is;
        _dbFile = dbFile;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public int read() throws IOException {
        return _is.read();
    }


    @Override
    public boolean markSupported() {
        return _is.markSupported();
    }


    @Override
    public synchronized void reset() throws IOException {
        _is.reset();
    }


    @Override
    public synchronized void mark(int readlimit) {
        _is.mark(readlimit);
    }


    @Override
    public void close() {
        try {
            _is.close();
        } catch (IOException ex) {
            throw new ElDocRuntimeException(ex);
        }
    }


    @Override
    public int available() throws IOException {
        return _is.available();
    }


    @Override
    public long skip(long n) throws IOException {
        return _is.skip(n);
    }


    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return _is.read(b, off, len);
    }


    @Override
    public int read(byte[] b) throws IOException {
        return _is.read(b);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public DbFile getDbFile() {
        return _dbFile;
    }
}
