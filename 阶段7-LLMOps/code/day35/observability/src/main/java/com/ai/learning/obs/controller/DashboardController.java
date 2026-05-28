package com.ai.learning.obs.controller;

import com.ai.learning.obs.metrics.LlmMetrics;
import com.ai.learning.obs.model.ServiceHealth;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Map;

/**
 * Dashboard controller — aggregated view of system health and metrics.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final LlmMetrics metrics;

    public DashboardController(LlmMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/health")
    public ServiceHealth health() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long uptime = runtime.getUptime() / 1000;

        var snap = metrics.getSnapshot();
        long errors = (long) snap.get("totalErrors");
        long requests = (long) snap.get("totalRequests");

        String status;
        if (requests == 0) {
            status = "UP";
        } else if ((double) errors / Math.max(1, requests) > 0.5) {
            status = "DOWN";
        } else if ((double) errors / Math.max(1, requests) > 0.2) {
            status = "DEGRADED";
        } else {
            status = "UP";
        }

        return new ServiceHealth(status, uptime, snap, getComponentStatus());
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metrics.getSnapshot();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        var snap = metrics.getSnapshot();
        return Map.of(
                "summary", Map.of(
                        "totalRequests", snap.get("totalRequests"),
                        "totalErrors", snap.get("totalErrors"),
                        "cacheHitRate", snap.get("hitRate"),
                        "avgLatencyMs", snap.get("p50LatencyMs"),
                        "p95LatencyMs", snap.get("p95LatencyMs")
                ),
                "latency", Map.of(
                        "last", snap.get("lastLatencyMs"),
                        "peak", snap.get("peakLatencyMs"),
                        "p50", snap.get("p50LatencyMs"),
                        "p95", snap.get("p95LatencyMs"),
                        "p99", snap.get("p99LatencyMs")
                ),
                "counters", Map.of(
                        "requests", snap.get("totalRequests"),
                        "tokens", snap.get("totalTokens"),
                        "errors", snap.get("totalErrors"),
                        "cacheHits", snap.get("cacheHits"),
                        "cacheMisses", snap.get("cacheMisses"),
                        "rateLimitBlocks", snap.get("rateLimitBlocks")
                ),
                "errorBreakdown", snap.get("errorBreakdown")
        );
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        metrics.reset();
        return Map.of("status", "reset");
    }

    private Map<String, Object> getComponentStatus() {
        var snap = metrics.getSnapshot();
        return Map.of(
                "llm", Map.of("status", "UP"),
                "cache", Map.of(
                        "status", "UP",
                        "hitRate", snap.get("hitRate")
                ),
                "rateLimit", Map.of("status", "UP"),
                "metricsSystem", Map.of(
                        "status", "UP",
                        "type", "Micrometer + Prometheus"
                )
        );
    }
}
