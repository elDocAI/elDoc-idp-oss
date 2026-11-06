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

import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * Wrapper for the Record not found in DB exception
 */
@ApplicationException
public class RecordNotFoundInDbException extends DatabaseException {

    @Serial
    private static final long serialVersionUID = -1056666146864528670L;


    public RecordNotFoundInDbException() {
        super("Record not found in DB");
    }


    public RecordNotFoundInDbException(String message) {
        super(message);
    }


    public RecordNotFoundInDbException(Throwable cause) {
        super(cause);
    }


    public RecordNotFoundInDbException(String message, Throwable cause) {
        super(message, cause);
    }
}
