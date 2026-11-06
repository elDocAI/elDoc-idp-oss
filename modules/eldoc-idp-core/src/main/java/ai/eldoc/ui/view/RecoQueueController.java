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
import ai.eldoc.ai.service.AiService;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.constants.Pages;
import ai.eldoc.context.*;
import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.data.WorkflowLazyQuery;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.RecoFileMeta;
import ai.eldoc.ocr.OcrService;
import ai.eldoc.reco.RecoFileProcessor;
import ai.eldoc.service.*;
import ai.eldoc.util.JsfUtils;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.Strings;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Recognition Documents processing queue
 */
@Named
@ViewScoped
public class RecoQueueController implements Serializable {

    @Serial
    private static final long serialVersionUID = 4676322777576154277L;
    private static final Logger LOG = LoggerFactory.getLogger(RecoQueueController.class);

    @Inject
    private WorkflowService _workflowService;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private RecoPreprocesorService _recoPreprocesorService;

    @Inject
    private FormDefinitionService _formDefService;

    @Inject
    private RecoFormService _recoFormService;

    @Inject
    private AiService _aiService;

    @Inject
    private ConfigService _configService;

    @Inject
    private TextIndexingService _textIndexingService;

    @Inject
    private FormDefController _formDefController;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private UserContext _userContext;

    @Inject
    private LocaleContext _localeContext;

    private Workflow _dataSource;
    private String _id;
    private boolean _forcePreprocessing;
    private boolean _autoClassification;
    private final List<String> _langs;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public RecoQueueController() {
        _forcePreprocessing = false;
        _langs = new ArrayList<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        _autoClassification = _configService.getIDPConfig().isAutoClassification();
        FormDefinition formDef = _formDefService.getByWebId(FormDefinition.FORM_ID_RECO, true, null);
        if (formDef == null) {
            // No IDP FRD1 FormDefinition present - means we need to init one here
            _formDefController.doInitIdpForm();
            formDef = _formDefService.getByWebId(FormDefinition.FORM_ID_RECO, true, null);
        }
        _dataSource = _workflowService.createWithFormDef(formDef, _userContext.getAclContext());

        // Set available languages
        _langs.add(OcrService.getDefaultLanguageCode());
    }


    public void handleFileUpload(FileUploadEvent event) {
        try {
            Workflow recoWF = handleFileUpload(event, false, false);
            if (recoWF != null) {
                // RecoWorkflow successfully created - we need to show message to user where to find his doc
                String uiMsgDtls = "Documents uploaded to the AI Document Processing queue, you may find them on the"
                        + " <strong><a target='_blank' href=" + Pages.PAGE_WORKFLOW_STATUS + "?" + WorkflowListController.URL_PARAM_VIEW + "=" + WorkflowLazyQuery.VIEW.RECOQUEUE.name().toLowerCase() + ">Workfow status</a></strong> page";
                JsfUtils.addInfoMessage(event.getComponent(), "", uiMsgDtls);
            }
        } catch (FormDefinitionNotFoundException e2) {
            LogHelper.setEvent("id00244");
        } catch (BpmNameNotResolvedException be3) {
            JsfUtils.addErrorMessage("Cannot assign validators for the recognition document", be3.getMessage());
        } catch (Exception e1) {
            LOG.error("Error handling file upload for IDP", e1);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"), e1.toString());
        }
    }


