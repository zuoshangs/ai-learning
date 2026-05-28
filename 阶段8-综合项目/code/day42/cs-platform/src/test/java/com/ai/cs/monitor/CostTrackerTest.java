package com.ai.cs.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CostTracker — LLM cost estimation and tracking.
 */
class CostTrackerTest {

    private MetricsCollector metricsCollector;
    private CostTracker costTracker;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
        costTracker = new CostTracker(metricsCollector, "deepseek-chat");
    }

    @Test
    void estimatesEnglishTokens() {
        // Use costTracker instance to call estimateTokens
        int tokens = costTracker.estimateTokens("hello world this is a test");
        assertTrue(tokens > 0);
        assertEquals(6, tokens);
    }

    @Test
    void estimatesChineseTokens() {
        int tokens = costTracker.estimateTokens("你好世界，这是一个测试");
        assertTrue(tokens > 0);
    }

    @Test
    void estimatesCostForChatModel() {
        CostTracker.CostEstimate estimate = costTracker.estimateCost(1000, 500, "deepseek-chat");
        assertEquals(1000, estimate.inputTokens());
        assertEquals(500, estimate.outputTokens());
        assertTrue(estimate.costUsd() > 0);
        assertEquals("deepseek-chat", estimate.model());
    }

    @Test
    void estimatesCostForReasonerModel() {
        CostTracker.CostEstimate estimate = costTracker.estimateCost(1000, 500, "deepseek-reasoner");
        assertTrue(estimate.costUsd() > 0);
    }

    @Test
    void reasonerCostsMoreThanChat() {
        CostTracker.CostEstimate chat = costTracker.estimateCost(1000, 500, "deepseek-chat");
        CostTracker.CostEstimate reasoner = costTracker.estimateCost(1000, 500, "deepseek-reasoner");
        assertTrue(reasoner.costUsd() > chat.costUsd(),
                "Reasoner should cost more than chat");
    }

    @Test
    void trackCallRecordsCost() {
        costTracker.trackCall("Hello, I need help with my order",
                "Sure, I can help you with your order", "deepseek-chat");

        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> cost = (Map<String, Object>) report.get("cost");
        Map<String, Object> byModel = (Map<String, Object>) cost.get("byModel");
        assertTrue(byModel.containsKey("deepseek-chat"));
    }
}
