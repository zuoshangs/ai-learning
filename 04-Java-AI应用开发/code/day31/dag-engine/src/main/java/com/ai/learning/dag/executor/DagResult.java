package com.ai.learning.dag.executor;

import java.util.*;

/**
 * DAG 执行结果
 */
public class DagResult {
    private final boolean success;
    private final String finalOutput;
    private final Map<String, Object> nodeOutputs;
    private final Map<String, Object> context;
    private final long elapsedMs;
    private final List<String> failedNodes;

    public DagResult(boolean success, String finalOutput,
                     Map<String, Object> nodeOutputs,
                     Map<String, Object> context,
                     long elapsedMs, List<String> failedNodes) {
        this.success = success;
        this.finalOutput = finalOutput;
        this.nodeOutputs = nodeOutputs;
        this.context = context;
        this.elapsedMs = elapsedMs;
        this.failedNodes = failedNodes;
    }

    public static DagResult failure(String error) {
        return new DagResult(false, error, Map.of(), Map.of(), 0, List.of());
    }

    // ---- Getters ----

    public boolean isSuccess() { return success; }
    public String getFinalOutput() { return finalOutput; }
    public Map<String, Object> getNodeOutputs() { return nodeOutputs; }
    public Map<String, Object> getContext() { return context; }
    public long getElapsedMs() { return elapsedMs; }
    public List<String> getFailedNodes() { return failedNodes; }
}
