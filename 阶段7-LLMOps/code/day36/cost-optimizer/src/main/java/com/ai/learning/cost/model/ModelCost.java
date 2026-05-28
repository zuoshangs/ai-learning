package com.ai.learning.cost.model;

import java.time.Instant;
import java.util.Map;

/**
 * Cost model for different LLM providers/models.
 * Pricing: per 1K input tokens and per 1K output tokens.
 */
public class ModelCost {

    private final String provider;
    private final String model;
    private final double inputCostPer1K;    // USD per 1K input tokens
    private final double outputCostPer1K;   // USD per 1K output tokens
    private final double inputCostPer1KCny; // CNY per 1K input tokens
    private final double outputCostPer1KCny;

    // Known model pricing (as of 2025)
    private static final Map<String, ModelCost> KNOWN_MODELS = Map.of(
        "deepseek-chat", new ModelCost("DeepSeek", "deepseek-chat", 0.00027, 0.00110, 0.0019, 0.0079),
        "deepseek-v4-flash", new ModelCost("DeepSeek", "deepseek-v4-flash", 0.00027, 0.00110, 0.0019, 0.0079),
        "deepseek-reasoner", new ModelCost("DeepSeek", "deepseek-reasoner", 0.00055, 0.00219, 0.0039, 0.0157),
        "gpt-4o", new ModelCost("OpenAI", "gpt-4o", 0.00500, 0.01500, 0.0360, 0.1080),
        "gpt-4o-mini", new ModelCost("OpenAI", "gpt-4o-mini", 0.00015, 0.00060, 0.0011, 0.0043),
        "claude-3-haiku", new ModelCost("Anthropic", "claude-3-haiku", 0.00025, 0.00125, 0.0018, 0.0090),
        "claude-3-sonnet", new ModelCost("Anthropic", "claude-3-sonnet", 0.00300, 0.01500, 0.0216, 0.1080),
        "qwen-turbo", new ModelCost("阿里云", "qwen-turbo", 0.0008, 0.0020, 0.0058, 0.0144),
        "qwen-plus", new ModelCost("阿里云", "qwen-plus", 0.0008 * 0.7, 0.0020 * 0.7, 0.0058 * 0.7, 0.0144 * 0.7)
    );

    public ModelCost(String provider, String model, double usdIn, double usdOut, double cnyIn, double cnyOut) {
        this.provider = provider; this.model = model;
        this.inputCostPer1K = usdIn; this.outputCostPer1K = usdOut;
        this.inputCostPer1KCny = cnyIn; this.outputCostPer1KCny = cnyOut;
    }

    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public double getInputCostPer1K() { return inputCostPer1K; }
    public double getOutputCostPer1K() { return outputCostPer1K; }
    public double getInputCostPer1KCny() { return inputCostPer1KCny; }
    public double getOutputCostPer1KCny() { return outputCostPer1KCny; }

    public static ModelCost forModel(String modelName) {
        return KNOWN_MODELS.getOrDefault(modelName, new ModelCost(
            "Unknown", modelName, 0.001, 0.003, 0.007, 0.022
        ));
    }

    /** Calculate cost for a call. */
    public CostResult calculate(int promptTokens, int completionTokens) {
        double usd = (promptTokens / 1000.0) * inputCostPer1K
                   + (completionTokens / 1000.0) * outputCostPer1K;
        double cny = (promptTokens / 1000.0) * inputCostPer1KCny
                   + (completionTokens / 1000.0) * outputCostPer1KCny;
        return new CostResult(provider, model, promptTokens, completionTokens, usd, cny);
    }

    /** Estimate cost before making the call. */
    public CostResult estimate(int estimatedInputTokens, int estimatedOutputTokens) {
        return calculate(estimatedInputTokens, estimatedOutputTokens);
    }

    public static class CostResult {
        private final String provider;
        private final String model;
        private final int promptTokens;
        private final int completionTokens;
        private final int totalTokens;
        private final double costUsd;
        private final double costCny;
        private final Instant timestamp;

        public CostResult(String provider, String model, int pt, int ct, double usd, double cny) {
            this.provider = provider; this.model = model;
            this.promptTokens = pt; this.completionTokens = ct;
            this.totalTokens = pt + ct;
            this.costUsd = usd; this.costCny = cny;
            this.timestamp = Instant.now();
        }

        // getters
        public String getProvider() { return provider; }
        public String getModel() { return model; }
        public int getPromptTokens() { return promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
        public int getTotalTokens() { return totalTokens; }
        public double getCostUsd() { return Math.round(costUsd * 100000.0) / 100000.0; }
        public double getCostCny() { return Math.round(costCny * 100000.0) / 100000.0; }
        public Instant getTimestamp() { return timestamp; }
    }
}
