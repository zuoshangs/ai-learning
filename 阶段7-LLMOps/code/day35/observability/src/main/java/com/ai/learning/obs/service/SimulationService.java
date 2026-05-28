package com.ai.learning.obs.service;

import com.ai.learning.obs.metrics.LlmMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Generates simulated traffic to produce realistic metrics.
 * Controlled by `simulation.enabled` property.
 */
@Service
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true", matchIfMissing = false)
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final LlmMetrics metrics;
    private final Random random = new Random();
    private final String[] prompts = {
            "hello", "what is ai", "how are you", "tell me a joke",
            "what is java", "explain microservices", "how does caching work",
            "what is observability", "what is kubernetes", "explain prometheus",
            "how to debug", "what is docker", "explain rest api",
            "what is machine learning", "how to write tests",
    };

    public SimulationService(LlmMetrics metrics) {
        this.metrics = metrics;
        log.info("Simulation service started — generating synthetic metrics");
    }

    @Scheduled(fixedRate = 1000)  // Every second
    public void simulateRequest() {
        // Small random delay to vary request pattern
        if (random.nextDouble() > 0.35) return;  // ~35% of ticks generate a request

        String prompt = prompts[random.nextInt(prompts.length)];
        long start = System.currentTimeMillis();
        metrics.recordRequest();

        // Simulate cache hit (30% chance)
        if (random.nextDouble() < 0.30 && random.nextDouble() < 0.7) {
            long latency = 2 + random.nextInt(8);
            metrics.recordLatency(latency);
            metrics.recordCacheHit();
            metrics.recordTokens(10 + random.nextInt(30));
            return;
        }

        // Simulate errors (5% chance)
        if (random.nextDouble() < 0.05) {
            long latency = 100 + random.nextInt(400);
            metrics.recordLatency(latency);
            String[] errorTypes = {"timeout", "rate_limit", "auth_error", "server_error"};
            metrics.recordError(errorTypes[random.nextInt(errorTypes.length)]);
            return;
        }

        // Simulate rate limiting (3% chance)
        if (random.nextDouble() < 0.03) {
            long latency = 1 + random.nextInt(5);
            metrics.recordLatency(latency);
            metrics.recordRateLimitBlock();
            return;
        }

        // Normal request
        long latency = 50 + random.nextInt(950);  // 50-1000ms
        metrics.recordLatency(latency);
        metrics.recordCacheMiss();
        metrics.recordTokens(50 + random.nextInt(200));

        try {
            Thread.sleep(1);  // Yield to prevent tight loop
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
