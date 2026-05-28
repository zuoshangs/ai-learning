package com.ai.cs.chat;

public record ChatResponse(String sessionId, String reply, int historySize, long timestamp) {
}
