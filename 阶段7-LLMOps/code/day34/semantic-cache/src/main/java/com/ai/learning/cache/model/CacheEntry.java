package com.ai.learning.cache.model;

import java.time.Instant;
import java.util.Map;

/**
 * A single entry in the semantic cache.
 * Stores the original query, its TF-IDF vector, the cached response, and metadata.
 */
public class CacheEntry {
    private final String query;
    private final Map<String, Double> vector;  // TF-IDF vector
    private final String response;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int hitCount = 0;

    public CacheEntry(String query, Map<String, Double> vector, String response, long ttlSeconds) {
        this.query = query;
        this.vector = vector;
        this.response = response;
        this.createdAt = Instant.now();
        this.expiresAt = ttlSeconds > 0 ? createdAt.plusSeconds(ttlSeconds) : null;
    }

    public String getQuery() { return query; }
    public Map<String, Double> getVector() { return vector; }
    public String getResponse() { return response; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getHitCount() { return hitCount; }

    public void incrementHit() { hitCount++; }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
