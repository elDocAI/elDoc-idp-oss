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
package ai.eldoc.ui.view;

import ai.eldoc.*;
import ai.eldoc.ai.config.*;
import ai.eldoc.ai.service.LLMProviderAndModelService;
import ai.eldoc.cluster.ClusterManager;
import ai.eldoc.cluster.ClusteredScheduler;
import ai.eldoc.constants.AppConstants;
import ai.eldoc.constants.Pages;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.context.LocaleContext;
import ai.eldoc.dao.DaoUtils;
import ai.eldoc.model.config.AppConfig;
import ai.eldoc.model.config.IDPConfig;
import ai.eldoc.reco.IdpMonitor;
import ai.eldoc.service.*;
import ai.eldoc.util.I18n;
import ai.eldoc.util.JsfUtils;
import jakarta.annotation.PostConstruct;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.omnifaces.cdi.ViewScoped;
import org.omnifaces.util.Faces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Configuration Page Bean
 */
@Named
@ViewScoped
public class ConfigController extends AbstractUIController implements Serializable {

    @Serial
    private static final long serialVersionUID = 6222660170527276248L;

    private static final Logger LOG = LoggerFactory.getLogger(ConfigController.class);

    @Inject
    private AppConfigService _appConfigService;

    @Inject
    private ConfigService _configService;

    @Inject
    private RecoPreprocesorService _recoPreprocesorService;

    @Inject
    private Scheduler _scheduler;

    @Inject
    private ClusteredScheduler _clusteredScheduler;

    @Inject
    private IdpMonitor _recoExecutorMonitor;

    @Inject
    private ClusterManager _clusterManager;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private LocaleContext _localeContext;

    @Inject
    private LLMProviderAndModelService _llmProviderAndModelService;

    private AppConfig _appConfig;
    private AIConfig _aiConfig;
    private IDPConfig _idpConfig;


