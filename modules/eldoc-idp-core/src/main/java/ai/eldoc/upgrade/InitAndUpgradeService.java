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
package ai.eldoc.upgrade;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.TenantContext;
import ai.eldoc.cluster.ClusterManager;
import ai.eldoc.dao.ShortIdSequenceDao;
import ai.eldoc.model.config.DbConfig;
import ai.eldoc.service.*;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.*;
import jakarta.enterprise.concurrent.ManagedThreadFactory;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static ai.eldoc.model.config.DbConfig.DB_VER_7_4_u29;
import static ai.eldoc.model.config.DbConfig.DB_VER_CURRENT;


/**
 * Init And Upgrade Service performs application initialization on upgrades
 */
@Startup
@Singleton
@DependsOn("DbClientService")
@TransactionManagement(TransactionManagementType.BEAN)
public class InitAndUpgradeService {

    private static final Logger LOG = LoggerFactory.getLogger(InitAndUpgradeService.class);

    public static final int DB_VER_MIN_SUPPORTED_TENANT = DB_VER_7_4_u29; // Minimum suppoerted version for Tenant DB
    public static final int DB_VER_MAX_SUPPORTED = DB_VER_CURRENT; // Maximum supported version for DBs (Global and Tenant)

    @Resource
    private ManagedThreadFactory _threadFactory;

    @Inject
    private DbClientService _dbClientService;

    @Inject
    private AppConfigService _appConfigService;

    @Inject
    private ConfigService _configService;

    @Inject
    private ListValueService _listValueService;

    @Inject
    private FormDefinitionService _formDefService;

    @Inject
    private ClusterManager _clusterManager;

    @Inject
    private ShortIdSequenceDao _shortIdSequenceDao;


    //
    // Class methods ------------------------------------------------------------------------------
    //


    /**
     * Executes Init And Upgrade logic on instance launch for Tenant DBs of all configured tenantIds.<br>
     * Exceptions are re-thrown so that in case of error, the container will stop deploying the app and unload it
     */
    @PostConstruct
    public void initAndUpgrade() {
        if (!_clusterManager.isPrimaryNode()) {
            LOG.info("InitAndUpgradeService: not a primary node - nothing to do");
            return;
        }

        // Perform Tenant DBs upgrade
        _appConfigService.getTenantIdSet().forEach(this::initAndUpgradeTenantInner);
    }


