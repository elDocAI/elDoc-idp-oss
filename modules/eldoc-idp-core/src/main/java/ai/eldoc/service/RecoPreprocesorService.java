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

import ai.eldoc.TenantContext;
import ai.eldoc.cluster.ClusterManager;
import ai.eldoc.constants.DirectoryConstants;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.dao.WorkflowDao;
import ai.eldoc.exceptions.ConcurrentWriteException;
import ai.eldoc.exceptions.TimeoutException;
import ai.eldoc.model.*;
import ai.eldoc.model.reco.RecoFileMeta;
import ai.eldoc.reco.RecoFileProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.*;
import jakarta.enterprise.concurrent.ManagedThreadFactory;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static jakarta.ejb.LockType.READ;


/**
 * RecoForms (RecoFiles) Preprocessing Service
 * <p>
 * IMPORTANT NOTE: Currently it is designed only for working with one single client (RecoPreprocesorWorker)
 */
@Singleton
@Lock(READ)
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
@TransactionManagement(TransactionManagementType.BEAN)
public class RecoPreprocesorService {

    private static final Logger LOG = LoggerFactory.getLogger(RecoPreprocesorService.class);

    public static final String ELDOC_JVM_OPTION_IDP_PREPROCESSING_THREADS = "ai.eldoc.idp.preprocessing.threads";

    @Resource
    private SessionContext _ctx;

    @Resource
    private ManagedThreadFactory _threadFactory;

    @Inject
    private DirectoryService _directoryService;

    @Inject
    private AppConfigService _appConfigService;

    @Inject
    private WorkflowDao _workflowDao;

    @Inject
    private WorkflowService _workflowService;

    @Inject
    private ClusterManager _clusterManager;

    @Inject
    private ConfigContext _configContext;

    private static final int MAX_PREPROCESSING_THREADS = Integer.getInteger(ELDOC_JVM_OPTION_IDP_PREPROCESSING_THREADS, 4);

    private final Map<String, Queue<Workflow>> _recoPreprocessingQueueByTenant;
    private final Map<String, Map<String, Future<String>>> _currentProcessingItemsByTenant; // Common queue for all tenants
    private final Map<String, AtomicBoolean> _busyByTenant;
    private final Map<String, Boolean> _firstLaunchInitByTenant;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public RecoPreprocesorService() {
        _recoPreprocessingQueueByTenant = new ConcurrentHashMap<>();
        _currentProcessingItemsByTenant = new ConcurrentHashMap<>();
        _busyByTenant = new ConcurrentHashMap<>();
        _firstLaunchInitByTenant = new ConcurrentHashMap<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        _appConfigService.getTenantIdSet().forEach(tenantId -> {
            initForTenantId(tenantId);
        });
    }


    /**
     * Inits service (lazy) for the given tenantId
     *
     * @param tenantId
     */
    public void initForTenantId(String tenantId) {
        if (!_recoPreprocessingQueueByTenant.containsKey(tenantId)) {
            _recoPreprocessingQueueByTenant.put(tenantId, new ConcurrentLinkedQueue<>());
        }
        if (!_currentProcessingItemsByTenant.containsKey(tenantId)) {
            _currentProcessingItemsByTenant.put(tenantId, new ConcurrentHashMap<>(MAX_PREPROCESSING_THREADS));
        }
        if (!_busyByTenant.containsKey(tenantId)) {
            _busyByTenant.put(tenantId, new AtomicBoolean(false));
        }
        if (!_firstLaunchInitByTenant.containsKey(tenantId)) {
            _firstLaunchInitByTenant.put(tenantId, false);
        }
    }


    /**
     * Add new item to the queue
     *
     * @param queueItem {@link Workflow}, that needs to be pre-processed
     * @return Whether the item was added to the queue or not
     */
    public boolean addQueueItem(Workflow queueItem) {
        return _recoPreprocessingQueueByTenant.get(TenantContext.getCurrentTenantId()).add(queueItem);
    }


