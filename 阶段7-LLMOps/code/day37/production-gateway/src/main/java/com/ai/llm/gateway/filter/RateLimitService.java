package com.ai.llm.gateway.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token Bucket rate limiter per API key.
 * Each key gets a bucket with configurable max requests per minute.
 */
public class RateLimitService {

    private final int defaultRpm;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitService(int defaultRpm) {
        this.defaultRpm = defaultRpm;
    }

    public boolean tryAcquire(String apiKey) {
        TokenBucket bucket = buckets.computeIfAbsent(apiKey, k -> new TokenBucket(defaultRpm));
        return bucket.tryConsume();
    }

    public int getRemainingTokens(String apiKey) {
        TokenBucket bucket = buckets.get(apiKey);
        return bucket == null ? defaultRpm : bucket.availableTokens();
    }

    public int getDefaultRpm() {
        return defaultRpm;
    }

    public long totalRequests() {
        return buckets.values().stream().mapToLong(TokenBucket::totalConsumed).sum();
    }

    public Map<String, Integer> getAllKeyUsage() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        buckets.forEach((key, bucket) -> result.put(key, bucket.availableTokens()));
        return result;
    }

    static class TokenBucket {
        private final int capacity;
        private final AtomicInteger tokens;
        private final AtomicInteger totalConsumed = new AtomicInteger(0);
        private volatile long lastRefillTime = System.currentTimeMillis();

        TokenBucket(int capacity) {
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);
        }

        boolean tryConsume() {
            refill();
            while (true) {
                int current = tokens.get();
                if (current <= 0) return false;
                if (tokens.compareAndSet(current, current - 1)) {
                    totalConsumed.incrementAndGet();
                    return true;
                }
            }
        }

        int availableTokens() {
            refill();
            return tokens.get();
        }

        long totalConsumed() {
            return totalConsumed.get();
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed < 1000) return; // refill once per second at most
            int toAdd = (int) (elapsed / 1000); // replenish 1 token per second
            if (toAdd > 0) {
                lastRefillTime = now;
                // Don't overflow
                tokens.updateAndGet(t -> Math.min(capacity, t + toAdd));
            }
        }
    }
}
