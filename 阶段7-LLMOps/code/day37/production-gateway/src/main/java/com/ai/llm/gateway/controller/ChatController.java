package com.ai.llm.gateway.controller;

import com.ai.llm.gateway.cache.SemanticCacheService;
import com.ai.llm.gateway.circuit.CircuitBreakerService;
import com.ai.llm.gateway.cost.CostAnalyzerService;
import com.ai.llm.gateway.filter.AuthFilter;
import com.ai.llm.gateway.filter.RateLimitService;
import com.ai.llm.gateway.llm.LlmProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Main chat controller with full LLMOps pipeline.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final LlmProxyService llmProxy;
    private final RateLimitService rateLimiter;
    private final SemanticCacheService cache;
    private final CircuitBreakerService circuitBreaker;
    private final CostAnalyzerService costAnalyzer;

    public ChatController(LlmProxyService llmProxy,
                          RateLimitService rateLimiter,
                          SemanticCacheService cache,
                          CircuitBreakerService circuitBreaker,
                          CostAnalyzerService costAnalyzer) {
        this.llmProxy = llmProxy;
        this.rateLimiter = rateLimiter;
        this.cache = cache;
        this.circuitBreaker = circuitBreaker;
        this.costAnalyzer = costAnalyzer;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body,
                                                    HttpServletRequest request) {
        String apiKey = (String) request.getAttribute(AuthFilter.ATTR_API_KEY);
        String keyTier = (String) request.getAttribute(AuthFilter.ATTR_KEY_TIER);
        String message = body.getOrDefault("message", "");
        String model = body.getOrDefault("model", "");

        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }

        // Rate limit check
        if (!rateLimiter.tryAcquire(apiKey)) {
            log.warn("Rate limit exceeded for key tier={}", keyTier);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Rate limit exceeded (30 rpm)",
                    "retryAfter", "60s",
                    "remainingTokens", rateLimiter.getRemainingTokens(apiKey)
            ));
        }

        // Process through full pipeline
        LlmProxyService.ChatResult result = llmProxy.chat(message, model, keyTier);

        if (result.error() != null && !result.error().isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", result.error(),
                    "model", result.model()
            ));
        }

        Map<String, Object> response = new HashMap<>(result.toMap());
        response.put("success", true);
        response.put("tier", keyTier);
        response.put("remainingTokens", rateLimiter.getRemainingTokens(apiKey));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cache/stats")
    public Map<String, Object> cacheStats() {
        return Map.of(
                "size", cache.size(),
                "hits", cache.hits(),
                "misses", cache.misses(),
                "hitRate", String.format("%.1f%%", cache.hitRate())
        );
    }

    @GetMapping("/pipeline/status")
    public Map<String, Object> pipelineStatus() {
        return Map.of(
                "circuitBreaker", Map.of(
                        "state", circuitBreaker.getState().name(),
                        "failures", circuitBreaker.getFailureCount()
                ),
                "rateLimiter", Map.of(
                        "defaultRpm", rateLimiter.getDefaultRpm(),
                        "totalRequests", rateLimiter.totalRequests()
                ),
                "cache", Map.of(
                        "size", cache.size(),
                        "hitRate", String.format("%.1f%%", cache.hitRate())
                )
        );
    }

    @GetMapping("/cost/report")
    public CostAnalyzerService.CostReport costReport() {
        return costAnalyzer.generateReport();
    }

    @GetMapping("/cost/optimizations")
    public Map<String, Object> optimizations() {
        var suggestions = costAnalyzer.suggestOptimizations();
        return Map.of("suggestions", suggestions);
    }
}
