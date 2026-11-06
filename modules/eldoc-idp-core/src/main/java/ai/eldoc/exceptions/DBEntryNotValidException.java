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
 * DB Entry not valid Exception
 */
@ApplicationException
public class DBEntryNotValidException extends DatabaseException {

    @Serial
    private static final long serialVersionUID = -8633858603680041513L;


    public DBEntryNotValidException() {
        super("DB entry not valid (not present in the DB or already outdated)");
    }


    public DBEntryNotValidException(String message) {
        super(message);
    }


    public DBEntryNotValidException(Throwable cause) {
        super(cause);
    }


    public DBEntryNotValidException(String message, Throwable cause) {
        super(message, cause);
    }
}