    /**
     * Triggers queue processing for all available tenants in separate threads
     */
    public void processQueue() {
        _appConfigService.getTenantIdSet().forEach(tenantId -> {
            if (!_busyByTenant.containsKey(tenantId)) {
                // TODO: _busyByTenant.containsKey(tenantId) is temporary workaround for the scenarious when new tenant is added during runtime by CloudManager,
                // but still not initialized and added to this map (but already present in the _appConfigService.getTenantIdSet())
                // See usages of the initForTenantId(String) in the CloudManagerService class
                LOG.debug("Pre-processing queue cannot run for not initialized tenant: {}", tenantId);
                return;
            }
            if (!_busyByTenant.get(tenantId).compareAndSet(false, true)) {
                LOG.debug("Pre-processing queue already running, skipping request to trigger launch...");
                return;
            }
            Thread thread = _threadFactory.newThread(() -> {
                TenantContext.setCurrentTenant(tenantId);
                processQueueByTenant(tenantId);
            });
            thread.start();
        });
    }


    /**
     * Triggers queue processing for a specific tenant
     *
     * @param tenantId
     */
    private void processQueueByTenant(String tenantId) {
        try {
            // First, we need to make sure that we properly initialized after possible server restart
            firstLaunchInit(tenantId);

            // Then initiate queue processing in loop until we have items in queue or service still was processing some items
            while (hasNext() || !_currentProcessingItemsByTenant.get(tenantId).isEmpty()) {

                if (_currentProcessingItemsByTenant.get(tenantId).isEmpty() && !_configContext.isIdpPreProcessingTimerEnabled()) {
                    // Preprocessing schedule is disabled, and all items in work are finished processing - exiting
                    break;
                }

                if (!_currentProcessingItemsByTenant.get(tenantId).isEmpty()) {
                    // We need to re-check currently processing queue and free it if possible
                    Iterator<Map.Entry<String, Future<String>>> entryIt = _currentProcessingItemsByTenant.get(tenantId).entrySet().iterator();
                    while (entryIt.hasNext()) {
                        Map.Entry<String, Future<String>> entry = entryIt.next();
                        if (entry.getValue().isDone()) {
                            entryIt.remove();
                            try {
                                LOG.trace("RecoWorkflow[{}] preprocessing completed with result [{}]", entry.getKey(), entry.getValue().get());
                            } catch (ExecutionException | InterruptedException ex) {
                                LOG.debug("RecoWorkflow[{}] preprocessing failed: {}", entry.getKey(), ex.toString());
                            }
                        }
                    }
                }

                if (_currentProcessingItemsByTenant.get(tenantId).size() < MAX_PREPROCESSING_THREADS && hasNext() && _configContext.isIdpPreProcessingTimerEnabled()) {
                    // In case we have available slots and also queueing items - add them to the processing (also in case we still have quota for IDP documents)
                    Workflow nextItem = next();
                    Future<String> prepropFuture = _ctx.getBusinessObject(RecoPreprocesorService.class).preprocessQueueItem(nextItem, tenantId, false, false);
                    _currentProcessingItemsByTenant.get(tenantId).put(nextItem.getId(), prepropFuture);
                }

                LOG.trace("Pre-processing queue status [{}/{}]: {}",
                        _currentProcessingItemsByTenant.get(tenantId).size(),
                        MAX_PREPROCESSING_THREADS,
                        new ArrayList<>(_currentProcessingItemsByTenant.get(tenantId).keySet())
                );

                threadSleep(500);
            }
        } catch (Exception e) {
            LogHelper.setEvent("id00103", e, e.toString(), null);
            LOG.error("Error processing queue", e);
        } finally {
            _busyByTenant.get(tenantId).set(false);
        }
    }