    //
    // Init Methods -------------------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        _appConfig = _configService.getAppConfig(); // Do not use _configContext.getAppConfig() to avoid making changes directly into in-memory appConfig
        ConfigService.initAppConfigSystemUrlFromRequestUrl(_appConfig, Faces.getRequestURL()); // System URL init helper
        _aiConfig = _llmProviderAndModelService.getAiConfig();
        _idpConfig = _configService.getIDPConfig();
    }


    //
    // Actions (Buttons) Methods ------------------------------------------------------------------
    //


    /**
     * elConfig save action
     *
     * @return
     */
    public String btnSave() {
        try {
            // Save AppConfig
            if (!_configService.saveAppConfig(_appConfig)) {
                throw new ElDocRuntimeException("Error saving Application config");
            }

            // Refresh appConfig
            _configContext.initConfigByTenantId(TenantContext.getCurrentTenantId());

            // Save AI config
            _configService.saveAIConfig(_aiConfig);

            // Save IDP config
            _configService.saveIDPConfig(_idpConfig);

            JsfUtils.addInfoMessage("Configuration saved");
        } catch (Exception e) {
            LOG.error("Configuration save or re-init error", e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"), e.getMessage());
        }

        setModeRead();
        return null;
    }


    public String btnCancel() {
        super.setModeRead();
        return JsfUtils.getNavigationTarget(Pages.PAGE_ADMIN_CONFIG);
    }


    public String btnConfReload() {
        try {
            _appConfigService.reloadConfigurations();
            _llmProviderAndModelService.resetAndInitConfigFileModels();
            return JsfUtils.getNavigationTarget(Pages.PAGE_ADMIN_CONFIG);
        } catch (Exception e) {
            LOG.error("Configuration re-init error", e);
            JsfUtils.addErrorMessage(_localeContext.getLabel("msg.error.general"), e.getMessage());
            return null;
        }
    }


    public List<SelectItem> getFileFormatsSI() {
        List<SelectItem> result = new ArrayList<>();
        result.add(new SelectItem(AppConfig.FILES_FORMAT_OPENDOCUMENT, "Open Document format"));
        result.add(new SelectItem(AppConfig.FILES_FORMAT_MSOFFICE, "Microsoft Office format"));
        return result;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //


    /**
     * Returns include page name for inCloud deployment or onPrem. It uses overrides for debugging purposes
     *
     * @return
     */
    public String getIncludePageName() {
        return "/WEB-INF/includes/admin/config/full.xhtml";
    }


    public void setLogLevel(String target, String logLevelStr) {
        java.util.logging.Logger elDocLogger = java.util.logging.Logger.getLogger(AppConstants.ELDOC_LOGGER_NAME);

        if (elDocLogger == null) {
            return;
        }

        if ("ELDOC".equals(target)) {
            elDocLogger.setLevel(java.util.logging.Level.parse(logLevelStr));
        } else if ("*".equals(target)) {
            setAvgLogginLevel(logLevelStr);
        } else {
            if (elDocLogger.getParent() == null) {
                return;
            }

            for (java.util.logging.Handler handler : elDocLogger.getParent().getHandlers()) {
                if ("CONSOLE".equals(target) && handler instanceof java.util.logging.ConsoleHandler) {
                    handler.setLevel(java.util.logging.Level.parse(logLevelStr));
                    break;
                } else if ("FILE".equals(target) && handler != null && handler.getClass().toString().endsWith("FileHandler")) {
                    handler.setLevel(java.util.logging.Level.parse(logLevelStr));
                    break;
                }
            }
        }
    }


    public String getLogLevelBtnStypeClass(String target, String logLevelCheck) {
        java.util.logging.Level logLevelCurrent = null;
        java.util.logging.Logger elDocLogger = java.util.logging.Logger.getLogger(AppConstants.ELDOC_LOGGER_NAME);

        if (elDocLogger == null) {
            return "p-button-secondary p-button-outlined";
        }

        if ("ELDOC".equals(target)) {
            logLevelCurrent = elDocLogger.getLevel();
        } else if ("*".equals(target)) {
            logLevelCurrent = getAvgLogginLevel();
            if (logLevelCurrent == null && "DEFAULT".equals(logLevelCheck)) {
                return "";
            }
        } else {
            if (elDocLogger.getParent() == null) {
                return "p-button-secondary p-button-outlined";
            }

            for (java.util.logging.Handler handler : elDocLogger.getParent().getHandlers()) {
                if ("CONSOLE".equals(target) && handler instanceof java.util.logging.ConsoleHandler) {
                    logLevelCurrent = handler.getLevel();
                    break;
                } else if ("FILE".equals(target) && handler != null && handler.getClass().toString().endsWith("FileHandler")) {
                    logLevelCurrent = handler.getLevel();
                    break;
                }
            }
        }

        if (logLevelCurrent != null && Objects.equals(logLevelCheck, logLevelCurrent.getName())) {
            return "";
        } else {
            return "p-button-secondary p-button-outlined";
        }
    }


    private java.util.logging.Level getAvgLogginLevel() {
        java.util.logging.Level firstLogLevel = null;
        Enumeration<String> loggerNames = java.util.logging.LogManager.getLogManager().getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            String loggerName = loggerNames.nextElement();
            if (AppConstants.ELDOC_LOGGER_NAME.equals(loggerName) || loggerName == null || java.util.logging.Logger.getLogger(loggerName) == null || java.util.logging.Logger.getLogger(loggerName).getLevel() == null) {
                // We also skip eldoc logger as it is handled separately
                continue;
            }

            if (firstLogLevel == null) {
                // Init first logging level
                firstLogLevel = java.util.logging.Logger.getLogger(loggerName).getLevel();
            } else {
                // Compare first logging level to others. On first mismatch we assume that logging levels were set to some single level,
                // otherwise they are set to default levels
                if (!Objects.equals(firstLogLevel, java.util.logging.Logger.getLogger(loggerName).getLevel())) {
                    return null;
                }
            }
        }
        return firstLogLevel;
    }


    private void setAvgLogginLevel(String levelStr) {
        Enumeration<String> loggerNames = java.util.logging.LogManager.getLogManager().getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            String loggerName = loggerNames.nextElement();
            if (AppConstants.ELDOC_LOGGER_NAME.equals(loggerName) || loggerName == null || java.util.logging.Logger.getLogger(loggerName) == null || java.util.logging.Logger.getLogger(loggerName).getLevel() == null) {
                // We also skip eldoc logger as it is handled separately
                continue;
            }
            java.util.logging.Level level;
            if ("DEFAULT".equals(levelStr)) {
                // Means we need to reset to the default level
                String levelDefaultStr = _configContext.getDefaultLogLevels().get(loggerName);
                if (levelDefaultStr == null) {
                    level = java.util.logging.Level.WARNING; // Due to some reason this logger is not present in the default logLevels map, adding it as well
                    _configContext.getDefaultLogLevels().put(loggerName, java.util.logging.Level.WARNING.getName());
                } else {
                    level = java.util.logging.Level.parse(levelDefaultStr);
                }
            } else {
                level = java.util.logging.Level.parse(levelStr);
            }
            java.util.logging.Logger.getLogger(loggerName).setLevel(level);
        }
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    public AppConfig getAppConfig() {
        return _appConfig;
    }


    public AIConfig getAiConfig() {
        return _aiConfig;
    }


    public void setAiConfig(AIConfig aiConfig) {
        _aiConfig = aiConfig;
    }


    public IDPConfig getIdpConfig() {
        return _idpConfig;
    }


    public void setIdpConfig(IDPConfig idpConfig) {
        _idpConfig = idpConfig;
    }


    public boolean isAiDefaultModelAgentConfigurable() {
        return _llmProviderAndModelService.isAgentModelConfigurable();
    }


    public boolean isAiDefaultModelVisionConfigurable() {
        return _llmProviderAndModelService.isVisionModelConfigurable();
    }


    public boolean isIdpExtractionModelDefaultConfigurable() {
        return isAiDefaultModelVisionConfigurable();
    }


    public boolean isIdpClassificationModelDefaultConfigurable() {
        return isAiDefaultModelAgentConfigurable();
    }


    public String getIdpExtractionModelDefault() {
        if (isNotBlank(_idpConfig.getExtractionDefaultModel()) && isAiDefaultModelVisionConfigurable()) {
            // IDP Extraction Model is configured
            Pair<LLMProviderConfig, LLMModelConfig> configuredModel = _llmProviderAndModelService.getProviderAndModelByPairId(_idpConfig.getExtractionDefaultModel(), true);
            if (configuredModel != null) {
                return _idpConfig.getExtractionDefaultModel();
            }
        }

        // Extraction model either isn't configured, configured and not available or can't be configured (i.e., configured via configuration file) - use system default
        Pair<LLMProviderConfig, LLMModelConfig> idpExtractionModelFallback = _llmProviderAndModelService.getIdpExtractionModelConfigFallback();
        if (idpExtractionModelFallback != null) {
            return LLMProviderAndModelService.getProviderAndModelPairId(idpExtractionModelFallback.getLeft(), idpExtractionModelFallback.getRight());
        }

        // No model available
        return null;
    }


    public void setIdpExtractionModelDefault(String idpExtractionModel) {
        _idpConfig.setExtractionDefaultModel(idpExtractionModel);
    }


    public String getIdpClassificationModelDefault() {
        if (isNotBlank(_idpConfig.getClassificationDefaultModel()) && isAiDefaultModelAgentConfigurable()) {
            // IDP Extraction Model is configured
            Pair<LLMProviderConfig, LLMModelConfig> configuredModel = _llmProviderAndModelService.getProviderAndModelByPairId(_idpConfig.getClassificationDefaultModel(), true);
            if (configuredModel != null) {
                return _idpConfig.getClassificationDefaultModel();
            }
        }

        // Classification model either isn't configured, configured and not available or can't be configured (i.e., configured via configuration file) - use system default
        Pair<LLMProviderConfig, LLMModelConfig> idpClassificationModelFallback = _llmProviderAndModelService.getIdpClassificationModelConfigFallback();
        if (idpClassificationModelFallback != null) {
            return LLMProviderAndModelService.getProviderAndModelPairId(idpClassificationModelFallback.getLeft(), idpClassificationModelFallback.getRight());
        }

        // No model available
        return null;
    }


    public void setIdpClassificationModelDefault(String idpClassificationModel) {
        _idpConfig.setClassificationDefaultModel(idpClassificationModel);
    }


    public List<SelectItem> getAiDefaultModelAgentSI() {
        return aiModelsToSelectItems(_llmProviderAndModelService.getAgentModels(true));
    }


    public List<SelectItem> getAiDefaultModelVisionSI() {
        return aiModelsToSelectItems(_llmProviderAndModelService.getVisionModels(true));
    }


    public static List<SelectItem> aiModelsToSelectItems(List<Pair<LLMProviderConfig, LLMModelConfig>> models) {
        List<SelectItem> result = new ArrayList<>();
        for (Pair<LLMProviderConfig, LLMModelConfig> model : models) {
            String modelLabel = model.getLeft().getName() + " - " + model.getRight().getNameEffective();
            String modelSystemId = LLMProviderAndModelService.getProviderAndModelPairId(model.getLeft(), model.getRight());
            result.add(new SelectItem(modelSystemId, modelLabel));
        }
        return result;
    }


    public List<String> getRecoPreProcessingItems() {
        return _recoPreprocesorService.getProcessingItemList();
    }


    public String getRecoProcessingItem() {
        return _recoExecutorMonitor.getWorkflowIdInWorkProcessor(TenantContext.getCurrentTenantId());
    }


    public String getRecoConversionItem() {
        return _recoExecutorMonitor.getWorkflowIdInWorkConversion(TenantContext.getCurrentTenantId());
    }


    public boolean isPrimaryNode() {
        return _clusterManager.isPrimaryNode();
    }


    //
    // Language related methods and getters -------------------------------------------------------
    //
    public List<SelectItem> getLangsSI() {
        return I18n.LANG_SYSTEM_CODES_ALL.stream()
                .map(lang -> new SelectItem(lang, _localeContext.getLabel("general.lang." + lang + ".label")))
                .sorted(Comparator.comparing(si -> (String) si.getValue()))
                .toList();
    }


    public List<String> completeLang(String query) {
        return getLangsSI().stream()
                .filter(langSI -> Strings.CI.contains((String) langSI.getValue(), query) || Strings.CI.contains(langSI.getLabel(), query))
                .map(langSI -> (String) langSI.getValue())
                .toList();
    }


    public String getLangUiLabelByCode(String langCode) {
        return getLangsSI().stream()
                .filter(langSI -> Objects.equals(langSI.getValue(), langCode))
                .map(SelectItem::getLabel)
                .findFirst()
                .orElse(null);
    }


    //
    // Indexes housekeeping -----------------------------------------------------------------------
    //
    public void doWorkflowsIndexesHouskeeping() {
        DaoUtils.getWorkflowDaoTenant().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: Workflows");
    }


    public void doWorkflowTasksIndexesHouskeeping() {
        DaoUtils.getWorkflowTaskDaoTenant().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: WorkflowTasks");
    }


    public void doFormDefIndexesHouskeeping() {
        DaoUtils.getFormDefDao().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: FormDefinitions");
    }


    public void doLogIndexesHouskeeping() {
        DaoUtils.getLogDao().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: Logs");
    }


    public void doReactionIndexesHouskeeping() {
        DaoUtils.getReactionDao().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: Reactions");
    }


    public void doReminderIndexesHouskeeping() {
        DaoUtils.getReminderDao().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: Reminders");
    }


    public void doListValueIndexesHouskeeping() {
        DaoUtils.getListValueDao().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: ListValues");
    }


    public void doNumSequenceIndexesHouskeeping() {
        DaoUtils.getNumSequenceDao().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: NumSequences");
    }


    public void doBpmProcessDefIndexesHouskeeping() {
        DaoUtils.getBpmProcessDefinitionDao().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: BpmProcessDefs");
    }


    public void doRecoFormIndexesHouskeeping() {
        DaoUtils.getRecoFormDao().doIndexHousekeep();
        JsfUtils.addInfoMessage("Indexes update completed for: RecoForms");
    }
}
