package com.ai.learning.gateway.ratelimit;

/**
 * Rate limiter interface.
 * Implementations: Token Bucket, Sliding Window.
 */
public interface RateLimiter {
    /**
     * Try to consume a token/request.
     * @param key identifier (e.g., API key, client IP)
     * @return true if allowed, false if rate limited
     */
    boolean tryAcquire(String key);

    /**
     * Get current count for a key (for monitoring).
     */
    int getCurrentCount(String key);

    /** Reset state for a key. */
    void reset(String key);

    /** Get the max allowed count per window. */
    int getLimit(String key);
}