    @Asynchronous
    public Future<String> preprocessQueueItem(Workflow recoDocQueueItem, String tenantId, boolean debugMode, boolean embedPreprocessedImages) {
        TenantContext.setCurrentTenant(tenantId);
        Workflow recoWF = null;
        LogExt logExt = null;
        DbFileRef preprocessedFileDbRef = null;
        try {
            // Obtain fresh copy from DB to make sure it is still present and was not deleted
            recoWF = _workflowDao.find(recoDocQueueItem.getId(), _directoryService.getAclContext(DirectoryConstants.ELDOC_RECO_BOT));

            if (recoWF == null) {
                LOG.warn("RecoWorkflow[{}] was not found by Reco Preprocesor Worker Service (possible reason: document was deleted before processing)", recoDocQueueItem.getId());
                return new AsyncResult<>(Boolean.FALSE.toString());
            }

            // Initiating logging
            logExt = new LogExt(recoWF).setLogAccount(DirectoryConstants.ELDOC_RECO_BOT);

            // Load source file
            DbFile sourceFile = _workflowDao.getFilesDao().getFile(null, recoWF.getFileByTag(Workflow.FILE_TAG.IDP_SOURCE.name()));
            if (sourceFile == null) {
                LogHelper.setEvent("id00090", null, null, logExt);
                return new AsyncResult<>(Boolean.FALSE.toString());
            }

            // Make sure that this RecoWorkflow doesn't have a preprocessed file
            if (recoWF.getFileByTag(Workflow.FILE_TAG.IDP_PREPROCESSED.name()) != null) {
                LogHelper.setEvent("id00108", null, null, logExt);
                return new AsyncResult<>(Boolean.FALSE.toString());
            }

            // IMPORTANT: this is a special event that is used for Licensing purposes. Be considerate in case you are touching it
            LogHelper.setEvent("id00089",
                    null,
                    null,
                    new LogExt(recoWF).setLogAccount(DirectoryConstants.ELDOC_RECO_BOT).setCount(RecoFileMeta.fromJson(sourceFile.getMetadataAsJson()).getPageCount())
            );

            RecoFileMeta sourceRecoFileMeta = RecoFileMeta.fromJson(sourceFile.getMetadataAsJson());

            // Preprocess and store preprocessed file
            try (DbFileDownloadStream downloadStream = _workflowDao.getFilesDao().getFileDownloadStream(sourceFile.getDbFileRef()); RecoFileProcessor recoFileProcessor = new RecoFileProcessor(sourceRecoFileMeta);) {

                // Define preprocessing properties
                recoFileProcessor.setLanguages(sourceRecoFileMeta.getLangs());
                recoFileProcessor.setForcePreprocessing(sourceRecoFileMeta.isForcePreprop());

                if (embedPreprocessedImages) {
                    recoFileProcessor.setDebugMode(true);
                }

                // Get and upload preprocessed file
                recoFileProcessor.loadFile(downloadStream.getDbFile().getFileName(), downloadStream);
                recoFileProcessor.preprocess();

                List<String> fileTags = List.of(Workflow.FILE_TAG.IDP_PREPROCESSED.name());

                RecoFileMeta preprocessedFileMeta = recoFileProcessor.getPreprocessedMeta();
                preprocessedFileMeta.setType(RecoFileMeta.TYPE_DEFAULT);
                preprocessedFileMeta.setTags(fileTags);
                preprocessedFileDbRef = _workflowDao.getFilesDao().uploadFromByteArray(
                        recoFileProcessor.getPreprocessedFileName(),
                        recoFileProcessor.getPreprocessedFileAsByteArray(),
                        recoFileProcessor.getPreprocessedFileContentType(),
                        preprocessedFileMeta.toMetadata());
                preprocessedFileDbRef.setTags(fileTags);
                recoWF.getFiles().add(preprocessedFileDbRef);

                // Update RecoFileMeta for the Source File
                _workflowDao.getFilesDao().updateFileMeta(null, sourceFile.getDbFileRef(), recoFileProcessor.getSourceMeta().toMetadata());
            }

            // Save Workflow (RecoWorkflow) - still saved as Draft
            if (_workflowDao.updateOrCreate(null, recoWF)) {
                LogHelper.setEvent("id00091", null, null, logExt);

                if (debugMode) {
                    // In debug mode we should terminate the doc
                    _workflowService.actionTerminate(recoWF, DirectoryConstants.ELDOC_RECO_BOT, null, null);
                } else {
                    // Send to execution automatically
                    AclContext aclCtx = new AclContext(recoWF.getAuthor(), recoWF.getAuthorBy(), null, null, null, null);
                    _workflowService.actionToExecState(recoWF, aclCtx, null, null);
                }

                return new AsyncResult<>(Boolean.TRUE.toString());
            } else {
                LogHelper.setEvent("id00098", null, null, logExt);
                return new AsyncResult<>(Boolean.FALSE.toString());
            }
        } catch (TimeoutException tex1) {
            LogHelper.setEvent("id00099", tex1, tex1.getMessage(), logExt);
        } catch (ConcurrentWriteException cex1) {
            LogHelper.setEvent("id00101", cex1, null, logExt);
            if (preprocessedFileDbRef != null) {
                // Try to delete created file in the DB
                _workflowDao.getFilesDao().deleteFile(null, preprocessedFileDbRef);
            }
            return new AsyncResult<>(Boolean.FALSE.toString());
        } catch (Throwable ex1) { // catch Throwable in order to cover both Exception and Error cases (Error thrown when out-of-memory happens)
            // This also may catch exception like:
            // org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException: Cannot decrypt PDF, the password is incorrect

            LOG.error("RecoWorkflow[{}] pre-processing error", recoDocQueueItem.getId(), ex1);
            LogHelper.setEvent("id00102", ex1, null, logExt);
        }

        // If we get here - means we already have some errors with document processing, as such - trying to terminate it gracefully
        try {
            if (_workflowDao.isValid(recoWF)) {
                _workflowService.actionTerminate(recoWF, DirectoryConstants.ELDOC_RECO_BOT, null, null);
            }
        } catch (Exception e) {
            LOG.debug("RecoWorkflow[{}] document not valid or can not be cancelled: " + e.toString(), recoDocQueueItem.getId());
        }

        return new AsyncResult<>(Boolean.FALSE.toString());
    }


