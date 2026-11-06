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
package ai.eldoc.context;

import ai.eldoc.Scheduler;
import ai.eldoc.TenantContext;
import ai.eldoc.cluster.ClusteredProperties;
import ai.eldoc.constants.AppConstants;
import ai.eldoc.model.TenantData;
import ai.eldoc.model.config.AppConfig;
import ai.eldoc.service.AppConfigService;
import ai.eldoc.service.ConfigService;
import ai.eldoc.util.I18n;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Configuration Context
 */
@Named
@ApplicationScoped
public class ConfigContext {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigContext.class);

    @Inject
    private AppConfigService _appConfigService;

    @Inject
    private ConfigService _configService;

    @Inject
    private ClusteredProperties _clusteredProp;

    private final Map<String, TenantData> _tenantDataMap;
    private final Map<String, String> _defaultLogLevels;

    private boolean _appStarted;

    /**
     * Defines enabled languages<br>
     * When two or more languages enabled - first language is treated as default (or main) language see: {@link #getDefaultLang()}.<br>
     * IMPORTANT: Order of the enabled languages should not be changed once system has got documents, as it will affect data presentation on UI
     */
    private final LinkedHashSet<String> _langsDefault;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ConfigContext() {
        _tenantDataMap = new ConcurrentHashMap<>();
        _defaultLogLevels = new ConcurrentHashMap<>();
        _langsDefault = new LinkedHashSet<>();
        initDefaultLogLevels();
    }


    //
    // Initialization Methods ---------------------------------------------------------------------
    //


    /**
     * Performs config initialization for the defined tenantIds
     */
    @PostConstruct
    protected void initConfig() {
        try {
            LOG.info("ConfigContext: Initialization of application configuration started...");

            // Init default system languages by validating provided via config params
            _appConfigService.getElDocConfig().getLangsDefault().stream()
                    .filter(I18n.LANG_SYSTEM_CODES_ALL::contains)
                    .forEachOrdered(_langsDefault::add);

            if (_langsDefault.isEmpty()) {
                // In case default system languages were not set - defaults to all supported languages
                _langsDefault.addAll(I18n.LANG_SYSTEM_CODES_ALL);
            }

            // Init per-tenant configuration
            _appConfigService.getTenantIdSet().forEach(tenantId -> {
                try {
                    TenantContext.setCurrentTenant(tenantId);
                    initConfigByTenantId(tenantId);
                } catch (Exception e) {
                    LOG.error("ConfigContext: Initialization of application configuration error", e);
                } finally {
                    TenantContext.clear();
                }
            });
            LOG.info("ConfigContext: Initialization of application configuration done");
        } catch (Exception e) {
            LOG.error("ConfigContext: Initialization of application configuration error", e);
        }
    }


    /**
     * Performs config initialization for the specified tenantId<br>
     * IMPORTANT: This method has to be executed in the valid TenantContext of the given tenantId
     *
     * @param tenantId
     */
    public void initConfigByTenantId(String tenantId) {
        // Init tenant Data
        TenantData tenantData = new TenantData(tenantId);

        // Init AppConfig for tenant
        AppConfig appConfig = _configService.getAppConfig();
        tenantData.setAppConfig(appConfig);

        // Store tenantData in the application scoped map
        _tenantDataMap.put(tenantId, tenantData);
    }


    /**
     * Removes config for the given tenantId from the configuration map<br>
     * Can be executed in any TenantContext
     *
     * @param tenantId
     */
    public void removeConfigByTenantId(String tenantId) {
        _tenantDataMap.remove(tenantId);
    }


    //
    // Application global listeners ---------------------------------------------------------------
    //


    /**
     * Triggered on application post construct event
     */
    public void onAppStartListener() {
        try {
            LOG.info("ConfigContext: Performing on-app-start initialization...");
            // 1. Enable schedulers in case they are enabled on system level
            if (!isSchedulersDisabled()) {
                LOG.info("ConfigContext: Enabling system schedulers...");
                setIdpPreProcessingTimerEnabled(true, false); // Do not force this property in case we are joining already running cluster
                setIdpProcessingTimerEnabled(true, false); // Do not force this property in case we are joining already running cluster
            } else {
                LOG.info("ConfigContext: System schedulers are disabled by JVM param");
            }
        } catch (Exception e) {
            LOG.error("Application exception while initialization on start", e);
        }
    }


    /**
     * Triggered on application pre-destroy event
     */
    public void onAppDestroyListener() {
        try {
            LOG.info("ConfigContext: Performing on-app-destroy steps...");
            // 1. Disable schedulers
            setIdpPreProcessingTimerEnabled(false);
            setIdpProcessingTimerEnabled(false);
        } catch (Exception e) {
            LOG.error("ConfigContext: Application exception while performing pre-destroy steps", e);
        }
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    public boolean isLicModIDP() {
        return true;
    }


    /**
     * Inits and remembers default log levels. Also checks whether ai.eldoc Logger is present and if not - adds it
     */
    private void initDefaultLogLevels() {
        Enumeration<String> loggerNames = java.util.logging.LogManager.getLogManager().getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            String loggerName = loggerNames.nextElement();
            if (loggerName == null || java.util.logging.Logger.getLogger(loggerName) == null || java.util.logging.Logger.getLogger(loggerName).getLevel() == null) {
                continue;
            }
            _defaultLogLevels.put(loggerName, java.util.logging.Logger.getLogger(loggerName).getLevel().getName());
        }

        if (!_defaultLogLevels.containsKey(AppConstants.ELDOC_LOGGER_NAME)) {
            java.util.logging.Logger elDocLogger = java.util.logging.Logger.getLogger(AppConstants.ELDOC_LOGGER_NAME);
            elDocLogger.setLevel(java.util.logging.Level.INFO);
            _defaultLogLevels.put(AppConstants.ELDOC_LOGGER_NAME, java.util.logging.Level.INFO.getName());
        }
    }


    //
    // Tenant configuration related getters -------------------------------------------------------
    //


    /**
     * @return AppConfig object
     */
    public AppConfig getAppConfig() {
        return _tenantDataMap.get(TenantContext.getCurrentTenantId()).getAppConfig();
    }


    /**
     * Returns system base URL without a trailing slash (defined via the settings page)
     */
    public String getSystemUrl() {
        return getAppConfig().getSystemUrl();
    }


    /**
     * Sets system base url, if missing, from requestUrl. Value is not saved in DB.
     * It is just a helper method for a new container-based deployment to help users explore the system, e.g.: opening files via WOPI, etc.
     */
    public void setSystemUrlIfMissing(String requestUrl) {
        ConfigService.initAppConfigSystemUrlFromRequestUrl(getAppConfig(), requestUrl);
    }


    /**
     * Returns system timezone
     *
     * @return
     */
    public String getSystemTimezone() {
        return getAppConfig().getSystemTimezone();
    }


    public String getInstanceId() {
        return getAppConfig().getInstanceId();
    }


    //
    // Other configuration getters & setters ------------------------------------------------------
    //


    public boolean isAppStarted() {
        return _appStarted;
    }


    public void setAppStarted(boolean isAppStarted) {
        _appStarted = isAppStarted;
    }


    public boolean isIdpPreProcessingTimerEnabled() {
        return _clusteredProp.getProperty(ClusteredProperties.IDP_TIMER_PREPROCESSING_PROP, false);
    }


    public void setIdpPreProcessingTimerEnabled(boolean recoSchedEnabled) {
        setIdpPreProcessingTimerEnabled(recoSchedEnabled, true);
    }


    public void setIdpPreProcessingTimerEnabled(boolean recoSchedEnabled, boolean force) {
        if (force) {
            _clusteredProp.setProperty(ClusteredProperties.IDP_TIMER_PREPROCESSING_PROP, recoSchedEnabled);
        } else {
            _clusteredProp.setPropertyIfAbsent(ClusteredProperties.IDP_TIMER_PREPROCESSING_PROP, recoSchedEnabled);
        }
    }


    public boolean isIdpProcessingTimerEnabled() {
        return _clusteredProp.getProperty(ClusteredProperties.IDP_TIMER_PROCESSING_PROP, false);
    }


    public void setIdpProcessingTimerEnabled(boolean recoSchedEnabled) {
        setIdpProcessingTimerEnabled(recoSchedEnabled, true);
    }


    public void setIdpProcessingTimerEnabled(boolean recoSchedEnabled, boolean force) {
        if (force) {
            _clusteredProp.setProperty(ClusteredProperties.IDP_TIMER_PROCESSING_PROP, recoSchedEnabled);
        } else {
            _clusteredProp.setPropertyIfAbsent(ClusteredProperties.IDP_TIMER_PROCESSING_PROP, recoSchedEnabled);
        }
    }


    public boolean isSchedulersDisabled() {
        return Scheduler.isSchedulersDisabled();
    }


    public Map<String, String> getDefaultLogLevels() {
        return _defaultLogLevels;
    }


    public String getVersion() {
        String ver = AppConfigService.class.getPackage().getImplementationVersion(); // Due to some reason it is null for the ai.eldoc package, so we need to take some sub-package
        return isBlank(ver) ? "0.0000.0" : ver;
    }


    //
    // System languages related getters -----------------------------------------------------------
    //


    /**
     * Returns enabled Language System Codes
     *
     * @return
     */
    public LinkedHashSet<String> getLangsEnabled() {
        if (TenantContext.getCurrentTenantId() == null // In case tenant context is not set
                || _tenantDataMap.get(TenantContext.getCurrentTenantId()) == null // or tenant configuration is not available yet (due to some reason)
                || getAppConfig().getLangs().isEmpty()) {                         // or in case no languages were defined in the tenant config
            return _langsDefault;                                                 // Then return default languages
        }

        return new LinkedHashSet<>(getAppConfig().getLangs()); // Otherwise return per-tenant defined languages
    }


    /**
     * Returns default languages enabled for the system
     *
     * @return Set with langSysCodes
     */
    public LinkedHashSet<String> getLangsDefault() {
        return _langsDefault;
    }


    /**
     * Defines whether system set in multi-language mode
     *
     * @return
     */
    public boolean isMultiLang() {
        return getLangsEnabled().size() > 1;
    }


    /**
     * Return system default language what is the first language of the Enabled Langs list
     *
     * @return
     */
    public String getDefaultLang() {
        return getLangsEnabled().getFirst();
    }


    /**
     * Returns Instance of the bean
     *
     * @return bean instance
     */
    public static ConfigContext getInstance() {
        return CDI.current().select(ConfigContext.class).get();
    }
}
