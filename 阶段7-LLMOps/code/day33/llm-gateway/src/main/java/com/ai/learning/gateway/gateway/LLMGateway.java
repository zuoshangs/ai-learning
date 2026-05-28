package com.ai.learning.gateway.gateway;

import com.ai.learning.gateway.auth.ApiKeyManager;
import com.ai.learning.gateway.circuitbreaker.CircuitBreaker;
import com.ai.learning.gateway.config.GatewayConfig;
import com.ai.learning.gateway.model.ChatRequest;
import com.ai.learning.gateway.model.ChatResponse;
import com.ai.learning.gateway.provider.DeepSeekProvider;
import com.ai.learning.gateway.provider.LlmProvider;
import com.ai.learning.gateway.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified LLM Gateway.
 * Orchestrates: Auth → Rate Limit → Circuit Breaker → Provider Call → Response
 */
@Component
public class LLMGateway {

    private static final Logger log = LoggerFactory.getLogger(LLMGateway.class);

    private final ApiKeyManager apiKeyManager;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final DeepSeekProvider deepSeekProvider;
    private final Map<String, LlmProvider> providers = new ConcurrentHashMap<>();
    private final GatewayConfig config;

    private long totalRequests = 0;
    private long totalErrors = 0;
    private long totalLatencyMs = 0;

    public LLMGateway(ApiKeyManager apiKeyManager, RateLimiter rateLimiter,
                      CircuitBreaker circuitBreaker, DeepSeekProvider deepSeekProvider,
                      GatewayConfig config) {
        this.apiKeyManager = apiKeyManager;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.deepSeekProvider = deepSeekProvider;
        this.config = config;
        providers.put("deepseek", deepSeekProvider);
    }

    /**
     * Process a chat request through the gateway pipeline.
     */
    public ChatResponse process(String apiKey, String providerName, ChatRequest request) {
        long start = System.nanoTime();

        // 1. Rate limit check
        if (!rateLimiter.tryAcquire(apiKey)) {
            log.warn("Rate limit exceeded for key={}, tier={}",
                    apiKey, apiKeyManager.getTier(apiKey));
            return ChatResponse.rateLimited();
        }

        // 2. Circuit breaker check
        if (!circuitBreaker.allowRequest()) {
            log.warn("Circuit breaker OPEN, rejecting request for key={}", apiKey);
            totalRequests++;
            totalErrors++;
            return ChatResponse.circuitOpen();
        }

        // 3. Route to provider
        LlmProvider provider = providers.getOrDefault(providerName, deepSeekProvider);
        ChatResponse response = provider.call(request);

        long latencyNs = System.nanoTime() - start;
        totalRequests++;
        totalLatencyMs += response.isSuccess() ? response.getLatencyMs() : 0;

        // 4. Update circuit breaker
        if (response.isSuccess()) {
            circuitBreaker.onSuccess();
        } else {
            totalErrors++;
            circuitBreaker.onFailure();
        }

        return response;
    }

    /** Get gateway statistics. */
    public Map<String, Object> getStats() {
        return Map.of(
                "totalRequests", totalRequests,
                "totalErrors", totalErrors,
                "avgLatencyMs", totalRequests > 0 ? totalLatencyMs / totalRequests : 0,
                "circuitState", circuitBreaker.getState().name(),
                "circuitFailures", circuitBreaker.getFailureCount(),
                "circuitRejected", circuitBreaker.getRejectedRequests(),
                "rateLimitType", config.getRateLimitType()
        );
    }

    /** Reset all state. */
    public void reset() {
        totalRequests = 0;
        totalErrors = 0;
        totalLatencyMs = 0;
        circuitBreaker.reset();
    }
}
