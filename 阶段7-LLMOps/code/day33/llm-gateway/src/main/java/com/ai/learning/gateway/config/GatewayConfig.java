package com.ai.learning.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayConfig {

    /** API keys for authentication: key -> owner */
    private Map<String, String> apiKeys = new HashMap<>();

    /** Rate limit type: token-bucket or sliding-window */
    private String rateLimitType = "token-bucket";

    /** Default rate limit per API key (requests per minute) */
    private int defaultRatePerMinute = 60;

    /** Per-key overrides: key-id -> rate per minute */
    private Map<String, Integer> rateOverrides = new HashMap<>();

    /** Circuit breaker config */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /** LLM provider config */
    private LlmProviderConfig llm = new LlmProviderConfig();

    public static class CircuitBreakerConfig {
        private int failureThreshold = 5;
        private long resetTimeoutMs = 30_000;
        private int halfOpenMaxRequests = 3;

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int v) { failureThreshold = v; }
        public long getResetTimeoutMs() { return resetTimeoutMs; }
        public void setResetTimeoutMs(long v) { resetTimeoutMs = v; }
        public int getHalfOpenMaxRequests() { return halfOpenMaxRequests; }
        public void setHalfOpenMaxRequests(int v) { halfOpenMaxRequests = v; }
    }

    public static class LlmProviderConfig {
        private String endpoint = "https://api.deepseek.com/chat/completions";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private int timeoutMs = 30_000;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { endpoint = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { apiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { model = v; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int v) { timeoutMs = v; }
    }

    public Map<String, String> getApiKeys() { return apiKeys; }
    public void setApiKeys(Map<String, String> v) { apiKeys = v; }
    public String getRateLimitType() { return rateLimitType; }
    public void setRateLimitType(String v) { rateLimitType = v; }
    public int getDefaultRatePerMinute() { return defaultRatePerMinute; }
    public void setDefaultRatePerMinute(int v) { defaultRatePerMinute = v; }
    public Map<String, Integer> getRateOverrides() { return rateOverrides; }
    public void setRateOverrides(Map<String, Integer> v) { rateOverrides = v; }
    public CircuitBreakerConfig getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerConfig v) { circuitBreaker = v; }
    public LlmProviderConfig getLlm() { return llm; }
    public void setLlm(LlmProviderConfig v) { llm = v; }
}
