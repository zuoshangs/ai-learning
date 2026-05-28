package com.ai.learning.obs.health;

import com.ai.learning.obs.metrics.LlmMetrics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the metrics & observability system itself.
 */
@Component
public class MetricsHealthIndicator implements HealthIndicator {

    private final LlmMetrics metrics;

    public MetricsHealthIndicator(LlmMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Health health() {
        var snap = metrics.getSnapshot();

        long errors = (long) snap.get("totalErrors");
        long requests = (long) snap.get("totalRequests");

        // If error rate exceeds 50%, mark as degraded
        if (requests > 10) {
            double errorRate = (double) errors / requests;
            if (errorRate > 0.5) {
                return Health.down()
                        .withDetail("errorRate", String.format("%.1f%%", errorRate * 100))
                        .withDetail("totalRequests", requests)
                        .withDetail("reason", "Error rate exceeds 50%")
                        .build();
            }
            if (errorRate > 0.2) {
                return Health.status("DEGRADED")
                        .withDetail("errorRate", String.format("%.1f%%", errorRate * 100))
                        .withDetail("totalRequests", requests)
                        .withDetail("reason", "Error rate exceeds 20%")
                        .build();
            }
        }

        return Health.up()
                .withDetail("totalRequests", requests)
                .withDetail("totalErrors", errors)
                .withDetail("hitRate", snap.get("hitRate"))
                .withDetail("avgLatencyMs", snap.get("p50LatencyMs"))
                .build();
    }
}
