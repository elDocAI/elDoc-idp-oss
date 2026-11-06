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

import ai.eldoc.exceptions.BadArgumentException;
import ai.eldoc.model.PropertyAwareModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;


/**
 * elDoc Global Properties (shared across cluster)
 */
@ApplicationScoped
public class ClusteredProperties implements PropertyAwareModel {

    private static final String ELDOC_CLUSTERED_CONFIG_NAME = "ai.eldoc.clusteredConfig";
    private static final String ELDOC_CLUSTERED_CONFIG_NAME_PREFIX = "ai.eldoc.clusteredConfig.";

    public static final String IDP_TIMER_PREPROCESSING_PROP = "idp.timer.preprocessing";
    public static final String IDP_TIMER_PROCESSING_PROP = "idp.timer.processing";

    @Inject
    private ClusterManager _clusterManager;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Creates and returns instance of Tenant Clustered Properties
     *
     * @param tenantId
     * @return
     */
    public TenantClusteredProperties getTenantClusteredProperties(String tenantId) {
        return new TenantClusteredProperties(tenantId);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    /**
     * Returns elDoc global properties (shared across cluster)
     *
     * @return
     */
    @Override
    public Map<String, Object> getProperties() {
        // Get IMap<String, Object> per tenant
        return _clusterManager.getInstance().getMap(ELDOC_CLUSTERED_CONFIG_NAME);
    }


    //
    // Inner Classes ------------------------------------------------------------------------------
    //
    /**
     * elDoc Tenant Global Properties (shared across cluster)<br>
     * NOTE: Implementation to be verified
     */
    public final class TenantClusteredProperties implements PropertyAwareModel {

        private final String _tenantId;


        /**
         * Creates per-tenant clustered properties
         *
         * @param tenantId
         * @throws BadArgumentException in case tenantId is null or blank
         */
        public TenantClusteredProperties(String tenantId) throws BadArgumentException {
            if (tenantId == null || tenantId.isBlank()) {
                throw new BadArgumentException("TenantId is null or blank");
            }
            _tenantId = tenantId;
        }


        @Override
        public Map<String, Object> getProperties() {
            // Get IMap<String, Object> per tenant
            return _clusterManager.getInstance().getMap(ELDOC_CLUSTERED_CONFIG_NAME_PREFIX + _tenantId);
        }

    }

}
