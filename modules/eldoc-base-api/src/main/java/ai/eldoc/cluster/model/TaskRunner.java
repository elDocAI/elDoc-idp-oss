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
package ai.eldoc.cluster.model;

import ai.eldoc.Registry;
import com.hazelcast.scheduledexecutor.NamedTask;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Cluster Task Runner
 */
public final class TaskRunner implements NamedTask, Runnable, Serializable {

    private static final long serialVersionUID = -6664914641015666637L;
    private static final Logger LOG = Logger.getLogger(TaskRunner.class.getName());

    private final NamedTask _task;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public TaskRunner(NamedTask event) {
        if (event == null) {
            throw new NullPointerException("Task must not be null");
        }
        _task = event;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public void run() {
        try {
            if (Registry.getInstance().getBeanManager() == null) {
                LOG.log(Level.WARNING, "Skipping scheduled task execution - application not initialized yet...");
                return;
            }
            Registry.getInstance().getBeanManager().getEvent().fire(_task);
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Error executing scheduled task: " + _task, e);
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns the name of the scheduled clustered event by calling MultiTenantTask.getName() on the underlying MultiTenantTask object
     *
     * @return
     */
    @Override
    public final String getName() {
        return _task.getName();
    }

}
