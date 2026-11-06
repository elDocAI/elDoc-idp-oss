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

import ai.eldoc.ai.config.AIConfig;
import ai.eldoc.dao.ConfigDao;
import ai.eldoc.model.config.*;
import com.mongodb.client.ClientSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Config Service
 */
@ApplicationScoped
public class ConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigService.class);

    @Inject
    private ConfigDao _configDao;


    //
    // DbConfig Methods ---------------------------------------------------------------------------
    //


    /**
     * Returns DbConfig for the current TenantContext
     *
     * @return DbConfig model or null in case nothing found
     */
    public DbConfig getDbConfig() {
        return _configDao.findByType(DbConfig.class);
    }


    /**
     * Saves DbConfig for the current TenantContext
     *
     * @param dbConfig
     * @return save result
     */
    public boolean saveDbConfig(DbConfig dbConfig) {
        return _configDao.updateOrCreate(null, dbConfig);
    }


    /**
     * Saves DbConfig for the current TenantContext
     *
     * @param dbClientSession
     * @param dbConfig
     * @return save result
     */
    public boolean saveDbConfig(ClientSession dbClientSession, DbConfig dbConfig) {
        return _configDao.updateOrCreate(dbClientSession, dbConfig);
    }


    //
    // AppConfig Methods ---------------------------------------------------------------------------
    //


    /**
     * Returns AppConfig for the current TenantContext (in case no AppConfig present in the DB - initializes and saves it in DB)
     *
     * @return
     */
    public AppConfig getAppConfig() {
        AppConfig appConfig = _configDao.findByType(AppConfig.class);
        if (appConfig == null) {
            // probably this is a new instance of the application - initialize new appConfig
            LOG.info("New instance detected - initializing config...");
            appConfig = AppConfig.createConfig();
            _configDao.create(null, appConfig);
            LOG.info("Config for the new instance initialized");
        }
        return appConfig;
    }


    /**
     * Saves AppConfig for the current TenantContext with updated values which require additional handling<br>
     * IMPORTANT: This method assumes that AppConfig already present in the DB (was initialized at least on the initial system start)
     *
     * @param appConfig
     * @return save result
     * @throws java.lang.Exception
     */
    public boolean saveAppConfig(AppConfig appConfig) throws Exception {
        // Prepare system URL before saving
        appConfig.setSystemUrl(normalizeBaseUrl(appConfig.getSystemUrl()));

        if (appConfig.getSystemTimezone() == null || appConfig.getSystemTimezone().isBlank()) {
            // It can be reset to NULL when wrong value entered via UI, thus we normilize it to "UTC"
            appConfig.setSystemTimezone("UTC");
        }

        // Save AppConfig
        return _configDao.updateOrCreate(null, appConfig);
    }


    //
    // AIConfig Methods ---------------------------------------------------------------------------
    //


    /**
     * @return AIConfig for the current TenantContext. In case no AIConfig present in the DB - creates a new instance of it (without saving it in the DB)
     */
    public AIConfig getAIConfig() {
        AIConfig aiConfig = _configDao.findByType(AIConfig.class);
        if (aiConfig == null) {
            // in case no AIConfig present - create the new one
            aiConfig = new AIConfig();
        }
        return aiConfig;
    }


    /**
     * Saves AI config for the current TenantContext
     *
     * @param aiConfig {@link AIConfig}, that need to be saved in DB
     * @return save result
     */
    public boolean saveAIConfig(AIConfig aiConfig) {
        return _configDao.updateOrCreate(null, aiConfig);
    }


    //
    // IDPConfig Methods --------------------------------------------------------------------------
    //


    /**
     * @return IDPConfig for the current TenantContext. In case no IDPConfig present in the DB - creates a new instance of it (without saving it in the DB)
     */
    public IDPConfig getIDPConfig() {
        IDPConfig idpConfig = _configDao.findByType(IDPConfig.class);
        if (idpConfig == null) {
            // in case no IDPConfig present - create the new one
            idpConfig = new IDPConfig();
            idpConfig.setAutoClassification(true);
        }
        return idpConfig;
    }


    /**
     * Saves IDP config for the current TenantContext
     *
     * @param idpConfig {@link IDPConfig}, that need to be saved in DB
     * @return save result
     */
    public boolean saveIDPConfig(IDPConfig idpConfig) {
        return _configDao.updateOrCreate(null, idpConfig);
    }


    //
    // Private Methods ----------------------------------------------------------------------------
    //


    /**
     * Sets system base url, if missing, based on the given requestUrl. Value is not saved in DB.
     *
     * @param appConfig
     * @param requestUrl
     */
    public static void initAppConfigSystemUrlFromRequestUrl(AppConfig appConfig, String requestUrl) {
        if (appConfig != null && requestUrl != null && (isBlank(appConfig.getSystemUrl()) || AppConfig.SYSTEM_URL_DEFAULT.equalsIgnoreCase(appConfig.getSystemUrl()))) {
            appConfig.setSystemUrl(normalizeBaseUrl(requestUrl));
        }
    }


    /**
     * Normalizes base URL
     *
     * @param value URL to be normalized
     * @return normalized URL (lower-cased, without trailing slash)
     */
    public static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            URI uri = new URI(value.trim());

            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new URISyntaxException(value, "URL must include protocol and host");
            }

            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }

            return new URI(
                    scheme,
                    null,
                    host,
                    port,
                    null,
                    null,
                    null
            ).toString();
        } catch (URISyntaxException e) {
            LOG.warn("Provided URL '{}' cannot be used as a system base URL. Error: {}", value, e.getMessage());
            return null;
        }
    }
}
