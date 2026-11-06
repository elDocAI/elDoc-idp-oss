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
 * BPM Exception indicates that BPM Name can not be resolved
 */
@ApplicationException
public class BpmNameNotResolvedException extends ElDocRuntimeException {

    @Serial
    private static final long serialVersionUID = 4722518651678612723L;

    private String _sourceId;


    public BpmNameNotResolvedException() {
        super("BPM name can not be resolved");
    }


    public BpmNameNotResolvedException(String message) {
        super(message);
    }


    public BpmNameNotResolvedException(Throwable cause) {
        super(cause);
    }


    public BpmNameNotResolvedException(String message, Throwable cause) {
        super(message, cause);
    }


    public String getSourceId() {
        return _sourceId;
    }


    public BpmNameNotResolvedException setSourceId(String sourceId) {
        _sourceId = sourceId;
        return this;
    }

}
