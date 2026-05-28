package com.ai.learning.gateway.controller;

import com.ai.learning.gateway.auth.ApiKeyManager;
import com.ai.learning.gateway.circuitbreaker.CircuitBreaker;
import com.ai.learning.gateway.gateway.LLMGateway;
import com.ai.learning.gateway.ratelimit.RateLimiter;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin controller for managing gateway configuration.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final LLMGateway gateway;
    private final ApiKeyManager apiKeyManager;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    public AdminController(LLMGateway gateway, ApiKeyManager apiKeyManager,
                           RateLimiter rateLimiter, CircuitBreaker circuitBreaker) {
        this.gateway = gateway;
        this.apiKeyManager = apiKeyManager;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
    }

    /** Gateway stats. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return gateway.getStats();
    }

    /** Register a new API key. */
    @PostMapping("/keys")
    public Map<String, String> addKey(@RequestParam String key, @RequestParam String tier, @RequestParam String owner) {
        apiKeyManager.addKey(key, tier, owner);
        return Map.of("status", "ok", "key", key, "tier", tier);
    }

    /** List all API keys (masked). */
    @GetMapping("/keys")
    public Map<String, Map<String, String>> listKeys() {
        var raw = apiKeyManager.getAllKeys();
        Map<String, String> masked = new java.util.LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            String k = entry.getKey();
            masked.put(k.length() > 8 ? k.substring(0, 8) + "..." + k.substring(k.length() - 4) : k, entry.getValue());
        }
        return Map.of("keys", masked);
    }

    /** Delete an API key. */
    @DeleteMapping("/keys/{key}")
    public Map<String, String> deleteKey(@PathVariable String key) {
        apiKeyManager.removeKey(key);
        return Map.of("status", "deleted");
    }

    /** Reset rate limiter for a key. */
    @PostMapping("/ratelimit/reset")
    public Map<String, String> resetRateLimit(@RequestParam String key) {
        rateLimiter.reset(key);
        return Map.of("status", "reset", "key", key);
    }

    /** Get rate limit status for a key. */
    @GetMapping("/ratelimit")
    public Map<String, Object> getRateLimit(@RequestParam String key) {
        return Map.of(
                "key", key,
                "current", rateLimiter.getCurrentCount(key),
                "limit", rateLimiter.getLimit(key)
        );
    }

    /** Reset circuit breaker. */
    @PostMapping("/circuit/reset")
    public Map<String, String> resetCircuit() {
        circuitBreaker.reset();
        return Map.of("status", "reset", "state", circuitBreaker.getState().name());
    }

    /** Get circuit breaker status. */
    @GetMapping("/circuit")
    public Map<String, Object> getCircuit() {
        return Map.of(
                "state", circuitBreaker.getState().name(),
                "failureCount", circuitBreaker.getFailureCount(),
                "rejectedRequests", circuitBreaker.getRejectedRequests(),
                "lastFailureTime", circuitBreaker.getLastFailureTime().toString(),
                "lastStateChange", circuitBreaker.getLastStateChange().toString()
        );
    }

    /** Test endpoint (no auth required). */
    @GetMapping("/keys/test")
    public Map<String, String> test() {
        return Map.of(
                "message", "Gateway admin API is running",
                "testKeys", "sk-gold-tier, sk-silver-tier, sk-free-tier"
        );
    }
}
