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
package ai.eldoc.reco;

import ai.eldoc.model.reco.*;
import ai.eldoc.util.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Abstract RecoField Processor which stores general RecoField matching/processing logic, shared between all RecoField processors
 */
public abstract class AbstractRecoFieldProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRecoFieldProcessor.class);

    public static final String DEFAULT_IMAGE_FIELD_FORMAT = "png";
    public static final String DEFAULT_IMAGE_FIELD_CONTENT_TYPE = MimeType.IMAGE_PNG;

    private final RecoField _recoField;
    private final RecoDocPageProcessor _recoWfPage;
    private final AbstractRecoFormProcessor _recoFormProcessor;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    protected AbstractRecoFieldProcessor(RecoDocPageProcessor recoWfPage, RecoField recoField, AbstractRecoFormProcessor recoFormProcessor) {
        _recoField = recoField;
        _recoWfPage = recoWfPage;
        _recoFormProcessor = recoFormProcessor;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public abstract RecoFieldResult process();


    /**
     * Processes specific RecoFieldResult, extracting data from it's value region
     * @param rfr
     */
    protected final void processFieldResult(RecoFieldResult rfr) {
        try {
            if (getRecoField().isTypeTable()) {
                processTableFieldResult(rfr);
            } else if (getRecoField().isTypeOmr()) {
                processOmrFieldResult(rfr);
            } else if (getRecoField().isTypeImage()) {
                processImageFieldResult(rfr);
            } else {
                processTextFieldResult(rfr);
            }
        } catch (Throwable ex) {
            LOG.error("", ex);
            getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FIELD, "Error extracting value for field");
        }
    }


    /**
     * Extracts value for currently processed field, if it is TABLE-type field. <br>
     * If table row is empty (all cells are empty), it is not included in the result.
     *
     * @param rfr
     */
    protected abstract void processTableFieldResult(RecoFieldResult rfr);


    /**
     * Extracts value for currently processed field, if it is OMR-type field, following next steps: <br>
     * 1. Locate each mark again in extended area to ensure we process optical mark region in case of scale/offset calculation issues;<br>
     * 2. Extract optical mark value (checked or not and it's confidence level);<br>
     * 3. If optical mark confidence level is lower, than {@code MIN_FIELD_CONFIDENCE_DBL} - treat it as unchecked.
     *
     * @param rfr
     */
    protected abstract void processOmrFieldResult(RecoFieldResult rfr);


    /**
     * Extracts value for currently processed field, if it is TEXT-type field
     *
     * @param rfr
     */
    protected abstract void processTextFieldResult(RecoFieldResult rfr);


    /**
     * Extracts value for the currently processed field, if it is IMAGE-type field
     *
     * @param rfr
     */
    protected abstract void processImageFieldResult(RecoFieldResult rfr);


    /**
     * Initializes RecoFieldResult, if it was not initialized previously. It is required to call this method before starting to work with
     * RecoFieldResult and after checks, that RecoField can be processed, as in general cases there can be cases, when specific processor
     * can't process RecoField - in this case, null should be returned by getRecoFieldResult()
     *
     * @return
     */
    protected RecoFieldResult initRecoFieldResult() {
        return RecoFieldResult.createDefault(getRecoForm(), getRecoField());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public final RecoDocPageProcessor getRecoWfPage() {
        return _recoWfPage;
    }


    public final AbstractRecoFormProcessor getRecoFormProcessor() {
        return _recoFormProcessor;
    }


    public final RecoField getRecoField() {
        return _recoField;
    }


    final RecoDebugger getRecoDebugger() {
        return getRecoFormProcessor().getRecoDebugger();
    }


    final RecoForm getRecoForm() {
        return getRecoFormProcessor().getRecoForm();
    }

}
