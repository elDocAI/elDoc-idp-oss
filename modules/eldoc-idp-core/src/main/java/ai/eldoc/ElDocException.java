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


/**
 * elDoc Exception which marked as {@code @ApplicationException} for better handling inside EJBs
 */
@ApplicationException
public class ElDocException extends Exception {

    private static final long serialVersionUID = 4751585652789464858L;


    public ElDocException() {
        super();
    }


    public ElDocException(String message) {
        super(message);
    }


    public ElDocException(Throwable cause) {
        super(cause);
    }


    public ElDocException(String message, Throwable cause) {
        super(message, cause);
    }

}
