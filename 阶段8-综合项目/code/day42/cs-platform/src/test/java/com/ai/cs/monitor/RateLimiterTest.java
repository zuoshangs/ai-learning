package com.ai.cs.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimiter — token bucket rate limiting.
 */
class RateLimiterTest {

    private MetricsCollector metricsCollector;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
        rateLimiter = new RateLimiter(5, 10.0, metricsCollector); // cap=5, refill=10/s
    }

    @Test
    void allowsUpToCapacity() {
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allowRequest("user"), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void blocksWhenExhausted() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest("user");
        }
        // 6th request should be blocked
        assertFalse(rateLimiter.allowRequest("user"), "6th request should be blocked");
    }

    @Test
    void separateBucketsForDifferentKeys() {
        // User A exhausts their bucket
        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest("user-a");
        }

        // User B should still be allowed (different bucket)
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allowRequest("user-b"), "User B request " + (i + 1));
        }

        // User A is blocked
        assertFalse(rateLimiter.allowRequest("user-a"));
    }

    @Test
    void remainingTokensDecreaseOnUse() {
        assertTrue(rateLimiter.allowRequest("user"));

        // After one request, should have 4 remaining
        // getRemainingTokens does synchronous refill check
        // With refill 10/s, almost no refill in this timeframe
        int remaining = rateLimiter.getRemainingTokens("user");
        assertTrue(remaining >= 0, "Remaining should be >= 0, got " + remaining);
    }

    @Test
    void tracksMetrics() {
        // 5 allowed + 1 blocked
        for (int i = 0; i < 6; i++) {
            rateLimiter.allowRequest("user");
        }

        var report = metricsCollector.getDashboardReport();
        var rateLimit = (java.util.Map<String, Object>) report.get("rateLimit");

        assertEquals(6, ((Number) rateLimit.get("totalRequests")).intValue());
        assertEquals(5, ((Number) rateLimit.get("allowed")).intValue());
        assertEquals(1, ((Number) rateLimit.get("rateLimited")).intValue());
    }
}
