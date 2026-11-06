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

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * LLM Model configuration
 */
public class LLMModelConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = -6855981758940523514L;

    // Model ID and name restricted chars
    private static final String MODEL_ID_FORBIDDEN_CHARS_PATTERN_STR = "[^a-zA-Z0-9\\[\\]()_@.:/-]";
    private static final String MODEL_NAME_FORBIDDEN_CHARS_PATTERN_STR = "[^a-zA-Z0-9\\[\\]()_@.: /-]";

    public static final String _ENABLED = "enbld";
    public static final String _NAME = "nm";
    public static final String _ID = "id";
    public static final String _TYPE = "typ";
    public static final String _PATH = "pth";
    public static final String _VISION = "vsn";
    public static final String _REASONING = "rs";
    public static final String _TOOLS_USE = "tu";
    public static final String _MAX_TOKENS_OUT = "mto";
    public static final String _TEMPERATURE = "tmp";
    public static final String _PRESENCE_PENALTY = "pp";
    public static final String _FREQUENCY_PENALTY = "fp";
    public static final String _REPETITION_PENALTY = "rp";
    public static final String _TOP_P = "tp";
    public static final String _DIMENSIONS = "dims";

    public static final int TYPE_ANY = -1;
    public static final int TYPE_CHAT = 1;
    public static final int TYPE_EMBEDDING = 2;
    public static final int TYPE_RERANK = 3;

    private String _name;
    private String _id;
    private boolean _enabled;
    private int _type;

    private String _path;

    private boolean _vision;
    private boolean _reasoning;
    private boolean _toolsUse;

    private Double _temperature;
    private Double _topP;

    private Double _presencePenalty;
    private Double _frequencyPenalty;
    private Double _repetitionPenalty;

    private Integer _dimensions;
    private Integer _maxTokensOut;


    //
    // Predefined configurations ------------------------------------------------------------------
    //
    public static void configureForVL(LLMModelConfig config) {
        config.setVision(true);

        config.setMaxTokensOut(4096);
        config.setPresencePenalty(0.0);
        config.setRepetitionPenalty(1.05);

        config.setTemperature(0.2);
        config.setTopP(0.9);
    }


    public static void configureForAgentic(LLMModelConfig config) {
        config.setToolsUse(true);

        config.setTemperature(0.1);
        config.setTopP(0.9);
    }


    public static void configureForChat(LLMModelConfig config) {
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    public void overrideWith(LLMModelConfig other) {
        if (isNotBlank(other._name)) {
            _name = other._name;
        }
        if (isNotBlank(other._id)) {
            _id = other._id;
        }
        if (isNotBlank(other._path)) {
            _path = other._path;
        }
        if (other._vision) {
            _vision = true;
        }
        if (other._reasoning) {
            _reasoning = true;
        }
        if (other._toolsUse) {
            _toolsUse = true;
        }
        if (other._temperature != null) {
            _temperature = other._temperature;
        }
        if (other._topP != null) {
            _topP = other._topP;
        }
        if (other._presencePenalty != null) {
            _presencePenalty = other._presencePenalty;
        }
        if (other._frequencyPenalty != null) {
            _frequencyPenalty = other._frequencyPenalty;
        }
        if (other._repetitionPenalty != null) {
            _repetitionPenalty = other._repetitionPenalty;
        }
        if (other._dimensions != null) {
            _dimensions = other._dimensions;
        }
        if (other._maxTokensOut != null) {
            _maxTokensOut = other._maxTokensOut;
        }
    }


    //
    // Getters & Setters Business API -------------------------------------------------------------
    //
    @BsonIgnore
    public boolean isChat() {
        return getType() == TYPE_CHAT;
    }


    @BsonIgnore
    public boolean isEmbedding() {
        return getType() == TYPE_EMBEDDING;
    }


    @BsonIgnore
    public boolean isRerank() {
        return getType() == TYPE_RERANK;
    }


    @BsonIgnore
    public String getNameEffective() {
        return isNotBlank(getName()) ? getName() : getModelId();
    }


    public void applyModelIdSanitized(String modelId) {
        if (isBlank(modelId)) {
            setModelId(null);
            return;
        }

        setModelId(modelId.replaceAll(MODEL_ID_FORBIDDEN_CHARS_PATTERN_STR, ""));
    }


    public void applyNameSanitized(String name) {
        if (isBlank(name)) {
            setName(null);
            return;
        }

        String sanitizedName = name
                .replaceAll(MODEL_NAME_FORBIDDEN_CHARS_PATTERN_STR, "")
                .trim()
                .replace(" ", "+");
        setName(sanitizedName.isEmpty() ? null : sanitizedName);
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //
    @BsonProperty(_ID)
    public String getModelId() {
        return _id;
    }


    /**
     * Used by MongoDB POJO codec.
     * Application/API code must use {@link #applyModelIdSanitized(String)}.
     */
    @BsonProperty(_ID)
    public void setModelId(String id) {
        _id = id;
    }


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


    @BsonProperty(_TYPE)
    public int getType() {
        return _type;
    }


    @BsonProperty(_TYPE)
    public void setType(int type) {
        _type = type;
    }


    @BsonProperty(_PATH)
    public String getPath() {
        return _path;
    }


    @BsonProperty(_PATH)
    public void setPath(String path) {
        _path = path;
    }


    @BsonProperty(_VISION)
    public boolean isVision() {
        return _vision;
    }


    @BsonProperty(_VISION)
    public void setVision(boolean vision) {
        _vision = vision;
    }


    @BsonProperty(_REASONING)
    public boolean isReasoning() {
        return _reasoning;
    }


    @BsonProperty(_REASONING)
    public void setReasoning(boolean reasoning) {
        _reasoning = reasoning;
    }


    @BsonProperty(_TOOLS_USE)
    public boolean isToolsUse() {
        return _toolsUse;
    }


    @BsonProperty(_TOOLS_USE)
    public void setToolsUse(boolean toolsUse) {
        _toolsUse = toolsUse;
    }


    @BsonProperty(_MAX_TOKENS_OUT)
    public Integer getMaxTokensOut() {
        return _maxTokensOut;
    }


    @BsonProperty(_MAX_TOKENS_OUT)
    public void setMaxTokensOut(Integer maxTokensOut) {
        _maxTokensOut = maxTokensOut;
    }


    @BsonProperty(_TEMPERATURE)
    public Double getTemperature() {
        return _temperature;
    }


    @BsonProperty(_TEMPERATURE)
    public void setTemperature(Double temperature) {
        _temperature = temperature;
    }


    @BsonProperty(_TOP_P)
    public Double getTopP() {
        return _topP;
    }


    @BsonProperty(_TOP_P)
    public void setTopP(Double topP) {
        _topP = topP;
    }


    @BsonProperty(_PRESENCE_PENALTY)
    public Double getPresencePenalty() {
        return _presencePenalty;
    }


    @BsonProperty(_PRESENCE_PENALTY)
    public void setPresencePenalty(Double presencePenalty) {
        _presencePenalty = presencePenalty;
    }


    @BsonProperty(_FREQUENCY_PENALTY)
    public Double getFrequencyPenalty() {
        return _frequencyPenalty;
    }


    @BsonProperty(_FREQUENCY_PENALTY)
    public void setFrequencyPenalty(Double frequencyPenalty) {
        _frequencyPenalty = frequencyPenalty;
    }


    @BsonProperty(_REPETITION_PENALTY)
    public Double getRepetitionPenalty() {
        return _repetitionPenalty;
    }


    @BsonProperty(_REPETITION_PENALTY)
    public void setRepetitionPenalty(Double repetitionPenalty) {
        _repetitionPenalty = repetitionPenalty;
    }


    @BsonProperty(_DIMENSIONS)
    public Integer getDimensions() {
        return _dimensions;
    }


    @BsonProperty(_DIMENSIONS)
    public void setDimensions(Integer dimensions) {
        _dimensions = dimensions;
    }


    //
    // Generic Methods ----------------------------------------------------------------------------
    //
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        LLMModelConfig that = (LLMModelConfig) o;
        return _type == that._type
                && _enabled == that._enabled
                && _vision == that._vision
                && _reasoning == that._reasoning
                && _toolsUse == that._toolsUse
                && Objects.equals(_name, that._name)
                && Objects.equals(_id, that._id)
                && Objects.equals(_path, that._path)
                && Objects.equals(_maxTokensOut, that._maxTokensOut)
                && Objects.equals(_temperature, that._temperature)
                && Objects.equals(_topP, that._topP)
                && Objects.equals(_presencePenalty, that._presencePenalty)
                && Objects.equals(_frequencyPenalty, that._frequencyPenalty)
                && Objects.equals(_repetitionPenalty, that._repetitionPenalty)
                && Objects.equals(_dimensions, that._dimensions);
    }


    @Override
    public int hashCode() {
        int result = 5 * Objects.hashCode(_id);
        result = 31 * result + _type;
        result = 31 * result + (_enabled ? 1 : 0);
        result = 31 * result + Objects.hashCode(_name);
        result = 31 * result + Objects.hashCode(_path);
        result = 31 * result + Boolean.hashCode(_vision);
        result = 31 * result + Boolean.hashCode(_reasoning);
        result = 31 * result + Boolean.hashCode(_toolsUse);
        result = 31 * result + Objects.hashCode(_maxTokensOut);
        result = 31 * result + Objects.hashCode(_temperature);
        result = 31 * result + Objects.hashCode(_topP);
        result = 31 * result + Objects.hashCode(_presencePenalty);
        result = 31 * result + Objects.hashCode(_frequencyPenalty);
        result = 31 * result + Objects.hashCode(_repetitionPenalty);
        result = 31 * result + Objects.hashCode(_dimensions);
        return result;
    }
}
