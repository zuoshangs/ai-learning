package com.ai.learning.gateway.config;

import com.ai.learning.gateway.auth.ApiKeyManager;
import com.ai.learning.gateway.circuitbreaker.CircuitBreaker;
import com.ai.learning.gateway.ratelimit.RateLimiter;
import com.ai.learning.gateway.ratelimit.SlidingWindowRateLimiter;
import com.ai.learning.gateway.ratelimit.TokenBucketRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter rateLimiter(GatewayConfig config, ApiKeyManager apiKeyManager) {
        // Provider function: get rate limit per key based on tier
        java.util.function.Function<String, Integer> limitProvider = apiKey -> {
            int override = config.getRateOverrides().getOrDefault(apiKey, -1);
            if (override > 0) return override;
            return apiKeyManager.getRateLimit(apiKey);
        };

        if ("sliding-window".equalsIgnoreCase(config.getRateLimitType())) {
            return new SlidingWindowRateLimiter(limitProvider);
        }
        return new TokenBucketRateLimiter(limitProvider);
    }

    @Bean
    public CircuitBreaker circuitBreaker(GatewayConfig config) {
        GatewayConfig.CircuitBreakerConfig cb = config.getCircuitBreaker();
        return new CircuitBreaker(cb.getFailureThreshold(), cb.getResetTimeoutMs(), cb.getHalfOpenMaxRequests());
    }
}
