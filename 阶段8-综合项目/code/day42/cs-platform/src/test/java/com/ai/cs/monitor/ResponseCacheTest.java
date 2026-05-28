package com.ai.cs.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResponseCache — LLM response caching.
 */
class ResponseCacheTest {

    private MetricsCollector metricsCollector;
    private ResponseCache cache;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
        cache = new ResponseCache(300, 100, metricsCollector);
    }

    @Test
    void missOnAbsentKey() {
        assertNull(cache.get("你好"));
    }

    @Test
    void hitAfterPut() {
        cache.put("你好", "您好！有什么可以帮助您的？");
        assertEquals("您好！有什么可以帮助您的？", cache.get("你好"));
    }

    @Test
    void queryNormalization() {
        cache.put("退货政策是什么?", "30天无理由退货");
        assertEquals("30天无理由退货", cache.get("退货政策是什么！"));
    }

    @Test
    void tracksMetrics() {
        cache.get("test");
        cache.put("test", "response");
        cache.get("test");

        Map<String, Object> report = metricsCollector.getDashboardReport();
        Map<String, Object> cacheSection = (Map<String, Object>) report.get("cache");
        assertEquals(1, ((Number) cacheSection.get("hits")).intValue());
        assertEquals(1, ((Number) cacheSection.get("misses")).intValue());
    }

    @Test
    void clearResetsCache() {
        cache.put("a", "1");
        cache.put("b", "2");
        assertEquals(2, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void evictsOldestWhenFull() {
        var smallCache = new ResponseCache(300, 2, new MetricsCollector());
        smallCache.put("a", "1");
        smallCache.put("b", "2");
        smallCache.put("c", "3"); // Should evict oldest

        // After eviction only 2 entries
        assertEquals(2, smallCache.size());
    }

    @Test
    void ttlExpiration() throws Exception {
        var ttlCache = new ResponseCache(0, 100, new MetricsCollector());
        ttlCache.put("test", "value");
        Thread.sleep(10); // Ensure at least 1ms has passed (TTL 0 = immediate)
        assertNull(ttlCache.get("test"));
    }
}