    /**
     * Uploads a file for IDP processing by automatically creating Workflow of the Recognition document type (frd1)
     *
     * @param event UI Upload event
     * @param recoDiag when TRUE indicates that upload is used via RecoDiagnostics page
     * @return created RecoWorkflow where the uploaded file was attached
     */
    /*package*/ Workflow handleFileUpload(FileUploadEvent event, boolean recoDiag, boolean embedPreprocessedImages) throws IOException, InterruptedException, ExecutionException {
        UploadedFile file = event.getFile();
        if (file == null || file.getSize() == 0) {
            LOG.debug("Processing file upload event: file is not present or its size is 0 bytes");
            return null;
        }

        LOG.debug("Processing file upload: {} (size: {} bytes)", file.getFileName(), file.getSize());

        if (!RecoFileProcessor.isFileSupported(file.getFileName())) {
            String msg = format("File format {0} is not supported", file.getFileName());
            LOG.warn(msg);
            JsfUtils.addWarnMessage(msg);
            return null;
        }

        // Try to open file and read number of pages - that will also check if file is supported and not broken
        int pageCount;
        try (InputStream is = file.getInputStream()) {
            pageCount = RecoFileProcessor.getNumberOfPages(file.getFileName(), is);
        } catch (Exception e) {
            JsfUtils.addWarnMessage(format("{0}: format is not supported of file is broken", file.getFileName()));
            return null;
        }

        // Create Workflow (RecoWorkflow)
        Workflow recoWF = _workflowService.create(FormDefinition.FORM_ID_RECO, _userContext.getAclContext());
        recoWF.setDateCreated(new Date());
        recoWF.setTags(_dataSource.getTags());

        if (recoDiag) {
            recoWF.setTopic("Reco-diagnostics: " + file.getFileName());
        } else {
            recoWF.setTopic(file.getFileName());
        }

        // We need to transfer any values set on the RecoQueue page to the actual RecoWorkflow documents
        // As we know that CustomFields set is absolutely the same - we may blindly copy values
        _dataSource.getCfDef().forEach(customField -> {
            String dsFieldName = _dataSource.getCfDsFieldName(customField);
            recoWF.getCfData().put(dsFieldName, _dataSource.getCfData().get(dsFieldName));
        });

        RecoFileMeta sourceFileMeta = new RecoFileMeta().setForcePreprop(isForcePreprocessing()).setLangs(getLangs());
        List<String> tags = List.of(Workflow.FILE_TAG.IDP_SOURCE.name());
        sourceFileMeta.setTags(tags);
        sourceFileMeta.setPageCount(pageCount);
        sourceFileMeta.setType(RecoFileMeta.TYPE_DEFAULT);
        if (isClassificationAvailable() && isAutoClassification()) {
            sourceFileMeta.setProperty(RecoFileMeta.IDP_AUTO_CLASSIFICATION, true);
        }

        // Upload Source file
        try (InputStream is = file.getInputStream()) {
            DbFileRef originalFile = _workflowDao.getFilesDao().uploadFromStream(
                    file.getFileName(),
                    is,
                    file.getContentType(),
                    sourceFileMeta.toMetadata()
            );
            originalFile.setTags(tags);
            recoWF.getFiles().add(originalFile);
        }

        // Save Workflow (RecoWorkflow) - still saved as Draft, it will be sent to Execution automatically after pre-processing
        if (_workflowDao.updateOrCreate(null, recoWF)) {
            if (recoDiag) {
                // In debug mode we force pre-processing asap
                Future<String> futureResult = _recoPreprocesorService.preprocessQueueItem(recoWF, TenantContext.getCurrentTenantId(), true, embedPreprocessedImages);
                futureResult.get();
            } else {
                // RecoWorkflow has to be manually submitted for pre-processing
                _recoPreprocesorService.addQueueItem(recoWF);
            }
            LogHelper.setEvent("id00088", null, null, new LogExt(recoWF).setLogAccount(DirectoryConstants.ELDOC_ROBOT));
            return recoWF;
        } else {
            throw new WorkflowSaveException().setEntity(recoWF);
        }
    }


    public List<String> completeTags(String query) {
        List<String> tags;
        if (isBlank(query)) {
            tags = new ArrayList<>(_recoFormService.getTagsDistinct());
            tags.addAll(_workflowDao.getTagsDistinct());
        } else {
            tags = _recoFormService.getTagsDistinct().stream()
                    .filter(tag -> Strings.CI.contains(tag, query))
                    .collect(Collectors.toList());

            tags.addAll(_workflowDao.getTagsDistinct().stream()
                    .filter(tag -> Strings.CI.contains(tag, query))
                    .toList());
        }
        return tags.stream().distinct().sorted().collect(Collectors.toList());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getId() {
        return _id;
    }


    public void setId(String id) {
        _id = id;
    }


    public Workflow getDataSource() {
        return _dataSource;
    }


    public void setDataSource(Workflow dataSource) {
        _dataSource = dataSource;
    }


    public boolean isForcePreprocessing() {
        return _forcePreprocessing;
    }


    public void setForcePreprocessing(boolean forcePreprocessing) {
        _forcePreprocessing = forcePreprocessing;
    }


    public boolean isAutoClassification() {
        return _autoClassification;
    }


    public void setAutoClassification(boolean autoClassification) {
        _autoClassification = autoClassification;
    }


    public boolean isClassificationAvailable() {
        return _aiService.isClassificationAvailable();
    }


    /**
     * Sets languages (only distinct values are to be stored)
     *
     * @param langs list of enabled languages
     */
    public void setLangs(List<String> langs) {
        _langs.clear();
        if (langs != null) {
            _langs.addAll(langs.stream().distinct().toList());
        }
    }


    /**
     * @return list of enabled languages
     */
    public List<String> getLangs() {
        return _langs;
    }
}
