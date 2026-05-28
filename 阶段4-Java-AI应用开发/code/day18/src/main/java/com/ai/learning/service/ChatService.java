package com.ai.learning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 聊天服务 — 支持多轮对话记忆 + 流式输出
 * 
 * 核心能力：
 * 1. 会话管理（多用户隔离）
 * 2. 对话历史记忆
 * 3. 同步对话
 * 4. 流式输出（SSE 打字机效果）
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;

    /** 会话存储：sessionId → 消息历史列表 */
    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();

    /** 最大保留的历史轮数（每轮=一问一答） */
    private static final int MAX_HISTORY_ROUNDS = 10;

    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // =========================================================
    // 会话管理
    // =========================================================

    /**
     * 创建新会话
     * @return 新会话ID
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(sessionId, new ArrayList<>());
        log.info("创建新会话: {}", sessionId);
        return sessionId;
    }

    /**
     * 获取会话历史
     */
    public List<Map<String, String>> getHistory(String sessionId) {
        return sessions.getOrDefault(sessionId, Collections.emptyList());
    }

    /**
     * 清空会话历史
     */
    public void clearHistory(String sessionId) {
        List<Map<String, String>> history = sessions.get(sessionId);
        if (history != null) {
            history.clear();
        }
    }

    // =========================================================
    // 同步对话（含记忆）
    // =========================================================

    /**
     * 同步对话 — 带多轮记忆
     * @param sessionId 会话ID
     * @param message   用户输入
     * @return AI回复
     */
    public String chat(String sessionId, String message) {
        // 1. 构建含上下文的提示词
        String prompt = buildPromptWithHistory(sessionId, message);

        // 2. 调用AI
        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 3. 保存到历史
        saveExchange(sessionId, message, response);

        return response;
    }

    // =========================================================
    // 流式对话（SSE打字机效果）
    // =========================================================

    /**
     * 流式对话 — 逐字输出（SSE打字机效果）
     * 流结束时自动保存完整回复到会话历史
     */
    public Flux<String> streamChat(String sessionId, String message) {
        // 1. 构建含上下文的提示词
        String prompt = buildPromptWithHistory(sessionId, message);

        // 2. 用 StringBuilder 收集流式输出的完整内容
        StringBuilder collector = new StringBuilder();

        // 3. 流式调用，逐块收集，结束时保存历史
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnNext(collector::append)       // 逐块收集
                .doOnComplete(() -> {
                    saveExchange(sessionId, message, collector.toString());
                    log.info("会话 {} 流式完成，共收到 {} 字符", sessionId, collector.length());
                });
    }

    /**
     * 保存一次问答到历史（供Controller流式完成后调用）
     */
    public void saveExchange(String sessionId, String userMessage, String assistantResponse) {
        List<Map<String, String>> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

        // 添加用户消息
        Map<String, String> userEntry = new HashMap<>();
        userEntry.put("role", "user");
        userEntry.put("content", userMessage);
        history.add(userEntry);

        // 添加AI回复
        Map<String, String> assistantEntry = new HashMap<>();
        assistantEntry.put("role", "assistant");
        assistantEntry.put("content", assistantResponse);
        history.add(assistantEntry);

        // 裁剪超出轮数的历史（太长的历史会浪费Token）
        while (history.size() > MAX_HISTORY_ROUNDS * 2) {
            history.remove(0); // 移除最早的消息（成对移除）
            if (!history.isEmpty()) history.remove(0);
        }
    }

    // =========================================================
    // 私有方法
    // =========================================================

    /**
     * 构建带历史上下文的提示词
     * 
     * 原理：把历史对话拼入 user 消息的开头，让AI"知道"之前说过什么。
     * 这是一种简单但有效的记忆实现方式。
     */
    private String buildPromptWithHistory(String sessionId, String currentMessage) {
        List<Map<String, String>> history = sessions.getOrDefault(sessionId, Collections.emptyList());

        if (history.isEmpty()) {
            // 没有历史，直接返回当前消息
            return currentMessage;
        }

        // 拼接历史对话
        StringBuilder sb = new StringBuilder();
        sb.append("以下是我们的对话历史（请结合历史回答当前问题）：\n\n");

        for (Map<String, String> entry : history) {
            String role = entry.get("role");
            String content = entry.get("content");
            if ("user".equals(role)) {
                sb.append("用户：").append(content).append("\n");
            } else {
                sb.append("AI助手：").append(content).append("\n");
            }
        }

        sb.append("\n--- 当前问题 ---\n");
        sb.append("用户：").append(currentMessage);

        return sb.toString();
    }
}
