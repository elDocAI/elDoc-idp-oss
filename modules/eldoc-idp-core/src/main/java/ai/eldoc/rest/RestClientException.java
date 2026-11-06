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

package ai.eldoc.rest;

import ai.eldoc.ElDocRuntimeException;
import jakarta.ejb.ApplicationException;


/**
 * REST Client Generic Exception
 */
@ApplicationException
public class RestClientException extends ElDocRuntimeException {

    private static final long serialVersionUID = -8911440390040283336L;


    public RestClientException() {
        super("REST Client Generic Exception");
    }


    public RestClientException(String message) {
        super(message);
    }


    public RestClientException(Throwable cause) {
        super(cause);
    }


    public RestClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
