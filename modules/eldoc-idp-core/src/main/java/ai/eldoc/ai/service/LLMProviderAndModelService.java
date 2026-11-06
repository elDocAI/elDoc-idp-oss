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
package ai.eldoc.ai.service;

import ai.eldoc.ai.config.*;
import ai.eldoc.ai.exceptions.AiChatException;
import ai.eldoc.ai.model.AiRequestContext;
import ai.eldoc.ai.model.ChatModelWrapper;
import ai.eldoc.context.ConfigContext;
import ai.eldoc.model.config.IDPConfig;
import ai.eldoc.service.AppConfigService;
import ai.eldoc.service.ConfigService;
import ai.eldoc.util.SystemUtils;
import dev.langchain4j.model.bedrock.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.apache.commons.lang3.StringUtils.*;


/**
 * Service for managing {@link ChatModel}s for AI-related workloads
 */
@ApplicationScoped
public class LLMProviderAndModelService {

    private static final Logger LOG = LoggerFactory.getLogger(LLMProviderAndModelService.class);

    private static final String ELDOC_AI_BEDROCK_CONNECTIONS_MAX_JVM_OPTION = "ai.eldoc.bedrock.connections.max";
    private static final String ELDOC_AI_BEDROCK_CONNECTIONS_MAX_SYSTEM_VAR = "ELDOC_AI_BEDROCK_CONNECTIONS_MAX";
    public final static int BEDROCK_CONNECTIONS_MAX = SystemUtils.getJvmOrEnvOrDefaultProperty(ELDOC_AI_BEDROCK_CONNECTIONS_MAX_JVM_OPTION, ELDOC_AI_BEDROCK_CONNECTIONS_MAX_SYSTEM_VAR, 100);


    @Inject
    private AppConfigService _appConfigService;

    @Inject
    private ConfigContext _configContext;

    @Inject
    private ConfigService _configService;

    private final Map<String, BedrockRuntimeClient> _bedrockClients = new ConcurrentHashMap<>();

    private final Map<Pair<String, LLMModelConfig>, ChatModel> _chatModels = new ConcurrentHashMap<>();


    @PreDestroy
    public void destroy() {
        for (BedrockRuntimeClient client : _bedrockClients.values()) {
            client.close();
        }
    }


    //
    // Listing available models ------------------------------------------------------------------
    //
    public List<Pair<LLMProviderConfig, LLMModelConfig>> getAgentModels(boolean activeOnly) {
        List<Pair<LLMProviderConfig, LLMModelConfig>> models = getProviderAndModelByType(LLMModelConfig.TYPE_CHAT, false, true, activeOnly);
        if (getAgentModelConfigConverted() != null) {
            models.addFirst(getAgentModelConfigConverted());
        }
        return models;
    }


    public List<Pair<LLMProviderConfig, LLMModelConfig>> getVisionModels(boolean activeOnly) {
        List<Pair<LLMProviderConfig, LLMModelConfig>> models = getProviderAndModelByType(LLMModelConfig.TYPE_CHAT, true, false, activeOnly);
        if (getVisionModelConfigConverted() != null) {
            models.addFirst(getVisionModelConfigConverted());
        }
        return models;
    }


    //
    // IDP Models ---------------------------------------------------------------------------------
    //
    public ChatModelWrapper getIdpClassificationModel(AiRequestContext requestContext) {
        Pair<LLMProviderConfig, LLMModelConfig> modelConfig = getIdpClassificationModelConfig();
        if (modelConfig == null) {
            throw new AiChatException("AI RAG: No suitable model configured for: IDP Classification")
                    .setErrorCode(AiChatException.MODEL_NOT_CONFIGURED);
        }
        return getChatModel(modelConfig.getLeft(), modelConfig.getRight(), requestContext);
    }


    public ChatModelWrapper getIdpExtractionModel(String modelId, AiRequestContext requestContext) {
        Pair<LLMProviderConfig, LLMModelConfig> modelConfig = getIdpExtractionModelConfig(modelId);
        if (modelConfig == null) {
            throw new AiChatException("AI RAG: No suitable model configured for: IDP Extraction")
                    .setErrorCode(AiChatException.MODEL_NOT_CONFIGURED);
        }
        return getChatModel(modelConfig.getLeft(), modelConfig.getRight(), requestContext);
    }


