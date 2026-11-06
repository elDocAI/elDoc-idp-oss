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
package ai.eldoc.listener;

import ai.eldoc.Registry;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.*;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Application CDI Lifecycle Observer
 */
@ApplicationScoped
public class CDILifecycleObserver {

    private static final Logger LOG = LoggerFactory.getLogger(CDILifecycleObserver.class);

    @Resource
    private ManagedExecutorService _managedExecutorService;

    @Resource
    private BeanManager _beanManager;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void onStartup(@Observes @Initialized(ApplicationScoped.class) Object init) {
        LOG.info("elDoc: Application CDI Context initialized");
        LOG.info("elDoc[CDI]: Start-up initializtion started...");
        // Once CDI constructed - populate global objects in the Registry
        Registry.getInstance().setExecutorService(_managedExecutorService);
        Registry.getInstance().setBeanManager(_beanManager);
        LOG.info("elDoc[CDI]: Start-up initializtion done");
    }


    public void onBeforeDestroy(@Observes @BeforeDestroyed(ApplicationScoped.class) Object init) {
        LOG.info("elDoc: Application CDI Context is goint to be destroyed...");
        //LOG.info("elDoc[CDI]: Pre-shutdown destroy started...");
        //LOG.info("elDoc[CDI]: Pre-shutdown destroy done");
    }


    public void onShutdown(@Observes @Destroyed(ApplicationScoped.class) Object init) {
        LOG.info("elDoc: Application CDI Context destroyed");
    }
}
