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
package ai.eldoc.ui.view;

import ai.eldoc.TenantContext;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.comparator.ItemSI;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.RecoFormDao;
import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.exceptions.NoRecoResultAvailableException;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.*;
import ai.eldoc.model.reco.Rectangle;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.reco.ImageProcessor;
import ai.eldoc.reco.RecoDebugger;
import ai.eldoc.reco.RecoDebugger.RecoLogEntry;
import ai.eldoc.security.SecurityUtils;
import ai.eldoc.service.RecognitionService;
import ai.eldoc.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.faces.annotation.RequestMap;
import jakarta.faces.event.PhaseId;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import org.apache.commons.lang3.Range;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.event.data.PageEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Backing bean for RecoForm pages
 */
@Named
@ViewScoped
public class RecoDiagController extends AbstractUIController implements Serializable {

    @Serial
    private static final long serialVersionUID = -2009220046861896884L;
    private static final Logger LOG = LoggerFactory.getLogger(RecoDiagController.class);

    private static final int PREVIEW_PAGE = RecoDebugger.ENTRY_TYPE_PAGE;
    private static final int PREVIEW_FIELD = RecoDebugger.ENTRY_TYPE_FIELD;

    @Inject
    private RecognitionService _recoService;

    @Inject
    private RecoFormDao _recoFormDao;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    @RequestMap
    private Map<String, Object> _requestMap;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private UserContext _userContext;

    @Inject
    private RecoQueueController _recoQueueBean;

    private Workflow _dataSource;
    private RecoFileMeta _recoFileMeta;
    private RecognitionMetaDoc _recoMetaDoc;
    private final List<Integer> _pageIndexList;
    private final List<Range<Integer>> _documentsList;
    private int _pageIdx = 0; // we always start from page with index 0
    private transient DefaultStreamedContent _currentPageImage; // transient - will help to tell container to not serialize this property to Hazelcast
    private Map<String, Map<Integer, String>> _pageDims;

    private int _logIdx;
    private List<String> _selectedDiagForms;
    private List<String> _selectedForms;
    private List<RecoField> _selectedFields;
    private List<Integer> _selectedLogTypes;
    private List<Integer> _selectedPreviewTypes;
    private boolean _embedPreprocessedImages;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public RecoDiagController() {
        _pageIndexList = new ArrayList<>();
        _documentsList = new ArrayList<>();
        _selectedFields = new ArrayList<>();
        _selectedLogTypes = new ArrayList<>();
        _selectedPreviewTypes = new ArrayList<>();
        _selectedDiagForms = new ArrayList<>();
        _pageDims = new HashMap<>();

        // Global service log should be disabled by default
        _selectedLogTypes.addAll(getLogTypeList());

        getPreviewTypeList().stream().filter(type -> type != PREVIEW_PAGE).forEach(_selectedPreviewTypes::add);

        _selectedForms = new ArrayList<>();
    }


