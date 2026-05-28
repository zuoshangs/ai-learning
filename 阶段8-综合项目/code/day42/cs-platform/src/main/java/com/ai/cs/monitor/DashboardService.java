package com.ai.cs.monitor;

import com.ai.cs.knowledge.KnowledgeBaseService;
import com.ai.cs.ticket.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dashboard service: aggregates data from all subsystems
 * into a unified dashboard report.
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final MetricsCollector metricsCollector;
    private final TicketService ticketService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RateLimiter rateLimiter;
    private final ResponseCache responseCache;

    public DashboardService(MetricsCollector metricsCollector,
                            TicketService ticketService,
                            KnowledgeBaseService knowledgeBaseService,
                            RateLimiter rateLimiter,
                            ResponseCache responseCache) {
        this.metricsCollector = metricsCollector;
        this.ticketService = ticketService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.rateLimiter = rateLimiter;
        this.responseCache = responseCache;
    }

    /**
     * Collect fresh metrics from all subsystems and return a complete dashboard report.
     */
    public Map<String, Object> getFullReport() {
        // Update ticket metrics
        var ticketStats = ticketService.getStats();
        metricsCollector.updateTicketMetrics(
                ticketStats.total, ticketStats.pending,
                ticketStats.inProgress, ticketStats.resolved,
                ticketStats.closed
        );

        // Update knowledge metrics
        metricsCollector.updateKnowledgeMetrics(
                knowledgeBaseService.documentCount(),
                knowledgeBaseService.getCategories().size()
        );

        var report = metricsCollector.getDashboardReport();

        // Add additional system info
        report.put("rateLimiter", Map.of(
                "capacity", rateLimiter.getTotalBuckets() > 0 ? 20 : "N/A",
                "activeBuckets", rateLimiter.getTotalBuckets()
        ));

        // Merge with existing cache metrics from MetricsCollector
        Map<String, Object> cacheReport = (Map<String, Object>) report.getOrDefault("cache", Map.of());
        Map<String, Object> mergedCache = new java.util.LinkedHashMap<>(cacheReport);
        mergedCache.put("size", responseCache.size());
        mergedCache.put("maxSize", 200);
        mergedCache.put("ttlSeconds", 300);
        report.put("cache", mergedCache);

        return report;
    }

    /**
     * Get quick status summary (lightweight, called frequently).
     */
    public Map<String, Object> getQuickStatus() {
        return metricsCollector.getQuickStatus();
    }
}
