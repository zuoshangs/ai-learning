package com.ai.learning.gateway.provider;

import com.ai.learning.gateway.model.ChatRequest;
import com.ai.learning.gateway.model.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * DeepSeek LLM provider implementation.
 * Calls the DeepSeek API directly via HTTP (bypasses Spring AI for gateway control).
 */
@Component
public class DeepSeekProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProvider.class);

    private final String endpoint;
    private final String apiKey;
    private final String defaultModel;
    private final int timeoutMs;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeepSeekProvider(
            @org.springframework.beans.factory.annotation.Value("${gateway.llm.endpoint}") String endpoint,
            @org.springframework.beans.factory.annotation.Value("${gateway.llm.api-key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${gateway.llm.model}") String defaultModel,
            @org.springframework.beans.factory.annotation.Value("${gateway.llm.timeout-ms}") int timeoutMs) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        log.info("DeepSeekProvider initialized: endpoint={}, apiKey={}..., model={}, timeout={}ms",
                endpoint,
                apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) : "MISSING",
                defaultModel, timeoutMs);
        this.defaultModel = defaultModel;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getProviderName() {
        return "deepseek";
    }

    @Override
    public ChatResponse call(ChatRequest request) {
        long start = System.currentTimeMillis();
        try {
            // Build request body
            ObjectNode body = mapper.createObjectNode();
            body.put("model", request.getModel() != null ? request.getModel() : defaultModel);
            body.put("temperature", request.getTemperature());
            body.put("max_tokens", request.getMaxTokens());

            ArrayNode messages = body.putArray("messages");
            for (ChatRequest.Message msg : request.getMessages()) {
                ObjectNode m = messages.addObject();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
            }

            String json = mapper.writeValueAsString(body);

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> httpRes = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (httpRes.statusCode() != 200) {
                String respBody = httpRes.body();
                log.warn("DeepSeek API returned {}: {}", httpRes.statusCode(), respBody);
                try {
                    JsonNode err = mapper.readTree(respBody);
                    log.warn("DeepSeek error body: {}", err);
                } catch (Exception ignored) {}
                return ChatResponse.error("provider_error:" + httpRes.statusCode());
            }

            JsonNode res = mapper.readTree(httpRes.body());
            String content = res.path("choices").get(0).path("message").path("content").asText();
            int promptTokens = res.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = res.path("usage").path("completion_tokens").asInt(0);

            return ChatResponse.success(content, defaultModel, promptTokens, completionTokens, latency);

        } catch (java.net.http.HttpTimeoutException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("DeepSeek API timeout after {}ms", timeoutMs);
            return ChatResponse.error("timeout");
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("DeepSeek API call failed: {}", e.getMessage());
            return ChatResponse.error("provider_error:" + e.getMessage());
        }
    }
}
