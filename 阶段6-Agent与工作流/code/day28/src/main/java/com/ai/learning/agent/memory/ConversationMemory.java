package com.ai.learning.agent.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话记忆实体
 * 表示一次会话中的全部消息记录
 */
public class ConversationMemory {

    private String sessionId;
    private List<Message> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ConversationMemory() {
        this.messages = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public ConversationMemory(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void addMessage(String role, String content) {
        this.messages.add(new Message(role, content));
        this.updatedAt = LocalDateTime.now();
    }

    public void addMessage(Message msg) {
        this.messages.add(msg);
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "ConversationMemory{" +
                "sessionId='" + sessionId + '\'' +
                ", messageCount=" + messages.size() +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    /**
     * 单条消息
     */
    public static class Message {
        private String role;    // "user" | "assistant" | "system" | "tool"
        private String content;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        @Override
        public String toString() {
            return role + ": " + (content.length() > 80 ? content.substring(0, 80) + "..." : content);
        }
    }
}