    private Pair<LLMProviderConfig, LLMModelConfig> getIdpClassificationModelConfig() {
        IDPConfig idpConfig = _configService.getIDPConfig();
        Pair<LLMProviderConfig, LLMModelConfig> providerAndModel = null;
        if (isNotBlank(idpConfig.getClassificationDefaultModel())) {
            providerAndModel = getProviderAndModelByPairId(idpConfig.getClassificationDefaultModel());
        }
        if (!isEnabled(providerAndModel)) {
            return getAgentModelConfig();
        }
        LLMModelConfig modelConfig = new LLMModelConfig();
        LLMModelConfig.configureForAgentic(modelConfig);
        modelConfig.overrideWith(providerAndModel.getRight());
        return Pair.of(providerAndModel.getLeft(), modelConfig);
    }


    public boolean isAgentModelConfigurable() {
        return getAgentModelConfigConverted() == null;
    }


    public boolean isVisionModelConfigurable() {
        return getVisionModelConfigConverted() == null;
    }


    public Pair<LLMProviderConfig, LLMModelConfig> getIdpClassificationModelConfigFallback() {
        return getAgentModelConfig();
    }


    public Pair<LLMProviderConfig, LLMModelConfig> getIdpExtractionModelConfigFallback() {
        return getVisionModelConfig();
    }


    public Pair<LLMProviderConfig, LLMModelConfig> getIdpExtractionModelConfig() {
        return getIdpExtractionModelConfig(null);
    }


    public Pair<LLMProviderConfig, LLMModelConfig> getIdpExtractionModelConfig(String preferredModelId) {
        IDPConfig idpConfig = _configService.getIDPConfig();
        Pair<LLMProviderConfig, LLMModelConfig> providerAndModel = null;
        if (isNotBlank(preferredModelId)) {
            providerAndModel = getProviderAndModelByPairId(preferredModelId);
            if (providerAndModel == null) {
                LOG.warn("AI RAG: Model with requested modelId not found: {}", preferredModelId);
            }
        }
        if (!isEnabled(providerAndModel) && isNotBlank(idpConfig.getExtractionDefaultModel())) {
            providerAndModel = getProviderAndModelByPairId(idpConfig.getExtractionDefaultModel());
        }
        if (!isEnabled(providerAndModel)) {
            return getIdpExtractionModelConfigFallback();
        }
        LLMModelConfig modelConfig = new LLMModelConfig();
        LLMModelConfig.configureForAgentic(modelConfig);
        modelConfig.overrideWith(providerAndModel.getRight());
        return Pair.of(providerAndModel.getLeft(), modelConfig);
    }


    //
    // Retrieving/creating specific model instances -----------------------------------------------
    //


    public ChatModelWrapper getAgenticModel(AiRequestContext requestContext) {
        Pair<LLMProviderConfig, LLMModelConfig> modelConfig = getAgentModelConfig();
        if (modelConfig == null) {
            throw new AiChatException("AI RAG: No suitable model configured for: Agent")
                    .setErrorCode(AiChatException.MODEL_NOT_CONFIGURED);
        }
        return getChatModel(modelConfig.getLeft(), modelConfig.getRight(), requestContext);
    }


    public Pair<LLMProviderConfig, LLMModelConfig> getAgentModelConfig() {
        AIConfig aiConfig = getAiConfig();
        Pair<LLMProviderConfig, LLMModelConfig> providerAndModel = getAgentModelConfigConverted();
        if (!isEnabled(providerAndModel) && isNotBlank(aiConfig.getDefaultModelAgent())) {
            providerAndModel = getProviderAndModelByPairId(aiConfig.getDefaultModelAgent());
        }
        if (!isEnabled(providerAndModel)) {
            List<Pair<LLMProviderConfig, LLMModelConfig>> models = getProviderAndModelByType(LLMModelConfig.TYPE_CHAT, false, true, true);
            providerAndModel = models.isEmpty() ? null : models.getFirst();
        }
        if (!isEnabled(providerAndModel)) {
            return null;
        }
        LLMModelConfig modelConfig = new LLMModelConfig();
        LLMModelConfig.configureForAgentic(modelConfig);
        modelConfig.overrideWith(providerAndModel.getRight());
        return Pair.of(providerAndModel.getLeft(), modelConfig);
    }


