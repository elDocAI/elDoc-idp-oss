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
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.dao.*;
import ai.eldoc.model.*;
import ai.eldoc.model.filemeta.TextIndexAwareFileMeta;
import ai.eldoc.model.filemeta.TextIndexFileMeta;
import ai.eldoc.reco.TextIndexFileProcessor;
import ai.eldoc.util.MimeType;
import jakarta.ejb.*;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static jakarta.ejb.LockType.READ;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Files Preprocessing Service
 */
@Singleton
@Lock(READ)
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@TransactionManagement(TransactionManagementType.BEAN)
public class TextIndexingService {

    private static final String ELDOC_FILE_PREPROCESSING_LANGS_JVM_OPTION = "ai.eldoc.files.preprocessing.ocr.defaultLang";
    private static final String ELDOC_FILE_PREPROCESSING_LANGS_SYSTEM_VAR = "ELDOC_FILES_PREPROCESSING_OCR_DEFAULT_LANG";

    @Inject
    private RecoDataFilesDao _recoDataFilesDao;

    @Inject
    private WorkflowFilesDao _workflowFilesDao;


    //
    // Methods ------------------------------------------------------------------------------------
    //


    public Pair<List<String>, Integer> extractText(String fileName, InputStream is, boolean enableVlOcr, AiRequestContext requestContext) throws IOException {
        return extractText(fileName, is, 0, -1, enableVlOcr, requestContext);
    }


    public Pair<List<String>, Integer> extractText(String fileName, InputStream is, int pageStart, int pageEnd, boolean enableVlOcr, AiRequestContext requestContext) throws IOException {
        try (TextIndexFileProcessor textIndexFileProcessor = new TextIndexFileProcessor()) {
            // Define preprocessing properties
            textIndexFileProcessor.setLanguages(getLangs());

            // Pass AI request context, if provided - it can be used in case AI (VL) LLM Model will be used for performing OCR
            if (enableVlOcr) {
                textIndexFileProcessor.setProperty(TextIndexFileProcessor.PROP_AI_ENABLED, true);
            }
            textIndexFileProcessor.setBillingRequestContext(requestContext);

            // Get and upload preprocessed file
            if (TextIndexFileProcessor.isFileSupported(fileName)) {
                textIndexFileProcessor.loadFile(fileName, is, pageStart, pageEnd);
            }
            Pair<List<String>, Integer> preprocessingResult = textIndexFileProcessor.preprocessAndGetText();
            int textIndexType = textIndexFileProcessor.isUseAiOcrEngine() ? TextIndexAwareFileMeta.TEXT_INDEX_TYPE_VL : TextIndexAwareFileMeta.TEXT_INDEX_TYPE_OCR;

            if (preprocessingResult.getRight() != 0) {
                LogExt log = new LogExt().setLogAccount(DirectoryConstants.ELDOC_ROBOT).setCount(preprocessingResult.getRight());
                log.setEntityType(Log.ENTITY_TYPE_FILE);
                // IMPORTANT: this is a special event that is used for Licensing purposes. Be considerate in case you are touching it
                LogHelper.saveOrIncrementDailyCount("id00409", null, null, log);
            }

            return Pair.of(preprocessingResult.getLeft(), textIndexType);
        }
    }


    public static String buildTextIndex(List<String> perPageFileText) {
        StringBuilder fileTextBuilder = new StringBuilder();
        for (int pageIdx = 0; pageIdx < perPageFileText.size(); pageIdx++) {
            fileTextBuilder.append(System.lineSeparator())
                    .append("### Page ").append(pageIdx + 1).append(System.lineSeparator())
                    .append(perPageFileText.get(pageIdx).trim());
        }
        return fileTextBuilder.toString();
    }


