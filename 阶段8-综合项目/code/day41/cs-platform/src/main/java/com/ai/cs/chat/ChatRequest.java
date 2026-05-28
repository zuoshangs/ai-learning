package com.ai.cs.chat;

public record ChatRequest(String sessionId, String message) {
    public ChatRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
