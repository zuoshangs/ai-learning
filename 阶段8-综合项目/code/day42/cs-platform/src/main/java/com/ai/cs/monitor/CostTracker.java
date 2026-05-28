package com.ai.cs.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LLM cost tracking and estimation.
 * Tracks token usage and estimates cost based on model pricing.
 *
 * DeepSeek pricing (per 1M tokens, as of 2026):
 * - deepseek-chat: $0.27 input, $1.10 output
 * - deepseek-reasoner: $0.55 input, $2.19 output
 */
@Component
public class CostTracker {

    private static final Logger log = LoggerFactory.getLogger(CostTracker.class);

    private final MetricsCollector metricsCollector;
    private final String defaultModel;

    // Pricing per 1M tokens (in micro-cents for precision)
    private static final long INPUT_COST_CHAT = 27_000;       // $0.27
    private static final long OUTPUT_COST_CHAT = 110_000;     // $1.10
    private static final long INPUT_COST_REASONER = 55_000;   // $0.55
    private static final long OUTPUT_COST_REASONER = 219_000; // $2.19

    public CostTracker(MetricsCollector metricsCollector,
                       @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model) {
        this.metricsCollector = metricsCollector;
        this.defaultModel = model;
    }

    /**
     * Estimate tokens from text length.
     * Rough estimate: 1 token ≈ 4 chars for English, ≈ 2 chars for Chinese.
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) (chineseChars / 1.5 + otherChars / 4.0);
    }

    /**
     * Track cost of an LLM call and report to MetricsCollector.
     * @param prompt the input prompt text
     * @param response the output response text
     * @param model the model used
     */
    public void trackCall(String prompt, String response, String model) {
        int inputTokens = estimateTokens(prompt);
        int outputTokens = estimateTokens(response);
        // Note: recordLlmCall with timing is done by the caller (ConversationService)
        metricsCollector.recordCost(inputTokens, outputTokens, model != null ? model : defaultModel);
    }

    /**
     * Track cost with known token counts (more accurate).
     */
    public void trackCallWithTokens(int inputTokens, int outputTokens, String model) {
        metricsCollector.recordCost(inputTokens, outputTokens, model != null ? model : defaultModel);
    }

    /**
     * Get cost estimate for a given token count.
     */
    public CostEstimate estimateCost(int inputTokens, int outputTokens, String model) {
        String m = model != null ? model : defaultModel;
        long inputCost, outputCost;

        if (m.contains("reasoner")) {
            inputCost = (long) inputTokens * INPUT_COST_REASONER / 1_000_000;
            outputCost = (long) outputTokens * OUTPUT_COST_REASONER / 1_000_000;
        } else {
            inputCost = (long) inputTokens * INPUT_COST_CHAT / 1_000_000;
            outputCost = (long) outputTokens * OUTPUT_COST_CHAT / 1_000_000;
        }

        long totalMicroCents = inputCost + outputCost;
        double totalUsd = totalMicroCents / 100_000_000.0;

        return new CostEstimate(inputTokens, outputTokens, totalUsd, m);
    }

    public record CostEstimate(int inputTokens, int outputTokens, double costUsd, String model) {}
}