    /**
     * Performs initialization on first launch
     * This method helps to pick-up Preprocessing Queue after server restart
     */
    private void firstLaunchInit(String tenantId) {
        if (_firstLaunchInitByTenant.get(tenantId)) {
            // In case init on first launch was already performed - nothing to do
            return;
        }

        // Check that initialization is performed on the primary node in the Cluster. In case not - skip initialization for the non-primary nodes
        if (!_clusterManager.isPrimaryNode()) {
            LOG.debug("Reco Pre-Processing Queue: not a primary node, initialization skipped on this node");
            _firstLaunchInitByTenant.put(tenantId, true);
            return;
        }

        LOG.info("Reco Pre-Processing Queue: performing initialization on first launch...");
        AtomicInteger counter = new AtomicInteger();

        // In order to avoid collision (when document was added to queue already just after restart and before this initialization) - we do add
        // documents one-by-one and checking that they are not already present in the queue
        _workflowDao.findAllPendingPreprocessing().forEach(recoWF -> {
            if (!_recoPreprocessingQueueByTenant.get(tenantId).contains(recoWF)) {
                _recoPreprocessingQueueByTenant.get(tenantId).add(recoWF);
                counter.incrementAndGet();
                LOG.trace("RecoWorkflow[{}] was added to the Reco Pre-Processing Queue", recoWF.getId());
            } else {
                LOG.trace("RecoWorkflow[{}] was NOT added to the Reco Pre-Processing Queue as it was already present in the queue", recoWF.getId());
            }
        });

        // Mark that initialization on first launch was performed
        _firstLaunchInitByTenant.put(tenantId, true);
        LOG.info("Reco Pre-Processing Queue: initialization on first launch done, number of documents added to the queue: {}", counter.get());
    }


    private void threadSleep(long timeMs) {
        try {
            Thread.sleep(timeMs);
        } catch (InterruptedException e) {
            LOG.error("Interrupted while sleeping...", e);
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    private boolean hasNext() {
        return !_recoPreprocessingQueueByTenant.get(TenantContext.getCurrentTenantId()).isEmpty();
    }


    private Workflow next() {
        return _recoPreprocessingQueueByTenant.get(TenantContext.getCurrentTenantId()).poll();
    }


    public boolean isActive() {
        return !_currentProcessingItemsByTenant.get(TenantContext.getCurrentTenantId()).isEmpty();
    }


    public List<String> getProcessingItemList() {
        return new ArrayList<>(_currentProcessingItemsByTenant.get(TenantContext.getCurrentTenantId()).keySet());
    }

}
