package com.ai.cs.chat;

import com.ai.cs.monitor.CostTracker;
import com.ai.cs.monitor.MetricsCollector;
import com.ai.cs.monitor.RateLimiter;
import com.ai.cs.monitor.ResponseCache;
import com.ai.cs.knowledge.KnowledgeBaseService;
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
 * Core conversation service with multi-turn memory + RAG knowledge injection
 * + rate limiting + response caching + cost tracking.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationMemory memory;
    private final KnowledgeBaseService knowledgeBase;
    private final HttpClient httpClient;
    private final MetricsCollector metricsCollector;
    private final RateLimiter rateLimiter;
    private final ResponseCache responseCache;
    private final CostTracker costTracker;

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

    public ConversationService(ConversationMemory memory, KnowledgeBaseService knowledgeBase,
                               HttpClient httpClient, MetricsCollector metricsCollector,
                               RateLimiter rateLimiter, ResponseCache responseCache,
                               CostTracker costTracker) {
        this.memory = memory;
        this.knowledgeBase = knowledgeBase;
        this.httpClient = httpClient;
        this.metricsCollector = metricsCollector;
        this.rateLimiter = rateLimiter;
        this.responseCache = responseCache;
        this.costTracker = costTracker;
    }

    /**
     * Process a chat message with RAG enhancement + LLMOps monitoring.
     */
    public ChatResponse processMessage(String sessionId, String message) {
        long start = System.currentTimeMillis();

        // 1. Rate limiting check
        if (!rateLimiter.allowRequest(sessionId != null ? sessionId : "anonymous")) {
            log.warn("Rate limited: session={}", sessionId);
            return new ChatResponse(sessionId != null ? sessionId : "limited",
                    "😅 请求过于频繁，请稍后再试。", 0, System.currentTimeMillis());
        }

        // 2. Check cache for identical/similar queries
        String cached = responseCache.get(message);
        if (cached != null) {
            log.info("Cache HIT for: {}", message.substring(0, Math.min(30, message.length())));
            String sid = sessionId != null ? sessionId : "cached";
            memory.addMessage(sid, "user", message);
            memory.addMessage(sid, "assistant", cached + "\n\n*(来自缓存)*");
            metricsCollector.recordMessage();
            return new ChatResponse(sid, cached + "\n\n*(来自缓存)*",
                    memory.getHistory(sid).size(), System.currentTimeMillis());
        }

        // 3. Auto-create/assign session
        String sid = memory.addMessage(sessionId, "user", message);
        metricsCollector.recordMessage();
        if (sessionId == null) {
            metricsCollector.recordSessionCreated();
        }
        // Not a new session count since session may already exist

        // 4. Build conversation prompt with history + RAG context
        String prompt = buildPromptWithRag(sid, message);

        // 5. Call LLM
        String reply = callLLM(prompt);

        // 6. Store in cache (for future identical queries)
        responseCache.put(message, reply);

        // 7. Track cost
        costTracker.trackCall(prompt, reply, model);

        // 8. Store assistant response
        memory.addMessage(sid, "assistant", reply);

        long elapsed = System.currentTimeMillis() - start;
        metricsCollector.recordLlmCall(elapsed, costTracker.estimateTokens(prompt + reply), model);

        log.info("Chat session={} turn={} time={}ms", sid,
                memory.getHistory(sid).size() / 2, elapsed);

        return new ChatResponse(sid, reply, memory.getHistory(sid).size(),
                System.currentTimeMillis());
    }

    /**
     * Build prompt with conversation history + RAG knowledge context.
     */
    private String buildPromptWithRag(String sessionId, String currentMessage) {
        List<Map<String, Object>> history = memory.getHistory(sessionId);
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个专业的AI客服助手。请基于对话历史和公司知识库，给出准确、友好的回答。\n");
        sb.append("如果知识库中有相关信息，请优先引用知识库内容。如果知识库中没有相关信息，请诚实告知用户。\n\n");

        // Inject RAG context from knowledge base
        String ragContext = knowledgeBase.buildRagContext(currentMessage, 3);
        if (!ragContext.isEmpty()) {
            sb.append(ragContext).append("\n\n");
        }

        sb.append("## 对话历史\n");
        for (Map<String, Object> msg : history) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");
            sb.append(role.equals("user") ? "用户: " : "客服: ");
            sb.append(content).append("\n");
        }

        sb.append("客服:");
        return sb.toString();
    }

    /**
     * Call DeepSeek API with the conversation prompt.
     */
    private String callLLM(String prompt) {
        try {
            String escapedPrompt = prompt.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t");

            String body = String.format("""
                    {
                      "model": "%s",
                      "messages": [{"role": "user", "content": "%s"}],
                      "max_tokens": %d,
                      "temperature": %.1f
                    }
                    """, model, escapedPrompt, maxTokens, temperature);

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

            String respBody = resp.body();
            int idx = respBody.indexOf("\"content\":\"");
            if (idx < 0) {
                log.warn("Unexpected LLM response format: {}", respBody.substring(0, 100));
                return "抱歉，我暂时无法理解您的问题。";
            }
            idx += 11;
            int end = respBody.indexOf("\"", idx);
            if (end < 0) return "抱歉，我暂时无法理解您的问题。";

            String content = respBody.substring(idx, end);
            // Unescape
            content = content.replace("\\n", "\n").replace("\\t", "\t");
            return content;

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
        metricsCollector.recordSessionClosed();
    }
}
