package com.ai.llm.gateway.llm;

import com.ai.llm.gateway.cache.SemanticCacheService;
import com.ai.llm.gateway.circuit.CircuitBreakerService;
import com.ai.llm.gateway.cost.CostAnalyzerService;
import com.ai.llm.gateway.monitor.MetricsService;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Core LLM proxy service with full LLMOps pipeline:
 * cache → circuit breaker → rate limit → LLM call → metrics → cost
 */
@Service
public class LlmProxyService {

    private static final Logger log = LoggerFactory.getLogger(LlmProxyService.class);

    private final HttpClient httpClient;
    private final SemanticCacheService cache;
    private final CircuitBreakerService circuitBreaker;
    private final MetricsService metrics;
    private final CostAnalyzerService costAnalyzer;

    @Value("${deepseek.api.url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;

    @Value("${deepseek.api.key:}")
    private String apiKey;

    @Value("${gateway.llm.model:deepseek-chat}")
    private String defaultModel;

    public LlmProxyService(HttpClient httpClient,
                           SemanticCacheService cache,
                           CircuitBreakerService circuitBreaker,
                           MetricsService metrics,
                           CostAnalyzerService costAnalyzer) {
        this.httpClient = httpClient;
        this.cache = cache;
        this.circuitBreaker = circuitBreaker;
        this.metrics = metrics;
        this.costAnalyzer = costAnalyzer;
    }

    /**
     * Process a chat request through the full pipeline.
     *
     * Pipeline: Auth → Trace (filters) → Cache → CircuitBreaker → LLM → Metrics → Cost
     */
    public ChatResult chat(String userMessage, String model, String apiKeyTier) {
        Timer.Sample sample = metrics.startTimer();
        metrics.recordRequest();

        String actualModel = (model != null && !model.isBlank()) ? model : defaultModel;

        try {
            // 1. Check cache
            CacheResult cached = checkCache(userMessage);
            if (cached != null) {
                metrics.stopTimer(sample);
                return new ChatResult(cached.response, actualModel, 0, 0, 0, true);
            }

            // 2. Check circuit breaker
            if (!circuitBreaker.isAvailable()) {
                metrics.recordError();
                metrics.stopTimer(sample);
                return new ChatResult("服务暂不可用（熔断中）", actualModel, 0, 0, 0, false, "CIRCUIT_OPEN");
            }

            // 3. Call LLM
            int inputTokens = estimateTokens(userMessage);
            String prompt = buildPrompt(userMessage);
            LlmResponse response = callDeepSeek(prompt, actualModel);

            if (response == null || !response.success) {
                circuitBreaker.onFailure();
                metrics.recordError();
                metrics.stopTimer(sample);

                String error = response != null ? response.errorMsg : "LLM call failed";
                return new ChatResult(error, actualModel, inputTokens, 0, 0, false, error);
            }

            circuitBreaker.onSuccess();

            // 4. Calculate cost
            int outputTokens = estimateTokens(response.content);
            double cost = costAnalyzer.recordUsage(actualModel, apiKeyTier, inputTokens, outputTokens);

            // 5. Record metrics
            metrics.recordTokens(actualModel, inputTokens + outputTokens);
            metrics.recordCost(cost);

            // 6. Cache the response
            cache.put(userMessage, response.content);

            metrics.stopTimer(sample);

            return new ChatResult(response.content, actualModel, inputTokens, outputTokens, cost, false);

        } catch (Exception e) {
            log.error("LLM proxy error: {}", e.getMessage());
            circuitBreaker.onFailure();
            metrics.recordError();
            metrics.stopTimer(sample);
            return new ChatResult("内部错误: " + e.getMessage(), actualModel, 0, 0, 0, false, "INTERNAL_ERROR");
        }
    }

    // ---- Internal methods ----

    private CacheResult checkCache(String query) {
        String cached = cache.get(query);
        if (cached != null) {
            log.info("Cache HIT for query: {}", query.substring(0, Math.min(30, query.length())));
            return new CacheResult(cached);
        }
        log.info("Cache MISS for query: {}", query.substring(0, Math.min(30, query.length())));
        return null;
    }

    private LlmResponse callDeepSeek(String prompt, String model) {
        try {
            String body = String.format("""
                    {
                      "model": "%s",
                      "messages": [{"role": "user", "content": "%s"}],
                      "max_tokens": 512,
                      "temperature": 0.7
                    }
                    """, model, prompt.replace("\"", "\\\"").replace("\n", "\\n"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                return new LlmResponse(false, "API error: " + resp.statusCode() + " - " + resp.body());
            }

            // Simple JSON parsing - extract content from DeepSeek response
            String respBody = resp.body();
            // Find "content":"..." in response
            int idx = respBody.indexOf("\"content\":\"");
            if (idx < 0) {
                return new LlmResponse(false, "Unexpected response format: " + respBody);
            }
            idx += 11; // skip "content":"
            int end = respBody.indexOf("\"", idx);
            if (end < 0) {
                return new LlmResponse(false, "Unterminated content field");
            }

            String content = respBody.substring(idx, end);
            return new LlmResponse(true, content);

        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            return new LlmResponse(false, e.getMessage());
        }
    }

    private String buildPrompt(String userMessage) {
        return userMessage;
    }

    private int estimateTokens(String text) {
        // Rough estimate: ~2 chars per token for Chinese, ~4 for English
        int chinese = 0, english = 0;
        for (char c : text.toCharArray()) {
            if (c > 0x4E00 && c < 0x9FFF) chinese++;
            else if (Character.isLetter(c)) english++;
        }
        return chinese / 2 + english / 4 + 5;
    }

    // ---- Internal types ----

    record CacheResult(String response) {}

    record LlmResponse(boolean success, String content, String errorMsg) {
        LlmResponse(boolean success, String content) {
            this(success, content, success ? null : content);
        }
    }

    public record ChatResult(String content, String model, int inputTokens, int outputTokens,
                             double cost, boolean fromCache, String error) {
        public ChatResult(String content, String model, int inputTokens, int outputTokens,
                          double cost, boolean fromCache) {
            this(content, model, inputTokens, outputTokens, cost, fromCache, null);
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "content", content,
                    "model", model,
                    "inputTokens", inputTokens,
                    "outputTokens", outputTokens,
                    "totalTokens", inputTokens + outputTokens,
                    "costUSD", String.format("%.6f", cost),
                    "fromCache", fromCache,
                    "error", error != null ? error : ""
            );
        }
    }
}
