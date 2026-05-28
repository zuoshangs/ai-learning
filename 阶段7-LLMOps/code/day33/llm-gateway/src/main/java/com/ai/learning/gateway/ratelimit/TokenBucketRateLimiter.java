package com.ai.learning.gateway.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Token Bucket rate limiter.
 * Each key gets a bucket with capacity (max burst) tokens, refilled at a constant rate.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Function<String, Integer> limitProvider;

    public TokenBucketRateLimiter(Function<String, Integer> limitProvider) {
        this.limitProvider = limitProvider;
    }

    private static class Bucket {
        final int capacity;       // max burst (e.g., 10)
        final double refillRate;  // tokens per second (e.g., limitPerMinute / 60.0)
        double tokens;
        long lastRefillNanos;

        Bucket(int limitPerMinute) {
            this.capacity = Math.max(limitPerMinute / 6, 1); // burst ~10s worth
            this.refillRate = limitPerMinute / 60.0;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized int getCurrentTokens() {
            refill();
            return (int) Math.floor(tokens);
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSec * refillRate);
            lastRefillNanos = now;
        }
    }

    @Override
    public boolean tryAcquire(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(limitProvider.apply(k)));
        return bucket.tryAcquire();
    }

    @Override
    public int getCurrentCount(String key) {
        Bucket bucket = buckets.get(key);
        return bucket != null ? bucket.getCurrentTokens() : 0;
    }

    @Override
    public void reset(String key) {
        buckets.remove(key);
    }

    @Override
    public int getLimit(String key) {
        return limitProvider.apply(key);
    }
}
