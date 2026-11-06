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

package ai.eldoc.ai.config;

import ai.eldoc.ElDocRuntimeException;
import ai.eldoc.crypto.SymmetricCryptoV2;
import ai.eldoc.model.config.Config;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.SerializationUtils;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import javax.crypto.*;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * LLM Provider configuration model
 */
public class LLMProviderConfig extends Config {

    @Serial
    private static final long serialVersionUID = -2035242947589420040L;

    public static final String PROVIDER_NAME_FORBIDDEN_CHARS_PATTERN_STR = "[^a-zA-Z0-9\\[\\]()_ \\-@#]";

    public static final String OPENAI_HOST_DEFAULT = "https://api.openai.com";
    public static final String OPENAI_PATH_DEFAULT = "/v1";

    public static final String _ENABLED = "enbld";
    public static final String _SECRET = "scrt";
    public static final String _NAME = "nm";
    public static final String _API_MODE = "apmod";
    public static final String _HOST = "hst";
    public static final String _PATH = "pth";
    public static final String _TIMEOUT_SEC = "rqTout";
    public static final String _REQUEST_MAX_SIZE_MB = "rqMb";
    public static final String _MODELS = "mdls";
    public static final String _BEDROCK_KEY_ID = "bdrKid";
    public static final String _BEDROCK_REGION = "bdrRg";
    public static final String _BEDROCK_SERVICE_TIER = "bdrSt";

    public static final int API_MODE_OPENAI = 1;
    public static final int API_MODE_AWS_BEDROCK = 2;
    public static final int API_MODE_ONNX = 3;

    private String _name;
    private int _apiMode;
    private String _secret;
    private boolean _enabled;

    private String _host;
    private String _path;
    private Integer _timeoutSec;
    private Double _requestMaxSizeMB;

    private List<LLMModelConfig> _models;

