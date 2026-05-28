package com.ai.cs.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory LLM response cache.
 * Caches responses for identical or similar questions.
 * Uses SHA-256 of normalized input as the cache key.
 */
@Component
public class ResponseCache {

    private static final Logger log = LoggerFactory.getLogger(ResponseCache.class);

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;
    private final int maxSize;

    private final MetricsCollector metricsCollector;

    public ResponseCache(@Value("${app.cache.ttl-seconds:300}") int ttlSeconds,
                         @Value("${app.cache.max-size:200}") int maxSize,
                         MetricsCollector metricsCollector) {
        this.ttlMs = ttlSeconds * 1000L;
        this.maxSize = maxSize;
        this.metricsCollector = metricsCollector;
        log.info("Response cache: ttl={}s, maxSize={}", ttlSeconds, maxSize);
    }

    /**
     * Get cached response for a query.
     * @param query the user's message
     * @return cached response or null if not found
     */
    public String get(String query) {
        String key = normalizeKey(query);
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            metricsCollector.recordCacheMiss();
            return null;
        }

        // Check TTL
        if (System.currentTimeMillis() - entry.timestamp > ttlMs) {
            cache.remove(key);
            metricsCollector.recordCacheMiss();
            return null;
        }

        metricsCollector.recordCacheHit();
        return entry.response;
    }

    /**
     * Store a response in the cache.
     */
    public void put(String query, String response) {
        // Evict oldest if at capacity
        if (cache.size() >= maxSize) {
            evictOldest();
        }

        String key = normalizeKey(query);
        cache.put(key, new CacheEntry(response, System.currentTimeMillis()));
        metricsCollector.recordCacheSize(cache.size());
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
        metricsCollector.recordCacheSize(0);
    }

    // ---- Internal ----

    private String normalizeKey(String query) {
        // Normalize: lowercase, trim, collapse whitespace
        String normalized = query.toLowerCase().trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\p{P}\\p{S}]", ""); // Remove punctuation

        // Truncate very long queries
        if (normalized.length() > 100) {
            normalized = normalized.substring(0, 100);
        }

        // Hash
        return sha256(normalized);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Fallback: use input itself (unlikely)
            return input;
        }
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (var entry : cache.entrySet()) {
            if (entry.getValue().timestamp < oldestTime) {
                oldestTime = entry.getValue().timestamp;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }

    private record CacheEntry(String response, long timestamp) {}
}
