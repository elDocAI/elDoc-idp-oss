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
package ai.eldoc.service;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.TenantContext;
import ai.eldoc.ai.assistants.dto.reco.*;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.ai.service.AiService;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.context.UserContext;
import ai.eldoc.dao.FormDefinitionDao;
import ai.eldoc.dao.RecoFormDao;
import ai.eldoc.exceptions.*;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.*;
import ai.eldoc.ocr.OcrService;
import ai.eldoc.pdf.ImageStripper;
import ai.eldoc.pdf.PdfUtils;
import ai.eldoc.reco.*;
import ai.eldoc.util.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static ai.eldoc.service.RecognitionService.PREFIX_REGEX;
import static ai.eldoc.util.FileUtils.getContentType;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Provides utility methods for working with {@link RecoForm}
 */
@ApplicationScoped
public class RecoFormService {

    private static final Logger LOG = LoggerFactory.getLogger(RecoFormService.class);

    private static final String RECO_FORM_NAME_PREFIX = "New RecoForm";
    private static final int AUTO_FORM_CONF_THRESHOLD = 55;

    @Inject
    private RecoFormDao _recoFormDao;

    @Inject
    private FormDefinitionDao _formDefDao;

    @Inject
    private RecoPostProcessorService _recoPostProcessorService;

    @Inject
    private RecoPreprocesorService _recoPreprocessorService;

    @Inject
    private CodeSnippetService _codeSnippetService;

    @Inject
    private AiService _aiService;

    @Inject
    private TextIndexingService _textIndexingService;

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private UserContext _userContext;

    @Inject
    private TaskService _taskService;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Finds {@link RecoForm} by its ID
     *
     * @param id
     * @return
     */
    public RecoForm findById(String id) {
        return _recoFormDao.find(id);
    }


    /**
     * Finds all {@link RecoForm}s
     *
     * @return
     */
    public List<RecoForm> findAll() {
        return _recoFormDao.findAll();
    }


    /**
     * Finds all {@link RecoForm}s by given status
     *
     * @param statusList
     * @return
     */
    public List<RecoForm> findAllByStatus(List<Integer> statusList) {
        return _recoFormDao.findAllByStatus(statusList);
    }


    /**
     * Updates record in the database in case {@link RecoForm} with given _id exist, or Creates a new
     * {@link RecoForm} in the database in case this is an unsaved {@link RecoForm}
     *
     * @param recoForm
     * @return
     */
    public boolean updateOrCreate(RecoForm recoForm) {
        return _recoFormDao.updateOrCreate(null, recoForm);
    }


    /**
     * Removes any existing attachments for specified {@link RecoForm} and replaces them with provided file.<br>
     * This method also performs preprocessing for provided source file and saves it as PREPROCESSED file
     *
     * @param recoForm
     * @param fileIS
     * @param fileName
     * @param contentType
     * @throws IOException
     * @throws TimeoutException
     */
    public void attachSourceFile(RecoForm recoForm, InputStream fileIS, String fileName, String contentType) throws IOException, TimeoutException {

        // RecoForm name should be updated only if blank or starts with "New RecoForm", that means name was set during form creation
        if (isBlank(recoForm.getName()) || recoForm.getName().startsWith(RECO_FORM_NAME_PREFIX)) {
            recoForm.setName(fileName);
        }

        DbFileRef sourceTmplFile = recoForm.getSourceFile();
        if (sourceTmplFile != null) {
            _recoFormDao.getFilesDao().deleteFile(null, recoForm, sourceTmplFile);
        }
        DbFileRef preprocessedTmplFile = recoForm.getPreprocessedFile();
        if (preprocessedTmplFile != null) {
            _recoFormDao.getFilesDao().deleteFile(null, recoForm, preprocessedTmplFile);
        }

        // Save original file to database
        sourceTmplFile = _recoFormDao.getFilesDao().uploadFromStream(fileName, fileIS, contentType, null);
        sourceTmplFile.addTag(RecoForm.FILE_TAG.SOURCE.name());
        recoForm.getFiles().add(sourceTmplFile);

        // Perform intermediate save
        updateOrCreate(recoForm);

        // Prepare preprocessed file and save in to database as well
        RecoFileMeta recoFileMeta = new RecoFileMeta().setLangs(recoForm.getLangs());
        recoFileMeta.setProperty(RecoFileMeta.IDP_AUTO_CLASSIFICATION, false);
        try (InputStream is = _recoFormDao.getFilesDao().getFileDownloadStream(sourceTmplFile)) {
            recoFileMeta.setPageCount(RecoFileProcessor.getNumberOfPages(fileName, is));
        }

        try (InputStream is = _recoFormDao.getFilesDao().getFileDownloadStream(sourceTmplFile); RecoFileProcessor recoFileProcessor = new RecoFileProcessor(recoFileMeta)) {
            recoFileProcessor.setProperties(recoForm.getProperties());
            recoFileProcessor.setLanguages(recoForm.getLangs());

            recoFileProcessor.loadFile(fileName, is);

            recoFileProcessor.preprocess();

            preprocessedTmplFile = _recoFormDao.getFilesDao().uploadFromByteArray(
                    recoFileProcessor.getPreprocessedFileName(),
                    recoFileProcessor.getPreprocessedFileAsByteArray(),
                    recoFileProcessor.getPreprocessedFileContentType(),
                    recoFileProcessor.getPreprocessedMeta().toMetadata());

            preprocessedTmplFile.addTag(RecoForm.FILE_TAG.PREPROCESSED.name());
            recoForm.getFiles().add(preprocessedTmplFile);

            // Update RecoFileMeta for the Source File
            if (!_recoFormDao.getFilesDao().updateFileMeta(null, sourceTmplFile, recoFileProcessor.getSourceMeta().toMetadata())) {
                LOG.error("RecoForm[{}]({}) error updating source file meta in DB", recoForm.getId(), recoForm.getName());
            }

            int pagesCount = recoFileProcessor.getPreprocessedMeta().getPageCount();

            if (recoForm.getPages().size() > pagesCount) {
                // Remove unnecessary pages
                recoForm.getPages().subList(pagesCount, recoForm.getPages().size()).clear();
            } else {
                // Otherwise add remaining pages to match the number of pages of uploaded file
                for (int pageIndex = recoForm.getPages().size(); pageIndex < pagesCount; pageIndex++) {
                    recoForm.getPages().add(new RecoPage(pageIndex));
                }
            }

            _recoFormDao.updateOrCreate(null, recoForm);
        }
    }


    /**
     * Removes all attachments from provided RecoForm
     *
     * @param recoForm
     */
    public void removeFormAttachments(RecoForm recoForm) {
        recoForm.getFiles().forEach(dbFileRef -> {
            try {
                _recoFormDao.getFilesDao().deleteFile(null, dbFileRef);
            } catch (Throwable th) {
                LOG.error("Error deleting file {} from RecoForm _id={}", dbFileRef, recoForm.getId(), th);
            }
        });
        recoForm.getFiles().clear();
        recoForm.setStatus(RecoForm.SUSPENDED);
        _recoFormDao.updateOrCreate(null, recoForm);
    }


    /**
     * Suspends provided recoForm.<br>
     * RecoForm can be suspended, if:
     * <ul>
     * <li>RecoForm is in status {@link RecoForm#ACTIVE}</li>
     * </ul>
     *
     * @param recoForm
     * @return
     */
    public boolean suspendForm(RecoForm recoForm) {
        if (recoForm.getStatus() != RecoForm.ACTIVE) {
            return false;
        }
        recoForm.setStatus(RecoForm.SUSPENDED);
        _recoFormDao.updateOrCreate(null, recoForm);
        return true;
    }


