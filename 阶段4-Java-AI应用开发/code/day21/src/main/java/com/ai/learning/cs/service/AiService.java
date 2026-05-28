package com.ai.learning.cs.service;

import com.ai.learning.cs.model.ChatResponse;
import com.ai.learning.cs.model.IntentType;
import com.ai.learning.cs.prompt.PromptTemplates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一 AI 服务层
 * 
 * 核心职责：
 * 1. 意图识别 — 用 AI 分类用户问题
 * 2. 路由 — 根据意图加载对应的系统提示词模板
 * 3. 对话 — 带记忆的多轮对话
 * 4. 重试 — 异常自动重试
 * 5. 降级 — API 不可用时的优雅降级
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final ChatClient chatClient;
    private final SessionManager sessionManager;

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 2;

    public AiService(ChatClient.Builder builder, SessionManager sessionManager) {
        this.chatClient = builder.build();
        this.sessionManager = sessionManager;
    }

    /**
     * 客服对话 — 意图识别 + 路由 + 带记忆 + 重试
     * 
     * @param sessionId 会话ID（为空则创建新会话）
     * @param message   用户消息
     * @return 客服响应
     */
    public ChatResponse chat(String sessionId, String message) {
        // 1. 新会话 → 创建
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = sessionManager.createSession();
        }

        // 2. 保存用户消息
        sessionManager.addUserMessage(sessionId, message);

        // 3. 意图识别（对每个新问题重新分类）
        IntentType intent = detectIntent(message);

        // 4. 构建消息列表：系统提示 + 历史
        String systemPrompt = PromptTemplates.getSystemPrompt(intent);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(sessionManager.getHistory(sessionId));

        // 5. 调用 AI + 自动重试
        String reply = callWithRetry(messages, MAX_RETRIES);

        // 6. 保存 AI 回复
        sessionManager.addAssistantMessage(sessionId, reply);

        return new ChatResponse(sessionId, reply, intent);
    }

    /**
     * 意图识别 — 用 AI 自己分类用户问题
     */
    private IntentType detectIntent(String message) {
        try {
            String result = chatClient.prompt()
                .system(PromptTemplates.INTENT_CLASSIFIER)
                .user(message)
                .call()
                .content();

            log.debug("意图识别结果: {}", result);

            // 解析 JSON 提取 intent
            if (result != null && result.contains("\"intent\"")) {
                for (IntentType t : IntentType.values()) {
                    if (result.contains("\"" + t.code + "\"")) {
                        log.info("用户意图: {} ({})", t.label, t.code);
                        return t;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("意图识别失败，默认 GENERAL: {}", e.getMessage());
        }
        return IntentType.GENERAL;
    }

    /**
     * 带重试的 AI 调用
     */
    private String callWithRetry(List<Message> messages, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                return chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();
            } catch (Exception e) {
                lastException = e;
                log.warn("AI 调用失败 (第{}次): {}", attempt, e.getMessage());
                if (attempt <= maxRetries) {
                    // 指数退避：1s, 2s
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { break; }
                }
            }
        }

        // 所有重试都失败 → 降级响应
        log.error("AI 调用全部失败，使用降级响应", lastException);
        return "😔 抱歉，系统暂时繁忙，请稍后再试。如果问题紧急，请拨打客服热线 400-xxx-xxxx。";
    }

    /**
     * 流式客服对话 — 返回完整的 Flux<String> 用于 SSE
     */
    public Flux<String> chatStream(String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = sessionManager.createSession();
        }
        sessionManager.addUserMessage(sessionId, message);
        IntentType intent = detectIntent(message);
        String systemPrompt = PromptTemplates.getSystemPrompt(intent);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(sessionManager.getHistory(sessionId));

        return Flux.concat(
            // 先发元数据
            Flux.just("data:{\"type\":\"meta\",\"sessionId\":\"" + sessionId + "\",\"intent\":\"" + intent.code + "\"}\n\n"),
            // 流式内容
            chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .map(chunk -> "data:" + chunk + "\n\n"),
            // 完成标记
            Flux.just("data:{\"type\":\"done\"}\n\n")
        );
    }
}
