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
package ai.eldoc;

import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * elDoc Runtime Exception which marked as {@code @ApplicationException} for better handling inside EJBs
 */
@ApplicationException
public class ElDocRuntimeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -6578784402527366163L;


    public ElDocRuntimeException() {
        super();
    }


    public ElDocRuntimeException(String message) {
        super(message);
    }


    public ElDocRuntimeException(Throwable cause) {
        super(cause);
    }


    public ElDocRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