    public ChatModelWrapper getVisionModel(AiRequestContext requestContext) {
        Pair<LLMProviderConfig, LLMModelConfig> modelConfig = getVisionModelConfig();
        if (modelConfig == null) {
            throw new AiChatException("AI RAG: No suitable model configured for: Vision")
                    .setErrorCode(AiChatException.MODEL_NOT_CONFIGURED);
        }
        return getChatModel(modelConfig.getLeft(), modelConfig.getRight(), requestContext);
    }


    public Pair<LLMProviderConfig, LLMModelConfig> getVisionModelConfig() {
        AIConfig aiConfig = getAiConfig();
        Pair<LLMProviderConfig, LLMModelConfig> providerAndModel = getVisionModelConfigConverted();
        if (!isEnabled(providerAndModel) && isNotBlank(aiConfig.getDefaultModelVision())) {
            providerAndModel = getProviderAndModelByPairId(aiConfig.getDefaultModelVision());
        }
        if (!isEnabled(providerAndModel)) {
            List<Pair<LLMProviderConfig, LLMModelConfig>> models = getProviderAndModelByType(LLMModelConfig.TYPE_CHAT, true, false, true);
            providerAndModel = models.isEmpty() ? null : models.getFirst();
        }
        if (!isEnabled(providerAndModel)) {
            return null;
        }
        LLMModelConfig modelConfig = new LLMModelConfig();
        LLMModelConfig.configureForVL(modelConfig);
        modelConfig.overrideWith(providerAndModel.getRight());
        return Pair.of(providerAndModel.getLeft(), modelConfig);
    }


    private ChatModelWrapper getChatModel(LLMProviderConfig providerConfig, LLMModelConfig modelConfig, AiRequestContext requestContext) {
        String modelKey = getProviderAndModelPairId(providerConfig, modelConfig);
        ChatModel model = _chatModels.computeIfAbsent(Pair.of(modelKey, modelConfig), key -> createChatModel(providerConfig, modelConfig));
        return new ChatModelWrapper(model, providerConfig, modelConfig, true);
    }


    private ChatModel createChatModel(LLMProviderConfig providerConfig, LLMModelConfig modelConfig) {
        LOG.info("AI RAG: Creating Non-Streaming Chat Model. Provider: {}; Model: {}", providerConfig.getName(), modelConfig.getNameEffective());
        if (providerConfig.isApiModeOpenAI()) {
            return createOpenAiChatModel(providerConfig, modelConfig);
        } else if (providerConfig.isApiModeAWSBedrock()) {
            return createBedrockChatModel(providerConfig, modelConfig);
        }
        return null;
    }


    /**
     * Creates AWS Bedrock ChatModel
     *
     * @return new AWS {@link BedrockChatModel} instance
     */
    private BedrockChatModel createBedrockChatModel(LLMProviderConfig providerConfig, LLMModelConfig modelConfig) {
        BedrockChatModel.Builder builder = BedrockChatModel.builder()
                .client(getBedrockRuntimeClient(providerConfig))
                .modelId(modelConfig.getModelId())
                .returnThinking(false)
                .region(Region.of(providerConfig.getBedrockRegion()))
                .defaultRequestParameters(getRequestParameters(providerConfig, modelConfig));
        Integer timeout = providerConfig.getTimeoutSec();
        if (timeout != null) {
            builder = builder.timeout(Duration.ofSeconds(timeout));
        }

        return builder.build();
    }


    /**
     * Returns an AWS Bedrock Runtime client or builds a new one if none was previously created
     *
     * @return {@link BedrockRuntimeClient} for use with AWS Bedrock models
     */
    private BedrockRuntimeClient getBedrockRuntimeClient(LLMProviderConfig providerConfig) {
        return _bedrockClients.computeIfAbsent(providerConfig.getKey(), name -> createBedrockRuntimeClient(providerConfig));
    }


    private BedrockRuntimeClient createBedrockRuntimeClient(LLMProviderConfig providerConfig) {
        Region modelRegion = Region.of(providerConfig.getBedrockRegion());

        AwsCredentials credentials = getBedrockCredentials(providerConfig);
        AwsCredentialsProvider provider = StaticCredentialsProvider.create(credentials);
        BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder()
                .credentialsProvider(provider);

        Integer timeout = providerConfig.getTimeoutSec();
        Duration timeoutDuration = timeout == null ? Duration.ofSeconds(60) : Duration.ofSeconds(timeout);
        // We need to explicitly set socket timeout, see: https://repost.aws/articles/AR_A_6aqweQYOR5FCgvM2vdA/solving-read-timed-out-error-and-high-latencies-in-amazon-bedrock-with-aws-java-sdk-client
        builder = builder
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(BEDROCK_CONNECTIONS_MAX)
                        .connectionAcquisitionTimeout(Duration.ofSeconds(30))
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(timeoutDuration))
                .overrideConfiguration(
                        clientConfig -> clientConfig
                                .apiCallAttemptTimeout(timeoutDuration.plusSeconds(15))
                                .apiCallTimeout(timeoutDuration.plusSeconds(30))
                );

