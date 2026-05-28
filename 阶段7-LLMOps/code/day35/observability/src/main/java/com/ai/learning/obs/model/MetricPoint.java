package com.ai.learning.obs.model;

import java.time.Instant;
import java.util.Map;

/**
 * A single metric data point for time-series tracking.
 */
public class MetricPoint {
    private final String name;
    private final double value;
    private final Map<String, String> tags;
    private final Instant timestamp;

    public MetricPoint(String name, double value, Map<String, String> tags) {
        this.name = name;
        this.value = value;
        this.tags = tags;
        this.timestamp = Instant.now();
    }

    public String getName() { return name; }
    public double getValue() { return value; }
    public Map<String, String> getTags() { return tags; }
    public Instant getTimestamp() { return timestamp; }
}
