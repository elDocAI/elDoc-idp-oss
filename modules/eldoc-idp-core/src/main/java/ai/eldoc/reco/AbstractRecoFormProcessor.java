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

import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.model.DbFile;
import ai.eldoc.model.Workflow;
import ai.eldoc.model.reco.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import static java.text.MessageFormat.format;


/**
 * Abstract RecoForm Processor which stores general RecoForm matching/processing logic, shared between all RecoForm processors
 */
public abstract class AbstractRecoFormProcessor implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRecoFormProcessor.class);

    private final Workflow _recoWF;
    private RecoFileMeta _recoFileMeta;

    private final RecoForm _recoForm;

    private RecognitionMetaDoc _recoMetaDoc;
    private RecognitionMetaFragment _recoMetaFragment;
    private RecoDebugger _recoDebugger;

    private final Map<Integer, RecoDocPageProcessor> _pageProcessors;
    private final List<RecoField> _extendedFieldsToRemove;

    private RecoFormResult _recoFormResultImported; // Holds raw result from external processor
    private RecoFormResult _recoFormResult;

    private final AiRequestContext _billingRequestContext;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public AbstractRecoFormProcessor(Workflow workflow, RecognitionMetaDoc recoMetaDoc, RecognitionMetaFragment recoMetaFragment, DbFile recoFile, RecoForm recoForm, AiRequestContext billingRequestContext) {
        _pageProcessors = new HashMap<>();
        _extendedFieldsToRemove = new ArrayList<>();

        _recoWF = workflow;
        _recoForm = recoForm;
        _recoMetaDoc = recoMetaDoc;
        _recoMetaFragment = recoMetaFragment;
        _recoFileMeta = RecoFileMeta.fromJson(recoFile.getMetadataAsJson());

        _billingRequestContext = billingRequestContext;
    }


    /**
     * Tells this RecoFormProcessor to use provided RecoDebugger for storing debugging information
     * for matching process
     *
     * @param recoDebugger {@link RecoDebugger} to be used
     * @return this RecoFormProcessor
     */
    public AbstractRecoFormProcessor withRecoDebugger(RecoDebugger recoDebugger) {
        _recoDebugger = recoDebugger;
        return this;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Performs main matching logic
     */
    public final void processWithTemplate() {
        initRecoFormProcessor();

        getRecoDebugger().setPageIdx(getRecoMetaDoc().getCurrentPageIdx());

        processWithTemplateInner();

        if (getRecoFormResult() != null) {
            // Update and log RecoForm confidence level
            getRecoFormResult().setRecoFormConfidence(RecoFormResultUtils.calculateMatchConfidence(getRecoFormResult()));
            getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FORM, format("RecoForm confidence level is: {0}", getRecoFormResult().getRecoFormConfidence()));

            trimRecoFormResult();
        }
    }


    abstract void processWithTemplateInner();


    /**
     * Removes empty RecoPageResults in the end of the RecoFormResult
     */
    void trimRecoFormResult() {
        for (int i = getRecoFormResult().getPages().size() - 1; i >= 0; i--) {
            if (getRecoFormResult().getPages().get(i).getFields().isEmpty()) {
                getRecoFormResult().getPages().remove(i);
            } else {
                break;
            }
        }
    }


    @Override
    public void close() throws Exception {
        boolean wasInterrupted = Thread.interrupted(); // Reset interrupted state. Interrupted flag may cause undesirable side-effects during resources clean-up, like closing inputStreams, etc.
        if (wasInterrupted) {
            // Restore interrupted stated, if needed
            Thread.currentThread().interrupt();
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     *
     * @return List of RecoPageProcessors, already created/used during matching process
     */
    public Map<Integer, RecoDocPageProcessor> getPageProcessors() {
        return _pageProcessors;
    }


    /**
     * Initializes RecoFormProcessor, if needed
     */
    void initRecoFormProcessor() {
    }


    //
    // Document data access methods
    //


    /**
     *
     * @return {@link Workflow} currently being processed
     */
    public final Workflow getRecoWF() {
        return _recoWF;
    }


    /**
     *
     * @return Metadata of the document being processed
     */
    public final RecoFileMeta getRecoFileMeta() {
        return _recoFileMeta;
    }


    //
    // RecoForm data access methods
    //


    /**
     *
     * @return Current RecoForm being matched
     */
    public final RecoForm getRecoForm() {
        return _recoForm;
    }


    /**
     *
     * @return Current RecoForm page
     */
    public abstract RecoPage getRecoPage();


    //
    // Matching process methods
    //


    /**
     *
     * @return List of the fields remaining to be matched on current step
     */
    public abstract List<RecoField> getFieldsToBeMatched();


    protected void processRecoFieldResult(RecoField recoField, RecoFieldResult recoFieldResult, RecoPageResult recoPageResult, Iterator<RecoField> fieldIterator, RecognitionMetaPage recoMetaPage) {
            processRecoFieldResultImported(recoField, recoFieldResult, recoPageResult, fieldIterator, recoMetaPage);
    }


    private void processRecoFieldResultImported(RecoField recoField, RecoFieldResult recoFieldResult, RecoPageResult recoPageResult, Iterator<RecoField> fieldIterator, RecognitionMetaPage recoMetaPage) {
        if (recoFieldResult == null) {
            // In case field was not found on the page - check whether it is a floating field. If we reached last page - field should be removed anyway
            if (getRecoMetaFragment().isLastPage(recoMetaPage)) {
                // As this field is not floating but it was not found yet - mark it as not found
                RecoFieldResult fieldResult = RecoFieldResult.createNotFound(getRecoForm(), recoField);

                RecoPageResult pageResultForField = getLastPageResultWithFields();
                if (pageResultForField == null) {
                    pageResultForField = recoPageResult;
                }

                pageResultForField.getFields().add(fieldResult);
                fieldIterator.remove(); // remove found field from the search fields list

                getRecoDebugger().log(RecoDebugger.ENTRY_TYPE_FIELD, "Field was not found and is not a floating field. Removing from further search");
            }

        } else {
            recoPageResult.getFields().add(recoFieldResult);

            // If table has it's anchor repeated and this is not last page - we don't need to remove it from the list of fields to be located
            // It is already remembered as _multiPageTableBeingChecked, so if we locate next field - it will be automatically removed
            if (recoFieldResult.isTypeTable() && !getRecoMetaFragment().isLastPage(recoMetaPage) && (recoField.getTableProp().isHeaderRepeated() || recoFieldResult.getMatchConfidenceBottomAnchor() == 0)) {
                return;
            }

            fieldIterator.remove(); // remove found field from the to be searched fields list
        }
    }


    //
    // Matching result data access methods
    //


    /**
     *
     * @return {@link RecognitionMetaDoc} containing metadata about currently processed RecoWorkflow (including matching-related settings)
     */
    public final RecognitionMetaDoc getRecoMetaDoc() {
        return _recoMetaDoc;
    }


    public RecognitionMetaFragment getRecoMetaFragment() {
        return _recoMetaFragment;
    }


    /**
     *
     * @return {@link RecoDebugger} to be used for storing debugging information during matching process
     */
    public final RecoDebugger getRecoDebugger() {
        if (_recoDebugger == null) {
            // In case no RecoDebugger was provided - create new instance
            _recoDebugger = new RecoDebugger();
            _recoDebugger.setDebugLogEnabled(false);
        }
        return _recoDebugger;
    }


    /**
     *
     * @return {@link RecoFormResult} with current matching results
     */
    public final RecoFormResult getRecoFormResult() {
        return _recoFormResult;
    }


    /**
     * Returns last RecoPageResult in current RecoFormResult, that has at least one RecoFieldResult defined.<br>
     * As during matching (for example, for floating fields), we can match it till the end of the document, fail to find the field
     * and switch to next one, starting from one of the previous pages, there can be situations, when we can have 'empty', but
     * initialized RecoPageResults in the end of RecoFormResult.
     *
     * @return
     */
    public final RecoPageResult getLastPageResultWithFields() {
        List<RecoPageResult> notEmptyPages = getRecoFormResult().getPages().stream()
                .filter(recoPageResult -> !recoPageResult.getFields().isEmpty())
                .toList();
        if (notEmptyPages.isEmpty()) {
            if (getRecoFormResult().getPages().isEmpty()) {
                return null;
            }
            return getRecoFormResult().getPages().get(0);
        } else {
            return notEmptyPages.get(notEmptyPages.size() - 1);
        }
    }


    /**
     *
     * @param idx RecoWorkflow page index
     * @return {@link RecoPageResult} for requested RecoWorkflow page or null, if no RecoPageResult was initialized for requested page
     */
    public RecoPageResult getRecoPageResultForPageIdx(int idx) {
        return getRecoFormResult().getPages().stream().filter(recoPageResult -> recoPageResult.getPageIdx() == idx).findFirst().orElse(null);
    }


    /**
     * Processes list of fields by matching them with document pages one by one
     *
     * @param docPageIdx First page of the fragment to be processed
     * @param docPageIdxEnd Last page of the document to be processed
     * @param fieldsToBeMatched List of the fields to be matched
     * @return
     */
    protected int matchFields(int docPageIdx, int docPageIdxEnd, List<RecoField> fieldsToBeMatched) {
        // Start processing document pages one by one
        for (int pageIdx = getRecoMetaDoc().getCurrentPageIdx() + docPageIdx; pageIdx < docPageIdxEnd; pageIdx++) {
            if (Thread.interrupted()) {
                LOG.debug("RecoWorkflow[{}] | RecoForm[{}]({}) matching was interrupted - exiting", getRecoWF().getId(), getRecoForm().getId(), getRecoForm().getName());
                Thread.currentThread().interrupt();
                return -1;
            }
            getRecoDebugger().setPageIdx(pageIdx + getRecoFileMeta().getPageIdxOffset());

            // Create page matcher and add it to the list in case we will need to get information on previously matched pages
            RecoDocPageProcessor pageProcessor = getRecoDocPageProcessor(pageIdx);
            pageProcessor.process(getFieldsToBeMatched());
            getFieldsToBeMatched().removeAll(getExtendedFieldsToRemove());

            if (fieldsToBeMatched.isEmpty()) {
                // If all fields for current RecoForm page was already matched - stop matching current RecoForm page
                return getLastPageResultWithFields().getPageIdx() - getRecoMetaDoc().getCurrentPageIdx() - getRecoFileMeta().getPageIdxOffset();
            } else if (!fieldsToBeMatched.isEmpty() && pageIdx == docPageIdxEnd - 1) {
                // If this is last page and there are floating fields left and last processed field is also floating -
                // switch to the last page with matched field, if any
                // IMPORTANT: last processed field is already removed from queue
                RecoPageResult lastPageWithFields = getLastPageResultWithFields();
                if (lastPageWithFields.getFields().isEmpty()) {
                    continue;
                }
                RecoFieldResult lastProcessedResult = lastPageWithFields.getFields().get(lastPageWithFields.getFields().size() - 1);
                RecoField lastProcessedField = getRecoFormResult().getRecoFieldByResult(lastProcessedResult);
                if (getRecoForm().isFloatingFieldCheckAndGetExtendableField(lastProcessedField) != null) {
                    // Increment number of processed document pages to mark, that current page was already processed
                    pageIdx = getRecoMetaDoc().getCurrentPageIdx() + getRecoFormResult().getPages().indexOf(lastPageWithFields);
                    docPageIdx = pageIdx - getRecoMetaDoc().getCurrentPageIdx() - 1;
                }
            }
        }
        return docPageIdx;
    }


    public List<RecoField> getExtendedFieldsToRemove() {
        return _extendedFieldsToRemove;
    }


    protected RecoFormResult getRecoFormResultImported() {
        return _recoFormResultImported;
    }


    protected void setRecoFormResultImported(RecoFormResult recoFormResultImported) {
        _recoFormResultImported = recoFormResultImported;
    }


    protected RecoDocPageProcessor getRecoDocPageProcessor(int pageIdx) {
        if (getPageProcessors().containsKey(pageIdx)) {
            return getPageProcessors().get(pageIdx);
        }
        RecoDocPageProcessor pageProcessor = new RecoDocPageProcessor(this, getRecoMetaDoc().getRecoMetaPageList().get(pageIdx));
        getPageProcessors().put(pageIdx, pageProcessor);
        return pageProcessor;
    }


    //
    // Helper methods -----------------------------------------------------------------------------
    //


    /**
     * Initializes RecoFormResult for current RecoForm processor<br/>
     * This also stores custom preprocessed file reference in the RecoFormResult, if CustomPreprocessing was already performed
     */
    protected void initRecoFormResult() {
        _recoFormResult = new RecoFormResult();
        _recoFormResult.setRecoForm(getRecoForm());
        getRecoDebugger().getRecoResultList().add(_recoFormResult);
    }


    protected AiRequestContext getBillingRequestContext() {
        return _billingRequestContext;
    }
}
