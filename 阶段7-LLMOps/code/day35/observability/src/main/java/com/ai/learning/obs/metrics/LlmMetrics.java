package com.ai.learning.obs.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom LLM metrics collector.
 * Wraps Micrometer counters, timers, and gauges for LLM operations.
 */
@Component
public class LlmMetrics {

    private final MeterRegistry registry;

    // Counters
    private final Counter totalRequests;
    private final Counter totalTokens;
    private final Counter totalErrors;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter rateLimitBlocks;

    // Timers
    private final Timer latency;

    // Custom aggregators (for dashboard that needs real-time values)
    private final AtomicLong lastLatencyMs = new AtomicLong(0);
    private final AtomicLong peakLatencyMs = new AtomicLong(0);
    private final Map<String, AtomicLong> errorCounters = new ConcurrentHashMap<>();

    public LlmMetrics(MeterRegistry registry) {
        this.registry = registry;

        totalRequests = Counter.builder("llm.requests.total")
                .description("Total LLM requests")
                .register(registry);

        totalTokens = Counter.builder("llm.tokens.total")
                .description("Total tokens consumed")
                .register(registry);

        totalErrors = Counter.builder("llm.errors.total")
                .description("Total LLM errors")
                .register(registry);

        cacheHits = Counter.builder("llm.cache.hits")
                .description("Cache hit count")
                .register(registry);

        cacheMisses = Counter.builder("llm.cache.misses")
                .description("Cache miss count")
                .register(registry);

        rateLimitBlocks = Counter.builder("llm.ratelimit.blocks")
                .description("Rate limited request count")
                .register(registry);

        latency = Timer.builder("llm.latency")
                .description("LLM request latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Register gauges
        registry.gauge("llm.latency.last", lastLatencyMs);
        registry.gauge("llm.latency.peak", peakLatencyMs);
    }

    public void recordRequest() {
        totalRequests.increment();
    }

    public void recordLatency(long ms) {
        latency.record(ms, TimeUnit.MILLISECONDS);
        lastLatencyMs.set(ms);
        if (ms > peakLatencyMs.get()) {
            peakLatencyMs.set(ms);
        }
    }

    public void recordTokens(int tokens) {
        totalTokens.increment(tokens);
    }

    public void recordError(String errorType) {
        totalErrors.increment();
        errorCounters.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }

    public void recordRateLimitBlock() {
        rateLimitBlocks.increment();
    }

    /** Get a snapshot of all metrics for the dashboard. */
    public Map<String, Object> getSnapshot() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("totalRequests", (long) totalRequests.count());
        snap.put("totalTokens", (long) totalTokens.count());
        snap.put("totalErrors", (long) totalErrors.count());
        snap.put("cacheHits", (long) cacheHits.count());
        snap.put("cacheMisses", (long) cacheMisses.count());
        snap.put("rateLimitBlocks", (long) rateLimitBlocks.count());
        snap.put("hitRate", calculateHitRate());
        snap.put("lastLatencyMs", lastLatencyMs.get());
        snap.put("peakLatencyMs", peakLatencyMs.get());
        snap.put("p50LatencyMs", getPercentile(0.5));
        snap.put("p95LatencyMs", getPercentile(0.95));
        snap.put("p99LatencyMs", getPercentile(0.99));
        snap.put("errorBreakdown", Map.copyOf(errorCounters));
        return snap;
    }

    private String calculateHitRate() {
        long hits = (long) cacheHits.count();
        long misses = (long) cacheMisses.count();
        long total = hits + misses;
        return total > 0 ? String.format("%.1f%%", (double) hits / total * 100) : "0.0%";
    }

    private double getPercentile(double p) {
        Timer timer = registry.find("llm.latency").timer();
        if (timer == null) return 0;
        return timer.totalTime(TimeUnit.MILLISECONDS) / Math.max(1, timer.count());
    }

    public void reset() {
        // Note: Micrometer counters cannot be reset. For demo we record new values.
        lastLatencyMs.set(0);
        peakLatencyMs.set(0);
        errorCounters.clear();
    }
}
