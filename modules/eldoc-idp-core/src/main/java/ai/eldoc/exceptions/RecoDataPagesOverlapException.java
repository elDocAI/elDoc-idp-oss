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
import ai.eldoc.model.reco.RecoData;
import jakarta.ejb.ApplicationException;
import java.io.Serial;


@ApplicationException
public class RecoDataPagesOverlapException extends ElDocRuntimeException {

    @Serial
    private static final long serialVersionUID = -1138403933576821750L;

    private Workflow _workflow;
    private RecoData _recoData1;
    private RecoData _recoData2;


    public RecoDataPagesOverlapException() {
        super("RecoData pages range overlaps with another RecoData");
    }


    public RecoDataPagesOverlapException(String message) {
        super(message);
    }


    public RecoDataPagesOverlapException(Throwable cause) {
        super(cause);
    }


    public RecoDataPagesOverlapException(String message, Throwable cause) {
        super(message, cause);
    }


    public Workflow getWorkflow() {
        return _workflow;
    }


    public RecoDataPagesOverlapException setWorkflow(Workflow workflow) {
        _workflow = workflow;
        return this;
    }


    public RecoData getRecoData1() {
        return _recoData1;
    }


    public RecoDataPagesOverlapException setRecoData1(RecoData recoData) {
        _recoData1 = recoData;
        return this;
    }


    public RecoData getRecoData2() {
        return _recoData2;
    }


    public RecoDataPagesOverlapException setRecoData2(RecoData recoData) {
        _recoData2 = recoData;
        return this;
    }
}
