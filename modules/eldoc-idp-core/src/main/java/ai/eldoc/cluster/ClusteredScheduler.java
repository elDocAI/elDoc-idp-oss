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
package ai.eldoc.cluster;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.cluster.model.DiagTask;
import ai.eldoc.cluster.model.TaskRunner;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.hazelcast.scheduledexecutor.NamedTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Time Manager Class
 */
@ApplicationScoped
public class ClusteredScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredScheduler.class);

    @Inject
    private ClusterManager _clusterManager;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Schedules clustered task (timer) which runs only on the single node in the elDoc Cluster<br>
     * Implementation is based on using the <a href="https://docs.hazelcast.com/imdg/4.1/computing/scheduled-executor-service.html">IScheduledExecutorService</a> provided by Hazelcast
     *
     * @param task task to be scheduled
     * @param initialDelaySec initial delay before first execution (in seconds)
     * @param invocationPeriodSec invocation period (in seconds)
     * @param cancelExisting defines whether should cancel any existing task (timer) with the same name.
     * When set to true - cancels existing timer and schedules the given timer.
     * When set to false - tries to schedule the given timer and in case it is already present in the clustered schedule - skips scheduling of the given (new) task (timer)
     *
     * @see <a href="https://docs.hazelcast.com/imdg/4.1/computing/scheduled-executor-service.html">https://docs.hazelcast.com/imdg/4.1/computing/scheduled-executor-service.html</a>
     */
    public void scheduleTask(NamedTask task, long initialDelaySec, long invocationPeriodSec, boolean cancelExisting) {
        scheduleTaskInner(_clusterManager.getScheduledExecutorService(), task, initialDelaySec, invocationPeriodSec, cancelExisting);
    }


    private void scheduleTaskInner(IScheduledExecutorService executorService, NamedTask task, long initialDelaySec, long invocationPeriodSec, boolean cancelExisting) {
        try {
            LOG.debug("Scheduling clustered task: '{}'", task.getName());

            if (!cancelExisting && isTaskPresent(executorService, task)) {
                // Nothing to do: there is no need to cancel existing timer
                // and also timer with the same name already present in the clustered schedule
                LOG.debug("Cluster scheduled task: '{}' scheduling was skipped as it is already present in the clustered schedule", task.getName());
                return;
            }

            // Make sure that no tasks with the same name present in the IScheduledExecutorService
            // Otherwise it will throw com.hazelcast.scheduledexecutor.DuplicateTaskException
            cancelTaskInner(executorService, task);

            // Perform actual scheduling of the task
            executorService.scheduleAtFixedRate(new TaskRunner(task), initialDelaySec, invocationPeriodSec, TimeUnit.SECONDS);
            LOG.debug("Cluster scheduled task: '{}' was scheduled successfully", task.getName());
        } catch (Exception ex) {
            throw new ElDocRuntimeException(ex);
        }
    }


    /**
     * Checks, if clustered task (timer) is scheduled in the elDoc Cluster<br>
     * Implementation is based on using the <a href="https://docs.hazelcast.com/imdg/4.1/computing/scheduled-executor-service.html">IScheduledExecutorService</a> provided by Hazelcast
     *
     * @param task Task to be checked
     * @return true, if provided task is scheduled in the elDoc Cluster
     */
    public boolean isTaskPresent(NamedTask task) {
        return isTaskPresent(_clusterManager.getScheduledExecutorService(), task);
    }


    private boolean isTaskPresent(IScheduledExecutorService executorService, NamedTask event) {
        AtomicBoolean isPresent = new AtomicBoolean(false);
        executorService.getAllScheduledFutures().values()
                .forEach(listOfTimersPerMember -> listOfTimersPerMember
                .forEach(timer -> isPresent.compareAndSet(false, Objects.equals(event.getName(), timer.getHandler().getTaskName()))));
        LOG.debug("Clustered scheduled task '{}' presence in the clustered schedule check: PRESENT={}", event, isPresent.get());
        return isPresent.get();
    }


    /**
     * Cancels scheduled clustered task (timer) in case any present
     *
     * @param task
     */
    public void cancelTask(NamedTask task) {
        cancelTaskInner(_clusterManager.getScheduledExecutorService(), task);
    }


    private void cancelTaskInner(IScheduledExecutorService executorService, NamedTask task) {
        LOG.debug("Cancelling clustered scheduled task '{}' (in case it was present in the clustered schedule)", task.getName());
        executorService.getAllScheduledFutures().values().stream()
                .flatMap(List::stream)
                .filter(timer -> Objects.equals(task.getName(), timer.getHandler().getTaskName()))
                .forEach(timer -> {
                    LOG.debug("Clustered scheduled task '{}' is present in the clustered schedule, trying to cancel it", task.getName());
                    try {
                        timer.cancel(false);
                        timer.dispose();
                        LOG.debug("Clustered scheduled task '{}' was cancelled successfully", task.getName());
                    } catch (Exception e) {
                        LOG.warn("Error cancelling clustered scheduled task: {}", task.getName(), e);
                    }
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Diagnostics methods
    ///////////////////////////////////////////////////////////////////////////////////////////////
    public void diagListTimers() {
        Set<String> clusterScheduledTasks = new HashSet<>();
        _clusterManager.getScheduledExecutorService()
                .getAllScheduledFutures()
                .values()
                .forEach(listOfTimersPerMember -> listOfTimersPerMember.forEach(timer -> clusterScheduledTasks.add(timer.getHandler().getTaskName())));
        LOG.info(">>> Cluster Scheduled Tasks ({}): {}", clusterScheduledTasks.size(), clusterScheduledTasks);
    }


    public void diagObserve(@Observes DiagTask task) {
        try {
            LOG.info(">>>>>>>>>> DIAG TASK: {}", task);
        } catch (Exception ex) {
            LOG.error("Error executing cluster scheduled task: {}", String.valueOf(task), ex);
        }
    }
}
