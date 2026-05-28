package com.ai.llm.gateway.monitor;

import com.ai.llm.gateway.cache.SemanticCacheService;
import com.ai.llm.gateway.circuit.CircuitBreakerService;
import com.ai.llm.gateway.filter.RateLimitService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Custom health indicator exposing upstream service status.
 */
@Component
public class CustomHealthIndicator implements HealthIndicator {

    private final SemanticCacheService cache;
    private final CircuitBreakerService circuitBreaker;
    private final RateLimitService rateLimiter;
    private final MetricsService metrics;

    public CustomHealthIndicator(SemanticCacheService cache,
                                 CircuitBreakerService circuitBreaker,
                                 RateLimitService rateLimiter,
                                 MetricsService metrics) {
        this.cache = cache;
        this.circuitBreaker = circuitBreaker;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @Override
    public Health health() {
        boolean circuitOk = circuitBreaker.getState() != CircuitBreakerService.State.OPEN;
        boolean cacheOk = cache.hitRate() >= 0; // Always operational

        Health.Builder builder;
        if (circuitOk && cacheOk) {
            builder = Health.up();
        } else {
            builder = Health.down();
        }

        return builder
                .withDetail("circuitBreaker", Map.of(
                        "state", circuitBreaker.getState().name(),
                        "failures", circuitBreaker.getFailureCount()
                ))
                .withDetail("cache", Map.of(
                        "size", cache.size(),
                        "hitRate", String.format("%.1f%%", cache.hitRate()),
                        "hits", cache.hits(),
                        "misses", cache.misses()
                ))
                .withDetail("rateLimiter", Map.of(
                        "defaultRpm", rateLimiter.getDefaultRpm(),
                        "totalRequests", rateLimiter.totalRequests()
                ))
                .withDetail("metrics", Map.of(
                        "totalRequests", String.format("%.0f", metrics.getRequestCount()),
                        "totalErrors", String.format("%.0f", metrics.getErrorCount()),
                        "totalTokens", String.format("%.0f", metrics.getTotalTokens()),
                        "totalCost", String.format("$%.4f", metrics.getTotalCost())
                ))
                .build();
    }
}
