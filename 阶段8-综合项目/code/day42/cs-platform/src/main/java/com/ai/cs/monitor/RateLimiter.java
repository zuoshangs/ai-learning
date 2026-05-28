package com.ai.cs.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter.
 * Each session gets a bucket with configurable capacity and refill rate.
 * When tokens are exhausted, requests are rate-limited.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final int capacity;
    private final double refillRate; // tokens per second
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final MetricsCollector metricsCollector;

    public RateLimiter(@Value("${app.rate-limit.capacity:20}") int capacity,
                       @Value("${app.rate-limit.refill-rate:5}") double refillRate,
                       MetricsCollector metricsCollector) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.metricsCollector = metricsCollector;
        log.info("Rate limiter: capacity={}, refillRate={}/s", capacity, refillRate);
    }

    /**
     * Check if a request from this key is allowed.
     * @param key session ID or IP address
     * @return true if allowed, false if rate limited
     */
    public boolean allowRequest(String key) {
        metricsCollector.recordRequest();

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        boolean allowed = bucket.tryConsume();

        if (allowed) {
            metricsCollector.recordAllowed();
            return true;
        } else {
            metricsCollector.recordRateLimited();
            log.warn("Rate limited: key={}", key.substring(0, Math.min(8, key.length())));
            return false;
        }
    }

    public int getRemainingTokens(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) return capacity;
        bucket.refill();
        return (int) Math.max(0, bucket.tokens);
    }

    public long getTotalBuckets() {
        return buckets.size();
    }

    /**
     * Token bucket implementation.
     */
    private class Bucket {
        private final double maxTokens;
        private volatile double tokens;
        private volatile long lastRefillTime;

        Bucket(double maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = maxTokens;
            this.lastRefillTime = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        synchronized void refill() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefillTime) / 1_000_000_000.0;
            tokens = Math.min(maxTokens, tokens + elapsed * refillRate);
            lastRefillTime = now;
        }
    }
}
