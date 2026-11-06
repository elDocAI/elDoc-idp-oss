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
package ai.eldoc.service;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.service.AppConfigService.TenantConfig;
import com.mongodb.*;
import com.mongodb.client.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * MongoDB Client Java Driver Service<br>
 * Creates and maintains MongoClient driver object<br>
 * Method {@link #destroy()} to be called on application destroy to close MongoDB connections
 *
 * Note: support for multitenancy is removed as it is part of the Enterprise Version
 */
@Startup
@Singleton
@DependsOn("EmbeddedMongoService")
@TransactionManagement(TransactionManagementType.BEAN)
public class DbClientService {

    private static final Logger LOG = LoggerFactory.getLogger(DbClientService.class);

    public static final String DB_NAME_PREFIX = "eldoc_";

    @Inject
    private AppConfigService _appConfigService;

    private MongoClient _tenantMongoClient;
    private final Map<String, String> _tenantDBNameMap;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public DbClientService() {
        _tenantDBNameMap = new ConcurrentHashMap<>();
    }


    @PostConstruct
    void init() {
        LOG.info("DbClientService: Initialization started");
        initTenants();
        LOG.info("DbClientService: Initialization done");
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Inits tenants by creating DbClient for each of the configured tenantIds
     */
    public void initTenants() {
        try {
            LOG.info("DbClientService: DB Client(s) initialization started for {} Tenant(s): {}", _appConfigService.getTenantIdSet().size(), _appConfigService.getTenantIdSet());

            _appConfigService.getTenantIdSet().forEach(tenantId -> {
                initTenant(tenantId);
            });

            LOG.info("DbClientService: DB Client(s) initialization for Tenants done");
        } catch (Exception e) {
            LOG.error("DbClientService: DB Client(s) initialization for Tenants ERROR", e);
        }
    }


    /**
     * Inits tenant by creating DbClient for the given tenantId (retrieves TenantConfig from the AppConfigService)
     *
     * @param tenantId
     */
    public void initTenant(String tenantId) {
        try {
            if (!AppConfigService.IDP_OSS_DEFAULT_TENANT_NAME.equals(tenantId)) {
                return;
            }

            TenantConfig tenantConfig = _appConfigService.getTenantConfig(tenantId);

            if (!tenantConfig.isDBParamsSet()) {
                LOG.info("DbClientService: Tenant[{}] DB Client params not set. Default DB Client to be used instead", tenantConfig.getTenantId());
                return;
            }

            LOG.info("DbClientService: Tenant[{}] DB Client initialization started for connection string: {}", tenantConfig.getTenantId(), tenantConfig.getDBConnStr());
            addClient(tenantConfig);
            LOG.info("DbClientService: Tenant[{}] DB Client initialization done", tenantConfig.getTenantId());
        } catch (Exception e) {
            LOG.error("DbClientService: Tenant[{}] DB Client initialization ERROR", tenantId, e);
        }
    }


    /**
     * Creates and adds a new Client based on the given tenantConfig
     *
     * @param tenantConfig
     */
    private void addClient(TenantConfig tenantConfig) throws Exception {
        String tenantId = tenantConfig.getTenantId();

        // Create and add the new client
        _tenantMongoClient = createDbClient(tenantConfig);

        // Check if custom DB Name was provided for the tenant and remember it if so
        if (isNotBlank(tenantConfig.getDBName())) {
            _tenantDBNameMap.put(tenantId, tenantConfig.getDBName());
            LOG.info("DbClientService: Tenant[{}] uses custom DB Name: {}", tenantId, tenantConfig.getDBName());
        }
    }


    /**
     * Creates MongoDB Client per the given TenantConfig
     *
     * @param tenantConfig
     * @return
     */
    private MongoClient createDbClient(TenantConfig tenantConfig) throws GeneralSecurityException, IOException {
        String tenantId = tenantConfig.getTenantId();
        String dbConn = tenantConfig.getDBConnStr();
        String dbAuth = isBlank(tenantConfig.getDBAuth()) ? getDefaultDbAuth(tenantConfig.getTenantId()) : tenantConfig.getDBAuth();
        String dbUser = tenantConfig.getDBUser();
        char[] dbPwd = tenantConfig.getDBPwd();

        ServerApi serverApi = ServerApi.builder()
                .version(ServerApiVersion.V1)
                .build();

        ConnectionString connString = new ConnectionString(dbConn);

        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applicationName(format("elDoc_v{0} [dbClient:{1}]", getClass().getPackage().getImplementationVersion(), tenantId))
                .applyConnectionString(connString)
                .serverApi(serverApi);

        applyCredential(settingsBuilder, tenantId, dbAuth, dbUser, dbPwd);

        if (tenantConfig.isTLS() || Boolean.TRUE.equals(connString.getSslEnabled())) {
            LOG.info("DbClientService: Tenant[{}] DB Client TLS enabled, initializing...", tenantConfig.getTenantId());

            SSLContext sslContext = buildMongoSSLContext(tenantConfig);
            settingsBuilder.applyToSslSettings(builder -> {
                builder.enabled(true);
                builder.invalidHostNameAllowed(Boolean.TRUE.equals(connString.getSslInvalidHostnameAllowed()));
                builder.context(sslContext);
            });

            LOG.info("DbClientService: Tenant[{}] DB Client TLS initialization completed", tenantConfig.getTenantId());
        }

        MongoClientSettings settings = settingsBuilder.build();
        return MongoClients.create(settings);
    }


    static void applyCredential(MongoClientSettings.Builder settingsBuilder, String tenantId, String dbAuth, String dbUser, char[] dbPwd) {
        boolean hasUser = isNotBlank(dbUser);
        boolean hasPassword = dbPwd != null && dbPwd.length > 0;
        if (hasUser != hasPassword) {
            throw new IllegalArgumentException(format("DbClientService: Tenant[{0}] DB username and password must either both be configured or both be blank", tenantId));
        }
        if (hasUser) {
            settingsBuilder.credential(MongoCredential.createCredential(dbUser, dbAuth, dbPwd));
        }
    }


    /**
     * Builds SSL Context for the given TenantConfig
     *
     * @param tenantConfig
     * @return
     * @throws Exception
     */
    private SSLContext buildMongoSSLContext(TenantConfig tenantConfig) throws GeneralSecurityException, IOException {
        String tenantId = tenantConfig.getTenantId();
        SSLContext sslContext = SSLContext.getInstance("TLS");

        // Trust managers — always use default cacerts unless overridden
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        if (isNotBlank(tenantConfig.getTrustStorePath())) {
            LOG.info("DbClientService: Tenant[{}] DB Client TLS TrustStore path: {}", tenantId, tenantConfig.getTrustStorePath());

            if (tenantConfig.getTrustStorePwd() == null || tenantConfig.getTrustStorePwd().length == 0) {
                throw new IllegalArgumentException(format("DbClientService: Tenant[{0}] DB Client TLS TrustStore password is missing", tenantId));
            }

            try {
                KeyStore ts = KeyStore.getInstance(new File(tenantConfig.getTrustStorePath()), tenantConfig.getTrustStorePwd()); // password may be null
                tmf.init(ts);
                LOG.info("DbClientService: Tenant[{}] DB Client TLS TrustStore loaded (Type: {}; Entries: {})", tenantId, ts.getType(), ts.size());
            } catch (IOException | GeneralSecurityException e) {
                throw new GeneralSecurityException(
                        format("DbClientService: Tenant[{0}] failed to load DB Client TLS TrustStore: {1}", tenantId, tenantConfig.getTrustStorePath()),
                        e);
            }
        } else {
            LOG.info("DbClientService: Tenant[{}] DB Client TLS TrustStore uses system default TrustStore", tenantId);
            tmf.init((KeyStore) null); // Default cacerts
        }
        LOG.info("DbClientService: Tenant[{}] DB Client TLS TrustStore initialization completed", tenantId);

        // Key managers — null by default (no client cert), or load from config
        KeyManager[] keyManagers = null;
        if (isNotBlank(tenantConfig.getKeyStorePath())) {
            LOG.info("DbClientService: Tenant[{}] DB Client TLS KeyStore path: {}", tenantId, tenantConfig.getKeyStorePath());

            if (tenantConfig.getKeyStorePwd() == null || tenantConfig.getKeyStorePwd().length == 0) {
                throw new IllegalArgumentException(format("DbClientService: Tenant[{0}] DB Client TLS KeyStore password is missing", tenantId));
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            try {
                KeyStore ks = KeyStore.getInstance(new File(tenantConfig.getKeyStorePath()), tenantConfig.getKeyStorePwd());
                kmf.init(ks, tenantConfig.getKeyStorePwd());
                LOG.info("DbClientService: Tenant[{}] DB Client TLS KeyStore loaded (Type: {}; Entries: {})", tenantId, ks.getType(), ks.size());
            } catch (IOException | GeneralSecurityException e) {
                throw new GeneralSecurityException(
                        format("DbClientService: Tenant[{0}] failed to load DB Client TLS KeyStore: {1}", tenantId, tenantConfig.getTrustStorePath()),
                        e);
            }
            keyManagers = kmf.getKeyManagers();
            LOG.info("DbClientService: Tenant[{}] DB Client TLS certificate auth enabled using keys from: {}", tenantId, tenantConfig.getKeyStorePath());
        } else {
            LOG.info("DbClientService: Tenant[{}] DB Client TLS certificate auth disabled", tenantId);
        }

        sslContext.init(keyManagers, tmf.getTrustManagers(), null);
        return sslContext;
    }


    /**
     * Closes all active connections to the MongoDB
     */
    @PreDestroy
    public void destroy() {
        try {
            LOG.info("DbClientService: Destroying...");
            if (_tenantMongoClient != null) {
                _tenantMongoClient.close();
            }
            _tenantDBNameMap.clear();
            LOG.info("DbClientService: Successfully destroyed");
        } catch (Exception e) {
            LOG.error("DbClientService: Destroy ERROR", e);
        }
    }


    public static String getDefaultDbAuth(String tenantId) {
        return DB_NAME_PREFIX + tenantId;
    }


    //
    // Getters ------------------------------------------------------------------------------------
    //


    /**
     * Returns Mongo Client for the given tenantId
     *
     * @param tenantId
     * @return
     */
    private MongoClient getClient(final String tenantId) {
        if (!AppConfigService.IDP_OSS_DEFAULT_TENANT_NAME.equals(tenantId)) {
            // In case nothing worked - throwing exception
            throw new ElDocRuntimeException(format("Tenant[{0}]: no DB Client present", tenantId));
        }

        return _tenantMongoClient;
    }


    /**
     * Returns Mongo Database for the the given tenantId
     *
     * @param tenantId
     * @return
     */
    @Lock(LockType.READ)
    public MongoDatabase getDatabase(final String tenantId) {
        return getClient(tenantId).getDatabase(getDatabaseName(tenantId));
    }


    @Lock(LockType.READ)
    public String getDatabaseName(final String tenantId) {
        if (tenantId == null) {
            throw new ElDocRuntimeException("TenantId cannot be null");
        }

        if (_tenantDBNameMap.containsKey(tenantId)) {
            return _tenantDBNameMap.get(tenantId); // Return custom DB name for tenant
        }

        return DB_NAME_PREFIX.concat(tenantId);
    }


    /**
     * Returns newly started ClientSession
     *
     * @param tenantId
     * @return
     */
    @Lock(LockType.READ)
    public ClientSession startSession(String tenantId) {
        return getClient(tenantId).startSession();
    }
}
