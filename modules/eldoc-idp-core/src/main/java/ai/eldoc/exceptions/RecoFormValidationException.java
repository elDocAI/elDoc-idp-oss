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
 * RecoForm validation exception
 */
@ApplicationException
public class RecoFormValidationException extends ElDocRuntimeException {

    @Serial
    private static final long serialVersionUID = -3634141719197044706L;

    public static final int FORM_SOURCE_FILE_REQUIRED = 1;
    public static final int FORM_POSTPROC_INVALID = 2;
    public static final int FORM_NAME_REQUIRED = 3;
    public static final int FORM_NAME_NOT_UNIQUE = 5;
    public static final int FORM_DESCRIPTION_TOO_LONG = 6;
    public static final int FORM_EXTRACTION_CONTEXT_TOO_LONG = 7;
    public static final int FORM_LANGS_REQUIRED = 4;
    public static final int FIELD_NAME_REQUIRED = 100;
    public static final int FIELD_NAME_NOT_UNIQUE = 101;
    public static final int FIELD_POSTPROC_INVALID = 102;
    public static final int FIELD_REGEX_REQUIRED = 103;
    public static final int FIELD_DATETIME_FORMAT_INVALID = 104;
    public static final int FIELD_ANCHOR_TEXT_REQUIRED = 105;
    public static final int FIELD_ANCHOR_REGEX_INVALID = 106;
    public static final int FIELD_ANCHOR_UNDTBL_TEXT_REQUIRED = 107;
    public static final int FIELD_ANCHOR_UNDTBL_REGEX_INVALID = 108;
    public static final int FIELD_ANCHOR_UNDFLD_TEXT_REQUIRED = 109;
    public static final int FIELD_ANCHOR_UNDFLD_REGEX_INVALID = 110;
    public static final int FIELD_REGEX_BLANK = 111;
    public static final int FIELD_REGEX_INVALID = 112;
    public static final int FIELD_REGEX_MISSING_GROUP = 113;
    public static final int FIELD_TAG_NOT_UNIQUE = 114;
    public static final int FIELD_CONF_THRESHOLD_INVALID = 115;
    public static final int FORM_KEYWORD_REQUIRED = 200;
    public static final int FORM_KEYWORD_REGEX_INVALID = 201;
    public static final int FORM_KEYWORD_REGEX_BLANK = 202;

    private int _errorCode;
    private Object[] _messageParams;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoFormValidationException() {
        super("Exception during RecoForm validation");
    }


    public RecoFormValidationException(String message) {
        super(message);
    }


    public RecoFormValidationException(Throwable cause) {
        super(cause);
    }


    public RecoFormValidationException(String message, Throwable cause) {
        super(message, cause);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public int getErrorCode() {
        return _errorCode;
    }


    public RecoFormValidationException setErrorCode(int errorCode) {
        _errorCode = errorCode;
        return this;
    }


    public Object[] getMessageParams() {
        return _messageParams;
    }


    public RecoFormValidationException setMessageParams(Object[] messageParams) {
        _messageParams = messageParams;
        return this;
    }

}
