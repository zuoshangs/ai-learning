package com.ai.learning.multiagent.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent 间统一消息模型
 */
public class AgentMessage {
    public enum MessageType { REQUEST, RESPONSE, ERROR }

    private final String id;
    private final String source;
    private final String target;
    private final MessageType type;
    private final String payload;
    private final Instant timestamp;

    public AgentMessage(String source, String target, MessageType type, String payload) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.source = source;
        this.target = target;
        this.type = type;
        this.payload = payload;
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public String getSource() { return source; }
    public String getTarget() { return target; }
    public MessageType getType() { return type; }
    public String getPayload() { return payload; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "[" + type + "] " + source + " → " + target + ": " + payload;
    }
}
