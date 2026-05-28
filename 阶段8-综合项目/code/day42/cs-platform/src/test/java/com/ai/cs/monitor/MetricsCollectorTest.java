package com.ai.cs.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsCollector — the central metrics accumulator.
 * Note: Map values may be Integer or Long; always use ((Number) val).intValue().
 */
class MetricsCollectorTest {

    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
    }

    @Test
    void initialCountsAreZero() {
        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> chat = (Map<String, Object>) report.get("chat");
        assertEquals(0, ((Number) chat.get("totalMessages")).intValue());
        assertEquals(0, ((Number) chat.get("totalLlmCalls")).intValue());
        assertEquals(0, ((Number) chat.get("activeSessions")).intValue());

        Map<String, Object> cache = (Map<String, Object>) report.get("cache");
        assertEquals(0, ((Number) cache.get("hits")).intValue());
        assertEquals(0, ((Number) cache.get("misses")).intValue());
    }

    @Test
    void recordsChatMetrics() {
        metricsCollector.recordMessage();
        metricsCollector.recordMessage();
        metricsCollector.recordMessage();
        metricsCollector.recordSessionCreated();
        metricsCollector.recordLlmCall(150, 50, "deepseek-chat");

        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> chat = (Map<String, Object>) report.get("chat");

        assertEquals(3, ((Number) chat.get("totalMessages")).intValue());
        assertEquals(1, ((Number) chat.get("totalSessions")).intValue());
        assertEquals(1, ((Number) chat.get("activeSessions")).intValue());
        assertEquals(1, ((Number) chat.get("totalLlmCalls")).intValue());
        assertEquals(50, ((Number) chat.get("totalTokens")).intValue());
    }

    @Test
    void recordsSessionClose() {
        metricsCollector.recordSessionCreated();
        metricsCollector.recordSessionCreated();

        Map<String, Object> report1 = metricsCollector.getDashboardReport();
        Map<String, Object> chat1 = (Map<String, Object>) report1.get("chat");
        assertEquals(2, ((Number) chat1.get("activeSessions")).intValue());

        metricsCollector.recordSessionClosed();

        Map<String, Object> report2 = metricsCollector.getDashboardReport();
        Map<String, Object> chat2 = (Map<String, Object>) report2.get("chat");
        assertEquals(1, ((Number) chat2.get("activeSessions")).intValue());
    }

    @Test
    void recordsCacheMetrics() {
        metricsCollector.recordCacheHit();
        metricsCollector.recordCacheHit();
        metricsCollector.recordCacheHit();
        metricsCollector.recordCacheMiss();

        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> cache = (Map<String, Object>) report.get("cache");

        assertEquals(3, ((Number) cache.get("hits")).intValue());
        assertEquals(1, ((Number) cache.get("misses")).intValue());
        assertEquals("75.0%", cache.get("hitRate"));
    }

    @Test
    void recordsRateLimitMetrics() {
        for (int i = 0; i < 10; i++) {
            metricsCollector.recordRequest();
            metricsCollector.recordAllowed();
        }
        for (int i = 0; i < 3; i++) {
            metricsCollector.recordRequest();
            metricsCollector.recordRateLimited();
        }

        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> rateLimit = (Map<String, Object>) report.get("rateLimit");

        assertEquals(13, ((Number) rateLimit.get("totalRequests")).intValue());
        assertEquals(10, ((Number) rateLimit.get("allowed")).intValue());
        assertEquals(3, ((Number) rateLimit.get("rateLimited")).intValue());
    }

    @Test
    void recordsCostMetrics() {
        metricsCollector.recordCost(100000, 50000, "deepseek-chat");

        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> cost = (Map<String, Object>) report.get("cost");

        assertTrue(Double.parseDouble((String) cost.get("totalCostUsd")) > 0);
    }

    @Test
    void updatesTicketMetrics() {
        metricsCollector.updateTicketMetrics(10, 3, 2, 4, 1);

        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> tickets = (Map<String, Object>) report.get("tickets");

        assertEquals(10, ((Number) tickets.get("total")).intValue());
        assertEquals(3, ((Number) tickets.get("pending")).intValue());
        assertEquals(2, ((Number) tickets.get("inProgress")).intValue());
        assertEquals(4, ((Number) tickets.get("resolved")).intValue());
        assertEquals(1, ((Number) tickets.get("closed")).intValue());
    }

    @Test
    void updatesKnowledgeMetrics() {
        metricsCollector.updateKnowledgeMetrics(7, 6);

        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> knowledge = (Map<String, Object>) report.get("knowledge");

        assertEquals(7, ((Number) knowledge.get("totalDocs")).intValue());
        assertEquals(6, ((Number) knowledge.get("categories")).intValue());
    }

    @Test
    void responseTimePercentiles() {
        for (int i = 0; i < 10; i++) {
            metricsCollector.recordLlmCall(100 + i * 50, 100, "deepseek-chat");
        }

        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> chat = (Map<String, Object>) report.get("chat");

        assertNotNull(chat.get("p50ResponseTimeMs"));
        assertNotNull(chat.get("p95ResponseTimeMs"));
    }

    @Test
    void systemInfoIsPresent() {
        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> system = (Map<String, Object>) report.get("system");

        assertEquals("CS Platform", system.get("service"));
        assertEquals("running", system.get("status"));
        assertNotNull(system.get("uptimeDisplay"));
    }
}
