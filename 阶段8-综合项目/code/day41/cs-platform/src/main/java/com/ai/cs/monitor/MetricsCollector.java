package com.ai.cs.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central metrics collector for the CS platform.
 * All subsystems report their metrics here.
 * Dashboard reads aggregated data from here.
 */
@Component
public class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);
    private final Instant startTime = Instant.now();

    // === Chat metrics ===
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong totalSessions = new AtomicLong(0);
    private final AtomicLong totalLlmCalls = new AtomicLong(0);
    private final AtomicLong totalLllmTokens = new AtomicLong(0);
    private final AtomicLong totalLlmTimeMs = new AtomicLong(0);
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final List<Long> recentResponseTimes = Collections.synchronizedList(new ArrayList<>());

    // === Cache metrics ===
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheSize = new AtomicLong(0);

    // === Rate limit metrics ===
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong rateLimitedRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);

    // === Cost metrics ===
    private final AtomicLong totalCostMicros = new AtomicLong(0); // in micro-cents
    private final Map<String, AtomicLong> costByModel = new ConcurrentHashMap<>();
    private final List<CostRecord> costHistory = Collections.synchronizedList(new ArrayList<>());

    // === Ticket metrics (aggregated periodically from TicketService) ===
    private volatile int ticketTotal = 0;
    private volatile int ticketPending = 0;
    private volatile int ticketInProgress = 0;
    private volatile int ticketResolved = 0;
    private volatile int ticketClosed = 0;

    // === Knowledge metrics (aggregated) ===
    private volatile int knowledgeTotalDocs = 0;
    private volatile int knowledgeCategories = 0;

    // ================================================================
    // Chat Metrics
    // ================================================================

    public void recordMessage() {
        totalMessages.incrementAndGet();
    }

    public void recordSessionCreated() {
        totalSessions.incrementAndGet();
        activeSessions.incrementAndGet();
    }

    public void recordSessionClosed() {
        activeSessions.decrementAndGet();
    }

    public void recordLlmCall(long durationMs, int tokens, String model) {
        totalLlmCalls.incrementAndGet();
        totalLllmTokens.addAndGet(tokens);
        totalLlmTimeMs.addAndGet(durationMs);
        recentResponseTimes.add(durationMs);
        // Keep only last 100 response times for avg calculation
        if (recentResponseTimes.size() > 100) {
            recentResponseTimes.remove(0);
        }
    }

    // ================================================================
    // Cache Metrics
    // ================================================================

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public void recordCacheSize(int size) {
        cacheSize.set(size);
    }

    // ================================================================
    // Rate Limit Metrics
    // ================================================================

    public void recordRequest() {
        totalRequests.incrementAndGet();
    }

    public void recordAllowed() {
        allowedRequests.incrementAndGet();
    }

    public void recordRateLimited() {
        rateLimitedRequests.incrementAndGet();
    }

    // ================================================================
    // Cost Metrics
    // ================================================================

    /**
     * Record LLM cost. Pricing per 1M tokens (DeepSeek):
     * - Input: $0.27 / 1M tokens
     * - Output: $1.10 / 1M tokens
     */
    public void recordCost(int inputTokens, int outputTokens, String model) {
        double inputCost = (inputTokens / 1_000_000.0) * 270_000; // micro-cents
        double outputCost = (outputTokens / 1_000_000.0) * 1_100_000; // micro-cents
        long microCents = (long) (inputCost + outputCost);
        totalCostMicros.addAndGet(microCents);
        costByModel.computeIfAbsent(model, k -> new AtomicLong()).addAndGet(microCents);
        costHistory.add(new CostRecord(System.currentTimeMillis(), microCents, model));
        // Keep last 1000 records
        if (costHistory.size() > 1000) {
            costHistory.remove(0);
        }
    }

    // ================================================================
    // Ticket Metrics (set externally)
    // ================================================================

    public void updateTicketMetrics(int total, int pending, int inProgress,
                                     int resolved, int closed) {
        this.ticketTotal = total;
        this.ticketPending = pending;
        this.ticketInProgress = inProgress;
        this.ticketResolved = resolved;
        this.ticketClosed = closed;
    }

    // ================================================================
    // Knowledge Metrics
    // ================================================================

    public void updateKnowledgeMetrics(int totalDocs, int categories) {
        this.knowledgeTotalDocs = totalDocs;
        this.knowledgeCategories = categories;
    }

    // ================================================================
    // Dashboard Report
    // ================================================================

    public Map<String, Object> getDashboardReport() {
        Map<String, Object> report = new LinkedHashMap<>();

        // System
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        Runtime rt = Runtime.getRuntime();
        long uptime = Duration.between(startTime, Instant.now()).getSeconds();

        report.put("system", Map.of(
                "service", "CS Platform",
                "status", "running",
                "uptime", uptime,
                "uptimeDisplay", formatDuration(uptime),
                "memory", Map.of(
                        "used", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024 + "MB",
                        "total", rt.totalMemory() / 1024 / 1024 + "MB",
                        "max", rt.maxMemory() / 1024 / 1024 + "MB",
                        "usedPercent", String.format("%.1f%%",
                                (double)(rt.totalMemory() - rt.freeMemory()) / rt.maxMemory() * 100)
                ),
                "threads", ManagementFactory.getThreadMXBean().getThreadCount(),
                "startedAt", startTime.toEpochMilli()
        ));

        // Chat
        double avgResponseTime = recentResponseTimes.isEmpty() ? 0 :
                recentResponseTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        report.put("chat", Map.of(
                "totalMessages", totalMessages.get(),
                "totalSessions", totalSessions.get(),
                "activeSessions", activeSessions.get(),
                "totalLlmCalls", totalLlmCalls.get(),
                "totalTokens", totalLllmTokens.get(),
                "totalLlmTimeMs", totalLlmTimeMs.get(),
                "avgResponseTimeMs", String.format("%.0f", avgResponseTime),
                "p50ResponseTimeMs", percentile(recentResponseTimes, 50),
                "p95ResponseTimeMs", percentile(recentResponseTimes, 95)
        ));

        // Cache
        long totalCacheChecks = cacheHits.get() + cacheMisses.get();
        double hitRate = totalCacheChecks > 0 ? (double) cacheHits.get() / totalCacheChecks * 100 : 0;

        report.put("cache", Map.of(
                "hits", cacheHits.get(),
                "misses", cacheMisses.get(),
                "size", cacheSize.get(),
                "hitRate", String.format("%.1f%%", hitRate)
        ));

        // Rate limiting
        report.put("rateLimit", Map.of(
                "totalRequests", totalRequests.get(),
                "allowed", allowedRequests.get(),
                "rateLimited", rateLimitedRequests.get()
        ));

        // Cost
        double totalCostUsd = totalCostMicros.get() / 100_000_000.0; // micro-cents to dollars
        report.put("cost", Map.of(
                "totalCostUsd", String.format("%.4f", totalCostUsd),
                "totalCostCents", String.format("%.2f¢", totalCostUsd * 100),
                "byModel", getCostByModel(),
                "hourlyTrend", getHourlyCostHistory()
        ));

        // Tickets
        report.put("tickets", Map.of(
                "total", ticketTotal,
                "pending", ticketPending,
                "inProgress", ticketInProgress,
                "resolved", ticketResolved,
                "closed", ticketClosed
        ));

        // Knowledge
        report.put("knowledge", Map.of(
                "totalDocs", knowledgeTotalDocs,
                "categories", knowledgeCategories
        ));

        return report;
    }

    /** Get simple summary for nav bar */
    public Map<String, Object> getQuickStatus() {
        return Map.of(
                "activeSessions", activeSessions.get(),
                "activeTickets", ticketPending + ticketInProgress,
                "cacheHitRate", cacheHits.get() + cacheMisses.get() > 0
                        ? String.format("%.0f%%", (double) cacheHits.get() /
                        (cacheHits.get() + cacheMisses.get()) * 100)
                        : "0%",
                "uptime", formatDuration(Duration.between(startTime, Instant.now()).getSeconds())
        );
    }

    // ================================================================
    // Internal
    // ================================================================

    private Map<String, String> getCostByModel() {
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : costByModel.entrySet()) {
            double usd = entry.getValue().get() / 100_000_000.0;
            result.put(entry.getKey(), String.format("$%.4f", usd));
        }
        return result;
    }

    private List<Map<String, Object>> getHourlyCostHistory() {
        List<Map<String, Object>> hourly = new ArrayList<>();
        synchronized (costHistory) {
            // Group by hour
            Map<Long, Double> byHour = new LinkedHashMap<>();
            for (CostRecord r : costHistory) {
                long hour = r.timestamp / 3600000 * 3600000;
                byHour.merge(hour, r.microCents / 100_000_000.0, Double::sum);
            }
            for (var entry : byHour.entrySet()) {
                hourly.add(Map.of(
                        "time", entry.getKey(),
                        "timeLabel", formatTimestamp(entry.getKey()),
                        "cost", String.format("%.4f", entry.getValue())
                ));
            }
        }
        return hourly;
    }

    private String percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return "0";
        List<Long> copy = new ArrayList<>(sorted);
        Collections.sort(copy);
        int idx = (int) Math.ceil(pct / 100.0 * copy.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= copy.size()) idx = copy.size() - 1;
        return String.valueOf(copy.get(idx));
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "秒";
        if (seconds < 3600) return (seconds / 60) + "分钟";
        if (seconds < 86400) return (seconds / 3600) + "小时" + ((seconds % 3600) / 60) + "分钟";
        return (seconds / 86400) + "天" + ((seconds % 86400) / 3600) + "小时";
    }

    private String formatTimestamp(long epochMs) {
        var d = new java.text.SimpleDateFormat("HH:mm");
        d.setTimeZone(java.util.TimeZone.getDefault());
        return d.format(new Date(epochMs));
    }

    // Internal cost record
    record CostRecord(long timestamp, long microCents, String model) {}
}
