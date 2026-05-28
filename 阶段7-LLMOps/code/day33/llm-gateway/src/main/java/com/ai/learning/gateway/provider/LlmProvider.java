package com.ai.learning.gateway.provider;

import com.ai.learning.gateway.model.ChatRequest;
import com.ai.learning.gateway.model.ChatResponse;

/**
 * Abstract interface for LLM providers.
 * Allows routing to different backends (DeepSeek, OpenAI, etc.).
 */
public interface LlmProvider {
    String getProviderName();
    ChatResponse call(ChatRequest request);
}
