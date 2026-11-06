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
import ai.eldoc.model.reco.RecognitionMetaDoc;
import jakarta.ejb.ApplicationException;
import java.io.Serial;


/**
 * RecoResult is not available exception
 */
@ApplicationException
public class NoRecoResultAvailableException extends ElDocRuntimeException {

    @Serial
    private static final long serialVersionUID = 1343975202061764063L;

    private Workflow _workflow;
    private RecognitionMetaDoc _recoMetaDoc;


    public NoRecoResultAvailableException() {
        super("No RecoResult available for the RecoWorkflow (no RecoForm was matched)");
    }


    public NoRecoResultAvailableException(String message) {
        super(message);
    }


    public NoRecoResultAvailableException(Throwable cause) {
        super(cause);
    }


    public NoRecoResultAvailableException(String message, Throwable cause) {
        super(message, cause);
    }


    public Workflow getWorkflow() {
        return _workflow;
    }


    public NoRecoResultAvailableException setWorkflow(Workflow workflow) {
        _workflow = workflow;
        return this;
    }


    public RecognitionMetaDoc getRecoMetaDoc() {
        return _recoMetaDoc;
    }


    public NoRecoResultAvailableException setRecoMetaDoc(RecognitionMetaDoc recoMetaDoc) {
        _recoMetaDoc = recoMetaDoc;
        return this;
    }

}
