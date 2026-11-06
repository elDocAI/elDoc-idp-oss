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

import jakarta.faces.application.Application;
import jakarta.faces.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Jakarta Faces Application Listener
 */
public class FacesAppListener implements SystemEventListener {

    // See more: http://javaeesquad.blogspot.com/2015/03/getting-notified-when-java-ee.html
    private static final Logger LOG = LoggerFactory.getLogger(FacesAppListener.class);


    @Override
    public void processEvent(SystemEvent event) throws AbortProcessingException {
        try {

            if (event instanceof PostConstructApplicationEvent) {
                LOG.info("elDoc: Application UI (Jakarta Faces) constructed");
            }

            if (event instanceof PreDestroyApplicationEvent) {
                LOG.info("elDoc: Application UI (Jakarta Faces) is shutting down...");
            }

        } catch (Exception e) {
            LOG.error("elDoc: Error processing event: " + event, e);
        }
    }


    @Override
    public boolean isListenerForSource(Object source) {
        return (source instanceof Application); // Only for Application
    }
}
