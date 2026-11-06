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
import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * Wrapper for the Database related exceptions
 */
@ApplicationException
public class DatabaseException extends ElDocRuntimeException {

    @Serial
    private static final long serialVersionUID = 8097971213310791614L;


    public DatabaseException() {
        super("Database Error");
    }


    public DatabaseException(String message) {
        super(message);
    }


    public DatabaseException(Throwable cause) {
        super(cause);
    }


    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