    @PostConstruct
    void init() {
        _recoFormDao.findAll().stream()
                .filter(RecoForm::isStatusActive)
                .map(RecoForm::getId)
                .forEach(_selectedForms::add);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void handleFileUpload(FileUploadEvent event) throws Exception {
        try {
            Workflow recoWF = _recoQueueBean.handleFileUpload(event, true, _embedPreprocessedImages);
            _dataSource = _workflowDao.find(recoWF.getId(), SecurityUtils.getSubjectAclContext());
            DbFile dbFile = _workflowDao.getFilesDao().getFile(null, _dataSource.getFileByTag(Workflow.FILE_TAG.IDP_PREPROCESSED.name()));
            _recoFileMeta = RecoFileMeta.fromJson(dbFile.getMetadataAsJson());
            JsfUtils.addInfoMessage("Preprocessing completed");
        } catch (Throwable e) {
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"), e.toString());
        }
    }


    public void performMatching() {
        try {
            try {
                AiRequestContext billingRequestContext = AiRequestContext.forSrcIDP(I18n.LANG_SYSTEM_CODE_EN, TenantContext.getCurrentTenantId(), SecurityUtils.getSubjectAclContext());
                AtomicBoolean requiresValidation = new AtomicBoolean();
                List<RecoForm> selectedForms = _selectedForms.stream()
                        .map(_recoFormDao::find)
                        .toList();
                DbFile fa = _workflowDao.getFilesDao().getFile(null, _dataSource.getFileByTag(Workflow.FILE_TAG.IDP_PREPROCESSED.name()));
                WorkflowTask wfTask = WorkflowTask.createFor(_dataSource, _userContext.getId());
                _recoMetaDoc = _recoService.recognizeAndProcessDiag(_dataSource, fa, wfTask, requiresValidation, selectedForms, billingRequestContext);
                _recoMetaDoc.getRecoResultList().forEach(recoFormResult -> {
                    RecoFileMeta customPreprocessedMeta = recoFormResult.getPreprocessedFileMeta();
                    if (customPreprocessedMeta == null) {
                        // No custom preprocessing was performed for fragment on current form - skipping
                        return;
                    }
                    int pageOffset = customPreprocessedMeta.getPageIdxOffset();
                    for (int pageIdx = 0; pageIdx < customPreprocessedMeta.getPagesDimensions().size(); pageIdx++) {
                        int preprocessedFilePageIdx = pageIdx + pageOffset;
                        Map<Integer, String> formPageDims = _pageDims.computeIfAbsent(recoFormResult.getRecoForm().getId(), k -> new HashMap<>());
                        formPageDims.put(preprocessedFilePageIdx, customPreprocessedMeta.getPagesDimensions().get(pageIdx));
                    }
                });
            } catch (NoRecoResultAvailableException ex) {
                _recoMetaDoc = ex.getRecoMetaDoc();
            }
            _logIdx = 0;
            updateRecoDebuggerInfo(true);
            if (!isAnyFormProcessed()) {
                JsfUtils.addWarnMessage("No Recognition Form was selected, please check diagnostics settings");
            }
            JsfUtils.addInfoMessage("Processing completed");
        } catch (Throwable e) {
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"), e.toString());
        }
    }


    /**
     * Updates a document fragments list, forms a list for the current document fragment and fields for the current form
     */
    private void updateRecoDebuggerInfo(boolean resetFilters) {
        // Update list of documents and pages for UI
        _documentsList.clear();
        _recoMetaDoc.getRecoResultList().stream()
                .map(recoResult -> Range.of(recoResult.getFirstPageIdx(), recoResult.getLastPageIdx()))
                .forEach(_documentsList::add);

        updatePageIndexList();
        for (int pageIdx = 0; pageIdx < _recoFileMeta.getPageCount(); pageIdx++) {
            _documentsList.add(Range.of(pageIdx, pageIdx));
        }

        if (resetFilters) {
            _selectedDiagForms.clear();
        }
        if (getMatchedRecoForm() != null && resetFilters) {
            _selectedDiagForms.add(getMatchedRecoForm().getId());
        } else if (resetFilters) {
            if (!getRecoFormsSI().isEmpty()) {
                _selectedDiagForms.add((String) getRecoFormsSI().getFirst().getValue());
            }
        }

        // Update list of fields for selected form
        if (!_selectedDiagForms.isEmpty()) {
            _selectedFields.addAll(getRecoFields());
        }
    }


    private void updatePageIndexList() {
        if (JsfUtils.getFacesContext().getCurrentPhaseId() != PhaseId.RENDER_RESPONSE) {
            // We perform this logic only during RENDER_RESPONSE
            return;
        }

        if (_requestMap.containsKey("PAGE_INDEX_UPDATE_DONE")) {
            // Nothing to do - already refreshed per request
            return;
        }

        _pageIndexList.clear();
        for (int pageIdx = 0; pageIdx < _recoFileMeta.getPageCount(); pageIdx++) {
            _pageIndexList.add(pageIdx);
        }

        setPageIdx(_documentsList.get(_logIdx).getMinimum());

        _requestMap.put("PAGE_INDEX_UPDATE_DONE", true);
    }


    /**
     * Returns requested page image as DefaultStreamedContent, drawing debugging information, that was requested on UI, on it
     *
     * @param pageIndex index of the page to be displayed
     * @return Page image as DefaultStreamedContent
     */
    private DefaultStreamedContent getPageImage(int pageIndex) {
        DbFileRef dbFileRef = _dataSource.getFileByTag( Workflow.FILE_TAG.IDP_PREPROCESSED.name());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();DbFileDownloadStream preprocessedFileIS = _workflowDao.getFilesDao().getFileDownloadStream(dbFileRef); PDDocument pdDocument = PdfUtils.loadPDF(preprocessedFileIS)) {
            // Draw all enabled fields rects on current page copy
            if (!_selectedDiagForms.isEmpty()) {
                for (RecoLogEntry logEntry : getLogEntryList()) {
                    if (logEntry.getRect() == null || logEntry.getPageIdx() != pageIndex) {
                        // If current log entry doesn't have Rectangle information, or it belongs to different page - skip it
                        continue;
                    }
                    if (!_selectedPreviewTypes.contains(logEntry.getEntryType())) {
                        // If current log entry type is not selected to be displayed on preview, skip it
                        continue;
                    }
                    if (!_selectedFields.contains(logEntry.getField()) && logEntry.getField() != null) {
                        // If current field is not selected to be displayed on preview, skip it
                        continue;
                    }

                    // Calculate scale for current field rectangles. This step is required, as RecoForm may perform custom preprocessing,
                    // and therefore page dimensions stored in RecoDebugger may differ from page dimensions of a PREPROCESSED file being
                    // displayed on the diag page (i.e., machine-generated file and form with IDP_IGNORE_TEXT_LAYER property set to true)
                    double scale = 1.0f;
                    Map<Integer, String> recoFormPageDimList = _pageDims.get(logEntry.getRecoForm());
                    if (recoFormPageDimList != null) {
                        // Extract dimensions of PREPROCESSED_FINAL file, generated by currently processed RecoForm
                        String recoFormPageDims = recoFormPageDimList.get(pageIndex);
                        Rectangle preprocessedFinalPageRect = ImageProcessor.getDimensionsFromString(recoFormPageDims);

                        // Extract dimensions of PREPROCESSED file, that is being displayed on UI
                        String recoDocPageDims = _recoFileMeta.getPagesDimensions().get(pageIndex);
                        Rectangle preprocessedPageRect = ImageProcessor.getDimensionsFromString(recoDocPageDims);

                        // Calculate rectangle's scale
                        scale = preprocessedFinalPageRect.getWidth() / preprocessedPageRect.getWidth();
                    }

                    Rectangle rect = logEntry.getRect().getScaledClone(1.0 / scale);

                    // In PDF, (0, 0) points are bottom-left point of the image, so point with Y=100 is ABOVE point with
                    // Y=50, so applying offset, it's Y component should be inverted
                    rect.setY(rect.getY() - pdDocument.getPage(pageIndex).getCropBox().getLowerLeftY());
                    rect.setX(rect.getX() + pdDocument.getPage(pageIndex).getCropBox().getLowerLeftX());

                    PdfUtils.addRect(pdDocument, pageIndex, rect, getRectColor(logEntry.getRectType()));
                }
            }

            BufferedImage currentPageBufImg = PdfUtils.renderPageToImage(pdDocument, pageIndex, PdfUtils.IMAGE_RENDER_WEB_DPI);

            ImageProcessor.saveToJpg(currentPageBufImg, baos, 0.9f);
            return DefaultStreamedContent.builder()
                    .contentType(MimeType.IMAGE_JPEG)
                    .contentLength((long) baos.size())
                    .stream(() -> new ByteArrayInputStream(baos.toByteArray())).build();
        } catch (Throwable ex) {
            LOG.error("Error rendering image for preview", ex);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"));
        }

        return null;
    }


