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
package ai.eldoc.ai.model;

import ai.eldoc.ai.config.LLMModelConfig;
import ai.eldoc.ai.config.LLMProviderConfig;
import ai.eldoc.ai.service.LLMProviderAndModelService;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


/**
 * Wrapper for ChatModel, that delegates all requests to wrapped models, counting tokens usage
 * from response. Can be used to count tokens, when original response is not available - for example,
 * in Agentic flow
 */
public class ChatModelWrapper implements ChatModel {

    private static final Logger LOG = LoggerFactory.getLogger(ChatModelWrapper.class);

    private static final String THINKING_PATTERN_STR = "<think(?:ing)?\\\\b[^>]*>(.*?)</think(?:ing)?>";
    private static final Pattern THINKING_PATTERN = Pattern.compile(THINKING_PATTERN_STR, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final boolean _handleThinking;

    private final ChatModel _delegate;
    private final LLMProviderConfig _providerConfig;
    private final LLMModelConfig _modelConfig;


    //
    // Constructor --------------------------------------------------------------------------------
    //
    public ChatModelWrapper(ChatModel delegate, LLMProviderConfig providerConfig, LLMModelConfig modelConfig, boolean handleThinking) {
        _delegate = delegate;
        _handleThinking = handleThinking;

        _providerConfig = providerConfig;
        _modelConfig = modelConfig;
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return chat(chatRequest);
    }


    @Override
    public Set<Capability> supportedCapabilities() {
        return _delegate.supportedCapabilities();
    }


    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();
        return chat(chatRequest);
    }


    @Override
    public ChatResponse chat(ChatMessage... messages) {
        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();
        return chat(chatRequest);
    }


    @Override
    public String chat(String userMessage) {
        // Delegate communication to another method to properly handle billing
        ChatResponse response = chat(UserMessage.from(userMessage));
        if (response == null || response.aiMessage() == null) {
            return null;
        }
        return response.aiMessage().text();
    }


    @Override
    public ModelProvider provider() {
        return _delegate.provider();
    }


    @Override
    public List<ChatModelListener> listeners() {
        return _delegate.listeners();
    }


    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return _delegate.defaultRequestParameters();
    }


    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        chatRequest = LLMProviderAndModelService.fixBedrockRequestParams(chatRequest, _providerConfig);
        ChatResponse response = _delegate.chat(chatRequest);
        if (response.finishReason() != FinishReason.STOP && response.finishReason() != FinishReason.TOOL_EXECUTION) {
            LOG.debug("{}: Unexpected finish reason: {}", _modelConfig.getNameEffective(), response.finishReason());
        }

        if (_handleThinking) {
            return copyAndHandleThinking(response);
        } else {
            return response;
        }
    }


    private static ChatResponse copyAndHandleThinking(ChatResponse response) {
        AiMessage originalMessage = response.aiMessage();
        Pair<String, String> parsedResponse = parse(originalMessage.text());
        String responseFinalText = parsedResponse.getRight();
        String responseThinking;
        if (isBlank(responseFinalText)) {
            // Message contained thinking block only, but ChatModel not thrown error - this is ToolExecution message - return it as is
            responseFinalText = originalMessage.text();
            responseThinking = null;
        } else {
            responseThinking = isNotBlank(originalMessage.thinking()) ? originalMessage.thinking() : parsedResponse.getLeft();
        }

        AiMessage.Builder responseMessageBuilder = AiMessage.builder()
                .attributes(originalMessage.attributes())
                .text(responseFinalText);
        if (isNotBlank(responseThinking)) {
            responseMessageBuilder = responseMessageBuilder.thinking(responseThinking);
        }
        AiMessage responseMessage = responseMessageBuilder
                .toolExecutionRequests(originalMessage.toolExecutionRequests())
                .build();

        ChatResponseMetadata responseMetadata = response.metadata().toBuilder()
                .id(response.id())
                .modelName(response.modelName())
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .build();

        return ChatResponse.builder()
                .aiMessage(responseMessage)
                .metadata(responseMetadata)
                .build();
    }


    private static Pair<String, String> parse(String input) {
        if (input == null || input.isEmpty()) {
            return Pair.of("", "");
        }

        Matcher m = THINKING_PATTERN.matcher(input);

        StringBuilder thinking = new StringBuilder();
        StringBuilder response = new StringBuilder();

        int lastEnd = 0;
        while (m.find()) {
            // Keep any non-thinking text before this block
            response.append(input, lastEnd, m.start());

            // Collect thinking content (inner group)
            String inner = m.group(1).trim();
            if (isNotBlank(inner)) {
                if (!thinking.isEmpty()) {
                    thinking.append('\n');
                }
                thinking.append(inner);
            }

            lastEnd = m.end();
        }

        // Keep the tail after the last thinking block
        response.append(input, lastEnd, input.length());

        return Pair.of(thinking.toString().trim(), response.toString().trim());
    }


    //
    // Getters & Setters --------------------------------------------------------------------------
    //


    public LLMProviderConfig getProviderConfig() {
        return _providerConfig;
    }


    public LLMModelConfig getModelConfig() {
        return _modelConfig;
    }
}
