package com.ai.learning.cost.controller;

import com.ai.learning.cost.service.CostAnalyzer;
import com.ai.learning.cost.service.PerformanceBenchmark;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Controller for cost analysis and optimization.
 */
@RestController
@RequestMapping("/api")
public class CostController {

    private final CostAnalyzer costAnalyzer;
    private final PerformanceBenchmark benchmark;

    public CostController(CostAnalyzer costAnalyzer, PerformanceBenchmark benchmark) {
        this.costAnalyzer = costAnalyzer;
        this.benchmark = benchmark;
    }

    /** Record a simulated usage. */
    @PostMapping("/usage/simulate")
    public Map<String, Object> simulate(@RequestParam(defaultValue = "user-test") String userId) {
        var record = costAnalyzer.simulateUsage(userId);
        return record.toMap();
    }

    /** Batched simulation. */
    @PostMapping("/usage/simulate-batch")
    public Map<String, Object> simulateBatch(
            @RequestParam(defaultValue = "user-test") String userId,
            @RequestParam(defaultValue = "10") int count) {
        for (int i = 0; i < count; i++) {
            costAnalyzer.simulateUsage(userId);
        }
        return Map.of("status", "ok", "simulated", count, "userId", userId);
    }

    /** Cost report. */
    @GetMapping("/cost/report")
    public Map<String, Object> costReport(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "24") int hours) {
        return costAnalyzer.costReport(userId, Duration.ofHours(hours));
    }

    /** Hourly cost trend. */
    @GetMapping("/cost/trend")
    public List<Map<String, Object>> costTrend(@RequestParam(defaultValue = "24") int hours) {
        return costAnalyzer.hourlyTrend(hours);
    }

    /** Optimization suggestions. */
    @GetMapping("/optimize/suggestions")
    public List<Map<String, Object>> suggestions() {
        return costAnalyzer.optimizationSuggestions();
    }

    /** Run benchmark. */
    @PostMapping("/benchmark/run")
    public Map<String, Object> runBenchmark(
            @RequestParam(defaultValue = "10") int concurrency,
            @RequestParam(defaultValue = "50") int totalRequests,
            @RequestParam(defaultValue = "200") int simulatedLatencyMs) {
        var result = benchmark.runBenchmark(concurrency, totalRequests, simulatedLatencyMs);
        return result.toMap();
    }

    /** Run all benchmark scenarios. */
    @PostMapping("/benchmark/all")
    public List<Map<String, Object>> benchmarkAll() {
        return benchmark.runAllScenarios().stream()
                .map(PerformanceBenchmark.BenchmarkResult::toMap)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Model pricing table. */
    @GetMapping("/models/pricing")
    public List<Map<String, Object>> modelPricing() {
        String[] models = {"deepseek-v4-flash","deepseek-reasoner","gpt-4o","gpt-4o-mini","claude-3-haiku","claude-3-sonnet","qwen-turbo","qwen-plus"};
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (String m : models) {
            var mc = com.ai.learning.cost.model.ModelCost.forModel(m);
            result.add(Map.of(
                "model", m, "provider", mc.getProvider(),
                "inputCostPer1KUsd", mc.getInputCostPer1K(),
                "outputCostPer1KUsd", mc.getOutputCostPer1K(),
                "inputCostPer1KCny", mc.getInputCostPer1KCny(),
                "outputCostPer1KCny", mc.getOutputCostPer1KCny()
            ));
        }
        return result;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "cost-optimizer");
    }
}