    /**
     * Checks, if any form was processed during a matching step.
     * If for any reason (no form matched by keywords, exception during a matching step, etc.)
     * no recoForm participated in the matching step, recoDebugger will contain only a log for recognition service.
     *
     * @return {@code true}, if any form was processed, {@code false} otherwise
     */
    private boolean isAnyFormProcessed() {
        return !_recoMetaDoc.getRecoDebugger().getLogEntryList().stream()
                .distinct()
                .allMatch(logEntry -> "recognitionService".equals(logEntry.getRecoForm()));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public RecognitionMetaDoc getRecoMetaDoc() {
        return _recoMetaDoc;
    }


    public List<String> getSelectedDiagForms() {
        _currentPageImage = null;
        return _selectedDiagForms;
    }


    public void setSelectedDiagForms(List<String> selectedDiagForms) {
        _selectedDiagForms = selectedDiagForms;
        _selectedFields.clear();
        _selectedFields.addAll(getRecoFields());
        _currentPageImage = null;
    }


    public List<String> getSelectedForms() {
        return _selectedForms;
    }


    public void setSelectedForms(List<String> selectedForms) {
        _selectedForms = selectedForms;
    }


    public List<Integer> getPageIndexList() {
        updatePageIndexList();
        return _pageIndexList;
    }


    public void setPageIdxEvent(PageEvent pageEvent) {
        setPageIdx(pageEvent.getPage());
        _currentPageImage = null; // reset current page image so it will be refreshed
    }


    public void setPageIdx(int pageIdx) {
        _pageIdx = pageIdx;
    }


    public int getPageIdx() {
        return _pageIdx;
    }


    public int getLogIdx() {
        return _logIdx;
    }


    public void setLogIdx(int logIdx) {
        _logIdx = logIdx;
        _currentPageImage = null;
        boolean resetFilters = logIdx < _recoMetaDoc.getRecoResultList().size();
        updateRecoDebuggerInfo(resetFilters);
    }


    public RecoForm getMatchedRecoForm() {
        if (!_recoMetaDoc.getRecoResultList().isEmpty()) {
            return _recoMetaDoc.getRecoResultList().stream()
                    .filter(result -> result.getFirstPageIdx() >= _documentsList.get(_logIdx).getMinimum())
                    .sorted(Comparator.comparingDouble(RecoFormResult::getRecoFormConfidence))
                    .map(RecoFormResult::getRecoForm)
                    .findFirst().orElse(null);
        }
        return null;
    }


    public DefaultStreamedContent getPageImage() {
        if (_currentPageImage == null) {
            _currentPageImage = getPageImage(getPageIdx());
        }

        try {
            if (_currentPageImage != null) {
                _currentPageImage.getStream().get().reset();
            }
        } catch (IOException ex) {
            LOG.error("Error displaying page image", ex);
        }

        return _currentPageImage;
    }


    public List<Integer> getLogTypeList() {
        return Arrays.asList(
                RecoDebugger.ENTRY_TYPE_FORM,
                RecoDebugger.ENTRY_TYPE_PAGE
        );
    }


    public List<Integer> getPreviewTypeList() {
        return Arrays.asList(
                PREVIEW_FIELD,
                PREVIEW_PAGE
        );
    }


    public String getLogTypeLabel(int logType) {
        return switch (logType) {
            case RecoDebugger.ENTRY_TYPE_FORM -> "Form";
            case RecoDebugger.ENTRY_TYPE_PAGE -> "Page";
            default -> "(N/A)";
        };
    }


    public String getPreviewTypeLabel(int previewType) {
        return switch (previewType) {
            case PREVIEW_FIELD -> "Field matching";
            case PREVIEW_PAGE -> "Offset and scale matching";
            default -> "(N/A)";
        };
    }


    public List<RecoField> getSelectedFields() {
        _currentPageImage = null;
        return _selectedFields;
    }


    public void setSelectedFields(List<RecoField> selectedFields) {
        _selectedFields = selectedFields;
    }


    public List<Integer> getSelectedLogTypes() {
        return _selectedLogTypes;
    }


    public void setSelectedLogTypes(List<Integer> selectedLogTypes) {
        _selectedLogTypes = selectedLogTypes;
    }


    public List<Integer> getSelectedPreviewTypes() {
        _currentPageImage = null;
        return _selectedPreviewTypes;
    }


    public void setSelectedPreviewTypes(List<Integer> selectedPreviewTypes) {
        _selectedPreviewTypes = selectedPreviewTypes;
    }


    public Workflow getDataSource() {
        return _dataSource;
    }


    private Color getRectColor(RecoDebugger.RECT_TYPE rectType) {
        return switch (rectType) {
            case BLIND_SRCH_RECT -> new Color(255, 220, 140, 255);
            case BLIND_ANCHOR_RECT -> new Color(255, 180, 10, 255);
            case ANCHOR_RECT -> Color.BLUE;
            case ANCHOR_UNDTBL_RECT -> Color.CYAN;
            case CELL, VALUE_RECT -> Color.RED;
            case VALUE_SRCH_RECT -> Color.YELLOW;
            default -> Color.LIGHT_GRAY;
        };
    }


    public List<SelectItem> getRecoDebuggerListSI() {
        List<SelectItem> fragmentsList = new ArrayList<>();
        for (int i = 0; i < _documentsList.size(); i++) {
            Range<Integer> fragment = _documentsList.get(i);
            int pageStart = fragment.getMinimum();
            int pageEnd = fragment.getMaximum();
            String recoDebuggerName;
            if (i < _recoMetaDoc.getRecoResultList().size()) {
                recoDebuggerName = format("Document {0,number,#}: ", i + 1) + (pageStart == pageEnd
                        ? format("p{0,number,#}", pageStart + 1)
                        : format("p{0,number,#}-{1,number,#}", pageStart + 1, pageEnd + 1));
            } else {
                recoDebuggerName = format("Page {0,number,#}", i - _recoMetaDoc.getRecoResultList().size() + 1);
            }
            fragmentsList.add(new SelectItem(i, recoDebuggerName));
        }
        return fragmentsList;
    }


    public List<SelectItem> getRecoFormsSI() {
        List<SelectItem> recoFormsSI = new ArrayList<>();

        // Add SelectItem for matched RecoForm
        RecoForm matchedForm = getMatchedRecoForm();
        if (matchedForm != null) {
            recoFormsSI.add(new SelectItem(matchedForm.getId(), "[Best] ".concat(matchedForm.getName())));
        }

        // Add all forms, that was matched by keywords, to the list
        List<String> fragmentForms = _recoMetaDoc.getRecoDebugger().getLogEntryList().stream()
                .filter(logEntry -> logEntry.getPageIdx() >= _documentsList.get(_logIdx).getMinimum() && logEntry.getPageIdx() <= _documentsList.get(_logIdx).getMaximum())
                .map(RecoLogEntry::getRecoForm)
                .distinct()
                .filter(formId -> !formId.equals("recognitionService"))
                .filter(formId -> matchedForm == null || !formId.equals(matchedForm.getId()))
                .toList();

        fragmentForms.stream()
                .filter(formId -> _recoMetaDoc.getRecoDebugger().getRecoResultList(_documentsList.get(_logIdx).getMinimum(), _documentsList.get(_logIdx).getMaximum()).stream().anyMatch(recoResult -> recoResult.getRecoForm().getId().equals(formId)))
                .map(_recoFormDao::find)
                .map(recoForm -> new SelectItem(recoForm.getId(), recoForm.getName()))
                .sorted(new ItemSI())
                .forEach(recoFormsSI::add);

        // Add all forms, that was excluded from matching by keywords, to the list
        fragmentForms.stream()
                .filter(formId -> recoFormsSI.stream().map(si -> (String) si.getValue()).noneMatch(otherForm -> otherForm.equals(formId)))
                .map(_recoFormDao::find)
                .map(recoForm -> new SelectItem(recoForm.getId(), "[Excluded] ".concat(recoForm.getName())))
                .sorted(new ItemSI())
                .forEach(recoFormsSI::add);

        return recoFormsSI;
    }


    public List<RecoField> getRecoFields() {
        return getLogEntryList().stream()
                .map(RecoLogEntry::getField)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }


    public List<SelectItem> getRecoFieldsSI() {
        return getRecoFields().stream()
                .map(recoField -> new SelectItem(recoField, recoField.getName()))
                .sorted(new ItemSI())
                .toList();
    }


    public List<SelectItem> getLogTypeSI() {
        return getLogTypeList().stream()
                .map(logType -> new SelectItem(logType, getLogTypeLabel(logType)))
                .toList();
    }


    public List<SelectItem> getPreviewTypeSI() {
        return getPreviewTypeList().stream()
                .map(previewType -> new SelectItem(previewType, getPreviewTypeLabel(previewType)))
                .sorted(new ItemSI())
                .toList();
    }


    public List<RecoLogEntry> getLogEntryListUI() {
        return getLogEntryList().stream()
                .filter(logEntry -> _selectedLogTypes.contains(logEntry.getEntryType()) || logEntry.getEntryType() == RecoDebugger.ENTRY_TYPE_FIELD || logEntry.getEntryType() == RecoDebugger.ENTRY_TYPE_RECOSERVICE)
                .filter(logEntry -> logEntry.getField() == null || _selectedFields.contains(logEntry.getField()))
                .filter(logEntry -> !isBlank(logEntry.getMessage()))
                .toList();
    }


    public List<RecoLogEntry> getLogEntryList() {
        if (_recoMetaDoc == null) {
            return new ArrayList<>();
        }
        return _recoMetaDoc.getRecoDebugger().getLogEntryList().stream()
                .filter(logEntry -> logEntry.getPageIdx() >= _documentsList.get(_logIdx).getMinimum() && logEntry.getPageIdx() <= _documentsList.get(_logIdx).getMaximum())
                .filter(logEntry -> _selectedDiagForms.contains(logEntry.getRecoForm()))
                .toList();
    }


    private double getImageDpiScale() {
        return ((double) PdfUtils.IMAGE_RENDER_WEB_DPI) / ((double) PdfUtils.IMAGE_RENDER_DEFAULT_DPI);
    }


    public boolean isEmbedPreprocessedImages() {
        return _embedPreprocessedImages;
    }


    public void setEmbedPreprocessedImages(boolean embedPreprocessedImages) {
        _embedPreprocessedImages = embedPreprocessedImages;
    }


    /**
     * Returns the list of attachments for Workflow and selected RecoData. Workflows of the RecoWorkflow type should show only the 1st file attachment on UI
     *
     * @return List of attachments for Workflow and selected RecoData
     */
    public List<DbFile> getFiles() {
        List<DbFile> result = new ArrayList<>();
        result.add(_workflowDao.getFilesDao().getFile(null, _dataSource.getFileByTag(Workflow.FILE_TAG.IDP_PREPROCESSED.name())));
        return result;
    }


    public String getFrdData() {
        return Json.createObjectBuilder()
                .add("imageDpiScale", getImageDpiScale())
                .add("modeEdit", isModeEdit())
                .build().toString();
    }
}
