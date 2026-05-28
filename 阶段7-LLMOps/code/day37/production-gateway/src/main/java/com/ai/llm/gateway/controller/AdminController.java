package com.ai.llm.gateway.controller;

import com.ai.llm.gateway.cost.CostAnalyzerService;
import com.ai.llm.gateway.monitor.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Admin controller: cost management, metrics, pricing.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final MetricsService metrics;
    private final CostAnalyzerService costAnalyzer;

    public AdminController(MetricsService metrics, CostAnalyzerService costAnalyzer) {
        this.metrics = metrics;
        this.costAnalyzer = costAnalyzer;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return Map.of(
                "requests", String.format("%.0f", metrics.getRequestCount()),
                "errors", String.format("%.0f", metrics.getErrorCount()),
                "totalTokens", String.format("%.0f", metrics.getTotalTokens()),
                "totalCost", String.format("$%.4f", metrics.getTotalCost()),
                "modelTokens", metrics.getModelTokens()
        );
    }

    @GetMapping("/pricing")
    public Set<String> pricing() {
        return costAnalyzer.getSupportedModels();
    }

    @GetMapping("/users")
    public Map<String, Long> users() {
        return costAnalyzer.getUserUsage();
    }

    @GetMapping("/cost")
    public CostAnalyzerService.CostReport cost() {
        return costAnalyzer.generateReport();
    }
}
