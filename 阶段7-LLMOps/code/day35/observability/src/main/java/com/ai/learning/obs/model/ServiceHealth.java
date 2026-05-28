package com.ai.learning.obs.model;

import java.util.Map;

/**
 * Snapshot of LLM service health and metrics for the dashboard.
 */
public class ServiceHealth {
    private String status;
    private long uptimeSeconds;
    private Map<String, Object> metrics;
    private Map<String, Object> components;

    public ServiceHealth() {}

    public ServiceHealth(String status, long uptimeSeconds,
                         Map<String, Object> metrics, Map<String, Object> components) {
        this.status = status;
        this.uptimeSeconds = uptimeSeconds;
        this.metrics = metrics;
        this.components = components;
    }

    public String getStatus() { return status; }
    public void setStatus(String s) { status = s; }
    public long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(long v) { uptimeSeconds = v; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> m) { metrics = m; }
    public Map<String, Object> getComponents() { return components; }
    public void setComponents(Map<String, Object> c) { components = c; }
}
