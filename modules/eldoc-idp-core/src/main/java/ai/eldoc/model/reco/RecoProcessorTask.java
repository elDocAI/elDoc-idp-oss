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
package ai.eldoc.model.reco;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.TenantContext;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.model.DbFile;
import ai.eldoc.model.Workflow;
import ai.eldoc.reco.AbstractRecoFormProcessor;
import ai.eldoc.reco.RecoFormAiProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.Callable;
import static java.text.MessageFormat.format;


/**
 * Represents a single matching step task as separate Runnable instance
 */
public class RecoProcessorTask implements Callable<RecoFormResult>, Serializable {

    @Serial
    private static final long serialVersionUID = 5541047360268018206L;
    private static final Logger LOG = LoggerFactory.getLogger(RecoProcessorTask.class);

    private final String _tenantId;
    private final Workflow _recoDoc;
    private final DbFile _recoFile;
    private final RecoForm _recoForm;
    private final RecognitionMetaDoc _recoMetaDoc;
    private final RecognitionMetaFragment _recoMetaFragment;

    private final AiRequestContext _billingRequestContext;

    private AbstractRecoFormProcessor _recoFormProcessor = null;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    /**
     * Creates new matching task
     *
     * @param tenantId ID of the tenant, which requests matching
     * @param recoWF {@link Workflow}, whose attachment is being matched
     * @param recoMetaDoc {@link RecognitionMetaDoc} with additional data about document/matching process
     * @param recoMetaFragment {@link RecognitionMetaFragment} fragment of the document to be processed
     * @param recoFile {@link DbFile} that contains reference to the document to be matched
     * @param recoForm {@link RecoForm}, that file must be matched against
     */
    public RecoProcessorTask(String tenantId, Workflow recoWF, RecognitionMetaDoc recoMetaDoc, RecognitionMetaFragment recoMetaFragment, DbFile recoFile, RecoForm recoForm, AiRequestContext billingRequestContext) {
        _tenantId = tenantId;
        _recoDoc = recoWF;
        _recoMetaDoc = recoMetaDoc;
        _recoMetaFragment = recoMetaFragment;
        _recoFile = recoFile;
        _recoForm = recoForm;
        _billingRequestContext = billingRequestContext;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public RecoFormResult call() throws Exception {
        TenantContext.setCurrentTenant(_tenantId);
        try {
            _recoFormProcessor = switch (_recoForm.getType()) {
                case RecoForm.TYPE_AIBASED ->
                    new RecoFormAiProcessor(_recoDoc, _recoMetaDoc, _recoMetaFragment, _recoFile, _recoForm, _billingRequestContext);
                default -> {
                    String msg = format("RecoForm[{0}][{1}] type [{2}] is unknown ", _recoForm.getId(), _recoForm.getName(), _recoForm.getType());
                    LOG.warn(msg);
                    throw new ElDocRuntimeException(msg);
                }
            };
            _recoFormProcessor
                    .withRecoDebugger(_recoMetaDoc.getRecoDebugger().forForm(_recoForm.getId()))
                    .processWithTemplate();

            return _recoFormProcessor.getRecoFormResult();
        } finally {
            if (_recoFormProcessor != null) {
                _recoFormProcessor.close();
            }
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public RecoFormResult getRecoFormResult() {
        if (_recoFormProcessor == null) {
            return null;
        }
        return _recoFormProcessor.getRecoFormResult();
    }


    //
    // Standard overrides -------------------------------------------------------------------------
    //
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(_tenantId);
        hash = 83 * hash + Objects.hashCode(_recoDoc);
        hash = 83 * hash + Objects.hashCode(_recoFile);
        hash = 83 * hash + Objects.hashCode(_recoForm);
        hash = 83 * hash + Objects.hashCode(_recoMetaDoc);
        hash = 83 * hash + Objects.hashCode(_recoMetaFragment);
        hash = 83 * hash + Objects.hashCode(_recoFormProcessor);
        hash = 83 * hash + Objects.hashCode(_billingRequestContext);
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
        final RecoProcessorTask other = (RecoProcessorTask) obj;
        if (!Objects.equals(_tenantId, other._tenantId)) {
            return false;
        }
        if (!Objects.equals(_recoDoc, other._recoDoc)) {
            return false;
        }
        if (!Objects.equals(_recoFile, other._recoFile)) {
            return false;
        }
        if (!Objects.equals(_recoForm, other._recoForm)) {
            return false;
        }
        if (!Objects.equals(_recoMetaDoc, other._recoMetaDoc)) {
            return false;
        }
        if (!Objects.equals(_recoMetaFragment, other._recoMetaFragment)) {
            return false;
        }
        if (!Objects.equals(_billingRequestContext, other._billingRequestContext)) {
            return false;
        }
        if (!Objects.equals(_recoFormProcessor, other._recoFormProcessor)) {
            return false;
        }
        return true;
    }

}