    /**
     * Suspends provided recoForms, if possible.<br>
     * For suspension conditions, see {@link #suspendForm(ai.eldoc.model.reco.RecoForm) }
     *
     * @param recoFormList
     * @return
     */
    public int suspendFormBatch(List<RecoForm> recoFormList) {
        if (recoFormList == null) {
            return 0; // nothing to do
        }

        final AtomicInteger batchOpDoneCounter = new AtomicInteger(0);
        // Filter RecoForms which actually can be resumed and then process them one-by-one
        recoFormList.stream()
                .filter(RecoForm::isStatusActive)
                .forEach(recoForm -> {
                    try {
                        if (suspendForm(recoForm)) {
                            batchOpDoneCounter.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        LOG.error("Error saving RecoForm to database, _id={}", recoForm.getId(), ex);
                    }
                });

        return batchOpDoneCounter.get();
    }


    /**
     * Deletes provided RecoForm from system. This cannot be undone.
     *
     * @param recoForm
     */
    public void deleteForm(RecoForm recoForm) {
        _recoFormDao.delete(null, recoForm);
    }


    /**
     * Deletes provided RecoForms from system. This cannot be undone.
     *
     * @param recoFormList
     * @return
     */
    public int deleteFormBatch(List<RecoForm> recoFormList) {
        if (recoFormList == null) {
            return 0; // nothing to do
        }

        AtomicInteger batchOpDoneCounter = new AtomicInteger(0);
        recoFormList.forEach(recoForm -> {
            try {
                deleteForm(recoForm);
                batchOpDoneCounter.incrementAndGet();
            } catch (Exception ex) {
                LOG.error("Error removing RecoForm from database, _id={}", recoForm.getId(), ex);
            }
        });
        return batchOpDoneCounter.get();
    }


    /**
     * Creates RecoForm copy with status {@link RecoForm#SUSPENDED}
     *
     * @param recoForm
     * @throws IOException
     */
    public void duplicateForm(RecoForm recoForm) throws IOException {
        // Init the copy
        RecoForm recoFormCopy = createRecoForm(_userContext.getId());
        recoFormCopy.setStatus(RecoForm.SUSPENDED);
        recoFormCopy.setType(recoForm.getType());
        recoFormCopy.setForm(recoForm.getForm());
        recoFormCopy.getProperties().putAll(recoForm.getProperties());
        recoFormCopy.setUsePostProc(recoForm.isUsePostProc());
        recoFormCopy.setPostProc(recoForm.getPostProc());
        recoFormCopy.setTags(recoForm.getTags());

        // Generate unique name for RecoForm copy
        String formNamePrefix = recoForm.getName() + " (copy";
        String formNameSuffix = ")";
        recoFormCopy.setName(formNamePrefix + formNameSuffix);
        if (!isRecoFormNameUnique(recoFormCopy)) {
            // RecoForm with such name already exists
            List<RecoForm> existingForms = _recoFormDao.findAll();
            for (int i = 1; !isRecoFormNameUnique(recoFormCopy, existingForms); i++) {
                recoFormCopy.setName(formNamePrefix + " " + String.valueOf(i) + formNameSuffix);
            }
        }

        recoFormCopy.getLangs().clear();
        recoFormCopy.getLangs().addAll(recoForm.getLangs());

        recoFormCopy.getPages().clear();
        recoFormCopy.getPages().addAll(recoForm.getPages());

        // Copy file attachments
        if (recoForm.getSourceFile() != null) {
            DbFileRef newSourceFile = copyFileWithMetadata(recoForm.getSourceFile());
            recoFormCopy.getFiles().add(newSourceFile);
        }

        if (recoForm.getPreprocessedFile() != null) {
            DbFileRef newPreprocessedFile = copyFileWithMetadata(recoForm.getPreprocessedFile());
            recoFormCopy.getFiles().add(newPreprocessedFile);
        }

        _recoFormDao.updateOrCreate(null, recoFormCopy);
    }


    private DbFileRef copyFileWithMetadata(DbFileRef sourceFile) {
        if (sourceFile == null) {
            return null;
        }
        DbFileRef fileCopy;
        try (DbFileDownloadStream sourceDownloadStream = _recoFormDao.getFilesDao().getFileDownloadStream(sourceFile)) {
            fileCopy = _recoFormDao.getFilesDao().uploadFromStream(
                    sourceDownloadStream.getDbFile().getFileName(),
                    sourceDownloadStream,
                    sourceDownloadStream.getDbFile().getContentType(),
                    null
            );
            if (sourceDownloadStream.getDbFile().getMetadata() != null) {
                _recoFormDao.getFilesDao().updateFileMeta(null, fileCopy, sourceDownloadStream.getDbFile().getMetadata());
            }
            fileCopy.addTags(sourceFile.getTags());
        }
        return fileCopy;
    }


    /**
     * Activates provided recoForm.<br>
     * RecoForm can be activated, if:
     * <ul>
     * <li>RecoForm is not in status {@link RecoForm#ACTIVE}</li>
     * <li>RecoForm passes validation</li>
     * </ul>
     *
     * @param recoForm
     * @return
     */
    public boolean activateForm(RecoForm recoForm) {
        if (recoForm.getStatus() != RecoForm.ACTIVE) {
            validateFormForActivation(recoForm);
            recoForm.setStatus(RecoForm.ACTIVE);
            updateOrCreate(recoForm);
            return true;
        } else {
            return false;
        }
    }


    /**
     * Activates provided recoForms, if possible.<br>
     * For activation conditions, see {@link #suspendForm(ai.eldoc.model.reco.RecoForm) }
     *
     * @param recoFormList
     * @return
     */
    public int activateFormBatch(List<RecoForm> recoFormList) {
        if (recoFormList == null) {
            return 0; // nothing to do
        }

        final AtomicInteger batchOpDoneCounter = new AtomicInteger(0);
        // Filter RecoForms which actually can be resumed and then process them one-by-one
        recoFormList.stream()
                .filter(recoForm -> recoForm.isStatusNew() || recoForm.isStatusSuspended())
                .forEach(recoForm -> {
                    try {
                        if (activateForm(recoForm)) {
                            batchOpDoneCounter.incrementAndGet();
                        }
                    } catch (RecoFormValidationException ex) {
                        // nothing to do
                    }
                });
        return batchOpDoneCounter.get();
    }


    /**
     * Creates new RecoForm with default settings
     *
     * @param systemId
     * @return
     */
    public static RecoForm createRecoForm(String systemId) {
        RecoForm recoForm = new RecoForm(
                systemId,
                Set.of(DirectoryConstants.ROLE_ADMINDOCRW),
                Set.of(DirectoryConstants.ROLE_ADMINDOCR)
        );
        recoForm.getPages().add(new RecoPage(0)); // in order to be able add keywords we have to add the 1st RecoPage
        recoForm.getLangs().add(OcrService.getDefaultLanguageCode());
        recoForm.setStatus(RecoForm.NEW);
        recoForm.setName(RECO_FORM_NAME_PREFIX + " (" + new Date() + ")");
        return recoForm;
    }


    /**
     * Imports {@link RecoForm}s from provided file to the system
     *
     * @param importFile RFS file with RecoForms to be imported
     * @param recoFormsImportList List, that will be filled with list of forms in import file
     * @param skippedForms List, that will be filled with forms, that was skipped during import - for example, due to consistency check
     * @return List of successfully imported forms
     * @throws IOException
     * @throws TimeoutException if embedded script (codes.lib) validation was interrupted during import
     * @throws ScriptingException if embedded script (codes.lib) has syntax errors or violates validation restrictions (see {@link CodeSnippetService#validate(CodeSnippet) }
     */
    public List<RecoForm> importRecoForms(File importFile, List<Path> recoFormsImportList, List<RecoForm> skippedForms) throws IOException, TimeoutException, ScriptingException {
        List<RecoForm> importedForms = new ArrayList<>();

        // Process uploaded file
        // Create ZIP FileSystem
        URI uri = URI.create("jar:".concat(importFile.toURI().toString()));
        try (FileSystem rfsImportFS = FileSystems.newFileSystem(uri, Collections.emptyMap())) {

            Path codesLibPath = rfsImportFS.getPath("codes.lib");
            if (Files.isRegularFile(codesLibPath)) {
                String code = Files.readString(codesLibPath);
                if (isNotBlank(code)) {
                    List<CodeSnippet> codeSnippetList = _codeSnippetService.findByType(CodeSnippet.TYPE_RECO);
                    CodeSnippet codeSnippet;
                    if (codeSnippetList.isEmpty()) {
                        codeSnippet = new CodeSnippet();
                        codeSnippet.setType(CodeSnippet.TYPE_RECO);
                    } else {
                        codeSnippet = codeSnippetList.get(0);
                    }

                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(_configContext.getSystemTimezone()));
                    SimpleDateFormat sdf = new SimpleDateFormat(_configContext.getAppConfig().getSystemDateFormat() + " " + _configContext.getAppConfig().getSystemTimeFormat());
                    sdf.setTimeZone(cal.getTimeZone());

                    code = codeSnippet.getCodeDecoded() + System.lineSeparator()
                            + System.lineSeparator()
                            + System.lineSeparator()
                            + "////" + System.lineSeparator()
                            + "//// Imported by RFS-import on: " + sdf.format(cal.getTime()) + System.lineSeparator()
                            + "////" + System.lineSeparator()
                            + System.lineSeparator()
                            + System.lineSeparator()
                            + new String(Base64.getDecoder().decode(code), StandardCharsets.UTF_8);
                    codeSnippet.setCode(Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8)));
                    _codeSnippetService.validate(codeSnippet);
                    _codeSnippetService.updateOrCreate(codeSnippet);
                }
            }

