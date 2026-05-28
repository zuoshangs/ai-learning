package com.ai.learning.dag.executor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAG 执行上下文 — 在节点间传递数据的键值存储
 *
 * - 线程安全（ConcurrentHashMap）
 * - 支持模板变量渲染（{varName} 引用方式）
 * - 自动记录节点输出到同名键
 */
public class DagContext {
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object v = data.get(key);
        return v != null ? (T) v : defaultValue;
    }

    public String getAsString(String key, String defaultValue) {
        Object v = data.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    @Override
    public String toString() {
        return "DagContext{" + data.size() + " entries}";
    }
}
