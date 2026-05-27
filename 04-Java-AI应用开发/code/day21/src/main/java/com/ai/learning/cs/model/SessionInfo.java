package com.ai.learning.cs.model;

public class SessionInfo {
    private String sessionId;
    private int messageCount;
    private long createdAt;

    public SessionInfo() {}

    public SessionInfo(String sessionId, int messageCount, long createdAt) {
        this.sessionId = sessionId;
        this.messageCount = messageCount;
        this.createdAt = createdAt;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String s) { this.sessionId = s; }
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int c) { this.messageCount = c; }
    public long getCreatedAt() { return createdAt; }
}
