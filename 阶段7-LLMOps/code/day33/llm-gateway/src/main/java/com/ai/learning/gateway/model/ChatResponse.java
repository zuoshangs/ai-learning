package com.ai.learning.gateway.model;

import java.util.List;

/**
 * Standard response from the LLM gateway.
 */
public class ChatResponse {
    private boolean success;
    private String content;
    private String model;
    private int promptTokens;
    private int completionTokens;
    private long latencyMs;
    private String error;

    public static ChatResponse success(String content, String model, int promptTokens, int completionTokens, long latencyMs) {
        ChatResponse r = new ChatResponse();
        r.success = true;
        r.content = content;
        r.model = model;
        r.promptTokens = promptTokens;
        r.completionTokens = completionTokens;
        r.latencyMs = latencyMs;
        return r;
    }

    public static ChatResponse error(String error) {
        ChatResponse r = new ChatResponse();
        r.success = false;
        r.error = error;
        return r;
    }

    public static ChatResponse rateLimited() {
        ChatResponse r = new ChatResponse();
        r.success = false;
        r.error = "rate_limited";
        r.content = "请求过于频繁，请稍后重试。Rate limit exceeded.";
        return r;
    }

    public static ChatResponse circuitOpen() {
        ChatResponse r = new ChatResponse();
        r.success = false;
        r.error = "circuit_open";
        r.content = "LLM 服务暂时不可用，熔断器已打开。请稍后再试。";
        return r;
    }

    // getters
    public boolean isSuccess() { return success; }
    public String getContent() { return content; }
    public String getModel() { return model; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public long getLatencyMs() { return latencyMs; }
    public String getError() { return error; }
    public void setSuccess(boolean v) { success = v; }
    public void setContent(String v) { content = v; }
    public void setModel(String v) { model = v; }
    public void setPromptTokens(int v) { promptTokens = v; }
    public void setCompletionTokens(int v) { completionTokens = v; }
    public void setLatencyMs(long v) { latencyMs = v; }
    public void setError(String v) { error = v; }
}
