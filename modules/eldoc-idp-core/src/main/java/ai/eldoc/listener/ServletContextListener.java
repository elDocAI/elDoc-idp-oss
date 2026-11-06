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
import ai.eldoc.context.ConfigContext;
import ai.eldoc.reco.ImageProcessor;
import jakarta.inject.Inject;
import jakarta.servlet.ServletContextEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Context Listener
 */
public class ServletContextListener implements jakarta.servlet.ServletContextListener {

    // See more: http://javaeesquad.blogspot.com/2015/03/getting-notified-when-java-ee.html
    private static final Logger LOG = LoggerFactory.getLogger(ServletContextListener.class);

    @Inject
    private ConfigContext _configContext;


    @Override
    public void contextInitialized(ServletContextEvent sce) {
        //sce.getServletContext().log("elDoc application initialized");
        LOG.info("elDoc: Servlet Context ready");
        LOG.info("elDoc[Servlet]: Start-up initializtion started...");
        ImageProcessor.init();
        // Perform initalizations
        _configContext.setAppStarted(true);
        _configContext.onAppStartListener();
        LOG.info("elDoc[Servlet]: Start-up initializtion done");
    }


    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOG.info("elDoc[Servlet]: Pre-shutdown destroy strated...");
        _configContext.setAppStarted(false);
        _configContext.onAppDestroyListener();
        // Once App is shutting down - remove global objects from the Registry
        Registry.getInstance().setExecutorService(null);
        Registry.getInstance().setBeanManager(null);
        LOG.info("elDoc[Servlet]: Pre-shutdown destroy done");
        //sce.getServletContext().log("elDoc application destroyed");
        LOG.info("elDoc: Servlet Context destroyed");
    }

}
