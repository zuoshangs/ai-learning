package com.ai.learning.cs.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器 — 多轮对话记忆
 * 
 * 管理多个用户会话，维护对话历史，支持历史裁剪防 Token 溢出。
 */
@Component
public class SessionManager {

    /** 最多保留的消息轮数 */
    private static final int MAX_HISTORY = 20;

    /** sessionId → 历史消息列表 */
    private final ConcurrentHashMap<String, List<Message>> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> creationTimes = new ConcurrentHashMap<>();

    /**
     * 创建新会话
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(sessionId, new ArrayList<>());
        creationTimes.put(sessionId, LocalDateTime.now());
        return sessionId;
    }

    /**
     * 添加用户消息到会话
     */
    public void addUserMessage(String sessionId, String content) {
        List<Message> history = getOrCreate(sessionId);
        history.add(new UserMessage(content));
        trimHistory(history);
    }

    /**
     * 添加 AI 回复到会话
     */
    public void addAssistantMessage(String sessionId, String content) {
        List<Message> history = getOrCreate(sessionId);
        history.add(new AssistantMessage(content));
        trimHistory(history);
    }

    /**
     * 获取会话的对话历史（不含 system）
     */
    public List<Message> getHistory(String sessionId) {
        return getOrCreate(sessionId);
    }

    /**
     * 获取所有会话概要
     */
    public Map<String, Object> listSessions() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String sid : sessions.keySet()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("messageCount", sessions.get(sid).size());
            info.put("createdAt", creationTimes.getOrDefault(sid, LocalDateTime.now()).toString());
            List<Message> history = sessions.get(sid);
            if (!history.isEmpty()) {
                String lastMsg = history.get(history.size() - 1).getText();
                info.put("lastMessage", lastMsg.length() > 60 ? lastMsg.substring(0, 60) + "..." : lastMsg);
            }
            result.put(sid, info);
        }
        return result;
    }

    private List<Message> getOrCreate(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> {
            creationTimes.put(k, LocalDateTime.now());
            return new ArrayList<>();
        });
    }

    /**
     * 裁剪过长的历史（保留最新 MAX_HISTORY 条）
     */
    private void trimHistory(List<Message> history) {
        if (history.size() > MAX_HISTORY) {
            int remove = history.size() - MAX_HISTORY;
            history.subList(0, remove).clear();
        }
    }
}