    /**
     * Executes Init And Upgrade logic for the Tenant DB of the given tenantId
     *
     * @param tenantId ID of the tenant, in scope of which the upgrade is performed
     */
    private void initAndUpgradeTenantInner(String tenantId) {
        try {
            TenantContext.setCurrentTenant(tenantId);

            LOG.info("Tenant[{}]: InitAndUpgradeService: Initialization started", tenantId);

            boolean firstInit = false;
            DbConfig dbConfig = _configService.getDbConfig();
            if (dbConfig == null) {
                // Perform DB first initialization only
                LOG.info("Tenant[{}]: No DBConfig found, performing first DB initialization only...", tenantId);
                dbConfig = new DbConfig();
                firstInit = true;
            }

            if (firstInit) {
                DbUtilsService.doAllIndexesHousekeeping(); // On the first initialization we need to create indexes
                _listValueService.initSystemListValues(); // Init ListValues (standard Directories of values)
                _shortIdSequenceDao.initCounters(); // Init counters for ShortIdSequence
                dbConfig.setVer(DB_VER_CURRENT);
                if (_configService.saveDbConfig(dbConfig)) {
                    LOG.info("Tenant[{}]: DB first initialization done, DB ver set to: {}", tenantId, DB_VER_CURRENT);
                } else {
                    throw new ElDocRuntimeException(String.format("Tenant[%s]: ERROR saving DB config with the current version: %s", tenantId, DB_VER_CURRENT)); // no need to continue with remaining updates
                }
            } else {
                LOG.info("Tenant[{}]: Current DB ver: {}", tenantId, dbConfig.getVer());
                if (Math.clamp(dbConfig.getVer(), DB_VER_MIN_SUPPORTED_TENANT, DB_VER_MAX_SUPPORTED) != dbConfig.getVer()) {
                    throw new ElDocRuntimeException(String.format("Tenant[%s]: DB version %s is out of supported range [%s, %s]", tenantId, dbConfig.getVer(), DB_VER_MIN_SUPPORTED_TENANT, DB_VER_MAX_SUPPORTED));
                }
            }

            boolean upgradePerformed = false;

            if (!upgradePerformed) {
                LOG.info("Tenant[{}]: No DB upgrades needed", tenantId);
            }

            // Execute once per launch init logic for non-first-initialization scenario
            if (!firstInit) {
                // But still the logic below we perform on each launch
                _listValueService.initSystemListValues(); // This is a workaround for few cases (1-2) where ListValues were not created on initial system deployment due to bug
                _formDefService.updateFormDefs(); // Update system FormDefinition names in case of localization changes
                _shortIdSequenceDao.initCounters(); // Init counters for ShortIdSequence
            }
        } catch (ElDocRuntimeException erx) {
            LOG.error("Tenant[{}]: InitAndUpgradeService: {}", tenantId, erx.getMessage());
            throw erx; // Rethrowing exception so that in case of error container will stop deploying the app and unload it
        } catch (Exception ex) {
            LOG.error("Tenant[{}]: InitAndUpgradeService: Error occurred during performing upgrades for Tenant[{}]", tenantId, tenantId, ex);
            throw ex; // Rethrowing exception so that in case of error container will stop deploying the app and unload it
        } finally {
            LOG.info("Tenant[{}]: InitAndUpgradeService: Initialization completed", tenantId);
            TenantContext.clear();
        }
    }


    private boolean upgrade(DbConfig dbConfig, String tenantId, int targetVersion, UpgradeStep upgradeStep) {
        if (dbConfig.getVer() >= targetVersion || targetVersion > DB_VER_CURRENT) {
            return false;
        }

        LOG.info("Tenant[{}]: Performing upgrade to DB ver: {}", tenantId, targetVersion);

        if (!upgradeStep.apply(targetVersion, tenantId)) {
            throw new ElDocRuntimeException(String.format("Tenant[%s]: ERROR performing DB upgrade to version: %s", tenantId, targetVersion));
        }

        dbConfig.setVer(targetVersion);
        if (!_configService.saveDbConfig(dbConfig)) {
            throw new ElDocRuntimeException(String.format("Tenant[%s]: ERROR saving DB config with the current version: %s", tenantId, targetVersion));
        }

        LOG.info("Tenant[{}]: DB ver upgraded to: {}", tenantId, targetVersion);
        return true;
    }


    private boolean upgradeTx(DbConfig dbConfig, String tenantId, int targetVersion, UpgradeStepWithTx upgradeStep) {
        if (dbConfig.getVer() >= targetVersion || targetVersion > DB_VER_CURRENT) {
            return false;
        }

        LOG.info("Tenant[{}]: Performing upgrade to DB ver: {}", tenantId, targetVersion);

        MongoDatabase db = _dbClientService.getDatabase(tenantId);

        try (ClientSession session = _dbClientService.startSession(tenantId)) {
            session.startTransaction();
            try {
                if (!upgradeStep.apply(targetVersion, session, db, tenantId)) {
                    throw new ElDocRuntimeException(String.format("Tenant[%s]: ERROR performing DB upgrade to version: %s", tenantId, targetVersion));
                }

                dbConfig.setVer(targetVersion);
                if (!_configService.saveDbConfig(session, dbConfig)) {
                    throw new ElDocRuntimeException(String.format("Tenant[%s]: ERROR saving DB config with the current version: %s", tenantId, targetVersion));
                }

                session.commitTransaction();
            } catch (Throwable e) {
                try {
                    session.abortTransaction();
                } catch (Throwable ignored) {
                }
                throw e;
            }
        }

        LOG.info("Tenant[{}]: DB ver upgraded to: {}", tenantId, targetVersion);
        return true;
    }

}
