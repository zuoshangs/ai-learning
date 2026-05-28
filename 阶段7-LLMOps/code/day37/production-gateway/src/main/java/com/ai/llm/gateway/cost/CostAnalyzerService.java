package com.ai.llm.gateway.cost;

import com.ai.llm.gateway.monitor.MetricsService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cost analyzer: tracks token usage per model/user and calculates cost.
 * Pricing based on DeepSeek API rates (USD/1M tokens).
 */
public class CostAnalyzerService {

    // Model pricing: [USD/1M input tokens, USD/1M output tokens]
    static final Map<String, double[]> MODEL_PRICING = new LinkedHashMap<>();
    static {
        MODEL_PRICING.put("deepseek-chat",    new double[]{0.27, 1.10});
        MODEL_PRICING.put("deepseek-reasoner", new double[]{0.55, 2.19});
        MODEL_PRICING.put("gpt-4o",           new double[]{2.50, 10.00});
        MODEL_PRICING.put("gpt-4o-mini",      new double[]{0.15, 0.60});
        MODEL_PRICING.put("claude-sonnet-4",   new double[]{3.00, 15.00});
        MODEL_PRICING.put("claude-haiku-3.5",  new double[]{0.80, 4.00});
    }

    // Per-model usage: [inputTokens, outputTokens]
    private final Map<String, long[]> modelUsage = new ConcurrentHashMap<>();
    // Per-user usage
    private final Map<String, AtomicLong> userTokens = new ConcurrentHashMap<>();

    /** Calculate cost for a single request */
    public double calculateCost(String model, int inputTokens, int outputTokens) {
        double[] prices = MODEL_PRICING.getOrDefault(model, new double[]{0.27, 1.10});
        double inputCost = inputTokens / 1_000_000.0 * prices[0];
        double outputCost = outputTokens / 1_000_000.0 * prices[1];
        return inputCost + outputCost;
    }

    /** Record usage and return cost */
    public double recordUsage(String model, String apiKey, int inputTokens, int outputTokens) {
        modelUsage.computeIfAbsent(model, k -> new long[2]);
        long[] usage = modelUsage.get(model);
        usage[0] += inputTokens;
        usage[1] += outputTokens;

        userTokens.computeIfAbsent(apiKey, k -> new AtomicLong(0))
                .addAndGet((long) inputTokens + outputTokens);

        return calculateCost(model, inputTokens, outputTokens);
    }

    // ---- Report methods ----
    public CostReport generateReport() {
        CostReport report = new CostReport();
        modelUsage.forEach((model, usage) -> {
            long inTokens = usage[0];
            long outTokens = usage[1];
            double[] prices = MODEL_PRICING.getOrDefault(model, new double[]{0.27, 1.10});
            double cost = inTokens / 1_000_000.0 * prices[0] + outTokens / 1_000_000.0 * prices[1];
            report.modelCosts.add(new ModelCost(model, inTokens, outTokens, cost));
            report.totalTokens += inTokens + outTokens;
            report.totalCost += cost;
        });

        return report;
    }

    public Map<String, Long> getUserUsage() {
        Map<String, Long> result = new LinkedHashMap<>();
        userTokens.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public Set<String> getSupportedModels() {
        return MODEL_PRICING.keySet();
    }

    // ---- Report types ----
    public static class CostReport {
        public final List<ModelCost> modelCosts = new ArrayList<>();
        public long totalTokens = 0;
        public double totalCost = 0;

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"totalTokens\": ").append(totalTokens).append(",");
            sb.append("\n  \"totalCostUSD\": ").append(String.format("%.4f", totalCost)).append(",");
            sb.append("\n  \"models\": [\n");
            for (int i = 0; i < modelCosts.size(); i++) {
                if (i > 0) sb.append(",\n");
                sb.append("    ").append(modelCosts.get(i).toJson());
            }
            sb.append("\n  ]\n}");
            return sb.toString();
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("## 📊 成本报告\n\n");
            sb.append("| 模型 | 输入Token | 输出Token | 成本(USD) |\n");
            sb.append("|------|----------|----------|----------|\n");
            for (ModelCost mc : modelCosts) {
                sb.append(String.format("| %s | %,d | %,d | $%.4f |\n",
                        mc.model, mc.inputTokens, mc.outputTokens, mc.cost));
            }
            sb.append(String.format("\n**总计**: %,d tokens → **$%.4f**\n", totalTokens, totalCost));
            return sb.toString();
        }
    }

    public static class ModelCost {
        public final String model;
        public final long inputTokens;
        public final long outputTokens;
        public final double cost;

        ModelCost(String model, long inputTokens, long outputTokens, double cost) {
            this.model = model;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.cost = cost;
        }

        String toJson() {
            return String.format("{\"model\":\"%s\",\"inputTokens\":%d,\"outputTokens\":%d,\"costUSD\":%.4f}",
                    model, inputTokens, outputTokens, cost);
        }
    }

    public static class OptimizationSuggestion {
        public final String type;
        public final double estimatedSaving;
        public final String description;

        public OptimizationSuggestion(String type, double estimatedSaving, String description) {
            this.type = type;
            this.estimatedSaving = estimatedSaving;
            this.description = description;
        }
    }

    /** Generate optimization suggestions based on usage patterns */
    public List<OptimizationSuggestion> suggestOptimizations() {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        CostReport report = generateReport();

        for (ModelCost mc : report.modelCosts) {
            // Suggest caching for repeated queries
            if (mc.cost > 0.01) {
                suggestions.add(new OptimizationSuggestion("CACHE", mc.cost * 0.5,
                        "对 \"" + mc.model + "\" 开启语义缓存，预计节省 50% 成本 ($" + String.format("%.4f", mc.cost * 0.5) + ")"));
            }

            // Suggest cheaper model for simple tasks
            if (mc.model.contains("reasoner") || mc.model.contains("gpt-4o")) {
                suggestions.add(new OptimizationSuggestion("MODEL_DOWNGRADE", mc.cost * 0.7,
                        "简单任务可改用 deepseek-chat，预计节省 70% ($" + String.format("%.4f", mc.cost * 0.7) + ")"));
            }
        }

        // Suggest rate limiting
        suggestions.add(new OptimizationSuggestion("RATE_LIMIT", 0.0,
                "已启用 Token Bucket 限流 (30rpm/key)，防止突发调用"));

        // Circuit breaker
        suggestions.add(new OptimizationSuggestion("CIRCUIT_BREAKER", 0.0,
                "已启用熔断器 (5次失败 → 30秒OPEN → HALF_OPEN)，保护后端"));

        return suggestions;
    }
}
