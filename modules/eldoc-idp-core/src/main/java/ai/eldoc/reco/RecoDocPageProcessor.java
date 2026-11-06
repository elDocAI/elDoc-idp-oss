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
import ai.eldoc.ocr.OcrText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Iterator;
import java.util.List;


/**
 * Abstract RecoPage Processor which stores general RecoPage matching/processing logic, shared between all RecoPage processors
 */
public class RecoDocPageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RecoDocPageProcessor.class);

    private final AbstractRecoFormProcessor _parent;
    private final RecognitionMetaPage _recoMetaPage;

    private OcrText _pageText = null;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoDocPageProcessor(AbstractRecoFormProcessor parent, RecognitionMetaPage recoMetaPage) {
        _parent = parent;
        _recoMetaPage = recoMetaPage;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * If there is already RecoPageResult for current page - use it during matching process. Otherwise - create new RecoPageResult and add it to current RecoFormResult
     */
    RecoPageResult initRecoPageResult() {
        RecoPageResult pageResult = getParent().getRecoFormResult().getPages().stream().filter(page -> page.getPageIdx() == getSourceFilePageIdx()).findFirst().orElse(null);
        if (pageResult == null) {
            // There is no RecoPageResult for current page - create new
            pageResult = new RecoPageResult(getSourceFilePageIdx());
            logPageProcessingStart();
        }
        return pageResult;
    }


    /**
     * Matches Document Page against RecoPage
     *
     * @param fieldsToBeMatched List of {@link RecoField}, that should be matched against this page
     * @return
     */
    public RecoPageResult process(List<RecoField> fieldsToBeMatched) {
        RecoPageResult recoPageResult = initRecoPageResult();
        if (!getParent().getRecoFormResult().getPages().contains(recoPageResult)) {
            // RecoFormResult may already contain PageResult for document page being processed - in this case it is re-used and no need to add it to RecoFormResult again
            getParent().getRecoFormResult().getPages().add(recoPageResult);
        }

        logPageProcessingStart();

        // Iterate over all defined on the template fields which has to be extracted
        Iterator<RecoField> fieldIterator = fieldsToBeMatched.iterator();

        while (fieldIterator.hasNext()) {
            if (Thread.interrupted()) {
                LOG.trace("RecoForm[{}] Page[{}] matching was interrupted - exiting", getParent().getRecoForm().getId(), getSourceFilePageIdx());
                Thread.currentThread().interrupt();
                // Return existing matching results, as ignoring them may result in leaks - for example, if IMAGE-type field was already matched before interruption,it should be properly cleaned up
                return recoPageResult;
            }
            RecoField recoField = fieldIterator.next();

            getRecoDebugger().setRecoField(recoField);

            AbstractRecoFieldProcessor fieldProcessor = createFieldProcessorForField(recoField);
            RecoFieldResult recoFieldResult = fieldProcessor.process();
            processRecoFieldResult(recoField, recoFieldResult, recoPageResult, fieldIterator);

            getRecoDebugger().setRecoField(null);
        }

        return recoPageResult;
    }


    /**
     * Process obtained RecoFieldResult, depending on context (confidence level, page, whether the field is floating, etc)
     *
     * @param recoField {@link RecoField} being processed
     * @param recoFieldResult {@link RecoFieldResult} Result of the matching process
     * @param recoPageResult {@link RecoPageResult} for the currently processed page
     * @param fieldIterator Iterator for the list of remaining fields
     */
    private void processRecoFieldResult(RecoField recoField, RecoFieldResult recoFieldResult, RecoPageResult recoPageResult, Iterator<RecoField> fieldIterator) {
        getParent().processRecoFieldResult(recoField, recoFieldResult, recoPageResult, fieldIterator, _recoMetaPage);
    }


    /**
     * Performs logging, that new document page is started processing
     */
    private void logPageProcessingStart() {
        if (getParent().getRecoForm().isTypeAuto()) {
            // Log extracted form fields
            // Do not repeat debug logging, if the page was already processed - for example, due to extended/floating fields
            getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_PAGE, "Total number of located form fields on page:", String.valueOf(getParent().getRecoFormResultImported().getFields().stream().filter(rfr -> !rfr.isTypeTable()).count()));
            String formFieldsList = getParent().getRecoFormResultImported().getFields().stream()
                    .filter(rfr -> !rfr.isTypeTable())
                    .map(formField -> "\"" + formField.getName() + "\"")
                    .collect(StringBuilder::new, (builder, c) -> builder.append(c).append(", "), StringBuilder::append)
                    .toString();
            getParent().getRecoFormResultImported().getFields().stream()
                    .filter(rfr -> !rfr.isTypeTable())
                    .forEach(formField -> {
                        getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FIELD, null, formField.getRegion(), RecoDebugger.RECT_TYPE.VALUE_SRCH_RECT);
                        getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FIELD, null, formField.getAnchorRegion(), RecoDebugger.RECT_TYPE.ANCHOR_SRCH_RECT);
                    });
            getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_PAGE, "List of located form fields on page:", formFieldsList);
            getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_PAGE, "Total number of located tables on page:", String.valueOf(getParent().getRecoFormResultImported().getFields().stream().filter(RecoFieldResult::isTypeTable).count()));
            getParent().getRecoFormResultImported().getFields().stream()
                    .filter(rfr -> rfr.isTypeTable())
                    .forEach(tableField -> getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FIELD, null, tableField.getRegion(), RecoDebugger.RECT_TYPE.VALUE_SRCH_RECT));
        }
    }


    /**
     * Creates RecoFieldProcessor for requested RecoField
     *
     * @param recoField
     * @return
     */
    protected final AbstractRecoFieldProcessor createFieldProcessorForField(RecoField recoField) {
        return new RecoFieldAiProcessor(this, recoField, getParent());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     *
     * @return RecoFormProcessor, that was used to create this RecoPage Processor
     */
    public final AbstractRecoFormProcessor getParent() {
        return _parent;
    }


    /**
     *
     * @return {@link RecoDebugger} to be used for storing debugging information during matching process
     */
    final RecoDebugger getRecoDebugger() {
        return getParent().getRecoDebugger();
    }


    /**
     *
     * @return Index of current page in source file (including page offset, that can appear due to custom preprocessing)
     */
    private int getSourceFilePageIdx() {
        return getRecoMetaPage().getPageIdx() + getParent().getRecoFileMeta().getPageIdxOffset();
    }


    /**
     *
     * @return {@link RecognitionMetaPage} containing metadata about currently processed document's page (including matching-related settings)
     */
    final RecognitionMetaPage getRecoMetaPage() {
        return _recoMetaPage;
    }

}
