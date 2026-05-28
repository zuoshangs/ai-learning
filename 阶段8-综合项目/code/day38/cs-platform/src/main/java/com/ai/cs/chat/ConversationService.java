package com.ai.cs.chat;

import com.ai.cs.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Core conversation service with multi-turn memory and LLM integration.
 * Pipeline: Build prompt with history → Call LLM → Store response → Return
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationMemory memory;
    private final HttpClient httpClient;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String model;

    @Value("${app.chat.max-tokens:1024}")
    private int maxTokens;

    @Value("${app.chat.temperature:0.7}")
    private double temperature;

    public ConversationService(ConversationMemory memory, HttpClient httpClient) {
        this.memory = memory;
        this.httpClient = httpClient;
    }

    /**
     * Process a chat message through the conversation pipeline.
     */
    public ChatResponse processMessage(String sessionId, String message) {
        long start = System.currentTimeMillis();

        // 1. Auto-create session if new
        String sid = memory.addMessage(sessionId, "user", message);

        // 2. Build conversation prompt with history
        String prompt = buildPrompt(sid);

        // 3. Call LLM
        String reply = callLLM(prompt);

        // 4. Store assistant response
        memory.addMessage(sid, "assistant", reply);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Chat session={} turn={} time={}ms", sid,
                memory.getHistory(sid).size() / 2, elapsed);

        return new ChatResponse(sid, reply, memory.getHistory(sid).size(),
                System.currentTimeMillis());
    }

    /**
     * Build a prompt with conversation history for multi-turn context.
     */
    private String buildPrompt(String sessionId) {
        List<Map<String, Object>> history = memory.getHistory(sessionId);
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个专业的AI客服助手。请基于对话历史和用户问题，给出准确、友好的回答。\n\n");
        sb.append("## 对话历史\n");

        for (Map<String, Object> msg : history) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");
            sb.append(role.equals("user") ? "用户: " : "客服: ");
            sb.append(content).append("\n");
        }

        // If this is the last user message, no need to add it again
        // The model will determine what to respond
        sb.append("客服:");
        return sb.toString();
    }

    /**
     * Call DeepSeek API with the conversation prompt.
     */
    private String callLLM(String prompt) {
        try {
            String body = String.format("""
                    {
                      "model": "%s",
                      "messages": [{"role": "user", "content": "%s"}],
                      "max_tokens": %d,
                      "temperature": %.1f
                    }
                    """, model, prompt.replace("\"", "\\\"")
                    .replace("\n", "\\n"), maxTokens, temperature);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> resp = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("LLM API error: {} {}", resp.statusCode(), resp.body());
                return "抱歉，AI 服务暂时不可用，请稍后再试。";
            }

            // Parse response
            String respBody = resp.body();
            int idx = respBody.indexOf("\"content\":\"");
            if (idx < 0) {
                log.warn("Unexpected LLM response format: {}", respBody.substring(0, 100));
                return "抱歉，我暂时无法理解您的问题。";
            }
            idx += 11; // skip "content":"
            int end = respBody.indexOf("\"", idx);
            if (end < 0) return "抱歉，我暂时无法理解您的问题。";

            return respBody.substring(idx, end);

        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            return "抱歉，AI 服务暂时不可用，请稍后再试。";
        }
    }

    public List<Map<String, Object>> getHistory(String sessionId) {
        return memory.getHistory(sessionId);
    }

    public void clearSession(String sessionId) {
        memory.clearSession(sessionId);
    }
}
