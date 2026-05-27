package com.ai.learning.cs.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服响应
 */
public class ChatResponse {
    private String sessionId;
    private String reply;
    private IntentType detectedIntent;
    private LocalDateTime timestamp;

    public ChatResponse() {}

    public ChatResponse(String sessionId, String reply, IntentType intent) {
        this.sessionId = sessionId;
        this.reply = reply;
        this.detectedIntent = intent;
        this.timestamp = LocalDateTime.now();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String s) { this.sessionId = s; }
    public String getReply() { return reply; }
    public void setReply(String r) { this.reply = r; }
    public IntentType getDetectedIntent() { return detectedIntent; }
    public void setDetectedIntent(IntentType i) { this.detectedIntent = i; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
