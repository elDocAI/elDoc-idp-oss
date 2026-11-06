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

import ai.eldoc.cluster.ClusterManager;
import ai.eldoc.model.Workflow;
import com.hazelcast.config.*;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * IDP Monitor
 */
@Singleton
@Lock(LockType.READ)
@ConcurrencyManagement(ConcurrencyManagementType.BEAN) // Having @ConcurrencyManagement(BEAN) ensures that @Schedules can run simultaniously, otherwise they will block each other
@TransactionManagement(TransactionManagementType.BEAN)
public class IdpMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(IdpMonitor.class);

    private static final String MAP_NAME_PROCESSOR = "ai.eldoc.recoProcessorTasks";
    private static final String MAP_NAME_CONVERSION = "ai.eldoc.recoConversionTasks";
    private static final int MAP_TTL_PROCESSOR = 60;
    private static final int MAP_TTL_CONVERSION = 60;

    @Inject
    private ClusterManager _clusterManager;

    private ThreadPoolExecutor _recoProcessorExecutor;

    private final Map<String, String> _processorTasks = new ConcurrentHashMap<>();
    private final Map<String, String> _conversionTasks = new ConcurrentHashMap<>();


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @PostConstruct
    protected void init() {
        //
        // Initialize RecoProcessor queue status map
        //
        EvictionConfig evictionConfig = new EvictionConfig()
                .setSize(1000)
                .setMaxSizePolicy(MaxSizePolicy.PER_NODE)
                .setEvictionPolicy(EvictionPolicy.LFU);

        MapConfig processorMapConfig = new MapConfig(MAP_NAME_PROCESSOR)
                .setTimeToLiveSeconds(MAP_TTL_PROCESSOR)
                .setEvictionConfig(evictionConfig);

        try {
            _clusterManager.getInstance().getConfig().addMapConfig(processorMapConfig);
            LOG.trace("IdpMonitor: distributed cache configuration set for Recognition monitor: {}", MAP_NAME_PROCESSOR);
        } catch (InvalidConfigurationException e) {
            LOG.info("IdpMonitor: distributed cache configuration is already present for Recognition monitor: {}", MAP_NAME_PROCESSOR);
        }

        //
        // Initialize Conversion queue status map
        //
        MapConfig conversionMapConfig = new MapConfig(MAP_NAME_CONVERSION)
                .setTimeToLiveSeconds(MAP_TTL_CONVERSION)
                .setEvictionConfig(evictionConfig);

        try {
            _clusterManager.getInstance().getConfig().addMapConfig(conversionMapConfig);
            LOG.trace("IdpMonitor: distributed cache configuration set for Conversion monitor: {}", MAP_NAME_CONVERSION);
        } catch (InvalidConfigurationException e) {
            LOG.info("IdpMonitor: distributed cache configuration is already present for Conversion monitor: {}", MAP_NAME_CONVERSION);
        }
    }


    @Schedule(second = "*/15", minute = "*", hour = "*", info = "IdpMonitorTimer", persistent = false)
    public void updateMonitorState() {
        pushProcessingState();
        if (_recoProcessorExecutor != null && LOG.isTraceEnabled()) {
            LOG.trace("RecoProcessor executor status [{}/{}], queue size [{}]", _recoProcessorExecutor.getActiveCount(), _recoProcessorExecutor.getCorePoolSize(), _recoProcessorExecutor.getQueue().size());
        }

        pushConversionState();
    }


    public void notifyProcessingStarted(String tenantId, Workflow task) {
        _processorTasks.put(tenantId, task.getId());
        pushProcessingState();
    }


    public void notifyProcessingEnded(String tenantId, Workflow task) {
        _processorTasks.remove(tenantId, task.getId());
        getProcessorActiveTasksHC().remove(tenantId, task.getId());
        pushProcessingState();
    }


    public void notifyConversionStarted(String tenantId, Workflow task) {
        _conversionTasks.put(tenantId, task.getId());
        pushConversionState();
    }


    public void notifyConversionEnded(String tenantId, Workflow task) {
        _conversionTasks.remove(tenantId, task.getId());
        getConversionActiveTasksHC().remove(tenantId, task.getId());
        pushConversionState();
    }


    void pushProcessingState() {
        _processorTasks.forEach((tenantId, taskId) -> {
            if (!getProcessorActiveTasksHC().containsKey(tenantId)) {
                getProcessorActiveTasksHC().put(tenantId, taskId);
            }
        });
    }


    void pushConversionState() {
        _conversionTasks.forEach((tenantId, taskId) -> {
            if (!getConversionActiveTasksHC().containsKey(tenantId)) {
                getConversionActiveTasksHC().put(tenantId, taskId);
            }
        });
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public String getWorkflowIdInWorkProcessor(String tenantId) {
        return getProcessorActiveTasksHC().get(tenantId);
    }


    public String getWorkflowIdInWorkConversion(String tenantId) {
        return getConversionActiveTasksHC().get(tenantId);
    }


    public void setRecoProcessorExecutor(ThreadPoolExecutor recoProcessorExecutor) {
        _recoProcessorExecutor = recoProcessorExecutor;
    }


    Map<String, String> getProcessorActiveTasksHC() {
        return _clusterManager.getInstance().getMap(MAP_NAME_PROCESSOR);
    }


    Map<String, String> getConversionActiveTasksHC() {
        return _clusterManager.getInstance().getMap(MAP_NAME_CONVERSION);
    }

}
