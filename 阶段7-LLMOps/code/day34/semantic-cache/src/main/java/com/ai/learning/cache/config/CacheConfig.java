package com.ai.learning.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the semantic cache.
 */
@Configuration
@ConfigurationProperties(prefix = "cache")
public class CacheConfig {

    /** Similarity threshold for cache hit (0.0 - 1.0). Higher = stricter match. */
    private double similarityThreshold = 0.55;

    /** Maximum cache entries before LRU eviction. */
    private int maxSize = 1000;

    /** Default TTL in seconds (0 = no expiry). */
    private long defaultTtlSeconds = 3600;

    /** LLM endpoint for cache misses. */
    private LlmConfig llm = new LlmConfig();

    /** Warmup queries to pre-populate cache on startup. */
    private String warmupFile = "";

    public static class LlmConfig {
        private String endpoint = "https://api.deepseek.com/chat/completions";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private int timeoutMs = 30000;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { endpoint = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { apiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { model = v; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int v) { timeoutMs = v; }
    }

    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double v) { similarityThreshold = v; }
    public int getMaxSize() { return maxSize; }
    public void setMaxSize(int v) { maxSize = v; }
    public long getDefaultTtlSeconds() { return defaultTtlSeconds; }
    public void setDefaultTtlSeconds(long v) { defaultTtlSeconds = v; }
    public LlmConfig getLlm() { return llm; }
    public void setLlm(LlmConfig v) { llm = v; }
    public String getWarmupFile() { return warmupFile; }
    public void setWarmupFile(String v) { warmupFile = v; }
}
