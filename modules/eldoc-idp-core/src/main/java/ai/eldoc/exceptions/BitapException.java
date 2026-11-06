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
import java.io.Serial;


/**
 * Bitap Exception
 */
public class BitapException extends ElDocRuntimeException {

    @Serial
    private static final long serialVersionUID = -7648205882969871556L;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public BitapException() {
        super("Bitap exception occurred");
    }


    public BitapException(String message) {
        super(message);
    }


    public BitapException(Throwable cause) {
        super(cause);
    }


    public BitapException(String message, Throwable cause) {
        super(message, cause);
    }

}