            // Read all imported files
            List<Path> importFileList = new ArrayList<>();
            rfsImportFS.getRootDirectories().forEach(rootDir -> {
                try (Stream<Path> walk = Files.walk(rootDir)) {
                    // This actually will be an objects of class: com.sun.nio.zipfs.ZipPath
                    importFileList.addAll(walk.filter(Files::isRegularFile).toList());
                } catch (Exception ex1) {
                    throw new ElDocRuntimeException(ex1);
                }
            });

            // Find all RecoForms
            recoFormsImportList.addAll(importFileList.stream().filter(path -> path.getFileName().toString().endsWith(".recoForm")).toList());

            // Process RecoForms import one-by-one and only import those which are valid
            for (Path recoFormPath : recoFormsImportList) {
                String recoFormJsonStr = Files.readString(recoFormPath);

                // Upgrade RFS form before deserialization, if needed
                recoFormJsonStr = upgradeToV74OnImport(recoFormJsonStr);

                RecoForm recoFormImported = RecoForm.fromJson(recoFormJsonStr);

                // Check required upgrades for imported from RFS RecoForms
                if (isRecoFormRequireUpgradeToV546OnImport(recoFormJsonStr)) {
                    // In v5.4.6 "recoField.required" property was added which we need to adjust to preserve previous (pre v5.4.6) behavior
                    recoFormImported.getPages().stream()
                            .map(RecoPage::getFields)
                            .flatMap(List::stream)
                            .forEach(recoField -> recoField.setRequired(true));
                }

                // Do the consistency check by making sure all files are present in the archive
                boolean consistencyCheckFail = false;
                for (DbFileRef dbFileImport : recoFormImported.getFiles()) {
                    // Locate file with document
                    boolean checkForDataFile = importFileList.stream()
                            .anyMatch(path -> path.getFileName().toString().startsWith(dbFileImport.getId()) && !path.getFileName().toString().endsWith(".meta"));

                    // Locate its metadata
                    boolean checkForMetaFile = importFileList.stream()
                            .anyMatch(path -> path.getFileName().toString().equals(dbFileImport.getId().concat(".meta")));

                    if (!(checkForDataFile && checkForMetaFile)) {
                        // Required file is not present in the archive
                        LOG.trace("RecoForm Import: consistency check failed for the RecoForm[{}] as no file found with the name '{}'", recoFormPath.getFileName(), dbFileImport.getId());
                        skippedForms.add(recoFormImported);
                        consistencyCheckFail = true;
                        break;
                    }
                }

                if (consistencyCheckFail) {
                    // Current RecoForm is not consistent, proceeding with next one
                    continue;
                }

                // Create the new local RecoForm and copy necessary data from the imported one
                RecoForm recoForm = createRecoForm(_userContext.getId());
                recoForm.setStatus(RecoForm.SUSPENDED);
                recoForm.setDescription(recoFormImported.getDescription());
                recoForm.setUsePostProc(recoFormImported.isUsePostProc());
                recoForm.setPostProc(recoFormImported.getPostProc());
                recoForm.getPages().clear();
                recoForm.getPages().addAll(recoFormImported.getPages());
                recoForm.setType(recoFormImported.getType());
                recoForm.getProperties().putAll(recoFormImported.getProperties());
                recoForm.setLangs(recoFormImported.getLangs());
                recoForm.setTags(recoFormImported.getTags());
                recoForm.setExtractionContext(recoFormImported.getExtractionContext());

                // Set RecoForm name and ensure it is unique
                recoForm.setName(recoFormImported.getName());
                if (!isRecoFormNameUnique(recoForm)) {
                    // RecoForm with such name already exists
                    String formNamePrefix = recoFormImported.getName() + " (import";
                    String formNameSuffix = ")";
                    List<RecoForm> existingForms = _recoFormDao.findAll();
                    for (int i = 1; !isRecoFormNameUnique(recoForm, existingForms); i++) {
                        recoForm.setName(formNamePrefix + " " + i + formNameSuffix);
                    }
                }

                // Try to locate target FormDefinition by its name
                if (isNotBlank(recoFormImported.getForm())) {
                    String targetFormUIName = recoFormImported.getForm();
                    FormDefinition formDefSearch = _formDefDao.findAll().stream()
                            .filter(formDefFilter -> Objects.equals(formDefFilter.getNameByLang(_configContext.getDefaultLang()), targetFormUIName))
                            .findFirst().orElse((FormDefinition) null);
                    if (formDefSearch != null) {
                        recoForm.setForm(formDefSearch.getFormId());
                    }
                }

                // Copy imported attachment files to the local DB
                for (DbFileRef dbFileImport : recoFormImported.getFiles()) {
                    Path dbFileImportPath = importFileList.stream()
                            .filter(path -> path.getFileName().toString().startsWith(dbFileImport.getId()) && !path.getFileName().toString().endsWith(".meta"))
                            .findFirst().get();

                    Path dbFileMetaImportPath = importFileList.stream()
                            .filter(path -> path.getFileName().toString().equals(dbFileImport.getId().concat(".meta")))
                            .findFirst().get();

                    String fileName = dbFileImportPath.getFileName().toString();

                    RecoFileMeta recoFileMeta = RecoFileMeta.fromJson(Files.readString(dbFileMetaImportPath)); // Read RecoFileMeta as JSON
                    DbFileRef dbFile = _recoFormDao.getFilesDao().uploadFromByteArray(fileName, Files.readAllBytes(dbFileImportPath), getContentType(fileName), recoFileMeta.toMetadata());
                    dbFile.addTags(dbFileImport.getTags()); // Copy imported file's tags
                    recoForm.getFiles().add(dbFile);
                }

                _recoFormDao.updateOrCreate(null, recoForm);
                importedForms.add(recoForm);
            }
        } catch (ParseException ex) {
            LOG.error(null, ex);
        }
        return importedForms;
    }


    /**
     * Checks whether pre-5.4.6 RecoForm JSON string requires upgrade by validating that all fields contain `required` property
     *
     * @param recoFormJsonStr Original RecoForm JSON string
     * @return {@code true}, if upgrade is required, {@code false} otherwise
     */
    static boolean isRecoFormRequireUpgradeToV546OnImport(String recoFormJsonStr) {
        AtomicBoolean requiredPropPresent = new AtomicBoolean();

        try {
            // Read RecoForm Json
            JsonObject recoFormJson = JsonbUtils.getInstance().fromJson(recoFormJsonStr, JsonObject.class);

            // Iterate over pages
            JsonArray pagesList = recoFormJson.getJsonArray(RecoForm._PAGES);
            pagesList.forEach(recoPageJson -> {
                // Iterate over page fields
                JsonArray pageFields = recoPageJson.asJsonObject().getJsonArray(RecoPage._FIELDS);
                pageFields.forEach(recoFieldJson -> {
                    // Check for presence of the "recoField.required" property which was added since v5.4.6
                    JsonValue reuiredFieldValue = recoFieldJson.asJsonObject().get(RecoField._REQUIRED);
                    if (reuiredFieldValue != null) {
                        requiredPropPresent.set(true);
                    }
                });
            });
        } catch (Exception e) {
            LOG.error("Error processing RecoForm JSON: {}", recoFormJsonStr, e);
        }

        return !requiredPropPresent.get();
    }


    /**
     * Performs upgrade of RecoForm JSON string to v7.4 format (field `_tags` renamed to `tags`)
     *
     * @param recoFormJsonStr Original RecoForm JSON string
     * @return Upgraded RecoForm JSON string if it contained `_tags` field, or original string otherwise
     */
    static String upgradeToV74OnImport(String recoFormJsonStr) {
        final String TAGS_FIELD_OLD = "_tags";
        try {
            // Read RecoForm Json
            JsonObject recoFormJson = JsonbUtils.getInstance().fromJson(recoFormJsonStr, JsonObject.class);

            if (!recoFormJson.containsKey(TAGS_FIELD_OLD)) {
                LOG.trace("RecoForm JSON does not contain _tags property, skipping upgrade");
                return recoFormJsonStr;
            }
            LOG.debug("RecoForm JSON contains _tags property, upgrading");
            JsonObjectBuilder builder = Json.createObjectBuilder(recoFormJson);
            builder.add(RecoForm._TAGS, recoFormJson.get(TAGS_FIELD_OLD));
            builder.remove(TAGS_FIELD_OLD);
            return builder.build().toString();
        } catch (Exception e) {
            LOG.error("Error processing RecoForm JSON: {}", recoFormJsonStr, e);
        }
        return recoFormJsonStr;
    }


    /**
     * Creates {@link RecoForm} export task
     *
     * @param recoFormList List of {@link RecoForm}s to be exported
     * @return id of the export task
     * @throws FileNotFoundException if there was error during export archive creation
     * @throws IOException if there was error during export archive creation
     */
    public String exportRecoForms(List<RecoForm> recoFormList) throws FileNotFoundException, IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        File zipFile = new File(System.getProperty("java.io.tmpdir"), "elDoc_recoForm_export_".concat(sdf.format(new Date())).concat(".rfs")); // rfs stands for RecoForm Set
        try (FileOutputStream fos = new FileOutputStream(zipFile); ZipOutputStream zos = new ZipOutputStream(fos);) {
            for (RecoForm recoForm : recoFormList) {
                String folderName = recoForm.getId();

                // We do remember the name of the FormDefinition, so then during the import we will try to assign its id located by name
                if (isNotBlank(recoForm.getForm())) {
                    FormDefinition formDef = _formDefDao.findByWebId(recoForm.getForm(), true, null);
                    if (formDef != null) {
                        recoForm.setForm(formDef.getNameByLang(_configContext.getDefaultLang()));
                    }
                }

                // 1. Save RecoFrom itself
                ZipEntry zipEntry = new ZipEntry(folderName.concat("/").concat(recoForm.getId()).concat(".recoForm"));
                zos.putNextEntry(zipEntry);
                zos.write(recoForm.toJson().getBytes(StandardCharsets.UTF_8));

                // 2. Save attachments
                for (DbFileRef dbFileRef : recoForm.getFiles()) {
                    DbFile file = _recoFormDao.getFilesDao().getFile(null, dbFileRef);

                    // 2.1. Save document file
                    String ext = FilenameUtils.getExtension(file.getFileName());
                    zipEntry = new ZipEntry(folderName.concat("/").concat(dbFileRef.getId()).concat(".").concat(ext));
                    zos.putNextEntry(zipEntry);
                    zos.write(_recoFormDao.getFilesDao().getFileBytes(dbFileRef));

                    // 2.2. Save document file's meta
                    zipEntry = new ZipEntry(folderName.concat("/").concat(dbFileRef.getId()).concat(".meta"));
                    zos.putNextEntry(zipEntry);
                    zos.write(JsonbUtils.getInstance().toJson(file.getMetadata()).getBytes(StandardCharsets.UTF_8));
                }
            }
            List<CodeSnippet> codeSnippetList = _codeSnippetService.findByType(CodeSnippet.TYPE_RECO);
            if (!codeSnippetList.isEmpty() && isNotBlank(codeSnippetList.get(0).getCode())) {
                ZipEntry zipEntry = new ZipEntry("codes.lib");
                zos.putNextEntry(zipEntry);
                zos.write(codeSnippetList.get(0).getCode().getBytes());
            }
        }

        return _taskService.setExportDownloadTask(zipFile.getAbsolutePath());
    }


    /**
     * Returns all tags (unique only) used with RecoForm Models
     *
     * @return
     */
    public Set<String> getTagsDistinct() {
        return _recoFormDao.getTagsDistinct();
    }


    //
    // Field initialization methods
    //


    /**
     * Performs field initialization.<br>
     * Steps performed:<br>
     * <ul>
     * <li>Fix field's regions (i.e. negative width/height;</li>
     * <li>If field is OMR field - initialize marks;</li>
     * </ul>
     *
     * @param recoForm
     * @param pageIdx
     * @param recoField
     * @throws IOException
     */
    public void initField(RecoForm recoForm, int pageIdx, RecoField recoField) throws IOException {
        if (recoField == null) {
            return;
        }

        // Check and fix anchors/value rectangles, if required (for example, if they have negative width/height)
        fixFieldRegions(recoField);

        if (recoField.isTypeOmr()) {
            initFieldOmr(recoForm, pageIdx, recoField);
        }
    }


    /**
     * Initializes OMR field
     *
     * @param recoForm
     * @param pageIdx
     * @param recoField
     * @throws IOException if there was problems loading sample file from database
     */
    private void initFieldOmr(RecoForm recoForm, int pageIdx, RecoField recoField) throws IOException {
        BufferedImage pageImage = null;
        BufferedImage fieldImage = null;

        // Load image file
        DbFileRef previewFile = recoForm.getPreprocessedFile();

        // Load image file for preview on UI
        if (previewFile == null) {
            return;
        }

        recoField.getOpMarks().clear();

        try (InputStream is = _recoFormDao.getFilesDao().getFileDownloadStream(previewFile); PDDocument document = PdfUtils.loadPDF(is); ImageProcessor imageProcessor = new ImageProcessor()) {

            Pair<BufferedImage, Boolean> pageImageData = PdfUtils.renderOrExtractDocPageImage(document, pageIdx, PdfUtils.IMAGE_RENDER_BEST_DPI, false);
            pageImage = pageImageData.getLeft();
            double pageImageToPageScale = pageImage.getWidth() / document.getPage(pageIdx).getCropBox().getWidth();

            imageProcessor.addPage(pageImage);
            imageProcessor.convertToGrayscale();
            imageProcessor.threshold();
            pageImage = imageProcessor.getPages().get(0);
            Rectangle pageImageRect;
            if (pageImageData.getRight()) {
                // Document page was rendered with IMAGE_RENDER_BEST_DPI - apply DPI scale
                pageImageRect = PdfUtils.getPageDimensionRect(document.getPage(pageIdx)).getScaledClone(pageImageToPageScale);
            } else {
                // Document page was exctracted as is
                // Extracted image can have offset from page borders, that should be applied to field area as well
                ImageStripper imageStripper = new ImageStripper();
                imageStripper.processPage(document.getPage(pageIdx));
                pageImageRect = imageStripper.getFullPageImageCandidateArea(); // here we already know, that image was extracted, so no need to check, if full page image candidate found
            }
            double imageOffsetX = 0 - pageImageRect.getX() * pageImageToPageScale;
            double imageOffsetY = 0 - pageImageRect.getY() * pageImageToPageScale;

            Rectangle fieldRect = recoField.getValueRegion().getScaledClone(pageImageToPageScale);
            fieldRect.translate(imageOffsetX, imageOffsetY);
            Rectangle pageRect = new Rectangle(0, 0, pageImage.getWidth(), pageImage.getHeight());
            Rectangle fieldRectCropped = fieldRect.createIntersection(pageRect);
            double markOffsetX = fieldRectCropped.getX() - fieldRect.getX();
            double markOffsetY = fieldRectCropped.getY() - fieldRect.getY();

            fieldImage = ImageProcessor.getSubimage(pageImage, fieldRectCropped);

            List<Rectangle> opMarkList = new ArrayList<>();

            // Find circles and add them to the field
            List<Rectangle> circlesList = ImageProcessor.findCircles(fieldImage);
            opMarkList.addAll(circlesList);

            // Add squares and add them to the field
            List<Rectangle> squaresList = ImageProcessor.findSquares(fieldImage);
            opMarkList.addAll(squaresList);

            RectangleUtils.sortNaturalOrder(opMarkList).stream()
                    .flatMap(row -> row.stream())
                    .forEach(mark -> {
                        RecoField opMark = RecoField.createDefault();
                        opMark.setName("Mark" + recoField.getOpMarks().size());
                        Rectangle scaledMark = mark.getScaledClone(1 / pageImageToPageScale);
                        scaledMark.translate(0 - imageOffsetX, 0 - imageOffsetY);
                        scaledMark.translate(markOffsetX, markOffsetY);
                        opMark.setValueRegion(scaledMark);

                        if (circlesList.contains(mark)) {
                            opMark.getOmrProp().setOpMarkType(OmrProperties.OPMARK_TYPE_CIRCLE);
                        } else {
                            opMark.getOmrProp().setOpMarkType(OmrProperties.OPMARK_TYPE_BOX);
                        }

                        recoField.getOpMarks().add(opMark);
                    });

        } finally {
            if (pageImage != null) {
                pageImage.flush();
            }
            if (fieldImage != null) {
                fieldImage.flush();
            }
        }
    }


    /**
     * Initializes RecoFormResult (and RecoForm) from AI output
     *
     * @param recoFormResult
     * @param recoForm
     * @throws java.io.IOException
     */
    public static void recoFormResultToRecoForm(RecoFormResult recoFormResult, RecoForm recoForm) throws IOException {
        if (recoFormResult == null) {
            throw new ElDocRuntimeException("Cannot initialize RecoForm");
        }

        // Clean RecoForm
        recoForm.getPages().clear();

        recoFormResult.setRecoForm(recoForm);

        for (int i = 0; i < recoFormResult.getPages().size(); i++) {

            RecoPageResult docAiPageResult = recoFormResult.getPages().get(i);
            RecoPage recoPage = new RecoPage(i);
            recoForm.getPages().add(recoPage);

            // Init text/OMR fields
            for (RecoFieldResult field : docAiPageResult.getFields()) {
                if (field.isTypeTable()) {
                    // Table fields will be processed later
                    continue;
                }
                RecoField recoField = recoFieldResultToRecoField(field, recoForm, recoPage, field.getName());
                recoPage.getFields().add(recoField);

                if (field.isTypeText()) {
                    // Nothing to do - TEXT-type fields are most basic and do not need any additional initialization
                } else if (field.isTypeOmr()) {
                    recoField.setType(RecoFieldDataType.OMR);
                    RecoField opMark = RecoField.createDefault();
                    opMark.setName(field.getName());
                    opMark.getOmrProp().setOpMarkType(OmrProperties.OPMARK_TYPE_BOX);
                    opMark.setValueRegion(new Rectangle(0, 0, field.getRegion().getWidth(), field.getRegion().getHeight()));
                    recoField.getOpMarks().add(opMark);
                }
            }

            // Init table fields
            for (RecoFieldResult table : docAiPageResult.getFields()) {
                if (!table.isTypeTable()) {
                    continue;
                }
                boolean prevPageHasSameTable = i == 0
                        ? false
                        : recoForm.getPages().subList(0, i).stream()
                        .map(RecoPage::getFields)
                        .flatMap(List::stream)
                        .map(RecoField::getName)
                        .anyMatch(table.getName()::equals);
                if (prevPageHasSameTable) {
                    continue;
                }
                RecoField tableRecoField = recoFieldResultToRecoField(table, recoForm, recoPage, table.getName());
                recoPage.getFields().add(tableRecoField);
                tableRecoField.setType(RecoFieldDataType.TABLE);

                List<RecoFieldResult> headerRow = table.getTableData().get(0);

                for (int colIdx = 0; colIdx < table.getTableData().getFirst().size(); colIdx++) {
                    RecoFieldResult cellResult = headerRow.get(colIdx);
                    RecoField cell = recoFieldResultToRecoField(cellResult, recoForm, recoPage, cellResult.getName());
                    tableRecoField.getColumns().add(cell);

                    cell.setRequired(false);
                }

                // Update columns value region
                tableRecoField.getColumns().stream()
                        .map(RecoField::getValueRegion)
                        .peek(col -> col.setX(col.getX() - tableRecoField.getValueRegion().getX()))
                        .peek(col -> col.setY(0))
                        .forEach(col -> col.setHeight(table.getRegion().getHeight()));

                // We need to re-order columns in table RecoFieldResult, as they are originally ordered by confidence levels, not coordinates
                List<List<RecoFieldResult>> newTableData = table.getTableData().stream()
                        .map(row -> RecoFormResultUtils.reorderResultCols(recoForm, tableRecoField, row))
                        .toList();

                table.getTableData().clear();
                table.getTableData().addAll(newTableData);
            }
        }

        reorderFields(recoForm);
    }


    private static RecoField recoFieldResultToRecoField(RecoFieldResult rfr, RecoForm recoForm, RecoPage recoPage, String fieldName) {
        RecoField recoField = RecoField.createDefault();
        recoField.setName(fieldName);
        if (rfr.getRegion() != null) {
            // Use copy of value region to aviod unexpected behaviour
            recoField.setValueRegion(rfr.getRegion().clone());
        }
        recoField.setAnchorText(rfr.getName());
        if (rfr.getAnchorRegion() != null) {
            recoField.setAnchorRegion(rfr.getAnchorRegion());
        }
        recoField.getProp().setAuto(true);
        rfr.setRfPageIdx(recoForm.getPages().indexOf(recoPage));
        rfr.setRfFieldIdx(recoPage.getFields().size()); // No need to do '-1', as field will be added to the page only after this method execution
        if (recoForm.getId().equals(RecoForm.AUTOMATIC_FORM_ID)) {
            recoField.setConfidenceThreshold(AUTO_FORM_CONF_THRESHOLD);
        }
        return recoField;
    }


    /**
     * Initializes RecoFormResult (and RecoForm) from AI output
     *
     * @param recoForm
     * @throws java.io.IOException
     */
    public void initRecoFormResultWithAi(RecoForm recoForm) throws IOException {
        RecoFormAiDTO generatedForm;
        int pagesCount = recoForm.getPages().size(); // Number of pages in RecoForm matches number of pages in preprocessed file
        try (DbFileDownloadStream is = _recoFormDao.getFilesDao().getFileDownloadStream(recoForm.getPreprocessedFile())) {
            recoForm.getPages().forEach(page -> page.getFields().clear());
            AclContext robotAcl = _directoryService.getAclContext(DirectoryConstants.ELDOC_RECO_BOT);
            AiRequestContext requestContext = AiRequestContext.forSrcIDP(I18n.LANG_SYSTEM_CODE_EN, TenantContext.getCurrentTenantId(), robotAcl);
            Pair<List<String>, Integer> newFileContent = _textIndexingService.extractText(is.getDbFile().getFileName(), is, true, requestContext);
            String fragmentText = TextIndexingService.buildTextIndex(newFileContent.getLeft());
            generatedForm = _aiService.generateRecoForm(recoForm.getId(), fragmentText, requestContext, 3);
        }

        if (generatedForm == null) {
            throw new ElDocRuntimeException("Cannot initialize RecoForm");
        }

        // Clean RecoForm
        recoForm.getPages().clear();
        for (int pageIdx = 0; pageIdx < pagesCount; pageIdx++) {
            RecoPage recoPage = new RecoPage(pageIdx);
            recoForm.getPages().add(recoPage);
        }

        generatedForm.getFields().stream()
                .map(this::textFieldFromAiDTO)
                .forEach(recoForm.getPages().getFirst().getFields()::add);

        generatedForm.getTables().stream()
                .map(this::tableFieldFromAiDTO)
                .forEach(recoForm.getPages().getFirst().getFields()::add);

        reorderFields(recoForm);

        updateRepeatedFieldNames(recoForm.getFields());

        recoForm.getFields().forEach(RecoFormService::fixTags);

        updateOrCreate(recoForm);
    }


    private RecoField tableFieldFromAiDTO(RecoFieldTableAiDTO dto) {
        RecoField recoField = textFieldFromAiDTO(dto); // re-use existing logic for generic properties
        recoField.setType(RecoFieldDataType.TABLE);

        dto.getColumns().stream()
                .map(this::textFieldFromAiDTO)
                .forEach(recoField.getColumns()::add);

        return recoField;
    }


    private RecoField textFieldFromAiDTO(RecoFieldAiDTO dto) {
        RecoField recoField = RecoField.createDefault();
        recoField.setType(RecoFieldDataType.TEXT);
        recoField.setName(dto.getName());
        recoField.getProp().setAiDescription(dto.getDescription());
        recoField.setValueRegion(dto.getFieldValueRect());
        return recoField;
    }


    /**
     * Reorders fields on the provided RecoForm's pages, so they will be ordered left-to-right, top-to-bottom by their value region
     *
     * @param recoForm
     */
    private static void reorderFields(RecoForm recoForm) {
        for (RecoPage recoPage : recoForm.getPages()) {
            List<RecoField> reorderedFields = recoForm.getFieldsArrangedByValueRect(recoPage);
            recoPage.getFields().clear();
            recoPage.getFields().addAll(reorderedFields);
        }
    }


    protected static void fixTags(RecoField recoField) {
        Set<String> fieldTagsCleaned = new HashSet<>();
        for (String existingTag : recoField.getTags()) {
            String cleanedTag = existingTag
                    .replaceAll(" ", "_")
                    .toUpperCase();
            fieldTagsCleaned.add(cleanedTag);
        }

        recoField.setTags(fieldTagsCleaned);

        if (recoField.isTypeTable()) {
            recoField.getColumns().forEach(RecoFormService::fixTags);
        }
    }


    /**
     * Checks provided fields lists, and if it has RecoFields with repeated names - adds
     * integer (auto-incremented) index as suffix to their names to avoid duplicates
     *
     * @param recoFieldsList
     */
    private static void updateRepeatedFieldNames(List<RecoField> recoFieldsList) {
        Map<String, Integer> fieldNamesCount = new HashMap<>();
        for (RecoField recoField : recoFieldsList) {
            String cleanedFieldName = cleanupFieldName(recoField.getName());
            if (isNotBlank(cleanedFieldName)) {
                // Replace field name with cleaned up, if there is text left (protection for cases, when field name contains only special characters, like '#' or '%')
                recoField.setName(cleanedFieldName);
            }
            if (recoField.isTypeTable()) {
                updateRepeatedFieldNames(recoField.getColumns());
            }

            String fieldNameForValidation = cleanedFieldName.toLowerCase(); // Use lowercase field name for checks for duplicates and counting field index
            if (!fieldNamesCount.containsKey(fieldNameForValidation)) {
                fieldNamesCount.put(fieldNameForValidation, 0);
                continue;
            }

            int repeatedFieldIdx = fieldNamesCount.get(fieldNameForValidation) + 1;
            fieldNamesCount.put(fieldNameForValidation, repeatedFieldIdx);
            recoField.setName(recoField.getName() + " [" + repeatedFieldIdx + "]");
        }
    }


    private static String cleanupFieldName(String fieldName) {
        return fieldName
                .replaceAll("[^\\p{L} 0-9]", "") // Remove any characters, that are not a letter in any language, number or whitespace
                .replaceAll(" +", " ") // Removes extra space characters from name
                .trim(); // Removes any leading/trailing whitespaces, that may occur on previous step
    }


    //
    // Form check and adjustment methods
    //


    /**
     * Tries to perform possible updates/fixes for RecoForm automatically, so that it meets internal matching/processing logic and restrictions
     *
     * @param recoForm
     */
    public void fixForm(RecoForm recoForm) {
        fixTableColumnGaps(recoForm);
        applyFldIgnoreProperty(recoForm);
        updateBitapMistakesCount(recoForm);
        fixRegions(recoForm);
        cleanupFieldsTags(recoForm);
    }


    /**
     * Checks and fixes each RecoForm field's regions, if needed
     *
     * @param recoForm
     */
    void fixRegions(RecoForm recoForm) {
        recoForm.getFields().parallelStream()
                .forEach(this::fixFieldRegions);
    }


    /**
     * Checks and fixes all field's regions (anchors and value), if needed
     *
     * @param recoField
     */
    void fixFieldRegions(RecoField recoField) {
        if (recoField.getAnchorRegion() != null) {
            fixRectangleCoords(recoField.getAnchorRegion());
        }
        if (recoField.getValueRegion() != null) {
            fixRectangleCoords(recoField.getValueRegion());
        }
        if (!recoField.getColumns().isEmpty()) {
            recoField.getColumns().stream()
                    .map(RecoField::getValueRegion)
                    .peek(r -> fixColumnHeight(r, recoField.getValueRegion()))
                    .forEach(this::fixRectangleCoords);
        }
        if (recoField.getTableProp().getAnchorUnderTable() != null) {
            fixRectangleCoords(recoField.getTableProp().getAnchorUnderTable());
        }
    }


    void fixColumnHeight(Rectangle col, Rectangle table) {
        col.setY(0);
        col.setHeight(table.getHeight());
    }


    /**
     * Fixes rectangle region
     * <ul>
     * <li>If rectangle have negative width - it should be flipped horizontally, keeping rectangle on same area</li>
     * <li>If rectangle have negative height - it should be flipped vertically, keeping rectangle on same area</li>
     * </ul>
     *
     * @param rect
     */
    void fixRectangleCoords(Rectangle rect) {
        if (rect.getWidth() < 0) {
            rect.setWidth(0 - rect.getWidth());
            rect.setX(rect.getX() - rect.getWidth());
        }
        if (rect.getHeight() < 0) {
            rect.setHeight(0 - rect.getHeight());
            rect.setY(rect.getY() - rect.getHeight());
        }
    }


    /**
     * If table field's column is marked to be ignored - automatically disable all functionality, including:
     * <ul>
     * <li>Clear column tags;</li>
     * <li>Set confidence threshold to 0;</li>
     * <li>Disable (but do not clear) regex;</li>
     * <li>Disable (but do not clear) post-processing;</li>
     * </ul>
     *
     * @param recoForm
     */
    void applyFldIgnoreProperty(RecoForm recoForm) {
        recoForm.getPages().forEach(page -> {
            page.getFields().stream()
                    .filter(RecoField::isTypeTable)
                    .forEach(recoField -> {
                        recoField.getColumns().stream()
                                .filter(column -> column.getProp().isIgnored())
                                .forEach(column -> {
                                    column.getTags().clear();
                                    column.setConfidenceThreshold(0);
                                    column.getProp().setUsePostProc(false);
                                    column.getTextProp().setUseRegex(false);
                                });
                    });
        });
    }


    /**
     * Try to fix possible gaps between columns in table fields, that may appear due to rounding issues on columns resizing on UI
     *
     * @param recoForm
     */
    void fixTableColumnGaps(RecoForm recoForm) {
        recoForm.getPages().forEach(page -> {
            page.getFields().stream()
                    .filter(RecoField::isTypeTable)
                    .forEach(field -> {
                        DoubleAdder fieldX = new DoubleAdder();
                        if (!field.getColumns().isEmpty()) {
                            field.getColumns().forEach(column -> {
                                column.getValueRegion().setX(fieldX.doubleValue());
                                fieldX.add(column.getValueRegion().getWidth());
                                column.getValueRegion().setY(0);
                                column.getValueRegion().setHeight(field.getValueRegion().getHeight());
                            });
                            field.getColumns()
                                    .get(field.getColumns().size() - 1)
                                    .getValueRegion()
                                    .setWidth(field.getValueRegion().getWidth() - field.getColumns().get(field.getColumns().size() - 1).getValueRegion().getX());
                        }
                    });
        });
    }


    /**
     * Adjusts acceptable number of mistakes for bitap search string, if Bitap regex is used
     *
     * @param recoForm
     */
    void updateBitapMistakesCount(RecoForm recoForm) {
        recoForm.getFields().stream()
                .filter(rf -> rf.getTextProp().getRegexType() == TextProperties.TYPE_BITAP)
                .forEach(rf -> {
                    int needleLength = Bitap.getNeedleLen(rf.getTextProp().getRegex());
                    int maxAllowedMistakes = (int) (needleLength / 5);
                    int normalizedMistakesCount = Math.min(maxAllowedMistakes, rf.getTextProp().getRegexBitapMistakesCount());
                    rf.getTextProp().setRegexBitapMistakesCount(normalizedMistakesCount);
                });
    }


    /**
     * Removes predefined internal tags from RecoFields, if present
     *
     * @param recoForm
     */
    void cleanupFieldsTags(RecoForm recoForm) {
        recoForm.getFields().forEach(this::cleanupFieldTags);
    }


    void cleanupFieldTags(RecoField recoField) {
        Set<String> predefinedInternalTags = Set.of(CustomField.TAG_RECO_FIELD);
        // Cleanup field-level tags
        recoField.getTags().removeAll(predefinedInternalTags);

        if (recoField.isTypeOmr()) {
            recoField.getOpMarks().forEach(this::cleanupFieldTags);
        }

        if (recoField.isTypeTable()) {
            recoField.getColumns().forEach(this::cleanupFieldTags);
        }
    }


    //
    // RecoForm validation methods
    //


    /**
     * Checks whether RecoForm is valid for activation
     *
     * @param recoForm
     * @throws RecoFormValidationException if there was any errors during validation
     */
    public void validateFormForActivation(RecoForm recoForm) throws RecoFormValidationException {
        validateFormAttachments(recoForm);

        validateFormName(recoForm);

        validateFormAiDesc(recoForm);

        validateFormLangs(recoForm);

        validateKeywords(recoForm);

        validateFormFields(recoForm);

        validateFormPostProcessing(recoForm);
    }


    /**
     * Checks, if all RecoForm fields are valid
     *
     * @param recoForm
     */
    void validateFormFields(RecoForm recoForm) {
        recoForm.getFields().forEach(rf -> validateField(recoForm, rf));
    }


    /**
     * Checks, that RecoForm has required attachments
     *
     * @param recoForm
     */
    static void validateFormAttachments(RecoForm recoForm) {
        if (recoForm.getSourceFile() == null) {
            // RecoForm should have source file
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_SOURCE_FILE_REQUIRED);
        }
    }


    /**
     * Checks, that RecoForm has valid name set
     *
     * @param recoForm
     */
    void validateFormName(RecoForm recoForm) {
        if (isBlank(recoForm.getName())) {
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_NAME_REQUIRED);
        }
        if (!isRecoFormNameUnique(recoForm)) {
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_NAME_NOT_UNIQUE);
        }
    }


    /**
     * Checks that RecoForm Description and ExtractionContext has valid values
     *
     * @param recoForm RecoForm to validate
     */
    void validateFormAiDesc(RecoForm recoForm) {
        if (isNotBlank(recoForm.getDescription())) {
            if (recoForm.getDescription().length() > 1000) {
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_DESCRIPTION_TOO_LONG).setMessageParams(new Object[]{1000});
            }
        }
        if (isNotBlank(recoForm.getExtractionContext())) {
            if (recoForm.getExtractionContext().length() > 3000) {
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_EXTRACTION_CONTEXT_TOO_LONG).setMessageParams(new Object[]{3000});
            }
        }
    }


    /**
     * Checks, that RecoForm has at least one language set
     *
     * @param recoForm
     */
    static void validateFormLangs(RecoForm recoForm) {
        if (CollectionUtils.isEmpty(recoForm.getLangs())) {
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_LANGS_REQUIRED);
        }
    }


    /**
     * Checks post-processing functions for RecoForm by compiling it (Engine.eval) without execution.<br>
     * This checks only general syntax errors, while script can have logic errors (referencing arguments by wrong name, referencing undefined variables/fields, missing return statement, etc). <br>
     * Such logic errors can be checked only by executing post-processor script, and therefore require some input data, which cannot be obtained without user interaction. Therefore, such errors are
     * intended to be checked using tester or reco diagnostics.
     *
     * @param recoForm
     * @throws RecoFormValidationException
     */
    void validateFormPostProcessing(RecoForm recoForm) throws RecoFormValidationException {
        if (!recoForm.isUsePostProc() || isBlank(recoForm.getPostProc())) {
            return;
        }

        try (OutputStream os = new ByteArrayOutputStream()) {
            Map<String, Object> context = new HashMap<>();
            _recoPostProcessorService.validateRecoDataPostProcessor(recoForm.getPostProcDecoded(), context, os);
        } catch (Throwable ex) {
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_POSTPROC_INVALID).setMessageParams(new Object[]{ex.getMessage()});
        }
    }


    /**
     * Validates keywords, set for form, following next rules:
     * <ul>
     * <li>RecoForm has to have at least one keyword defined;</li>
     * <li>All regex-based keywords (inclusive and minus) has to have non-blank, valid regex defined;</li>
     * </ul>
     *
     * @param recoForm
     * @throws RecoFormValidationException if there was any errors during validation
     */
    static void validateKeywords(RecoForm recoForm) throws RecoFormValidationException {
        if (recoForm.getType() == RecoForm.TYPE_AIBASED) {
            return; // AI/LLM Based recoforms do not require keywords
        }

        // RecoForm should have at least one keyword provided
        if (recoForm.getKeywords().isEmpty()) {
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_KEYWORD_REQUIRED);
        }

        // Validate REGEX-type keywords, if any
        for (String keyword : recoForm.getKeywordsInclusive()) {
            if (keyword.startsWith(PREFIX_REGEX)) {
                try {
                    String regex = keyword.substring(PREFIX_REGEX.length());
                    if (isBlank(regex)) {
                        throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_KEYWORD_REGEX_BLANK).setMessageParams(new Object[]{keyword});
                    }
                    Pattern.compile(regex, CASE_INSENSITIVE);
                } catch (PatternSyntaxException pse) {
                    throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_KEYWORD_REGEX_INVALID).setMessageParams(new Object[]{keyword, pse.getMessage()});
                }
            }
        }

        // Validate REGEX-type keywords, if any
        for (String minusKeyword : recoForm.getKeywordsMinus()) {
            if (minusKeyword.startsWith(PREFIX_REGEX)) {
                try {
                    String regex = minusKeyword.substring(PREFIX_REGEX.length());
                    if (isBlank(regex)) {
                        throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_KEYWORD_REGEX_BLANK).setMessageParams(new Object[]{minusKeyword});
                    }
                    Pattern.compile(regex, CASE_INSENSITIVE);
                } catch (PatternSyntaxException pse) {
                    throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FORM_KEYWORD_REGEX_INVALID).setMessageParams(new Object[]{"-" + minusKeyword, pse.getMessage()});
                }
            }
        }
    }


    /**
     * Performs initial validation for RecoField.<br>
     * General validation rules:<br>
     * 1. If anchor is regex-based - it should have valid regex provided;<br>
     * 2. If recoField has regex provided - it should be compilable;<br>
     * 3. If recoField has post-processing function provided - it should be valid;<br>
     * <br>
     * Field of regex-based form is valid if additionally: <br>
     * 1. recoField regex is mandatory;
     *
     * @param recoForm {@link RecoForm} provided field belongs to
     * @param recoField {@link RecoField} to be validated
     * @throws RecoFormValidationException if there was any errors during validation
     */
    void validateField(RecoForm recoForm, RecoField recoField) throws RecoFormValidationException {

        validateFieldName(recoField, recoForm.getFields());

        validateFieldTags(recoField, recoForm.getFields());

        // Check recoField anchors for anchor-based forms
        if (recoForm.getType() != RecoForm.TYPE_AIBASED) {
            validateFieldAnchors(recoField);
        }

        // Check Confidence threshold settings
        if (recoField.getConfidenceThreshold() < 0 || recoField.getConfidenceThreshold() > 100) {
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_CONF_THRESHOLD_INVALID).setMessageParams(new Object[]{recoField.getName()});
        }

        validateFieldRegex(recoField);

        validateFieldPostProc(recoField);

        validateDateTimeFormat(recoField);
    }


    /**
     * Checks, if field has unique name within provided scope, defined by RecoFieldList (for example, fields of form, columns of the table, etc). Throws exception in case name is not unique.
     *
     * @param recoField
     * @param recoFieldList
     * @throws RecoFormValidationException thrown in case provided RecoField has non-unique name within provided scope
     */
    static void validateFieldName(RecoField recoField, List<RecoField> recoFieldList) throws RecoFormValidationException {
        if (isBlank(recoField.getName())) {
            // Field name is required
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_NAME_REQUIRED);
        }
        for (RecoField other : recoFieldList) {
            if (recoField == other) {
                // This is same RecoField - skip check
                continue;
            }

            if (recoField.getName().equals(other.getName())) {
                // Field name is required
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_NAME_NOT_UNIQUE).setMessageParams(new Object[]{recoField.getName()});
            }
        }

        if (recoField.isTypeTable()) {
            for (RecoField col : recoField.getColumns()) {
                validateFieldName(col, recoField.getColumns());
            }
        } else if (recoField.isTypeOmr()) {
            for (RecoField opMark : recoField.getOpMarks()) {
                validateFieldName(opMark, recoField.getOpMarks());
            }
        }
    }


    /**
     * Checks, if field has unique tag within provided scope, defined by RecoFieldList (for example, fields of form, columns of the table, etc). Throws exception in case name is not unique.
     *
     * @param recoField
     * @param recoFieldList
     * @throws RecoFormValidationException if there was any errors during validation
     */
    static void validateFieldTags(RecoField recoField, List<RecoField> recoFieldList) throws RecoFormValidationException {
        if (recoField.isTypeTable()) {
            for (RecoField col : recoField.getColumns()) {
                validateFieldTags(col, recoField.getColumns());
            }
        } else if (recoField.isTypeOmr()) {
            for (RecoField opMark : recoField.getOpMarks()) {
                validateFieldTags(opMark, recoField.getOpMarks());
            }
        }

        if (recoField.getTags().isEmpty()) {
            // Allow fields to have no tags
            return;
        }
        for (RecoField otherRecoField : recoFieldList) {
            if (recoField == otherRecoField) {
                continue;
            }

            if (otherRecoField.getTags().isEmpty()) {
                // Allow fields to have no tags
                continue;
            }

            if (!Collections.disjoint(recoField.getTags(), otherRecoField.getTags())) {
                Set<String> duplicateTags = new HashSet<>(recoField.getTags());
                duplicateTags.retainAll(otherRecoField.getTags());
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_TAG_NOT_UNIQUE)
                        .setMessageParams(new Object[]{recoField.getName(), otherRecoField.getName(), duplicateTags.toString()});
            }
        }
    }


    /**
     * Checks, that provided date format(s) are valid by trying to create {@link SimpleDateFormat} for each of them
     *
     * @param recoField
     * @throws RecoFormValidationException if there was any errors during validation
     */
    static void validateDateTimeFormat(RecoField recoField) throws RecoFormValidationException {
        if (isBlank(recoField.getTextProp().getDateTimeFormat())) {
            return;
        }

        String regex[] = recoField.getTextProp().getDateTimeFormat().split("\\|");

        for (int i = 0; i < regex.length; i++) {
            try {
                new SimpleDateFormat(regex[i]);
            } catch (IllegalArgumentException ex) {
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_DATETIME_FORMAT_INVALID)
                        .setMessageParams(new Object[]{recoField.getName(), i, ex.getMessage()});
            }
        }
    }


    /**
     * Checks, if anchors of provided RecoField are valid.<br>
     * General validation rules:<br>
     * 1. If field has 'ImageBasedAnchors' property set to true - anchors should have no anchor text;<br>
     * 2. If field has 'ImageBasedAnchors' property set to false - all anchors should have text; <br>
     * 3. If main anchor is of regex type - provided regex should be valid;<br>
     * 4. If provided RecoField is table, it's anchor under table is required and of regex type - provided regex should be valid;
     *
     * @param recoField {@link RecoField}'s, whose anchors are to be validated
     * @throws RecoFormValidationException if there was any errors during validation
     */
    static void validateFieldAnchors(RecoField recoField) throws RecoFormValidationException {
        if (recoField.getProp().isAuto()) {
            // Do not validate anchors for auto-generated fields
            return;
        }

        // Validate main anchor
        if (isBlank(recoField.getAnchorText())) {
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_ANCHOR_TEXT_REQUIRED).setMessageParams(new Object[]{recoField.getName()});
        }
        if (recoField.getAnchorText().startsWith(PREFIX_REGEX)) {
            try {
                Pattern.compile(recoField.getAnchorText().substring(PREFIX_REGEX.length()));
            } catch (PatternSyntaxException pse) {
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_ANCHOR_REGEX_INVALID).setMessageParams(new Object[]{recoField.getName(), pse.getMessage()});
            }
        }

        // Validate anchor under table
        if (recoField.isTypeTable() && !recoField.getTableProp().isAnchorUnderTableDisabled()) {
            if (isBlank(recoField.getTableProp().getAnchorUnderTableText())) {
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_ANCHOR_UNDTBL_TEXT_REQUIRED).setMessageParams(new Object[]{recoField.getName()});
            } else if (recoField.getTableProp().getAnchorUnderTableText().startsWith(PREFIX_REGEX)) {
                try {
                    Pattern.compile(recoField.getTableProp().getAnchorUnderTableText().substring(PREFIX_REGEX.length()));
                } catch (PatternSyntaxException pse) {
                    throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_ANCHOR_UNDTBL_REGEX_INVALID)
                            .setMessageParams(new Object[]{recoField.getName(), pse.getMessage()});
                }
            }
        } else if (recoField.isTypeTextExtendable() && !recoField.getExtendableTextProp().isBottomAnchorDisabled()) {
            if (isBlank(recoField.getExtendableTextProp().getBottomAnchorText())) {
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_ANCHOR_UNDFLD_TEXT_REQUIRED).setMessageParams(new Object[]{recoField.getName()});
            } else if (recoField.getExtendableTextProp().getBottomAnchorText().startsWith(PREFIX_REGEX)) {
                try {
                    Pattern.compile(recoField.getExtendableTextProp().getBottomAnchorText().substring(PREFIX_REGEX.length()));
                } catch (PatternSyntaxException pse) {
                    throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_ANCHOR_UNDFLD_REGEX_INVALID)
                            .setMessageParams(new Object[]{recoField.getName(), pse.getMessage()});
                }
            }
        }
    }


    /**
     * Validates Regex for field, if provided, by compiling it into Pattern without execution. <br>
     * This checks only for general syntax errors and doesn't validate regex execution result. This behaviour should be checked with tester or reco diagnostics.
     *
     * @param recoField {@link RecoField}'s, whose regex is to be validated
     * @throws RecoFormValidationException if there was any errors during validation
     */
    protected static void validateFieldRegex(RecoField recoField) throws RecoFormValidationException {
        if (recoField.getTextProp().isUseRegex()) {
            if (isBlank(recoField.getTextProp().getRegex())) {
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_REGEX_BLANK).setMessageParams(new Object[]{recoField.getName()});
            } else if (recoField.getTextProp().getRegexType() == TextProperties.TYPE_BITAP) {
                // TODO: Add proper BITAP validation logic
                return;
            }

            // Check, if regex is valid by compiling it and contains required capturing group
            try {
                // For testing, if required named capturing group is present, pattern should be compiled and
                // successfully matched with some string, as if no matches found IllegalStateException will
                // be thrown by matcher.group(...) without checking, if such group is present. As long as there
                // is no native way to get list of all groups or check, if group present in pattern/matcher/predicate
                // we need to make whole regex optional - this way, any valid regex will stay valid and will match any string
                Pattern regex = Pattern.compile("(" + recoField.getTextProp().getRegex() + ")?");
                Matcher matcher = regex.matcher("");
                matcher.find();
                matcher.group(RecognitionService.REGEX_VALUE_GROUP_NAME);
            } catch (PatternSyntaxException pse) {
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_REGEX_INVALID).setMessageParams(new Object[]{recoField.getName(), pse.getMessage()});
            } catch (IllegalArgumentException iae) {
                throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_REGEX_MISSING_GROUP)
                        .setMessageParams(new Object[]{recoField.getName(), RecognitionService.REGEX_VALUE_GROUP_NAME});
            }
        }
        if (recoField.isTypeTable()) {
            recoField.getColumns().forEach(RecoFormService::validateFieldRegex);
        }
    }


    /**
     * Checks post-processing functions for field by compiling it (Engine.eval) without execution <br>
     * This checks only general syntax errors, while script can have logic errors (referencing arguments by wrong name, referencing undefined variables/fields, missing return statement, etc). <br>
     * Such logic errors can be checked only by executing post-processor script, and therefore require some input data, which cannot be obtained without user interaction. Therefore, such errors are
     * intended to be checked using tester or reco diagnostics.
     *
     * @param recoField {@link RecoField}'s, whose post-processing function is to be validated
     * @throws RecoFormValidationException if there was any errors during validation
     */
    void validateFieldPostProc(RecoField recoField) throws RecoFormValidationException {
        if (!recoField.getProp().isUsePostProc() || isBlank(recoField.getProp().getPostProc())) {
            return;
        }
        try (OutputStream os = new ByteArrayOutputStream()) {
            Map<String, Object> context = new HashMap<>();
            _recoPostProcessorService.validateFieldPostProcessor(recoField.getProp().getPostProcDecoded(), context, os);
        } catch (Throwable ex) {
            throw new RecoFormValidationException().setErrorCode(RecoFormValidationException.FIELD_POSTPROC_INVALID).setMessageParams(new Object[]{recoField.getName(), ex.getMessage()});
        }
    }


    /**
     * Checks, if RecoForm with same name is present in the system
     *
     * @param recoForm
     * @return
     */
    public boolean isRecoFormNameUnique(RecoForm recoForm) {
        RecoForm existingForm = _recoFormDao.findByName(recoForm.getName());
        return existingForm == null || Objects.equals(existingForm.getId(), recoForm.getId());
    }


    /**
     * Checks, if RecoForm with same name is present in the system
     *
     * @param recoForm
     * @param recoFormsList List of existing RecoForms to be used in check
     * @return
     */
    public boolean isRecoFormNameUnique(RecoForm recoForm, List<RecoForm> recoFormsList) {
        RecoForm existingForm = recoFormsList.stream().filter(form -> Objects.equals(form.getName(), recoForm.getName())).findFirst().orElse(null);
        return existingForm == null || Objects.equals(existingForm.getId(), recoForm.getId());
    }


    public DbFileDownloadStream getFile(DbFileRef fileRef) {
        return _recoFormDao.getFilesDao().getFileDownloadStream(fileRef);
    }
}