    //
    // ApiMode-specific params
    //
    // AWS Bedrock
    private String _bedrockKeyId;
    private String _bedrockRegion;
    private String _bedrockServiceTier;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public LLMProviderConfig() {
        super();
        _apiMode = API_MODE_OPENAI;
        _models = new ArrayList<>();
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public LLMProviderConfig clone() {
        return SerializationUtils.clone(this);
    }


    @BsonIgnore
    public boolean isApiModeOpenAI() {
        return getApiMode() == API_MODE_OPENAI;
    }


    @BsonIgnore
    public boolean isApiModeAWSBedrock() {
        return getApiMode() == API_MODE_AWS_BEDROCK;
    }


    @BsonIgnore
    public boolean isApiModeOnnx() {
        return getApiMode() == API_MODE_ONNX;
    }


    @BsonIgnore
    public String getBedrockKeyIdDecrypted(byte[] cipherKey) {
        try {
            if (isBlank(getBedrockKeyId())) {
                return null;
            }
            byte[] decryptedText = SymmetricCryptoV2.decrypt(cipherKey, Base64.getDecoder().decode(getBedrockKeyId()));
            return new String(decryptedText, StandardCharsets.UTF_8);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException | DecoderException ex) {
            throw new ElDocRuntimeException(ex);
        }
    }


    public static String encrypt(String value, byte[] cipherKey) throws Exception {
        byte[] encText = SymmetricCryptoV2.encrypt(cipherKey, value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encText);
    }


    //
    // Getters & Setters Business API -------------------------------------------------------------
    //
    public void applyNameSanitized(String name) {
        if (name == null) {
            setName(null);
            setKey(null);
            return;
        }

        String nameSanitized = name.replaceAll(PROVIDER_NAME_FORBIDDEN_CHARS_PATTERN_STR, "").trim();
        setName(nameSanitized.isEmpty() ? null : nameSanitized);
        setKey(nameSanitized.isEmpty() ? null : nameSanitized.replace(" ", "+"));
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    /**
     * Returns a provider key like 'Bedrock+Mantle' (same what is returned by {@link #getName()} but spaces replaced with '+`).
     * It is stored sanitized and spaces replace with '+' when set via {@link #applyNameSanitized(String)})
     *
     * @return
     */
    @Override
    public String getKey() {
        return super.getKey();
    }


    /**
     * Returns provider name like 'Bedrock Mantle' (it is stored sanitized when set via {@link #applyNameSanitized(String)})
     *
     * @return
     */
    @BsonProperty(_NAME)
    public String getName() {
        return _name;
    }


    /**
     * Used by MongoDB POJO codec.
     * Application/API code must use {@link #applyNameSanitized(String)}.
     */
    @BsonProperty(_NAME)
    public void setName(String name) {
        _name = name;
    }


    @BsonProperty(_ENABLED)
    public boolean isEnabled() {
        return _enabled;
    }


    @BsonProperty(_ENABLED)
    public void setEnabled(boolean enabled) {
        _enabled = enabled;
    }


    @BsonProperty(_API_MODE)
    public int getApiMode() {
        return _apiMode;
    }


    @BsonProperty(_API_MODE)
    public void setApiMode(int apiMode) {
        _apiMode = apiMode;
    }


    @BsonProperty(_SECRET)
    public String getSecret() {
        return _secret;
    }


    @BsonProperty(_SECRET)
    public void setSecret(String secret) {
        _secret = secret;
    }


    @BsonIgnore
    public String getSecretDecrypted(byte[] cipherKey) {
        try {
            if (isBlank(getSecret())) {
                return null;
            }
            byte[] decryptedText = SymmetricCryptoV2.decrypt(cipherKey, Base64.getDecoder().decode(getSecret()));
            return new String(decryptedText, StandardCharsets.UTF_8);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException | DecoderException ex) {
            throw new ElDocRuntimeException(ex);
        }
    }


    @BsonProperty(_HOST)
    public String getHost() {
        return _host;
    }


    @BsonProperty(_HOST)
    public void setHost(String host) {
        _host = host;
    }


    @BsonProperty(_PATH)
    public String getPath() {
        return _path;
    }


    @BsonProperty(_PATH)
    public void setPath(String path) {
        _path = path;
    }


    @BsonProperty(_TIMEOUT_SEC)
    public Integer getTimeoutSec() {
        return _timeoutSec;
    }


    @BsonProperty(_TIMEOUT_SEC)
    public void setTimeoutSec(Integer timeoutSec) {
        _timeoutSec = timeoutSec;
    }


    @BsonProperty(_REQUEST_MAX_SIZE_MB)
    public Double getRequestMaxSizeMB() {
        return _requestMaxSizeMB;
    }


    @BsonProperty(_REQUEST_MAX_SIZE_MB)
    public void setRequestMaxSizeMB(Double requestMaxSizeMB) {
        _requestMaxSizeMB = requestMaxSizeMB;
    }


    @BsonProperty(_MODELS)
    public List<LLMModelConfig> getModels() {
        return _models;
    }


    @BsonProperty(_MODELS)
    public void setModels(List<LLMModelConfig> models) {
        _models = models;
    }


    @BsonProperty(_BEDROCK_KEY_ID)
    public String getBedrockKeyId() {
        return _bedrockKeyId;
    }


    @BsonProperty(_BEDROCK_KEY_ID)
    public void setBedrockKeyId(String bedrockKeyId) {
        _bedrockKeyId = bedrockKeyId;
    }


    @BsonProperty(_BEDROCK_REGION)
    public String getBedrockRegion() {
        return _bedrockRegion;
    }


    @BsonProperty(_BEDROCK_REGION)
    public void setBedrockRegion(String bedrockRegion) {
        _bedrockRegion = bedrockRegion;
    }


    @BsonProperty(_BEDROCK_SERVICE_TIER)
    public String getBedrockServiceTier() {
        return _bedrockServiceTier;
    }


    @BsonProperty(_BEDROCK_SERVICE_TIER)
    public void setBedrockServiceTier(String bedrockServiceTier) {
        _bedrockServiceTier = bedrockServiceTier;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        LLMProviderConfig that = (LLMProviderConfig) o;
        return _apiMode == that._apiMode
                && _enabled == that._enabled
                && Objects.equals(_name, that._name)
                && Objects.equals(_secret, that._secret)
                && Objects.equals(_host, that._host)
                && Objects.equals(_path, that._path)
                && Objects.equals(_models, that._models)
                && Objects.equals(_timeoutSec, that._timeoutSec)
                && Objects.equals(_requestMaxSizeMB, that._requestMaxSizeMB)
                && Objects.equals(_bedrockKeyId, that._bedrockKeyId)
                && Objects.equals(_bedrockRegion, that._bedrockRegion)
                && Objects.equals(_bedrockServiceTier, that._bedrockServiceTier);
    }


    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(_name);
        result = 31 * result + _apiMode;
        result = 31 * result + (_enabled ? 1 : 0);
        result = 31 * result + Objects.hashCode(_secret);
        result = 31 * result + Objects.hashCode(_host);
        result = 31 * result + Objects.hashCode(_path);
        result = 31 * result + Objects.hashCode(_models);
        result = 31 * result + Objects.hashCode(_timeoutSec);
        result = 31 * result + Objects.hashCode(_requestMaxSizeMB);
        result = 31 * result + Objects.hashCode(_bedrockKeyId);
        result = 31 * result + Objects.hashCode(_bedrockRegion);
        result = 31 * result + Objects.hashCode(_bedrockServiceTier);
        return result;
    }
}
