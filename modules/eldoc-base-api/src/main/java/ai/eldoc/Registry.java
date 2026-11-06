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

import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.inject.spi.BeanManager;


/**
 * Registry Singleton Class<br><br>
 *
 * Provides global registry for storing global objects.
 */
public final class Registry {

    private ManagedExecutorService _executorService;

    private BeanManager _beanManager;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    private Registry() {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public ManagedExecutorService getExecutorService() {
        return _executorService;
    }


    public void setExecutorService(ManagedExecutorService executorService) {
        _executorService = executorService;
    }


    public BeanManager getBeanManager() {
        return _beanManager;
    }


    public void setBeanManager(BeanManager beanManager) {
        _beanManager = beanManager;
    }


    public static Registry getInstance() {
        return InstanceHolder._instance;
    }


    //
    // Inner Classes used for thread-safety which provides instace of the singleton class ---------
    // See more:
    // https://www.geeksforgeeks.org/java-singleton-design-pattern-practices-examples/
    // https://stackoverflow.com/questions/16106260/thread-safe-singleton-class/16106598#16106598
    //
    private static class InstanceHolder {

        // Perform lazy initialization
        private static final Registry _instance = new Registry();
    }

}
