package com.ai.learning.multiagent.orchestrator;

import com.ai.learning.multiagent.core.AgentResult;
import java.util.List;

public class OrchestrationResult {
    private final boolean success;
    private final String summary;
    private final List<AgentResult> details;
    private final long elapsedMs;

    public OrchestrationResult(boolean success, String summary,
                               List<AgentResult> details, long elapsedMs) {
        this.success = success;
        this.summary = summary;
        this.details = details;
        this.elapsedMs = elapsedMs;
    }

    public boolean isSuccess() { return success; }
    public String getSummary() { return summary; }
    public List<AgentResult> getDetails() { return details; }
    public long getElapsedMs() { return elapsedMs; }
}