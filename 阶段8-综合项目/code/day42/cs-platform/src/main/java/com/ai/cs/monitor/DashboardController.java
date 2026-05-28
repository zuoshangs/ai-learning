package com.ai.cs.monitor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dashboard REST controller.
 * Provides metrics and monitoring data for the admin dashboard UI.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final MetricsCollector metricsCollector;
    private final ResponseCache responseCache;

    public DashboardController(DashboardService dashboardService,
                                MetricsCollector metricsCollector,
                                ResponseCache responseCache) {
        this.dashboardService = dashboardService;
        this.metricsCollector = metricsCollector;
        this.responseCache = responseCache;
    }

    /**
     * Full dashboard report (aggregated from all subsystems).
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> report() {
        return ResponseEntity.ok(dashboardService.getFullReport());
    }

    /**
     * Quick status summary for nav bar / header.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(dashboardService.getQuickStatus());
    }

    /**
     * Get raw metrics (for detail views).
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        var report = metricsCollector.getDashboardReport();
        return ResponseEntity.ok(Map.of(
                "chat", report.get("chat"),
                "cache", report.get("cache"),
                "rateLimit", report.get("rateLimit"),
                "cost", report.get("cost")
        ));
    }

    /**
     * Admin action: clear response cache.
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, Object>> clearCache() {
        int before = responseCache.size();
        responseCache.clear();
        return ResponseEntity.ok(Map.of(
                "action", "clear_cache",
                "before", before,
                "after", 0
        ));
    }

    /**
     * Health check (same as existing /api/admin/health).
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "CS Platform Dashboard"
        ));
    }
}
