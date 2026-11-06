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
package ai.eldoc;

import ai.eldoc.cluster.ClusterManager;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.service.*;
import jakarta.annotation.*;
import jakarta.ejb.*;
import jakarta.enterprise.concurrent.ManagedThreadFactory;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;


/**
 * elDoc Scheduler<br><br>
 *
 * In elDoc application we do not use Persistent Timers (persistent=true) due to the following reasons:<br>
 * <pre>
 * - Payara Micro Cluster executes timer only on the node with the same name which has created that timer;
 * - While Payara Micro Node which has created that timer is off-line - timer is inactive;
 * - We currently also do not use behavior like: when the server comes back online, it is required to execute missed persistent timers.
 * </pre>
 *
 * As such elDoc implements own Clustered Timers based on Hazelcast IScheduledExecutorService
 *
 * @see <a href="https://stackoverflow.com/questions/66211779/implementing-clustered-coordianted-timer-runs-on-one-node-only-in-payara-micro">https://stackoverflow.com/questions/66211779/implementing-clustered-coordianted-timer-runs-on-one-node-only-in-payara-micro</a>
 */
@Singleton
@Lock(LockType.READ)
@ConcurrencyManagement(ConcurrencyManagementType.BEAN) // Having @ConcurrencyManagement(BEAN) ensures that @Schedules can run simultaniously, otherwise they will block each other
@TransactionManagement(TransactionManagementType.BEAN)
public class Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(Scheduler.class);

    public static final String ELDOC_JVM_OPTION_DISABLE_SCHEDULERS = "ai.eldoc.disableSchedulers";

    @Resource
    private TimerService _timerService;

    @Resource
    private ManagedThreadFactory _threadFactory;

    @Inject
    private AppConfigService _appConfigService;

    @Inject
    private WorkflowService _workflowService;

    @Inject
    private RecoPreprocesorService _recoPreprocesorService;

    @Inject
    private RecognitionService _recognitionService;

    @Inject
    private FilesCleanupService _filesCleanupService;

    @Inject
    private ClusterManager _clusterManager;

    @Inject
    private ConfigContext _configContext;

    private ThreadPoolExecutor _retentionTimerExecutor;


    //
    // Initialization Methods ---------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        try {
            // We are using pooledExecuter in order to control the number of spawned threads
            _retentionTimerExecutor = new ThreadPoolExecutor(1, 10, 1, TimeUnit.SECONDS, new LinkedBlockingDeque<>(), _threadFactory);
        } catch (Exception e) {
            LOG.error("Timer Executor Initialization error", e);
        }
    }


    @PreDestroy
    void destroy() {
        shutdownExecutor(_retentionTimerExecutor, "RetentionTimerExecutor");
        _retentionTimerExecutor = null;
    }


    //
    // Timer Methods ------------------------------------------------------------------------------
    //


    /**
     * Workflow Retention Timer<br>
     * Processes Workflows and apply Retention policy as per respective FormDefinition configuration<br>
     * Runs at 4:15:30 every day<br>
     * NOTE: This timer has to run on the single node in the cluster. Because it is a non-persistent timer ("persistent = false") this is achieved by checking for primary node
     */
    @Schedule(second = "30", minute = "15", hour = "4", info = "WorkflowRetentionTimer", persistent = false)
    public void workflowRetentionTimer() {
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Running WorkflowRetentionTimer...");
            }

            if (!_configContext.isAppStarted()) {
                LOG.trace("Running WorkflowRetentionTimer cancelled - application is not yet started...");
                return;
            }

            if (isSchedulersDisabled()) {
                LOG.trace("Running WorkflowRetentionTimer cancelled - schedulers are disabled");
                return;
            }

            if (!_clusterManager.isPrimaryNode()) {
                LOG.trace("Running WorkflowRetentionTimer cancelled - not a primary node");
                return;
            }

            _appConfigService.getTenantIdSet().forEach(tenantId -> {
                _retentionTimerExecutor.submit(() -> {
                    try {
                        TenantContext.setCurrentTenant(tenantId);
                        _workflowService.runRetentionTimer(tenantId);
                    } catch (Exception e) {
                        LOG.error("Tenant[{}] WorkflowRetentionTimer error", tenantId, e);
                    } finally {
                        TenantContext.clear();
                    }
                });
            });
        } catch (Exception e) {
            LOG.error("WorkflowRetentionTimer Error", e);
        }
    }


    /**
     * IDP Pre-processing Timer<br>
     * We keep it in separate class as otherwise it locks its parent class during execution<br>
     * NOTE: This timer has to run on each node in the cluster. Thus it is a non-persistent timer ("persistent = false") and no need to check for a primary node
     */
    @Schedule(second = "*/5", minute = "*", hour = "*", info = "IdpPreProcessingTimer", persistent = false)
    protected void idpPreProcessingTimer() {
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Running IdpPreProcessingTimer...");
            }

            if (!_configContext.isAppStarted()) {
                LOG.trace("Running IdpPreProcessingTimer cancelled - application is not yet started...");
                return;
            }

            if (!_configContext.isIdpPreProcessingTimerEnabled()) {
                LOG.trace("Running IdpPreProcessingTimer cancelled - schedulers are disabled");
                return;
            }

            _recoPreprocesorService.processQueue();
        } catch (Exception e) {
            LOG.error("IdpPreProcessingTimer Error", e);
        }
    }


    /**
     * IDP Processing Timer<br>
     * NOTE: This timer has to run on the single node in the cluster. Because it is a non-persistent timer ("persistent = false") this is achieved by checking for primary node
     */
    @Schedule(second = "*/5", minute = "*", hour = "*", info = "IdpProcessingTimer", persistent = false)
    protected void idpProcessingTimer() {
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Running IdpProcessingTimer...");
            }

            if (!_configContext.isAppStarted()) {
                LOG.trace("Running IdpProcessingTimer cancelled - application is not yet started...");
                return;
            }

            if (!_clusterManager.isPrimaryNode()) {
                LOG.trace("Running IdpProcessingTimer cancelled - not a primary node");
                return;
            }

            if (!_configContext.isIdpProcessingTimerEnabled()) {
                LOG.trace("Running IdpProcessingTimer cancelled - schedulers are disabled");
                return;
            }

            _appConfigService.getTenantIdSet().forEach(tenantId -> {
                // Currently we do implement this by doing simple iterationing over all tenants.
                // Approach like implemented for RecoPreprocesorService to be used once this service is omptimized
                // to automatically pull new documents like RecoPreprocesorService
                try {
                    TenantContext.setCurrentTenant(tenantId);
                    _recognitionService.processPending();
                } catch (Exception e) {
                    LOG.error("Tenant[{}] IdpProcessingTimer error", tenantId, e);
                } finally {
                    TenantContext.clear();
                }
            });
        } catch (Exception e) {
            LOG.error("IdpProcessingTimer Error", e);
        }
    }


    /**
     * Files cleanup timer<br>
     * We keep it in a separate class as otherwise it locks its parent class during execution<br>
     * NOTE: This timer has to run on each node in the cluster. Thus it is a non-persistent timer ("persistent = false") and no need to check for a primary node
     */
    @Schedule(second = "50", minute = "5", hour = "*", info = "FilesCleanupTimer", persistent = false)
    protected void filesCleanupTimer() {
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Running FilesCleanupTimer...");
            }

            if (!_configContext.isAppStarted()) {
                LOG.trace("Running FilesCleanupTimer cancelled - application is not yet started...");
                return;
            }

            _filesCleanupService.processQueue();
        } catch (Exception e) {
            LOG.error("FilesCleanupTimer Error", e);
        }
    }


    public static boolean isSchedulersDisabled() {
        return Boolean.getBoolean(ELDOC_JVM_OPTION_DISABLE_SCHEDULERS);
    }


    private void shutdownExecutor(ThreadPoolExecutor ex, String executorName) {
        if (ex == null) {
            return;
        }

        ex.shutdown(); // stop accepting new tasks

        try {
            // 1) Give running tasks a short chance to complete
            if (!ex.awaitTermination(30, TimeUnit.SECONDS)) {

                // 2) Cancel queued tasks + interrupt running tasks
                List<Runnable> dropped = ex.shutdownNow();
                if (!dropped.isEmpty()) {
                    LOG.warn("{} forced shutdown. Dropped {} queued tasks", executorName, dropped.size());
                }

                // 3) Wait a bit more for interruption to take effect
                if (!ex.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOG.warn("{} did not terminate cleanly", executorName);
                }
            }
        } catch (InterruptedException ie) {
            // Preserve interrupt status and escalate to shutdownNow
            ex.shutdownNow();
            Thread.currentThread().interrupt();
            LOG.warn("{} shutdown interrupted", executorName);
        } catch (Exception e) {
            LOG.error("{} destroy error", executorName, e);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Diagnostics methods
    ///////////////////////////////////////////////////////////////////////////////////////////////
    public void diagListTimers() {
        LOG.info(">>> EJB TimerService timers ({}): {}",
                _timerService.getAllTimers().size(),
                _timerService.getAllTimers().stream()
                        .map(timer -> String.valueOf(timer.getInfo()) + "(persistent=" + timer.isPersistent() + ')')
                        .collect(Collectors.toList())
        );
    }
}


/*
 * NOTE 1:
 *
 * You must know: CDI Events are not multi-threaded
 * If there are 10 observers and each of them take 7 minutes to execute, then the total execution time for the one event is 70 minutes.
 * It would do you absolutely no good to schedule that event to fire more frequently than 70 minutes.
 *
 * What would happen if you did? Depends on the @Singleton @Lock policy
 *
 * @Lock(WRITE) is the default. In this mode the timeout method would essentially be locked until the previous invocation completes.
 * Having it fire every 5 minutes even though you can only process one every 70 minutes would eventually cause all the pooled timer threads to be waiting on your Singleton.
 *
 * @Lock(READ) allows for parallel execution of the timeout method. Events will fire in parallel for a while. However since they actually are taking 70 minutes each,
 * within an hour or so we'll run out of threads in the timer pool just like above.
 * The elegant solution is to use @Lock(WRITE) then specify some short timeout like @AccessTimeout(value = 1, unit = TimeUnit.MINUTES) on the timeout method.
 * When the next 5 minute invocation is triggered, it will wait up until 1 minute to get access to the Singleton before giving up.
 * This will keep your timer pool from filling up with backed up jobs -- the "overflow" is simply discarded.
 *
 * See more: http://tomee.apache.org/examples-trunk/schedule-events/
 */
