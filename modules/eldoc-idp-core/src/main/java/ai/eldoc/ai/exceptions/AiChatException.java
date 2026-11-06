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
package ai.eldoc.ai.exceptions;

import ai.eldoc.ElDocRuntimeException;
import java.io.Serial;
import java.util.Arrays;


/**
 * AI Chat Communication/processing exception
 */
public class AiChatException extends ElDocRuntimeException {

    @Serial
    private static final long serialVersionUID = 3250481040907353895L;

    public static final int MODEL_NOT_CONFIGURED = 1;

    private int _errorCode;
    private Object[] _messageParams;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public AiChatException() {
        super("Exception during AI Chat message processing");
    }


    public AiChatException(String message) {
        super(message);
    }


    public AiChatException(Throwable cause) {
        super(cause);
    }


    public AiChatException(String message, Throwable cause) {
        super(message, cause);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public int getErrorCode() {
        return _errorCode;
    }


    public AiChatException setErrorCode(int errorCode) {
        _errorCode = errorCode;
        return this;
    }


    public Object[] getMessageParams() {
        return _messageParams;
    }


    public AiChatException setMessageParams(Object[] messageParams) {
        _messageParams = messageParams;
        return this;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + _errorCode;
        hash = 59 * hash + Arrays.deepHashCode(_messageParams);
        return hash;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AiChatException other = (AiChatException) obj;
        if (_errorCode != other._errorCode) {
            return false;
        }
        return Arrays.deepEquals(_messageParams, other._messageParams);
    }

}