    public DbFileRef uploadTextIndexFile(DbFile sourceFile, List<String> fileTextIndex, int textIndexType, boolean updateSourceMeta) {
        // Transfer folder ID - should be sourced from Root Revision
        String folderId = null;
        TextIndexFileMeta textIndexFileMeta = buildTextIndexMeta(fileTextIndex, folderId);

        StringBuilder textIndexContentBuilder = new StringBuilder();
        for (String pageContent : fileTextIndex) {
            textIndexContentBuilder.append(pageContent.trim()).append(System.lineSeparator());
        }

        return uploadTextIndexFile(sourceFile, textIndexContentBuilder.toString(), textIndexFileMeta, textIndexType, updateSourceMeta);
    }


    public DbFileRef uploadTextIndexFile(DbFile sourceFile, String textIndexFileContent, TextIndexFileMeta textIndexFileMeta, int textIndexType, boolean updateSourceMeta) {
        textIndexFileMeta.setRefId(sourceFile.getId());

        FilesDao filesDao = getFilesDao(sourceFile.getDbFileRef());
        List<DbFileRef> existingTextIndexRef = filesDao.getFileRefs(null, sourceFile.getId(), TextIndexFileMeta.TYPE_TEXT_INDEX);
        if (!existingTextIndexRef.isEmpty()) {
            // There are some previous Text Index files in DB, need to remove them
            for (DbFileRef oldIndexRef : existingTextIndexRef) {
                filesDao.deleteFile(null, oldIndexRef);
            }
        }

        String fileName = sourceFile.getFileName() + getFilenameSuffixByFileType(TextIndexFileMeta.TYPE_TEXT_INDEX, textIndexType);
        String contentType = getContentTypeByFileType(TextIndexFileMeta.TYPE_TEXT_INDEX, textIndexType);

        DbFileRef textIndexFileRef = filesDao.uploadFromByteArray(fileName, textIndexFileContent.getBytes(StandardCharsets.UTF_8), contentType, textIndexFileMeta.toMetadata());
        filesDao.updateFileMeta(null, textIndexFileRef, textIndexFileMeta.toMetadata());

        if (updateSourceMeta) {
            sourceFile.getMetadata().put(TextIndexAwareFileMeta._TEXT_INDEX_STATE, TextIndexAwareFileMeta.TEXT_INDEX_STATE_READY);
            sourceFile.getMetadata().put(TextIndexAwareFileMeta._TEXT_INDEX_TYPE, textIndexType);
            filesDao.updateFileMeta(null, sourceFile.getDbFileRef(), sourceFile.getMetadata());
        }

        return textIndexFileRef;
    }


