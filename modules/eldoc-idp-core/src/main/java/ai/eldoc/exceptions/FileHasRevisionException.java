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
package ai.eldoc.exceptions;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.model.DbFileRef;
import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * Exception indicates that file was attempted to be deleted, but it has revision in the DB,
 * thus it can not be deleted, otherwise it will break the revision chain
 */
@ApplicationException
public class FileHasRevisionException extends ElDocRuntimeException {

    @Serial
    private static final long serialVersionUID = -1072344956479826425L;

    private DbFileRef _dbFileRef;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public FileHasRevisionException() {
        super("File has revisions in the DB thus can not be deleted (as it will break revisions chain) and new revisions must not reference to it");
    }


    public FileHasRevisionException(String message) {
        super(message);
    }


    public FileHasRevisionException(Throwable cause) {
        super(cause);
    }


    public FileHasRevisionException(String message, Throwable cause) {
        super(message, cause);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public DbFileRef getDbFileRef() {
        return _dbFileRef;
    }


    public FileHasRevisionException setDbFileRef(DbFileRef dbFileRef) {
        _dbFileRef = dbFileRef;
        return this;
    }

}
