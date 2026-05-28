package com.ai.learning.cost.service;

import com.ai.learning.cost.model.ModelCost;
import com.ai.learning.cost.model.UsageRecord;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Tracks LLM usage and computes cost analysis.
 */
@Service
public class CostAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CostAnalyzer.class);
    private final List<UsageRecord> records = new CopyOnWriteArrayList<>();
    private final AtomicInteger idSeq = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        // Seed with sample data for demo
        seedSampleData();
        log.info("CostAnalyzer initialized with {} sample records", records.size());
    }

    /** Record a usage. */
    public UsageRecord recordUsage(String userId, String model, String sessionId,
                                    int promptTokens, int completionTokens,
                                    long latencyMs, boolean cached) {
        var cost = ModelCost.forModel(model).calculate(promptTokens, completionTokens);
        var record = new UsageRecord(
            "req-" + idSeq.incrementAndGet(), userId, model, sessionId,
            promptTokens, completionTokens, cost.getCostUsd(), cost.getCostCny(),
            latencyMs, cached
        );
        records.add(record);
        return record;
    }

    /** Simulate a usage record with random data. */
    public UsageRecord simulateUsage(String userId) {
        String[] models = {"deepseek-v4-flash", "deepseek-reasoner", "gpt-4o-mini", "qwen-turbo"};
        String model = models[new Random().nextInt(models.length)];
        int pt = 100 + new Random().nextInt(2000);
        int ct = 50 + new Random().nextInt(800);
        long lat = 200 + (long)(new Random().nextDouble() * 3000);
        boolean cached = new Random().nextDouble() < 0.25;
        return recordUsage(userId, model, "session-" + (int)(Math.random() * 20),
                          pt, ct, lat, cached);
    }

    /** Cost report: aggregated costs. */
    public Map<String, Object> costReport(String userId, Duration period) {
        Instant since = Instant.now().minus(period);
        var filtered = records.stream()
                .filter(r -> r.getTimestamp().isAfter(since))
                .filter(r -> userId == null || userId.equals(r.getUserId()))
                .collect(Collectors.toList());

        // By model
        Map<String, List<UsageRecord>> byModel = filtered.stream()
                .collect(Collectors.groupingBy(UsageRecord::getModel));

        Map<String, Object> modelBreakdown = new LinkedHashMap<>();
        double totalUsd = 0, totalCny = 0;
        long totalTokens = 0;

        for (var entry : byModel.entrySet()) {
            double mUsd = entry.getValue().stream().mapToDouble(UsageRecord::getCostUsd).sum();
            double mCny = entry.getValue().stream().mapToDouble(UsageRecord::getCostCny).sum();
            long mTokens = entry.getValue().stream().mapToLong(UsageRecord::getTotalTokens).sum();
            modelBreakdown.put(entry.getKey(), Map.of(
                "requests", entry.getValue().size(),
                "totalTokens", mTokens,
                "costUsd", Math.round(mUsd * 10000.0) / 10000.0,
                "costCny", Math.round(mCny * 100.0) / 100.0
            ));
            totalUsd += mUsd; totalCny += mCny; totalTokens += mTokens;
        }

        return Map.of(
            "period", period.toHours() + "h",
            "userId", userId != null ? userId : "all",
            "totalRequests", filtered.size(),
            "totalTokens", totalTokens,
            "avgTokensPerRequest", filtered.size() > 0 ? totalTokens / filtered.size() : 0,
            "totalCostUsd", Math.round(totalUsd * 10000.0) / 10000.0,
            "totalCostCny", Math.round(totalCny * 100.0) / 100.0,
            "avgCostPerRequestUsd", filtered.size() > 0
                    ? Math.round(totalUsd / filtered.size() * 100000.0) / 100000.0 : 0,
            "costByModel", modelBreakdown
        );
    }

    /** Hourly cost trend for the last N hours. */
    public List<Map<String, Object>> hourlyTrend(int hours) {
        Map<String, Map<String, Object>> hourly = new LinkedHashMap<>();
        Instant now = Instant.now();

        for (int h = hours - 1; h >= 0; h--) {
            Instant start = now.minus(h + 1, java.time.temporal.ChronoUnit.HOURS);
            Instant end = now.minus(h, java.time.temporal.ChronoUnit.HOURS);
            String label = h == 0 ? "now" : "-" + h + "h";
            int finalH = h;
            var inWindow = records.stream()
                    .filter(r -> r.getTimestamp().isAfter(start) && !r.getTimestamp().isAfter(end))
                    .collect(Collectors.toList());
            double usd = inWindow.stream().mapToDouble(UsageRecord::getCostUsd).sum();
            double cny = inWindow.stream().mapToDouble(UsageRecord::getCostCny).sum();
            hourly.put(label, Map.of(
                "label", label, "requests", inWindow.size(),
                "costUsd", Math.round(usd * 10000.0) / 10000.0,
                "costCny", Math.round(cny * 100.0) / 100.0
            ));
        }
        return new ArrayList<>(hourly.values());
    }

    /** Optimization suggestions based on usage patterns. */
    public List<Map<String, Object>> optimizationSuggestions() {
        List<Map<String, Object>> suggestions = new ArrayList<>();

        var byModel = records.stream().collect(Collectors.groupingBy(UsageRecord::getModel));
        for (var entry : byModel.entrySet()) {
            String model = entry.getKey();
            long cached = entry.getValue().stream().filter(UsageRecord::isCached).count();
            long total = entry.getValue().size();

            // 1. Cache usage
            if (total > 10) {
                double cacheRate = (double) cached / total;
                if (cacheRate < 0.2) {
                    suggestions.add(Map.of(
                        "type", "cache", "severity", "high",
                        "model", model, "currentCacheRate",
                        String.format("%.0f%%", cacheRate * 100),
                        "suggestion", "启用语义缓存，类似查询可节省 " +
                            String.format("%.0f%%", (1 - cacheRate) * 100) + " 的重复调用费用"
                    ));
                }
            }

            // 2. Model downgrade
            double avgCost = entry.getValue().stream()
                    .mapToDouble(UsageRecord::getCostUsd).average().orElse(0);
            if (avgCost > 0.003 && ("deepseek-reasoner".equals(model) || "gpt-4o".equals(model))) {
                String alt = model.contains("reasoner") ? "deepseek-v4-flash" : "gpt-4o-mini";
                var altCost = ModelCost.forModel(alt);
                var saving = avgCost - altCost.estimate(500, 200).getCostUsd();
                suggestions.add(Map.of(
                    "type", "model_switch", "severity", "medium",
                    "model", model, "alternative", alt,
                    "avgCostPerRequest", Math.round(avgCost * 100000.0) / 100000.0,
                    "estimatedSavingPerRequest", Math.round(saving * 100000.0) / 100000.0,
                    "suggestion", "考虑切换到 " + alt + "，每请求可节省约 $" + String.format("%.4f", saving)
                ));
            }
        }

        // 3. Batch processing
        long totalRequests = records.size();
        double avgTokens = records.stream().mapToLong(UsageRecord::getTotalTokens).average().orElse(0);
        if (totalRequests > 100 && avgTokens < 300) {
            suggestions.add(Map.of(
                "type", "batching", "severity", "low",
                "avgTokensPerRequest", Math.round(avgTokens),
                "suggestion", "请求 Token 量较小（平均" + Math.round(avgTokens) + "），可合并多个问题为一次请求以降低调用次数"
            ));
        }

        // 4. Cost spikes
        Map<String, Long> modelErrors = records.stream()
                .collect(Collectors.groupingBy(UsageRecord::getModel, Collectors.counting()));
        for (var entry : modelErrors.entrySet()) {
            if (entry.getValue() > 100) {
                suggestions.add(Map.of(
                    "type", "high_usage", "severity", "medium",
                    "model", entry.getKey(),
                    "requestCount", entry.getValue(),
                    "suggestion", "模型 " + entry.getKey() + " 使用量超过 100 次，建议使用语义缓存减少重复调用"
                ));
            }
        }

        return suggestions;
    }

    private void seedSampleData() {
        String[] users = {"user-alice", "user-bob", "user-charlie"};
        String[] models = {"deepseek-v4-flash", "deepseek-reasoner", "gpt-4o-mini"};
        Random r = new Random(42);

        for (int i = 0; i < 200; i++) {
            String user = users[r.nextInt(users.length)];
            String model = models[r.nextInt(models.length)];
            int pt = 100 + r.nextInt(2000);
            int ct = 50 + r.nextInt(500);
            long lat = 200 + (long)(r.nextDouble() * 2000);
            boolean cached = r.nextDouble() < 0.20;
            var cost = ModelCost.forModel(model).calculate(pt, ct);
            records.add(new UsageRecord(
                "seed-" + i, user, model, "seed-session",
                pt, ct, cost.getCostUsd(), cost.getCostCny(), lat, cached
            ));
        }
    }
}