    public TextIndexFileMeta buildTextIndexMeta(List<String> fileTextIndex, String folderId) {
        TextIndexFileMeta textIndexFileMeta = new TextIndexFileMeta();
        textIndexFileMeta.setType(TextIndexFileMeta.TYPE_TEXT_INDEX);
        if (folderId != null) {
            // Required field, as it is used to filter content during query to indexing store
            textIndexFileMeta.setFolderId(folderId);
        }
        int nextPageStart = 0;
        for (String pageContent : fileTextIndex) {
            byte[] pageContentBytes = pageContent.trim().concat(System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            textIndexFileMeta.getPageOffsets().add(nextPageStart);
            nextPageStart += pageContentBytes.length;
        }
        return textIndexFileMeta;
    }


    private FilesDao getFilesDao(DbFileRef fileRef) {
        if (isRecoDataFile(fileRef)) {
            return _recoDataFilesDao;
        } else if (isWorkflowTenantFile(fileRef)) {
            return _workflowFilesDao;
        }

        throw new ElDocRuntimeException("File not supported: " + fileRef);
    }


    private DbFileDownloadStream getFileDownloadStream(DbFileRef fileRef) {
        if (isRecoDataFile(fileRef)) {
            return _recoDataFilesDao.getFileDownloadStream(fileRef);
        } else if (isWorkflowTenantFile(fileRef)) {
            return _workflowFilesDao.getFileDownloadStream(fileRef);
        }

        throw new ElDocRuntimeException("File not supported: " + fileRef);
    }


    public String getFileTextIndexContent(DbFileRef fileRef, int pageStart, int pageEnd, boolean withPageSeparator) throws IOException {
        DbFile fileTextIndex = getTextIndexFile(fileRef);
        if (fileTextIndex == null) {
            return null;
        }
        TextIndexFileMeta fileMeta = TextIndexFileMeta.fromJson(fileTextIndex.getMetadataAsJson());

        if (pageEnd == -1) {
            pageEnd = fileMeta.getPageOffsets().size();
        }

        try (InputStream fis = getFileDownloadStream(fileTextIndex.getDbFileRef())) {
            if (!fileMeta.getPageOffsets().isEmpty()) {
                // We need to add page indexes between pages for AI/LLM reference, so process file per page and then merge
                int pagesCount = fileMeta.getPageOffsets().size();

                List<String> pagesContent = new ArrayList<>();
                long firstRequestedPageStart = fileMeta.getPageOffsets().get(pageStart);
                fis.skipNBytes(firstRequestedPageStart);
                for (int pageIdx = pageStart; pageIdx < pageEnd; pageIdx++) {
                    long pageOffsetStart = fileMeta.getPageOffsets().get(pageIdx);
                    long pageOffsetEnd = (pageIdx == pagesCount - 1) ? fileTextIndex.getLength() : fileMeta.getPageOffsets().get(pageIdx + 1);
                    byte[] pageContentBytes = fis.readNBytes((int) (pageOffsetEnd - pageOffsetStart));
                    pagesContent.add(new String(pageContentBytes, StandardCharsets.UTF_8));
                }

                if (withPageSeparator) {
                    return buildTextIndex(pagesContent);
                } else {
                    return String.join(System.lineSeparator(), pagesContent);
                }
            } else {
                return new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }


    private DbFile getTextIndexFile(DbFileRef fileRef) {
        List<DbFileRef> textIndexRefsList = getFilesDao(fileRef).getFileRefs(null, fileRef.getId(), TextIndexFileMeta.TYPE_TEXT_INDEX);
        if (textIndexRefsList.isEmpty()) {
            return null;
        }

        return getFilesDao(textIndexRefsList.getFirst()).getFile(null, textIndexRefsList.getFirst());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    public static String getFilenameSuffixByFileType(int fileType, int textIndexType) {
        if (fileType == TextIndexFileMeta.TYPE_TEXT_INDEX) {
            return switch (textIndexType) {
                case TextIndexAwareFileMeta.TEXT_INDEX_TYPE_VL -> ".md";
                default -> ".txt";
            };
        }
        return "";
    }


    public static String getContentTypeByFileType(int fileType, int textIndexType) {
        if (fileType == TextIndexFileMeta.TYPE_TEXT_INDEX) {
            return switch (textIndexType) {
                case TextIndexAwareFileMeta.TEXT_INDEX_TYPE_VL -> MimeType.TEXT_MARKDOWN;
                default -> MimeType.TEXT_PLAIN;
            };
        }
        return MimeType.TEXT_PLAIN;
    }


    private boolean isRecoDataFile(DbFileRef fileRef) {
        return fileRef.getCollection().equals(RecoDataFilesDaoMongoImpl.BUCKET_NAME);
    }


    private boolean isWorkflowTenantFile(DbFileRef fileRef) {
        return fileRef.getCollection().equals(WorkflowFilesDaoTenantMongoImpl.BUCKET_NAME);
    }


    private static List<String> getLangs() {
        String langs = System.getProperty(ELDOC_FILE_PREPROCESSING_LANGS_JVM_OPTION);
        if (isBlank(langs)) {
            langs = System.getenv(ELDOC_FILE_PREPROCESSING_LANGS_SYSTEM_VAR);
        }
        return isBlank(langs) ? List.of() : List.of(langs.split(","));
    }


    public static TextIndexingService getInstance() {
        return CDI.current().select(TextIndexingService.class).get();
    }

}
