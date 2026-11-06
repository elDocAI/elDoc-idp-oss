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

import jakarta.faces.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Simple Jakarta Faces LifeCycle Listener
 */
public class FacesLifecycleListener implements PhaseListener {

    private static final long serialVersionUID = -7306124110362685179L;
    private static final Logger LOG = LoggerFactory.getLogger(FacesLifecycleListener.class);


    @Override
    public PhaseId getPhaseId() {
        return PhaseId.ANY_PHASE;
    }


    @Override
    public void beforePhase(PhaseEvent event) {
        LOG.info("{} - START", event.getPhaseId());
    }


    @Override
    public void afterPhase(PhaseEvent event) {
        LOG.info("{} - END", event.getPhaseId());
    }
}
