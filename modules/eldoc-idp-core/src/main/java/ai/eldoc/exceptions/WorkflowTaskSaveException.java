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

import ai.eldoc.model.WorkflowTask;
import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * WorkflowTask Save Exception
 */
@ApplicationException
public class WorkflowTaskSaveException extends DatabaseException {

    @Serial
    private static final long serialVersionUID = -392876380019589643L;

    private WorkflowTask _wfTask;


    public WorkflowTaskSaveException() {
        super("WorkflowTask can not be saved in the DB");
    }


    public WorkflowTaskSaveException(String message) {
        super(message);
    }


    public WorkflowTaskSaveException(Throwable cause) {
        super(cause);
    }


    public WorkflowTaskSaveException(String message, Throwable cause) {
        super(message, cause);
    }


    public WorkflowTask getEntity() {
        return _wfTask;
    }


    public WorkflowTaskSaveException setEntity(WorkflowTask docResponse) {
        _wfTask = docResponse;
        return this;
    }

}
