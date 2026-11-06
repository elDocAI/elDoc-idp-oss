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

import ai.eldoc.WebSessionManager;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Servlet Session Listener Class
 */
@WebListener
public class SessionListener implements HttpSessionListener {

    private static final Logger LOG = Logger.getLogger(SessionListener.class.getName());

    @Inject
    private WebSessionManager _sessionManager;


    @Override
    public void sessionCreated(HttpSessionEvent se) {
        LOG.log(Level.FINEST, "elDoc HTTP Session Created event, session ID: {0}", se.getSession().getId());
        //_localSessionManager.addSession(se.getSession());
    }


    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        LOG.log(Level.FINEST, "elDoc HTTP Session Destroyed event, session ID: {0}", se.getSession().getId());
        _sessionManager.removeSession(se.getSession());
    }

}
