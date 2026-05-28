package com.ai.learning.cost.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A single usage record for cost tracking.
 */
public class UsageRecord {
    private final String id;
    private final String userId;
    private final String model;
    private final String sessionId;
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final double costUsd;
    private final double costCny;
    private final long latencyMs;
    private final boolean cached;
    private final Instant timestamp;

    public UsageRecord(String id, String userId, String model, String sessionId,
                       int promptTokens, int completionTokens,
                       double costUsd, double costCny, long latencyMs, boolean cached) {
        this.id = id; this.userId = userId; this.model = model; this.sessionId = sessionId;
        this.promptTokens = promptTokens; this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.costUsd = costUsd; this.costCny = costCny;
        this.latencyMs = latencyMs; this.cached = cached;
        this.timestamp = Instant.now();
    }

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getModel() { return model; }
    public String getSessionId() { return sessionId; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public double getCostUsd() { return costUsd; }
    public double getCostCny() { return costCny; }
    public long getLatencyMs() { return latencyMs; }
    public boolean isCached() { return cached; }
    public Instant getTimestamp() { return timestamp; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id); map.put("userId", userId); map.put("model", model);
        map.put("sessionId", sessionId); map.put("promptTokens", promptTokens);
        map.put("completionTokens", completionTokens); map.put("totalTokens", totalTokens);
        map.put("costUsd", costUsd); map.put("costCny", costCny);
        map.put("latencyMs", latencyMs); map.put("cached", cached);
        map.put("timestamp", timestamp.toString());
        return map;
    }
}
