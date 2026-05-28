package com.ai.learning.obs.service;

import com.ai.learning.obs.metrics.LlmMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated LLM service that generates realistic metrics.
 * In production, this would proxy real LLM calls.
 */
@Service
public class LlmProxyService {

    private static final Logger log = LoggerFactory.getLogger(LlmProxyService.class);

    private final LlmMetrics metrics;
    private final Random random = new Random();

    // Simulated cache
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private int cacheSize = 0;

    public LlmProxyService(LlmMetrics metrics) {
        this.metrics = metrics;
        // Pre-populate some cache entries
        cache.put("hello", "Hello! How can I help you today?");
        cache.put("what is ai", "AI (Artificial Intelligence) is the simulation of human intelligence by machines.");
        cacheSize = cache.size();
    }

    /**
     * Simulate an LLM request with realistic metrics.
     */
    public String call(String prompt) {
        long start = System.currentTimeMillis();
        metrics.recordRequest();

        String normalized = prompt.trim().toLowerCase();

        // Check cache
        if (cache.containsKey(normalized)) {
            metrics.recordCacheHit();
            String response = cache.get(normalized);
            long latency = System.currentTimeMillis() - start;
            metrics.recordLatency(latency);
            metrics.recordTokens(response.length() / 2 + 5);  // rough token estimate
            log.info("Cache HIT for prompt='{}' latency={}ms", prompt, latency);
            return "[CACHE] " + response;
        }

        // Simulate occasional errors
        if (random.nextDouble() < 0.05) {  // 5% error rate
            long latency = System.currentTimeMillis() - start;
            metrics.recordLatency(latency);
            metrics.recordError("timeout");
            log.warn("LLM TIMEOUT for prompt='{}' latency={}ms", prompt, latency);
            throw new RuntimeException("LLM timeout");
        }

        // Simulate rate limiting
        if (random.nextDouble() < 0.03) {  // 3% rate limited
            long latency = System.currentTimeMillis() - start;
            metrics.recordLatency(latency);
            metrics.recordRateLimitBlock();
            log.warn("RATE LIMITED for prompt='{}'", prompt);
            return "[RATE_LIMITED] Too many requests. Please try again later.";
        }

        // Normal LLM call simulation
        try {
            // Simulate LLM processing time (50-500ms)
            int processingTime = 50 + random.nextInt(450);
            Thread.sleep(processingTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long latency = System.currentTimeMillis() - start;
        String response = generateResponse(prompt);
        int tokens = response.length() / 2 + 10;

        metrics.recordLatency(latency);
        metrics.recordTokens(tokens);
        metrics.recordCacheMiss();

        log.info("LLM OK prompt='{}' latency={}ms tokens={}", prompt, latency, tokens);
        return response;
    }

    private String generateResponse(String prompt) {
        return "关于「" + prompt + "」的回答：这是一个模拟的LLM响应。"
                + "在实际部署中，这里会返回来自大语言模型的真实回答。"
                + "当前请求已计入可观测性系统。";
    }

    /**
     * Scheduled task: periodically purge old cache and report metrics.
     */
    @Scheduled(fixedRate = 30_000)
    public void reportMetrics() {
        var snap = metrics.getSnapshot();
        log.info("Metrics report: requests={}, errors={}, hitRate={}, p50Latency={}ms",
                snap.get("totalRequests"), snap.get("totalErrors"),
                snap.get("hitRate"), snap.get("p50LatencyMs"));
    }
}