        return builder.region(modelRegion)
                .build();
    }


    /**
     * Creates ChatModel via OpenAI-compatible API
     *
     * @return new {@link OpenAiChatModel} instance
     */
    private OpenAiChatModel createOpenAiChatModel(LLMProviderConfig providerConfig, LLMModelConfig modelConfig) {
        OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(getOpenAiEndpointNormalized(providerConfig))
                .modelName(modelConfig.getModelId())
                .returnThinking(false)
                .defaultRequestParameters(getRequestParameters(providerConfig, modelConfig));
        String apikey = getOpenAiSecretDecrypted(providerConfig);
        if (isNotBlank(apikey)) {
            builder = builder.apiKey(apikey);
        }
        if (providerConfig.getTimeoutSec() != null) {
            builder = builder.timeout(Duration.ofSeconds(providerConfig.getTimeoutSec()));
        }
        return builder.build();
    }


    private String getOpenAiEndpointNormalized(LLMProviderConfig providerConfig) {
        String rawHost = trimToNull(providerConfig.getHost());
        String rawPath = trimToNull(providerConfig.getPath());

        String host = rawHost == null ? LLMProviderConfig.OPENAI_HOST_DEFAULT : rawHost;
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "https://" + host;
        }
        host = host.replaceAll("/+$", "");

        String path = rawPath == null ? LLMProviderConfig.OPENAI_PATH_DEFAULT : rawPath;
        path = path.replaceAll("^/+", "")
                .replaceAll("/+$", "");

        URI uri = URI.create(host + "/" + path).normalize();
        return uri.toString();
    }


    private String getOpenAiSecretDecrypted(LLMProviderConfig providerConfig) {
        if (isBlank(providerConfig.getSecret())) {
            return null;
        }
        return providerConfig.getSecretDecrypted(getKeyEncryptionKeyBytes());
    }


    private AwsCredentials getBedrockCredentials(LLMProviderConfig providerConfig) {
        if (isBlank(providerConfig.getSecret()) || isBlank(providerConfig.getBedrockKeyId())) {
            return null;
        }
        String keyId = providerConfig.getBedrockKeyIdDecrypted(getKeyEncryptionKeyBytes());
        String secret = providerConfig.getSecretDecrypted(getKeyEncryptionKeyBytes());
        return AwsBasicCredentials.create(keyId, secret);
    }


    private byte[] getKeyEncryptionKeyBytes() {
        return _configContext.getAppConfig().getInstanceKeyByteArray();
    }


    private ChatRequestParameters getRequestParameters(LLMProviderConfig providerConfig, LLMModelConfig modelConfig) {
        DefaultChatRequestParameters.Builder<?> builder;
        Map<String, Object> customRequestParameters = new HashMap<>();
        if (providerConfig.isApiModeOpenAI()) {
            builder = OpenAiChatRequestParameters.builder();
        } else {
            builder = BedrockChatRequestParameters.builder();
        }
        if (modelConfig.getTemperature() != null) {
            builder = builder.temperature(modelConfig.getTemperature());
        }
        if (modelConfig.getTopP() != null) {
            builder = builder.topP(modelConfig.getTopP());
        }
        if (modelConfig.getPresencePenalty() != null) {
            if (providerConfig.isApiModeAWSBedrock()) {
                LOG.debug("AI RAG: Presence penalty is not supported for AWS Bedrock models. Provider: {}, Model: {}", providerConfig.getName(), modelConfig.getNameEffective());
            } else {
                builder = builder.presencePenalty(modelConfig.getPresencePenalty());
            }
        }
        if (modelConfig.getFrequencyPenalty() != null) {
            if (providerConfig.isApiModeAWSBedrock()) {
                LOG.warn("AI RAG: Frequency penalty is not supported for AWS Bedrock models. Provider: {}, Model: {}", providerConfig.getName(), modelConfig.getNameEffective());
            } else {
                builder = builder.frequencyPenalty(modelConfig.getFrequencyPenalty());
            }
        }
        if (modelConfig.getRepetitionPenalty() != null) {
            customRequestParameters.put("repetition_penalty", modelConfig.getRepetitionPenalty());
        }
        if (modelConfig.getMaxTokensOut() != null) {
            builder = builder.maxOutputTokens(modelConfig.getMaxTokensOut());
        }

        if (builder instanceof OpenAiChatRequestParameters.Builder openAiRequestParametersBuilder) {
            openAiRequestParametersBuilder.customParameters(customRequestParameters);
        }

        if (builder instanceof BedrockChatRequestParameters.Builder bedrockRequestParametersBuilder) {
            applyBedrockConfig(bedrockRequestParametersBuilder, providerConfig);
        }

        return builder.build();
    }


    private void applyBedrockConfig(BedrockChatRequestParameters.Builder builder, LLMProviderConfig config) {
        BedrockServiceTier serviceTier = bedrockServiceTierFromConfig(config);
        if (serviceTier != BedrockServiceTier.DEFAULT) {
            builder.serviceTier(serviceTier);
        }
    }


    public static BedrockServiceTier bedrockServiceTierFromConfig(LLMProviderConfig config) {
        if (isBlank(config.getBedrockServiceTier())) {
            return BedrockServiceTier.DEFAULT;
        }
        String configParamAdj = config.getBedrockServiceTier().trim().toUpperCase(Locale.ROOT);
        return switch (configParamAdj) {
            case "PRIORITY" -> BedrockServiceTier.PRIORITY;
            case "FLEX" -> BedrockServiceTier.FLEX;
            case "RESERVED" -> BedrockServiceTier.RESERVED;
            case "DEFAULT" -> BedrockServiceTier.DEFAULT;
            default -> BedrockServiceTier.DEFAULT;
        };
    }


    public static ChatRequest fixBedrockRequestParams(ChatRequest request, LLMProviderConfig providerConfig) {
        if (!providerConfig.isApiModeAWSBedrock()) {
            // This is AWS Bedrock-specific handling
            return request;
        }

        BedrockServiceTier serviceTier = bedrockServiceTierFromConfig(providerConfig);
        if (serviceTier == BedrockServiceTier.DEFAULT) {
            // No need to fix request params
            return request;
        }

        BedrockChatRequestParameters fixedParams = BedrockChatRequestParameters.builder()
                .overrideWith(request.parameters())
                .serviceTier(serviceTier)
                .build();
        return request.toBuilder()
                // Transfer settings from original request
                .messages(request.messages())
                // Apply fixed params
                .parameters(fixedParams)
                .build();
    }


    public AIConfig getAiConfig() {
        return _configService.getAIConfig();
    }


    /**
     * Retrieves the provider and model configuration matching the specified provider+model identifier.
     * Searches through all available provider configurations and their associated models to
     * find the first matching model with the given identifier.
     *
     * @param id The unique identifier of the model to locate.
     * @return A {@code Pair} containing the matching {@code LLMProviderConfig} and {@code LLMModelConfig},
     * or {@code null} if no match is found.
     */
    public Pair<LLMProviderConfig, LLMModelConfig> getProviderAndModelByPairId(String id) {
        return getProviderAndModelByPairId(id, false);
    }


    /**
     * Retrieves the provider and model configuration matching the specified provider+model identifier.
     * Searches through all available provider configurations and their associated models to
     * find the first matching model with the given identifier.
     *
     * @param providerAndModelPairId The unique identifier of the model to locate.
     * @param activeOnly Whether to only consider active providers and models.
     * @return A {@code Pair} containing the matching {@code LLMProviderConfig} and {@code LLMModelConfig},
     * or {@code null} if no match is found.
     */
    public Pair<LLMProviderConfig, LLMModelConfig> getProviderAndModelByPairId(String providerAndModelPairId, boolean activeOnly) {
        if (isBlank(providerAndModelPairId) || !providerAndModelPairId.contains("|")) {
            return null;
        }
        String configKey = providerAndModelPairId.substring(0, providerAndModelPairId.indexOf("|"));
        LLMProviderConfig providerConfig = getProviderConfigByKey(configKey, activeOnly);
        if (providerConfig == null) {
            return null;
        }
        for (LLMModelConfig modelConfig : providerConfig.getModels()) {
            if (activeOnly && !modelConfig.isEnabled()) {
                continue;
            }
            if (getProviderAndModelPairId(providerConfig, modelConfig).equals(providerAndModelPairId)) {
                return Pair.of(providerConfig, modelConfig);
            }
        }
        return null;
    }


    public LLMProviderConfig getProviderConfigByKey(String key, boolean activeOnly) {
        List<LLMProviderConfig> providersFromConfig = getProviderAndModelConfigsFromConfigFile();
        LLMProviderConfig provider = providersFromConfig.stream()
                .filter(p -> p.getKey().equals(key))
                .findFirst()
                .orElse(null);
        if (provider != null) {
            return provider;
        }

        return getLLMProviderConfigAll().stream()
                .filter(providerConfig -> !activeOnly || providerConfig.isEnabled())
                .filter(providerConfig -> Objects.equals(providerConfig.getKey(), key))
                .findFirst()
                .orElse(null);
    }


    /**
     * Finds first model that fits provided criteria<br>
     * Criteria are treated as fitting if it is either not required (argument == false) or the model supports it
     *
     * @param isVision Whether the model must support vision capabilities
     * @param isToolsUse Whether the model must support tools usage
     * @return Configuration of the first model that matches provided criteria or null if none found
     */
    private List<Pair<LLMProviderConfig, LLMModelConfig>> getProviderAndModelByType(int modelType, boolean isVision, boolean isToolsUse, boolean activeOnly) {
        List<LLMProviderConfig> providerConfigs = getLLMProviderConfigAll();
        List<Pair<LLMProviderConfig, LLMModelConfig>> result = new ArrayList<>();
        for (LLMProviderConfig providerConfig : providerConfigs) {
            if (activeOnly && !providerConfig.isEnabled()) {
                continue;
            }
            for (LLMModelConfig modelConfig : providerConfig.getModels()) {
                if (activeOnly && !modelConfig.isEnabled()) {
                    continue;
                }
                boolean isVisionFit = !isVision || modelConfig.isVision();
                boolean isToolsFit = !isToolsUse || modelConfig.isToolsUse();
                boolean isTypeFit = modelType != LLMModelConfig.TYPE_ANY && modelConfig.getType() == modelType;
                if (isVisionFit && isToolsFit && isTypeFit) {
                    result.add(Pair.of(providerConfig, modelConfig));
                }
            }
        }
        return result;
    }


    //
    // Methods for working with config file-based model configuration -----------------------------
    //


    private Pair<LLMProviderConfig, LLMModelConfig> getAgentModelConfigConverted() {
        try {
            return convertConfigFileModel(_appConfigService.getAiConfig().getAgentModelConfig(), LLMModelConfig.TYPE_CHAT);
        } catch (Exception e) {
            LOG.error("Error converting agent model config from file", e);
            return null;
        }
    }


    private Pair<LLMProviderConfig, LLMModelConfig> getVisionModelConfigConverted() {
        try {
            return convertConfigFileModel(_appConfigService.getAiConfig().getVisionModelConfig(), LLMModelConfig.TYPE_CHAT);
        } catch (Exception e) {
            LOG.error("Error converting vision model config from file", e);
            return null;
        }
    }


    private Pair<LLMProviderConfig, LLMModelConfig> convertConfigFileModel(AppConfigService.AiModelConfig modelConfig, int modelType) throws Exception {
        if (!modelConfig.isAiModelAvailable()) {
            return null;
        }

        LLMProviderConfig providerConfig = new LLMProviderConfig();
        providerConfig.setEnabled(true);
        int apiMode = -1;
        if (modelConfig.isProviderBedrock()) {
            apiMode = LLMProviderConfig.API_MODE_AWS_BEDROCK;
        } else if (modelConfig.isProviderOnnx()) {
            apiMode = LLMProviderConfig.API_MODE_ONNX;
        } else if (modelConfig.isProviderOpenai()) {
            apiMode = LLMProviderConfig.API_MODE_OPENAI;
        } else {
            // Unknown provider
            return null;
        }
        if (isNotBlank(modelConfig.getOpenaiEndpoint())) {
            URI endpointUri = URI.create(modelConfig.getOpenaiEndpoint());
            String endpointUriStr = "";
            if (endpointUri.getScheme() != null) {
                endpointUriStr = endpointUri.getScheme() + "://";
            }
            endpointUriStr += endpointUri.getHost();
            if (endpointUri.getPort() != -1) {
                endpointUriStr += ":" + endpointUri.getPort();
            }
            providerConfig.setHost(endpointUriStr);
            providerConfig.setPath(endpointUri.getPath());
        }
        providerConfig.setApiMode(apiMode);
        providerConfig.applyNameSanitized(modelConfig.getProvider() + "-" + modelConfig.getModelType());
        providerConfig.setRequestMaxSizeMB(modelConfig.getRequestMaxSizeMB());
        if (isNotBlank(modelConfig.getBedrockKeyId())) {
            providerConfig.setBedrockKeyId(LLMProviderConfig.encrypt(modelConfig.getBedrockKeyId(), getKeyEncryptionKeyBytes()));
        }
        providerConfig.setBedrockRegion(modelConfig.getBedrockRegion());
        if (isNotBlank(modelConfig.getSecret())) {
            providerConfig.setSecret(LLMProviderConfig.encrypt(modelConfig.getSecret(), getKeyEncryptionKeyBytes()));
        }
        providerConfig.setTimeoutSec(modelConfig.getTimeoutSec());
        if (modelConfig.getParams().containsKey("service_tier")) {
            providerConfig.setBedrockServiceTier(String.valueOf(modelConfig.getParams().get("service_tier")));
        }

        LLMModelConfig targetModelConfig = new LLMModelConfig();
        targetModelConfig.setEnabled(true);
        targetModelConfig.setType(modelType);
        targetModelConfig.applyModelIdSanitized(modelConfig.getModelId());
        targetModelConfig.setPath(modelConfig.getModelPath());
        targetModelConfig.setDimensions(modelConfig.getModelDimensions());
        targetModelConfig.setMaxTokensOut(modelConfig.getMaxOutputTokens());

        providerConfig.getModels().add(targetModelConfig);

        return Pair.of(providerConfig, targetModelConfig);
    }


    //
    // Overrides ----------------------------------------------------------------------------------
    //


    public List<LLMProviderConfig> getLLMProviderConfigAll() {
        return List.of();
    }


    private List<LLMProviderConfig> getProviderAndModelConfigsFromConfigFile() {
        List<LLMProviderConfig> providersFromConfigs = new ArrayList<>();

        Pair<LLMProviderConfig, LLMModelConfig> agentModelConfig = getAgentModelConfigConverted();
        if (agentModelConfig != null) {
            providersFromConfigs.add(agentModelConfig.getLeft());
        }

        Pair<LLMProviderConfig, LLMModelConfig> visionModelConfig = getVisionModelConfigConverted();
        if (visionModelConfig != null) {
            providersFromConfigs.add(visionModelConfig.getLeft());
        }

        return providersFromConfigs;
    }


    public void onProviderConfigUpdate(String providerKey) {
        // Remove cached ChatModels
        for (Pair<String, LLMModelConfig> cacheKey : _chatModels.keySet()) {
            if (cacheKey.getLeft().startsWith(providerKey)) {
                _chatModels.remove(cacheKey);
            }
        }

        // Close and remove cached Bedrock clients, if present
        if (_bedrockClients.containsKey(providerKey)) {
            _bedrockClients.get(providerKey).close();
            _bedrockClients.remove(providerKey);
        }
    }


    public void resetAndInitConfigFileModels() {
        onProviderConfigUpdate(AppConfigService.AiModelConfig.MODEL_PROVIDER_OPENAI);
        onProviderConfigUpdate(AppConfigService.AiModelConfig.MODEL_PROVIDER_AWS);
        onProviderConfigUpdate(AppConfigService.AiModelConfig.MODEL_PROVIDER_ONNX);
    }


    private boolean isEnabled(Pair<LLMProviderConfig, LLMModelConfig> model) {
        if (model == null) {
            return false;
        }
        LLMProviderConfig provider = model.getLeft();
        LLMModelConfig config = model.getRight();
        return provider.isEnabled() && config.isEnabled();
    }


    //
    // Static Methods -----------------------------------------------------------------------------
    //


    public static String getProviderAndModelPairId(LLMProviderConfig providerConfig, LLMModelConfig modelConfig) {
        return providerConfig.getKey() + "|" + modelConfig.getNameEffective();
    }
}
