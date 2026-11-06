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

import ai.eldoc.context.LocaleContext;
import ai.eldoc.dao.*;
import ai.eldoc.model.*;
import ai.eldoc.service.UrlService;
import ai.eldoc.util.FileUtils;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Files Manager Bean<br>
 * Provides functionality for managing files via UI<br>
 * This class actively uses per-RequestScope cache in order to lower number of requests to DB as due to JSF UI specifics and partial updates - number of
 * getter-methods invocations can be quite high
 */
@Named
@RequestScoped
public class WorkflowFilesController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowFilesController.class);

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private WorkflowTaskDao _workflowTaskDao;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private UrlService _urlService;

    private final Map<String, List<DbFile>> _filesCacheMap = new ConcurrentHashMap<>();
    private final Map<String, List<DbFile>> _filesLatestRevCacheMap = new ConcurrentHashMap<>();
    private final Map<String, String> _fileNamesCacheMap = new ConcurrentHashMap<>();


    //
    // Action Methods (Buttons) -------------------------------------------------------------------
    //


    /**
     * Deletes file attachment from the document and updates document in the DB
     *
     * @param dataSource
     * @param dbFile
     */
    public void btnDeleteFile(BasicModel dataSource, DbFile dbFile) {
        btnDeleteFileInner(dataSource, dbFile.getDbFileRef());
    }


    /**
     * Deletes file attachment from the document and updates document in the DB
     *
     * @param dataSource
     * @param dbFileRef
     */
    public void btnDeleteFile(BasicModel dataSource, DbFileRef dbFileRef) {
        btnDeleteFileInner(dataSource, dbFileRef);
    }


    /**
     * Deletes file attachment from the document and updates the document in the DB
     *
     * @param dataSource
     * @param dbFileRef
     */
    private void btnDeleteFileInner(BasicModel dataSource, DbFileRef dbFileRef) {
        if (dataSource instanceof Workflow workflow) {
            // Workflow attachments may have file-revisions, as such we need to locate a root revision to properly handle file deletion
            // from Workflow as it stores only the root revision dbFileRef
            DbFileRef rootDbFileRef = _workflowDao.getFilesDao().getFileRootRev(dbFileRef);

            // 1: Delete reference to attachment from the document
            workflow.removeFile(rootDbFileRef);

            // 2: In case of Workflow - attachment can be actually template, so clear template as well
            if (workflow.isTemplate(rootDbFileRef)) {
                workflow
                        .getTemplates()
                        .removeIf(templateData -> templateData.getDbFileRef().getCollection().equals(rootDbFileRef.getCollection())
                                && templateData.getDbFileRef().getId().equals(rootDbFileRef.getId()));
            }

            // 3: Delete attachment from DB
            _workflowDao.getFilesDao().deleteFile(null, dbFileRef);
            _workflowDao.updateOrCreate(null, workflow);
        } else if (dataSource instanceof WorkflowTask wfTask) {
            // 1: Delete reference to attachment from the document
            wfTask.removeFile(dbFileRef);

            // 2: Delete attachment from DB
            _workflowTaskDao.getFilesDao().deleteFile(null, dbFileRef);
            _workflowTaskDao.updateOrCreate(null, wfTask);
        }
        resetCache();
    }


    /**
     * Resets attachment - currently used with Templates: deletes all revisions of the template
     * and leaves only the initial version of the file attachment
     *
     * @param dataSource
     * @param dbFile
     */
    public void btnResetFile(BasicModel dataSource, DbFile dbFile) {
        btnResetFileInner(dataSource, dbFile.getDbFileRef());
    }


    /**
     * Resets attachment - currently used with Templates: deletes all revisions of the template
     * and leaves only the initial version of the file attachment
     *
     * @param dataSource
     * @param dbFileRef
     */
    public void btnResetFile(BasicModel dataSource, DbFileRef dbFileRef) {
        btnResetFileInner(dataSource, dbFileRef);
    }


    /**
     * Resets attachment - currently used with Templates: deletes all revisions of the template
     * and leaves only the initial version of the file attachment
     *
     * @param dataSource
     * @param dbFileRef
     */
    private void btnResetFileInner(BasicModel dataSource, DbFileRef dbFileRef) {
        try {
            // Find all revisions from root to latest
            FileAwareDao fileAwareDao = DaoUtils.getFileAwareDao(dbFileRef);
            if (!(fileAwareDao instanceof FileRevisionAwareDao)) {
                return; // Not a file-revision aware DAO, nothing to do
            }

            FileRevisionAwareDao fileRevAwareDao = (FileRevisionAwareDao) fileAwareDao;

            List<DbFileRef> allRevisionsChain = fileRevAwareDao.getFilesDao().getFileRevisions(dbFileRef);

            // remove root revision - it will be the first element
            allRevisionsChain.remove(0);

            if (!allRevisionsChain.isEmpty()) {
                // reverse collection
                Collections.reverse(allRevisionsChain);
                // delete all revisions except (root one) starting from the lates revision
                allRevisionsChain.forEach(dbFileRefItem -> fileRevAwareDao.getFilesDao().deleteFile(null, dbFileRefItem, false));
            }
        } catch (Exception e) {
            LOG.error("Error processing: {}", dbFileRef, e);
        }
        resetCache();
    }


    //
    // Files Getters ------------------------------------------------------------------------------
    //


    /**
     * Returns files for the given dataSource<br>
     * NOTE: lookup results are cached for the request-scope
     *
     * @param dataSource dataSource, files are retrieved from the standard fields
     * @return
     */
    public List<DbFile> getFiles(BasicModel dataSource) {
        if (dataSource instanceof Workflow workflow) {

            String cacheId = "workflow-" + workflow.getId();
            if (_filesCacheMap.get(cacheId) == null) {
                _filesCacheMap.put(cacheId, _workflowDao.getFilesDao().getFiles(null, workflow.getFiles()));
            }
            return _filesCacheMap.get(cacheId);

        } else if (dataSource instanceof WorkflowTask wfTask) {

            String cacheId = "docResponse-" + wfTask.getId();
            if (_filesCacheMap.get(cacheId) == null) {
                _filesCacheMap.put(cacheId, _workflowTaskDao.getFilesDao().getFiles(null, wfTask.getFiles()));
            }
            return _filesCacheMap.get(cacheId);

        } else {
            LOG.warn("Model not known: {}", dataSource);
            return new ArrayList<>();
        }
    }


    /**
     * Returns files for the given dataSource<br>
     * Performs lookup for latest revisions of the given files<br>
     * NOTE: lookup results are cached for the request-scope
     *
     * @param dataSource dataSource, files are retrieved from the standard fields
     * @return
     */
    public List<DbFile> getFilesLatestRev(BasicModel dataSource) {
        if (dataSource instanceof Workflow workflow) {

            if (_filesLatestRevCacheMap.get(dataSource.getId()) == null) {
                _filesLatestRevCacheMap.put(dataSource.getId(), _workflowDao.getFilesDao().getFilesLatestRev(workflow.getFiles()));
            }
            return _filesLatestRevCacheMap.get(dataSource.getId());

        } else {
            LOG.warn("Model not known: {}", dataSource);
            return new ArrayList<>();
        }
    }


    /**
     * Resets cache of files per dataSources.<br>
     * You need to do it in order to reflect uploaded or downloaded files in the FileDownload UI control
     */
    public void resetCache() {
        _filesCacheMap.clear();
        _filesLatestRevCacheMap.clear();
    }


    //
    // Files Utilitlity Methods -------------------------------------------------------------------
    //


    /**
     * Returns file icon
     *
     * @param dbFile
     * @return
     */
    public String getIcon(DbFile dbFile) {
        String fileIcon = "fa-solid fa-fw fa-file"; // default

        String fileName = dbFile.getFileName() != null ? dbFile.getFileName().toLowerCase() : "";

        if (fileName.endsWith(".doc") || fileName.endsWith(".docx") || fileName.endsWith(".odt") || fileName.endsWith(".rtf")) {

            fileIcon = "fa-regular fa-fw fa-file-word";

        } else if (fileName.endsWith(".odp") || fileName.endsWith(".ppt") || fileName.endsWith(".pptx") || fileName.endsWith(".sxi")) {

            fileIcon = "fa-regular fa-fw fa-file-powerpoint";

        } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx") || fileName.endsWith(".ods") || fileName.endsWith(".sxc")
                || fileName.endsWith(".csv")) {

            fileIcon = "fa-regular fa-fw fa-file-excel";

        } else if (fileName.endsWith(".jpeg") || fileName.endsWith(".jpg") || fileName.endsWith(".jpe") || fileName.endsWith(".gif")
                || fileName.endsWith(".png") || fileName.endsWith(".svg") || fileName.endsWith(".bmp") || fileName.endsWith(".ico")
                || fileName.endsWith(".tif") || fileName.endsWith(".tiff")) {

            fileIcon = "fa-regular fa-fw fa-file-image";

        } else if (fileName.endsWith(".pdf")) {

            fileIcon = "fa-regular fa-fw fa-file-pdf";

        }
        return fileIcon;
    }


    /**
     * Returns file name & size name string for UI<br>
     * Note: this methods makes a request to DB in order to retrieve DbFile from DB.
     * When DbFile instance in available always use another method: {@link #getFileNameSizeLabel(DbFile)}}
     *
     * @param dbFileRef
     * @return
     */
    public String getFileNameSizeLabel(DbFileRef dbFileRef) {
        return getFileNameSizeLabel(DaoUtils.getFile(dbFileRef));
    }


    /**
     * Returns file name & size name string for UI
     *
     * @param dbFile
     * @return
     */
    public String getFileNameSizeLabel(DbFile dbFile) {
        try {
            String fileName;
            if (dbFile.isFileNotFound()) {
                fileName = _localeContext.getLabel(dbFile.getFileName()); // In this case labelId already stored in the fileName field
            } else {
                fileName = dbFile.getFileName();
            }
            return FileUtils.getFileDownloadName(fileName, dbFile.getLength());
        } catch (Exception e) {
            LOG.error("Error preparing filename for UI: {}", dbFile, e);
            return "ERR";
        }
    }


    /**
     * Returns file href
     *
     * @param dbFileRef
     * @return
     */
    public String getFileHref(DbFileRef dbFileRef) {
        return _urlService.toFileHref(dbFileRef);
    }


    /**
     * Returns file href
     *
     * @param dbFile
     * @return
     */
    public String getFileHref(DbFile dbFile) {
        return getFileHref(dbFile.getDbFileRef());
    }


    /**
     * Checks if the given file (assuming template) supports computedFormFields<br>
     * Currently text replacement supported only in Text Document formats, like docx
     *
     * @return true - if the file supports fields replacement, otherwise false
     */
    public boolean isComputeFormFieldsSupported(TemplateRef templateRef) {
        try {
            if (!_fileNamesCacheMap.containsKey(templateRef.getDbFileRef().toString())) {
                // Caching resolved filenames for better performance in scope of the current request
                _fileNamesCacheMap.put(templateRef.getDbFileRef().toString(), DaoUtils.getFile(templateRef.getDbFileRef()).getFileName());
            }
            String filename = _fileNamesCacheMap.get(templateRef.getDbFileRef().toString());
            return Strings.CI.endsWith(filename, ".doc")
                    || Strings.CI.endsWith(filename, ".docx")
                    || Strings.CI.endsWith(filename, ".odt")
                    || Strings.CI.endsWith(filename, ".rtf");
        } catch (Exception e) {
            LOG.error("Error processing: {}", templateRef, e);
            return false;
        }
    }

}
