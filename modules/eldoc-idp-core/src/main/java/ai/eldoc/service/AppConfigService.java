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
import ai.eldoc.crypto.SymmetricCryptoV2;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;
import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.interpol.ConfigurationInterpolator;
import org.apache.commons.configuration2.interpol.Lookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.naming.*;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * elDoc Configuration Service
 */
@Startup
@Singleton
@TransactionManagement(TransactionManagementType.BEAN)
public class AppConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfigService.class);

    public static final String IDP_OSS_DEFAULT_TENANT_NAME = "idposs";

    private static final String ELDOC_CONFIG_DIR_SYSTEM_VAR = "ELDOC_IDP_OSS_CONFIG_DIR";
    private static final String ELDOC_CONFIG_DIR_JVM_OPTION = "ai.eldoc.idp-oss.configDir";
    private static final String ELDOC_CONFIG_FILENAME = "eldoc-idp-oss.conf";

    private static final String TENANTID_PATTERN = "\\w{1,20}";

    private PropertiesConfiguration _config;

    private ElDocConfig _elDocConfig;
    private Map<String, TenantConfig> _tenantConfigMap;
    private EmbeddedMongoConfig _embeddedMongoConfig;
    private RememberMeConfig _rememberMeConfig;
    private IdpConfig _idpConfig;
    private AiConfig _aiConfig;


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @PostConstruct
    void init() {
        try {
            LOG.info("AppConfigService: Initialization started");

            // Trying to resolve config dir in JVM options first, and then in system variables
            String eldocConfigDir = resolveConfigDirectory(
                    System.getProperty(ELDOC_CONFIG_DIR_JVM_OPTION),
                    System.getenv(ELDOC_CONFIG_DIR_SYSTEM_VAR),
                    Path.of("").toAbsolutePath());

            if (eldocConfigDir == null || eldocConfigDir.isBlank()) {
                LOG.warn("No configuration file was found via JVM option '-D{}', environment variable '{}', or working directory and its parents; continuing with default configuration",
                        ELDOC_CONFIG_DIR_JVM_OPTION, ELDOC_CONFIG_DIR_SYSTEM_VAR);
                _config = new PropertiesConfiguration();
                _config.setProperty("tenantId", IDP_OSS_DEFAULT_TENANT_NAME);
                _config.setProperty("idp.ocr.type", "tess");
            } else {
                LOG.info("elDoc Configuration directory resolved to: {}", eldocConfigDir);

                // Build configuration first, then register custom lookups on its interpolator
                FileBasedConfigurationBuilder<PropertiesConfiguration> builder = new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                        .configure(new Parameters()
                                .fileBased()
                                .setEncoding("UTF-8")
                                .setBasePath(eldocConfigDir)
                                .setFileName(ELDOC_CONFIG_FILENAME)
                                .setListDelimiterHandler(new DefaultListDelimiterHandler(','))
                        );
                _config = builder.getConfiguration();
            }

            ConfigurationInterpolator interpolator = _config.getInterpolator();
            // Important for  ${file:${env:ENV_VAR}}
            interpolator.setEnableSubstitutionInVariables(true);

            // Register custom lookups, including nested-resolution-friendly File lookup
            interpolator.registerLookup(EnvVarWithEmptyStringFallbackLookup.PREFIX, new EnvVarWithEmptyStringFallbackLookup());
            interpolator.registerLookup(FileLookup.PREFIX, new FileLookup());

            // Perform eager loading of all configuration values now (fail fast)
            configurationKeysEagerLoad();

            _elDocConfig = new ElDocConfig();
            initTenantConfig();
            _embeddedMongoConfig = EmbeddedMongoConfig.create(getConfig());
            _rememberMeConfig = new RememberMeConfig();
            _idpConfig = new IdpConfig();
            _aiConfig = new AiConfig();

            LOG.info("AppConfigService: Initialization completed");
        } catch (ConfigurationException | ElDocRuntimeException ex) {
            // Throwing exception in @PostConstruct method of the @Startup @Singleton will trigger app undeploy as it is treated as failed to initialize and deploy
            throw new ElDocRuntimeException("elDoc Initialization error: " + ex.getMessage());
        } catch (Exception e) {
            // Throwing exception in @PostConstruct method of the @Startup @Singleton will trigger app undeploy as it is treated as failed to initialize and deploy
            throw new ElDocRuntimeException("elDoc Initialization error", e);
        }
    }


    static String resolveConfigDirectory(String jvmOption, String environmentVariable, Path workingDirectory) {
        String configuredDirectory = jvmOption != null ? jvmOption : environmentVariable;
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return configuredDirectory;
        }

        Path currentDirectory = workingDirectory.toAbsolutePath().normalize();
        while (currentDirectory != null) {
            Path configDirectory = currentDirectory.resolve("configs");
            if (Files.isRegularFile(configDirectory.resolve(ELDOC_CONFIG_FILENAME))) {
                return configDirectory.toString();
            }
            currentDirectory = currentDirectory.getParent();
        }
        return null;
    }


    /**
     * Performs eager loading of configuration keys to trigger all possible interpolations
     */
    private void configurationKeysEagerLoad() throws ConfigurationException {
        List<String> errors = new ArrayList<>();
        // Iterate over all keys and force resolution using typed getters (interpolation-aware)
        Iterator<String> keysIter = _config.getKeys();
        LOG.trace("Starting eager loading of configuration keys to trigger interpolation and detect potential issues early...");
        while (keysIter.hasNext()) {
            String key = keysIter.next();
            try {
                Object raw = _config.getProperty(key);
                if (raw instanceof Collection) {
                    Object value = _config.getList(String.class, key);
                    LOG.trace("{}={}", key, value);
                } else {
                    Object value = _config.getString(key);
                    LOG.trace("{}={}", key, value);
                }
            } catch (Exception ex) {
                errors.add(key);
            }
        }

        if (!errors.isEmpty()) {
            throw new ConfigurationException("Configuration warm-up failed. See previous logs for details. Keys failed to load: " + String.join(", ", errors));
        }
    }


    @Lock(LockType.READ)
    private ImmutableConfiguration getConfig() {
        return ConfigurationUtils.unmodifiableConfiguration(_config);
    }


    /**
     * Reloads configurations from file by doing configuration factory re-init
     */
    @Lock(LockType.WRITE)
    public void reloadConfigurations() {
        init();
    }


    private void initTenantConfig() {
        _tenantConfigMap = new ConcurrentHashMap<>();
        getElDocConfig().getTenantIdList().forEach(tenantId -> {
            try {
                // validation for matching: (regex construct \w{1,20}) characters: [a-zA-Z_0-9]
                if (!tenantId.matches(TENANTID_PATTERN)) {
                    throw new ElDocRuntimeException("TenantId doesn't match the pattern: '" + TENANTID_PATTERN + "', skipping...");
                }
                _tenantConfigMap.put(tenantId, TenantConfig.createFromConfigForTenantId(getConfig(), tenantId));
            } catch (ElDocRuntimeException ex) {
                LOG.error("Tenant[{}] {}", tenantId, ex.getMessage());
            } catch (Exception e) {
                LOG.error("Tenant[{}] Error loading configuration", tenantId, e);
            }
        });
    }


    private void doKeysCheck(String prefix, String[] configKeySuffixArray) {
        doKeysCheck(Arrays.stream(configKeySuffixArray).map(suffix -> prefix + suffix).toArray(String[]::new));
    }


    private void doKeysCheck(String[] configKeys) {
        boolean isValid = true;
        for (String key : configKeys) {
            if (!getConfig().containsKey(key)) {
                // key is not present
                isValid = false;
                LOG.error("Config key was not found: {}", key);
            }
        }
        if (!isValid) {
            throw new ElDocRuntimeException("Configuration is not valid. Please, check log and adjust " + ELDOC_CONFIG_FILENAME + " file accordingly");
        }
    }


    public static AppConfigService getInstance() {
        try {
            Context ic = new InitialContext();
            return (AppConfigService) ic.lookup("java:global/" + ic.lookup("java:app/AppName") + "/AppConfigService!ai.eldoc.service.AppConfigService");
        } catch (NamingException ex) {
            LOG.error(null, ex);
        }
        return null;
    }


    //
    // Getters for Tenant Configs -----------------------------------------------------------------
    //
    @Lock(LockType.READ)
    public Set<String> getTenantIdSet() {
        return _tenantConfigMap.keySet();
    }


    @Lock(LockType.READ)
    public Collection<TenantConfig> getTenantConfigList() {
        return _tenantConfigMap.values();
    }


    @Lock(LockType.READ)
    public TenantConfig getTenantConfig(String tenantId) {
        return _tenantConfigMap.get(tenantId);
    }


    @Lock(LockType.READ)
    public List<TenantConfig> getTenantConfigsRequiringEmbeddedMongo() {
        return tenantConfigsRequiringEmbeddedMongo(_tenantConfigMap.values());
    }


    static List<TenantConfig> tenantConfigsRequiringEmbeddedMongo(Collection<TenantConfig> tenantConfigs) {
        return tenantConfigs.stream()
                .filter(TenantConfig::requiresEmbeddedMongo)
                .toList();
    }


    @Lock(LockType.WRITE)
    public boolean applyEmbeddedMongoConnection(String tenantId, String dbConnection) {
        if (isBlank(dbConnection)) {
            throw new IllegalArgumentException("Embedded MongoDB connection string cannot be blank");
        }

        TenantConfig tenantConfig = _tenantConfigMap.get(tenantId);
        if (tenantConfig == null || !tenantConfig.requiresEmbeddedMongo()) {
            return false;
        }

        _tenantConfigMap.put(tenantId, tenantConfig.withDBConnection(dbConnection));
        return true;
    }


    /**
     * Adds new TenantConfig by the given tenantId into the tenantConfigMap<br>
     * <pre>
     * IMPORTANT:
     * - In case tenantId already present in the configuration map - it will overwrite existing config.
     * - For the newly added tenant config you have to handle initialization of the DbClientService on your own as well!
     * </pre>
     *
     * @param tenantId
     * @param tenantConfig
     */
    @Lock(LockType.READ)
    public void addTenantConfig(String tenantId, TenantConfig tenantConfig) {
        _tenantConfigMap.put(tenantId, tenantConfig);
    }


    /**
     * Removes TenantConfig by the given tenantId from the the tenantConfigMap<br>
     * Results during runtime removal are not known & not predicateble as of now
     * <pre>
     * IMPORTANT:
     * - Consider removing DbClient from the DbClientService for the removed tenantId (can be dangereous)
     * </pre>
     *
     * @param tenantId
     */
    @Lock(LockType.READ)
    public void removeTenantConfig(String tenantId) {
        _tenantConfigMap.remove(tenantId);
    }


    /**
     * Returns tenantId list by the given domain in case any found (based on defined ones in the eldoc-idp-oss.conf file).<br>
     * In case no tenantId were found by given domian - returns list of tenants with empty Domain or catch-all "*" Domain
     *
     * @param domain
     * @return
     */
    @Lock(LockType.READ)
    public List<String> getTenantListByDomain(String domain) {
        List<String> tenantList;

        // Try to locate TenantConfigs with the given URL
        tenantList = _tenantConfigMap.values().stream()
                .filter(tenantConfig -> tenantConfig.getDomain().equalsIgnoreCase(domain))
                .map(TenantConfig::getTenantId)
                .collect(Collectors.toList());

        if (!tenantList.isEmpty()) {
            // In case any tenants were found with the exact match to the given URL - return them and exit
            if (LOG.isTraceEnabled()) {
                LOG.trace("Tenants list by FQDN({}) with exact match returned only: {}", domain, tenantList);
            }
            return tenantList;
        }

        tenantList = _tenantConfigMap.values().stream()
                .filter(tenantConfig -> tenantConfig.getDomain().equals("*") || isBlank(tenantConfig.getDomain()))
                .map(TenantConfig::getTenantId)
                .collect(Collectors.toList());

        if (LOG.isTraceEnabled()) {
            LOG.trace("Tenants list by FQDN({}) with wildcard match returned only: {}", domain, tenantList);
        }

        return tenantList;

    }


    //
    // Getters for configs ------------------------------------------------------------------------
    //
    @Lock(LockType.READ)
    public ElDocConfig getElDocConfig() {
        return _elDocConfig;
    }


    @Lock(LockType.READ)
    public EmbeddedMongoConfig getEmbeddedMongoConfig() {
        return _embeddedMongoConfig;
    }


    @Lock(LockType.READ)
    public RememberMeConfig getRememberMeConfig() {
        return _rememberMeConfig;
    }


    @Lock(LockType.READ)
    public IdpConfig getIdpConfig() {
        return _idpConfig;
    }


    @Lock(LockType.READ)
    public AiConfig getAiConfig() {
        return _aiConfig;
    }


    /**
     * Reads Double value from config with fallback to the provided default value if the variable is not configured
     * or resolves to empty string
     *
     * @param key Configuration key to extract
     * @param defaultValue Value to be used if the key is missing from config or resolves to empty string
     * @return Configured value or default, if not set in config
     * @throws NumberFormatException if the string does not contain a parsable number.
     */
    private Double getConfigValue(String key, Double defaultValue) throws NumberFormatException {
        String configuredValue = getConfig().getString(key);
        if (isBlank(configuredValue)) {
            return defaultValue;
        }
        return Double.valueOf(configuredValue);
    }


    /**
     * Reads Integer value from config with fallback to the provided default value if the variable is not configured
     * or resolves to empty string
     *
     * @param key Configuration key to extract
     * @param defaultValue Value to be used if the key is missing from config or resolves to empty string
     * @return Configured value or default, if not set in config
     * @throws NumberFormatException if the string does not contain a parsable number.
     */
    private Integer getConfigValue(String key, Integer defaultValue) throws NumberFormatException {
        String configuredValue = getConfig().getString(key);
        if (isBlank(configuredValue)) {
            return defaultValue;
        }
        return Integer.valueOf(configuredValue);
    }


    /**
     * Reads Boolean value from config with fallback to the provided default value if the variable is not configured
     * or resolves to empty string
     *
     * @param key Configuration key to extract
     * @param defaultValue Value to be used if the key is missing from config or resolves to empty string
     * @return Configured value or default, if not set in config
     */
    private Boolean getConfigValue(String key, Boolean defaultValue) {
        String configuredValue = getConfig().getString(key);
        if (isBlank(configuredValue)) {
            return defaultValue;
        }
        return Boolean.valueOf(configuredValue);
    }


    //
    // Specific configuration getters -------------------------------------------------------------
    //

    /**
     * elDoc general configurations
     *
     */
    public class ElDocConfig {

        private static final String PARAM_TENANTID = "tenantId"; // main param to be used in SaaS-based deployments
        private static final String PARAM_I18N_LANGS = "i18n.langs"; // enabled UI languages
        private static final String PARAM_URL_CIPHER_KEY = "url.cipherKey";

        private final byte[] _defaultUrlCipherKey = SymmetricCryptoV2.generateKey256bit();


        /**
         * Returns tenantId list (like ["cidXXX", "cidYYY"]), which used as db name suffix: eldoc_cidXXX<br>
         * IMPORTANT: This is for internal use inside AppConfigService only. For the public access please use {@link AppConfigService#getTenantIdSet()}
         *
         * @return tenantId list or defaults to empty list
         */
        private List<String> getTenantIdList() {
            return getConfig().getList(String.class, PARAM_TENANTID, new ArrayList<>());
        }


        /**
         * Returns list of default enabled system languages<br>
         *
         * @return
         */
        public List<String> getLangsDefault() {
            return getConfig().getList(String.class, PARAM_I18N_LANGS, Arrays.asList());
        }


        /**
         * Returns bytes arrays which contains cipher key. In case of error reading or decoding param from config - default key is returned
         *
         * @return
         */
        public byte[] getUrlCipherKey() {
            try {
                if (isNotBlank(getConfig().getString(PARAM_URL_CIPHER_KEY))) {
                    return Base64.getDecoder().decode(getConfig().getString(PARAM_URL_CIPHER_KEY));
                }
            } catch (Exception e) {
                LOG.error("Error reading or base64-decoding param '{}': {}", PARAM_URL_CIPHER_KEY, e.getMessage());
            }
            LOG.warn("Using a temporary random cipher key; provide a persistent generated key for the param: {}", PARAM_URL_CIPHER_KEY);
            return _defaultUrlCipherKey.clone();
        }
    }


    /**
     * RememberMe Configuration settings for Apache Shiro
     */
    public class RememberMeConfig {

        private static final String _REMEMBERME_CIPHERKEY = "rememberMe.cipherKey";
        private static final String _REMEMBERME_COOKIE_NAME = "rememberMe.cookie.name";
        private static final String _REMEMBERME_COOKIE_MAXAGE = "rememberMe.cookie.maxAge";

        private final String _defaultCipherKey = Base64.getEncoder().encodeToString(SymmetricCryptoV2.generateKey256bit());


        public String getCipherKey() {
            try {
                String cipherKey = getConfig().getString(_REMEMBERME_CIPHERKEY);
                if (isNotBlank(cipherKey)) {
                    Base64.getDecoder().decode(cipherKey);
                    return cipherKey;
                }
            } catch (Exception e) {
                LOG.error("Error reading or base64-decoding param '{}': {}", _REMEMBERME_CIPHERKEY, e.getMessage());
            }
            LOG.warn("Using a temporary random cipher key; provide a persistent generated key for the param: {}", _REMEMBERME_CIPHERKEY);
            return _defaultCipherKey;
        }


        public String getCookieName() {
            return getConfig().getString(_REMEMBERME_COOKIE_NAME);
        }


        public String getCookieMaxEge() {
            return getConfig().getString(_REMEMBERME_COOKIE_MAXAGE);
        }

    }


    /**
     * Embedded MongoDB runtime configuration.
     */
    public static class EmbeddedMongoConfig {

        static final String PARAM_DATA_DIR = "embeddedMongo.dataDir";
        static final String PARAM_BINARY_CACHE_DIR = "embeddedMongo.binaryCacheDir";
        static final String PARAM_HOST = "embeddedMongo.host";
        static final String PARAM_PORT = "embeddedMongo.port";
        static final String PARAM_VERSION = "embeddedMongo.version";
        static final String PARAM_REPLICA_SET = "embeddedMongo.replicaSet";

        static final String DEFAULT_HOST = "127.0.0.1";
        static final int DEFAULT_PORT = 0;
        static final String DEFAULT_VERSION = "8.2";
        static final String DEFAULT_REPLICA_SET = "eldoc-rs";

        private final Path _dataDir;
        private final Path _binaryCacheDir;
        private final String _host;
        private final int _port;
        private final String _version;
        private final String _replicaSet;


        private EmbeddedMongoConfig(Path dataDir, Path binaryCacheDir, String host, int port, String version, String replicaSet) {
            _dataDir = dataDir;
            _binaryCacheDir = binaryCacheDir;
            _host = host;
            _port = port;
            _version = version;
            _replicaSet = replicaSet;
        }


        static EmbeddedMongoConfig create(ImmutableConfiguration config) {
            Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
            Path dataDir = getPath(config, PARAM_DATA_DIR, tempDir.resolve("eldoc-idp-oss-db-data"));
            Path binaryCacheDir = getPath(config, PARAM_BINARY_CACHE_DIR, tempDir.resolve("eldoc-idp-oss-db-bin"));
            String host = getString(config, PARAM_HOST, DEFAULT_HOST);
            String version = getString(config, PARAM_VERSION, DEFAULT_VERSION);
            String replicaSet = getString(config, PARAM_REPLICA_SET, DEFAULT_REPLICA_SET);
            if (!replicaSet.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("Embedded MongoDB replica-set name contains unsupported characters: " + replicaSet);
            }
            int port = getPort(config.getString(PARAM_PORT, ""));
            return new EmbeddedMongoConfig(dataDir, binaryCacheDir, host, port, version, replicaSet);
        }


        private static Path getPath(ImmutableConfiguration config, String key, Path defaultPath) {
            String configuredPath = config.getString(key, "");
            return (isBlank(configuredPath) ? defaultPath : Path.of(configuredPath)).toAbsolutePath().normalize();
        }


        private static String getString(ImmutableConfiguration config, String key, String defaultValue) {
            String configuredValue = config.getString(key, "");
            return isBlank(configuredValue) ? defaultValue : configuredValue.trim();
        }


        private static int getPort(String configuredPort) {
            if (isBlank(configuredPort)) {
                return DEFAULT_PORT;
            }
            try {
                int port = Integer.parseInt(configuredPort.trim());
                if (port < 0 || port > 65535) {
                    throw new IllegalArgumentException("Embedded MongoDB port must be between 0 and 65535: " + port);
                }
                return port;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Embedded MongoDB port must be a number: " + configuredPort, e);
            }
        }


        public Path getDataDir() {
            return _dataDir;
        }


        public Path getBinaryCacheDir() {
            return _binaryCacheDir;
        }


        public String getHost() {
            return _host;
        }


        public int getPort() {
            return _port;
        }


        public String getVersion() {
            return _version;
        }


        public String getReplicaSet() {
            return _replicaSet;
        }
    }


    public class IdpConfig {

        private static final String PARAM_OCR_TYPE = "idp.ocr.type";
        private static final String PARAM_OCR_URL = "idp.ocr.url";
        private static final String PARAM_TESSDATA_PATH = "idp.tessdata.path";
        private volatile String _runtimeTessDataPath;


        /**
         * Returns OCR type ('tess', etc) for the requested tenantId (if set per tenant), otherwise returns default OCR type set per the whole system
         *
         * @param tenantId tenantId or null for getting the default system value
         * @return
         */
        public String getOcrType(String tenantId) {
            if (tenantId != null) {
                return getConfig().getString(PARAM_OCR_TYPE + "." + tenantId, getConfig().getString(PARAM_OCR_TYPE, ""));
            }
            return getConfig().getString(PARAM_OCR_TYPE, "");
        }


        public String getOcrUrl() {
            return getConfig().getString(PARAM_OCR_URL, "");
        }


        public String getTessDataPath() {
            String runtimeTessDataPath = _runtimeTessDataPath;
            return runtimeTessDataPath == null ? getConfig().getString(PARAM_TESSDATA_PATH, "") : runtimeTessDataPath;
        }


        void setRuntimeTessDataPath(String path) {
            _runtimeTessDataPath = path;
        }
    }


    public final class AiConfig {

        private final AiModelConfig _agentModelConfig;
        private final AiModelConfig _visionModelConfig;


        public AiConfig() {
            _agentModelConfig = new AiModelConfig(AiModelConfig.MODEL_TYPE_AGENT);
            _visionModelConfig = new AiModelConfig(AiModelConfig.MODEL_TYPE_VISION);
        }


        public AiModelConfig getAgentModelConfig() {
            return _agentModelConfig;
        }


        public AiModelConfig getVisionModelConfig() {
            return _visionModelConfig;
        }

    }


    public final class AiModelConfig {

        public static final String MODEL_TYPE_AGENT = "agent";
        public static final String MODEL_TYPE_VISION = "vl";

        public static final String MODEL_PROVIDER_AWS = "bedrock";
        public static final String MODEL_PROVIDER_OPENAI = "openai";
        public static final String MODEL_PROVIDER_ONNX = "onnx";

        private static final String PARAM_PFX = "ai.model.";

        //
        // General parameters
        //
        private static final String PARAM_SFX_PROVIDER = ".provider";
        private static final String PARAM_SFX_SECRET = ".secret";
        private static final String PARAM_SFX_TIMEOUT_SEC = ".timeoutSec";
        private static final String PARAM_SFX_MODEL_ID = ".modelId";
        private static final String PARAM_SFX_MODEL_DIMS = ".modelDims";
        private static final String PARAM_SFX_MODEL_PATH = ".modelPath";
        private static final String PARAM_SFX_MODEL_MAX_OUTPUT_TOKENS = ".maxTokensOut";
        private static final String PARAM_SFX_MODEL_REQUEST_MAX_SIZE_MB = ".requestMaxSizeMB";
        private static final String PARAM_SFX_MODEL_PARAMS = ".param";

        //
        // Bedrock-specific parameters
        //
        private static final String PARAM_SFX_BEDROCK_REGION = ".bedrock.region";
        private static final String PARAM_SFX_BEDROCK_KEY_ID = ".bedrock.keyId";

        //
        // OpenAI API-specific parameters
        //
        private static final String PARAM_SFX_OPENAI_ENDPOINT = ".openai.endpoint";

        private final String _modelType;


        public AiModelConfig(String modelType) {
            _modelType = modelType;
        }


        public boolean isProviderBedrock() {
            return MODEL_PROVIDER_AWS.equals(getProvider());
        }


        public boolean isProviderOpenai() {
            return MODEL_PROVIDER_OPENAI.equals(getProvider());
        }


        public boolean isProviderOnnx() {
            return MODEL_PROVIDER_ONNX.equals(getProvider());
        }


        /**
         * @return {@code true} if at least one AI LLM model provider is properly configured, {@code false} otherwise
         */
        public boolean isAiModelAvailable() {
            return (isProviderBedrock() && isBedrockAiConfigured())
                    || (isProviderOpenai() && isOpenAiConfigured())
                    || (isProviderOnnx() && isNotBlank(getModelPath()));
        }


        /**
         * @return {@code true} if all parameters, required for connecting to AWS Bedrock, configured, {@code false} otherwise
         */
        private boolean isBedrockAiConfigured() {
            return isNotBlank(getBedrockKeyId())
                    && isNotBlank(getSecret())
                    && isNotBlank(getBedrockRegion())
                    && isNotBlank(getModelId());
        }


        /**
         * @return {@code true} if all parameters, required for connecting to OpenAI, configured, {@code false} otherwise
         */
        private boolean isOpenAiConfigured() {
            return isNotBlank(getOpenaiEndpoint()) && isNotBlank(getModelId());
        }


        public String getProvider() {
            return getConfig().getString(PARAM_PFX + _modelType + PARAM_SFX_PROVIDER);
        }


        /**
         * @return model provider API- or Secret-key (used with providers like OpenAI API, Bedrock API, etc.)
         */
        public String getSecret() {
            return getConfig().getString(PARAM_PFX + _modelType + PARAM_SFX_SECRET);
        }


        public Integer getTimeoutSec() {
            return getConfigValue(PARAM_PFX + _modelType + PARAM_SFX_TIMEOUT_SEC, (Integer) null);
        }


        public String getModelId() {
            return getConfig().getString(PARAM_PFX + _modelType + PARAM_SFX_MODEL_ID);
        }


        public Integer getModelDimensions() {
            return getConfigValue(PARAM_PFX + _modelType + PARAM_SFX_MODEL_DIMS, (Integer) null);
        }


        public String getModelPath() {
            return getConfig().getString(PARAM_PFX + _modelType + PARAM_SFX_MODEL_PATH);
        }


        public int getMaxOutputTokens() {
            return getConfigValue(PARAM_PFX + _modelType + PARAM_SFX_MODEL_MAX_OUTPUT_TOKENS, -1);
        }


        public double getRequestMaxSizeMB() {
            return getConfigValue(PARAM_PFX + _modelType + PARAM_SFX_MODEL_REQUEST_MAX_SIZE_MB, 0.0);
        }


        public Map<String, Object> getParams() {
            Map<String, Object> params = new HashMap<>();
            ImmutableConfiguration subset = getConfig().immutableSubset(PARAM_PFX + _modelType + PARAM_SFX_MODEL_PARAMS);
            subset.getKeys().forEachRemaining(key -> params.put(key, subset.getProperty(key)));
            return params;
        }


        public String getBedrockRegion() {
            return getConfig().getString(PARAM_PFX + _modelType + PARAM_SFX_BEDROCK_REGION);
        }


        public String getBedrockKeyId() {
            return getConfig().getString(PARAM_PFX + _modelType + PARAM_SFX_BEDROCK_KEY_ID);
        }


        public String getOpenaiEndpoint() {
            return getConfig().getString(PARAM_PFX + _modelType + PARAM_SFX_OPENAI_ENDPOINT);
        }


        public String getModelType() {
            return _modelType;
        }

    }


    /**
     * Tenant Configuration Model
     */
    public static class TenantConfig implements Serializable {

        @Serial
        private static final long serialVersionUID = 6368341742810042724L;

        private static final String PARAM_SFX_DB_CONN = ".db.conn";
        private static final String PARAM_SFX_DB_USER = ".db.user";
        private static final String PARAM_SFX_DB_PWD = ".db.pwd";
        private static final String PARAM_SFX_DB_AUTH = ".db.auth";
        private static final String PARAM_SFX_DB_NAME = ".db.name";

        private static final String PARAM_SFX_DB_TLS = ".db.tls";
        private static final String PARAM_SFX_DB_TRUSTSTORE = ".db.ts";
        private static final String PARAM_SFX_DB_TRUSTSTORE_PWD = ".db.tsPwd";
        private static final String PARAM_SFX_DB_KEYSTORE = ".db.ks";
        private static final String PARAM_SFX_DB_KEYSTORE_PWD = ".db.ksPwd";

        private static final String PARAM_SFX_DOMAIN = ".domain";

        private final String _tenantId; // has to match regex pattern '\w', allowed characters: [a-zA-Z_0-9]
        private final String _dbConn;
        private final String _dbName;
        private final String _dbAuth;
        private final String _dbUser;
        private final char[] _dbPwd;
        private final boolean _tls;
        private final String _trustStorePath;
        private final char[] _trustStorePwd;
        private final String _keyStorePath;
        private final char[] _keyStorePwd;
        private final String _domain;


        /**
         * Constructor to be used for adding Tenant configuration via CloudManager API
         *
         * @param tenantId
         * @param dbConn
         * @param dbName
         * @param dbAuth
         * @param dbUser
         * @param dbPwd
         * @param tls
         * @param trustStorePath
         * @param trustStorePwd
         * @param keyStorePath
         * @param keyStorePwd
         * @param domain defined Domain access address or use null or "*" for default
         */
        public TenantConfig(String tenantId, String dbConn, String dbName, String dbAuth, String dbUser, String dbPwd, boolean tls, String trustStorePath, String trustStorePwd, String keyStorePath, String keyStorePwd, String domain) {
            this(tenantId, dbConn, dbName, dbAuth, dbUser, dbPwd != null ? dbPwd.toCharArray() : null, tls,
                    trustStorePath, trustStorePwd != null ? trustStorePwd.toCharArray() : null,
                    keyStorePath, keyStorePwd != null ? keyStorePwd.toCharArray() : null, domain);
        }


        private TenantConfig(String tenantId, String dbConn, String dbName, String dbAuth, String dbUser, char[] dbPwd, boolean tls, String trustStorePath, char[] trustStorePwd, String keyStorePath, char[] keyStorePwd, String domain) {
            _tenantId = tenantId;
            _dbConn = dbConn;
            _dbName = dbName;
            _dbAuth = dbAuth;
            _dbUser = dbUser;
            _dbPwd = copy(dbPwd);
            _tls = tls;
            _trustStorePath = trustStorePath;
            _trustStorePwd = copy(trustStorePwd);
            _keyStorePath = keyStorePath;
            _keyStorePwd = copy(keyStorePwd);
            _domain = isBlank(domain) ? "*" : domain;
        }


        public TenantConfig withDBConnection(String dbConnection) {
            if (isBlank(dbConnection)) {
                throw new IllegalArgumentException("DB connection string cannot be blank");
            }
            return new TenantConfig(_tenantId, dbConnection, _dbName, _dbAuth, _dbUser, _dbPwd, _tls,
                    _trustStorePath, _trustStorePwd, _keyStorePath, _keyStorePwd, _domain);
        }


        private static char[] copy(char[] value) {
            return value != null ? value.clone() : null;
        }


        /**
         * Constructor to be used for reading Tenant configuration from config file
         *
         * @param tenantId
         */
        private static TenantConfig createFromConfigForTenantId(ImmutableConfiguration config, String tenantId) {
            return new TenantConfig(
                    tenantId,
                    config.getString(tenantId + PARAM_SFX_DB_CONN, ""),
                    config.getString(tenantId + PARAM_SFX_DB_NAME),
                    config.getString(tenantId + PARAM_SFX_DB_AUTH),
                    config.getString(tenantId + PARAM_SFX_DB_USER, ""),
                    config.getString(tenantId + PARAM_SFX_DB_PWD, ""),
                    Boolean.parseBoolean(config.getString(tenantId + PARAM_SFX_DB_TLS, "false")),
                    config.getString(tenantId + PARAM_SFX_DB_TRUSTSTORE),
                    config.getString(tenantId + PARAM_SFX_DB_TRUSTSTORE_PWD),
                    config.getString(tenantId + PARAM_SFX_DB_KEYSTORE),
                    config.getString(tenantId + PARAM_SFX_DB_KEYSTORE_PWD),
                    config.getString(tenantId + PARAM_SFX_DOMAIN, "*") // _SFX_URL used for fallback
            );
        }


        //
        // Getters --------------------------------------------------------------------------------
        //
        public String getTenantId() {
            return _tenantId;
        }


        public String getDBConnStr() {
            return _dbConn;
        }


        public String getDBName() {
            return _dbName;
        }


        public String getDBAuth() {
            return _dbAuth;
        }


        public String getDBUser() {
            return _dbUser;
        }


        public char[] getDBPwd() {
            return copy(_dbPwd);
        }


        public boolean isTLS() {
            return _tls;
        }


        public String getTrustStorePath() {
            return _trustStorePath;
        }


        public char[] getTrustStorePwd() {
            return copy(_trustStorePwd);
        }


        public String getKeyStorePath() {
            return _keyStorePath;
        }


        public char[] getKeyStorePwd() {
            return copy(_keyStorePwd);
        }


        /**
         * Returns tenant Domain Name or "*" in case no Domain Name was defined
         *
         * @return
         */
        public String getDomain() {
            return _domain;
        }


        /**
         * Indicates whether DB Connection params were provided for this tenant or default DB Connection to be used instead
         *
         * @return
         */
        public boolean isDBParamsSet() {
            return isNotBlank(getDBConnStr());
        }


        public boolean requiresEmbeddedMongo() {
            return isBlank(getDBConnStr());
        }

    }


    /**
     * Provides configuration param value lookup support for Environment variables with fallback to empty string instead of null<br>
     * This will prevent returning original param value (i.e. `${env:SOME_VAR}`) if environment variable doesn't exist<br>
     * This implementation allows to use following syntax in the configuration file:
     * <pre>
     * some.key=${env:SOME_VAR}
     * </pre>
     * Which will perform lookup for `SOME_VAR` variable and return its value or empty string, if variable is not defined
     */
    private static class EnvVarWithEmptyStringFallbackLookup implements Lookup {

        public static final String PREFIX = "env";


        @Override
        public String lookup(String paramValue) {
            try {
                String envValue = System.getenv(paramValue);
                return envValue == null ? "" : envValue;
            } catch (Exception ex) {
                throw new ElDocRuntimeException("Error performing lookup for Environment Variable: " + paramValue, ex);
            }
        }
    }


    /**
     * Provides configuration param value lookup support for values stored in external files
     * This implementation allows to use following syntax in the configuration file:
     * <pre>
     * some.key=${file:/path/to/file}
     * </pre>
     */
    private static class FileLookup implements Lookup {

        public static final String PREFIX = "file";


        @Override
        public String lookup(String paramValue) {
            try {
                return Files.readString(Path.of(paramValue)).trim();
            } catch (Exception e) {
                throw new RuntimeException("Cannot read file: " + paramValue, e);
            }
        }
    }
}
