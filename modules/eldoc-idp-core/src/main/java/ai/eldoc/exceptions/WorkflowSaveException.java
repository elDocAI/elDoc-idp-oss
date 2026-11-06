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

import ai.eldoc.model.Workflow;
import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * Workflow Save Exception
 */
@ApplicationException
public class WorkflowSaveException extends DatabaseException {

    @Serial
    private static final long serialVersionUID = 1111790039960811334L;

    private Workflow _workflow;


    public WorkflowSaveException() {
        super("Workflow can not be saved in the DB");
    }


    public WorkflowSaveException(String message) {
        super(message);
    }


    public WorkflowSaveException(Throwable cause) {
        super(cause);
    }


    public WorkflowSaveException(String message, Throwable cause) {
        super(message, cause);
    }


    public Workflow getEntity() {
        return _workflow;
    }


    public WorkflowSaveException setEntity(Workflow workflow) {
        _workflow = workflow;
        return this;
    }

}
