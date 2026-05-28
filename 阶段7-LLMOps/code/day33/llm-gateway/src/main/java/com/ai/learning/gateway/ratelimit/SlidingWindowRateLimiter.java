package com.ai.learning.gateway.ratelimit;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Sliding Window Log rate limiter.
 * Maintains a queue of timestamps for each key, sliding window per limit.
 * More memory-heavy than token bucket, but gives exact window semantics.
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private static final long WINDOW_MS = 60_000; // 1 minute

    private final Map<String, Queue<Long>> windows = new ConcurrentHashMap<>();
    private final Function<String, Integer> limitProvider;

    public SlidingWindowRateLimiter(Function<String, Integer> limitProvider) {
        this.limitProvider = limitProvider;
    }

    @Override
    public boolean tryAcquire(String key) {
        int limit = limitProvider.apply(key);
        Queue<Long> timestamps = windows.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        // Prune old entries (best-effort, not synchronized)
        while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
            timestamps.poll();
        }

        synchronized (timestamps) {
            // Re-check after lock (might have been pruned by another thread)
            while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
                timestamps.poll();
            }
            if (timestamps.size() < limit) {
                timestamps.offer(now);
                return true;
            }
            return false;
        }
    }

    @Override
    public int getCurrentCount(String key) {
        Queue<Long> timestamps = windows.get(key);
        if (timestamps == null) return 0;
        long windowStart = System.currentTimeMillis() - WINDOW_MS;
        // Don't prune here — just count
        int count = 0;
        for (Long ts : timestamps) {
            if (ts >= windowStart) count++;
        }
        return count;
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }

    @Override
    public int getLimit(String key) {
        return limitProvider.apply(key);
    }
}
