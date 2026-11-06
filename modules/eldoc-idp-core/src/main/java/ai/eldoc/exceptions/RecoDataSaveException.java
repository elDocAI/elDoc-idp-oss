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
import ai.eldoc.model.reco.RecoData;
import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * RecoData save exception
 */
@ApplicationException
public class RecoDataSaveException extends DatabaseException {

    @Serial
    private static final long serialVersionUID = -2451557641400218055L;

    private Workflow _workflow;
    private RecoData _recoData;


    public RecoDataSaveException() {
        super("RecoData can not be saved in the DB");
    }


    public RecoDataSaveException(String message) {
        super(message);
    }


    public RecoDataSaveException(Throwable cause) {
        super(cause);
    }


    public RecoDataSaveException(String message, Throwable cause) {
        super(message, cause);
    }


    public Workflow getWorkflow() {
        return _workflow;
    }


    public RecoDataSaveException setWorkflow(Workflow workflow) {
        _workflow = workflow;
        return this;
    }


    public RecoData getRecoData() {
        return _recoData;
    }


    public RecoDataSaveException setRecoData(RecoData recoData) {
        _recoData = recoData;
        return this;
    }

}
