package com.ai.learning.cache.service;

import com.ai.learning.cache.config.CacheConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LLM service used when cache misses.
 * Calls DeepSeek API directly via HTTP.
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmService(CacheConfig config) {
        CacheConfig.LlmConfig llm = config.getLlm();
        this.endpoint = llm.getEndpoint();
        this.apiKey = llm.getApiKey();
        this.model = llm.getModel();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String call(String query) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.7);
            body.put("max_tokens", 1024);

            ArrayNode messages = body.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", query);

            String json = mapper.writeValueAsString(body);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofMillis(30000))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                log.warn("LLM API returned {}: {}", res.statusCode(), res.body());
                return "（LLM服务暂时不可用）";
            }

            JsonNode root = mapper.readTree(res.body());
            return root.path("choices").get(0).path("message").path("content").asText("");

        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            return "（LLM调用失败: " + e.getMessage() + "）";
        }
    }
}
