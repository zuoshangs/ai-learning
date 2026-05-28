package com.ai.learning.dag.model;

import java.util.*;

/**
 * DAG 节点 — 工作流中的最小执行单元
 *
 * 每个节点包含：
 * - id: 全局唯一标识
 * - type: 节点类型 (START/LLM/TOOL/CONDITION/END)
 * - dependencies: 前置依赖节点 ID 列表
 * - config: 节点专用配置（prompt、toolName、condition 等）
 */
public class DagNode {
    private String id;
    private NodeType type;
    private List<String> dependencies = new ArrayList<>();
    private Map<String, Object> config = new HashMap<>();

    // 运行时状态（执行后填充）
    private transient Object output;
    private transient boolean executed = false;
    private transient String error;

    public DagNode() {}

    public DagNode(String id, NodeType type) {
        this.id = id;
        this.type = type;
    }

    public DagNode(String id, NodeType type, List<String> dependencies) {
        this.id = id;
        this.type = type;
        this.dependencies = dependencies;
    }

    // ---- Getters / Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public NodeType getType() { return type; }
    public void setType(NodeType type) { this.type = type; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }

    public Object getOutput() { return output; }
    public void setOutput(Object output) { this.output = output; }

    public boolean isExecuted() { return executed; }
    public void setExecuted(boolean executed) { this.executed = executed; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    /** 便捷方法：获取字符串类型的配置值 */
    public String getConfigString(String key, String defaultValue) {
        Object v = config.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    /** 便捷方法：获取 boolean 类型的配置值 */
    public boolean getConfigBoolean(String key, boolean defaultValue) {
        Object v = config.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        return defaultValue;
    }

    @Override
    public String toString() {
        return "DagNode{" + "id='" + id + '\'' + ", type=" + type + '}';
    }
}
