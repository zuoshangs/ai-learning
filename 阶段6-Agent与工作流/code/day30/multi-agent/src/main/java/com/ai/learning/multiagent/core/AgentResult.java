package com.ai.learning.multiagent.core;

/**
 * Agent 执行结果 —— 带状态标记，用于错误隔离。
 */
public class AgentResult {
    private final String agentName;
    private final boolean success;
    private final String data;
    private final String error;

    public AgentResult(String agentName, boolean success, String data, String error) {
        this.agentName = agentName;
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static AgentResult ok(String agentName, String data) {
        return new AgentResult(agentName, true, data, null);
    }

    public static AgentResult fail(String agentName, String error) {
        return new AgentResult(agentName, false, null, error);
    }

    public String getAgentName() { return agentName; }
    public boolean isSuccess() { return success; }
    public String getData() { return data; }
    public String getError() { return error; }
}