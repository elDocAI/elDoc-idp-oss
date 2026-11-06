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
import ai.eldoc.model.Workflow;
import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * RecoData is not available
 */
@ApplicationException
public class RecoDataNotAvailableException extends ElDocRuntimeException {

    @Serial
    private static final long serialVersionUID = -1487549671708188583L;

    private Workflow _workflow;


    public RecoDataNotAvailableException() {
        super("RecoData is not available");
    }


    public RecoDataNotAvailableException(String message) {
        super(message);
    }


    public RecoDataNotAvailableException(Throwable cause) {
        super(cause);
    }


    public RecoDataNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }


    public Workflow getWorkflow() {
        return _workflow;
    }


    public RecoDataNotAvailableException setWorkflow(Workflow workflow) {
        _workflow = workflow;
        return this;
    }

}
