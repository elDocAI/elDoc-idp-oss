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

import jakarta.ejb.ApplicationException;


/**
 * Response Status Code Exception thrown when returned status code is not equal to the expected status code
 */
@ApplicationException
public class ResponseStatusCodeException extends RestClientException {

    private static final long serialVersionUID = -4228667295244141346L;

    private int _statusCode;


    public ResponseStatusCodeException() {
        super("Unexpected Response Status Code Exception");
    }


    public ResponseStatusCodeException(String message) {
        super(message);
    }


    public ResponseStatusCodeException(Throwable cause) {
        super(cause);
    }


    public ResponseStatusCodeException(String message, Throwable cause) {
        super(message, cause);
    }


    public int getStatusCode() {
        return _statusCode;
    }


    public ResponseStatusCodeException setStatusCode(int statusCode) {
        _statusCode = statusCode;
        return this;
    }


    @Override
    public String getMessage() {
        return super.getMessage() + " (Status Code: " + _statusCode + ')';
    }

}
