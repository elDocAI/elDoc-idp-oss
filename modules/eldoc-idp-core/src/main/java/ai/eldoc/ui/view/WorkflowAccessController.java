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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import java.util.Objects;


/**
 * Workflow Concurrent Access Manager Controller
 */
@Named
@ApplicationScoped
public class WorkflowAccessController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowAccessController.class);

    public static final String WF_CONCURRENT_ACCESS_MGR_KEY = "ai.eldoc.wf.concurrentAccessMgr";

    @Inject
    private CacheManager _cacheManager;

    //
    // Methods ------------------------------------------------------------------------------------
    //


    @PostConstruct
    void init() {
        if (_cacheManager == null) {
            LOG.error("CacheManager is not available, Workflow concurrent access manager can not start");
        } else {
            Cache workflowCuncurrentAccessMap = _cacheManager.getCache(WF_CONCURRENT_ACCESS_MGR_KEY, String.class, String.class);
            if (workflowCuncurrentAccessMap == null) {
                // configure cache
                /*
                 * We can not use like this:
                 * MutableConfiguration<String, WebSessionData> config
                 * as while creating the casche on the remote node - it will not be able to load the Class WebSessionData
                 */
                MutableConfiguration<String, String> config = new MutableConfiguration<String, String>().setTypes(String.class, String.class);
                //config.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(Duration.ONE_MINUTE));
                //config.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(Duration.ONE_MINUTE));
                config.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(Duration.ONE_DAY));

                // create cache
                _cacheManager.createCache(WF_CONCURRENT_ACCESS_MGR_KEY, config);

                LOG.info("Workflow Concurrent Access Manager distributed cache initialized");
            }
        }
    }


    @PreDestroy
    public void destroy() {

    }


    /**
     * Adds the lock for specified entity and systemId
     *
     * @param docId
     * @param systemIdViewIdPair
     * @return true - if successfully added the lock, otherwise false
     */
    public boolean putLock(String docId, String systemIdViewIdPair) {
        LOG.trace("Workflow[{}] setting lock for systemId: {}", docId, systemIdViewIdPair);
        if (_cacheManager == null) {
            return false;
        }
        Cache<String, String> workflowCuncurrentAccessMap = _cacheManager.getCache(WF_CONCURRENT_ACCESS_MGR_KEY, String.class, String.class);

        // This doesn't work because on the page refresh @PostConstruct fired before @PreDestroy for the same docId&systemId pair,
        // thus it was extended with the viewId
        //return workflowCuncurrentAccessMap.putIfAbsent(docId, systemId);
        String lockedBy = workflowCuncurrentAccessMap.get(docId);
        if (lockedBy == null) {
            workflowCuncurrentAccessMap.put(docId, systemIdViewIdPair);
            return true;
        }

        // Rule: we put new lock for the systemId from any new viewId, but remove lock only for the specific viewId
        String[] lockedByPair = lockedBy.split("\\|");
        String[] systemIdPair = systemIdViewIdPair.split("\\|");
        if (Objects.equals(systemIdPair[0], lockedByPair[0])) {
            // SystemId is the same - accept lock for the viewId without checking specific viewId
            workflowCuncurrentAccessMap.put(docId, systemIdViewIdPair);
            return true;
        } else {
            return false;
        }
    }


    /**
     * Removes the lock for specified entity and systemId
     *
     * @param docId
     * @param systemIdViewIdPair
     * @return true - if successfully removed the lock, otherwise false
     */
    public boolean removeLock(String docId, String systemIdViewIdPair) {
        LOG.trace("Workflow[{}] removing lock for systemId: {}", docId, systemIdViewIdPair);
        if (_cacheManager == null) {
            return false;
        }
        Cache<String, String> workflowCuncurrentAccessMap = _cacheManager.getCache(WF_CONCURRENT_ACCESS_MGR_KEY, String.class, String.class);
        // Rule: we put new lock for the systemId from any new viewId, but remove lock only for the specific viewId
        return workflowCuncurrentAccessMap.remove(docId, systemIdViewIdPair);
    }


    /**
     * Checks whether entity is locked by any systemId
     *
     * @param docId
     * @return
     */
    public boolean isLocked(String docId) {
        return isLocked(docId, null);
    }


    /**
     * Checks whether entity is locked by specified systemId
     *
     * @param docId
     * @param systemIdViewIdPair
     * @return
     */
    public boolean isLocked(String docId, String systemIdViewIdPair) {
        if (_cacheManager == null) {
            return true;
        }
        Cache<String, String> workflowCuncurrentAccessMap = _cacheManager.getCache(WF_CONCURRENT_ACCESS_MGR_KEY, String.class, String.class);

        if (systemIdViewIdPair == null) {
            return workflowCuncurrentAccessMap.containsKey(docId);
        } else {
            if (workflowCuncurrentAccessMap.containsKey(docId)) {
                return Objects.equals(workflowCuncurrentAccessMap.get(docId), systemIdViewIdPair);
            } else {
                return false;
            }
        }
    }


    /**
     * Returns systemId|viewId pair who locked the given entity or null in case entity is not locked
     *
     * @param docId
     * @return
     */
    public String getLockedBy(String docId) {
        if (_cacheManager == null) {
            return null;
        }
        Cache<String, String> workflowCuncurrentAccessMap = _cacheManager.getCache(WF_CONCURRENT_ACCESS_MGR_KEY, String.class, String.class);
        return workflowCuncurrentAccessMap.get(docId);
    }
}
