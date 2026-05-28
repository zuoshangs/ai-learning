package com.ai.learning.obs.health;

import com.ai.learning.obs.metrics.LlmMetrics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Health indicator for the LLM backend.
 * Checks if the DeepSeek API is reachable (lightweight ping).
 */
@Component
public class LlmHealthIndicator implements HealthIndicator {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final LlmMetrics metrics;

    public LlmHealthIndicator(LlmMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Health health() {
        try {
            // Lightweight check — just DNS + TCP connect to api.deepseek.com
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/ping"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
            int code = res.statusCode();

            if (code == 200 || code == 404 || code == 400) {
                // 200 = ping endpoint exists, 404/400 = ping not found but server is up
                return Health.up()
                        .withDetail("endpoint", "api.deepseek.com")
                        .withDetail("statusCode", code)
                        .withDetail("latencyMs", "ok")
                        .build();
            }

            return Health.down()
                    .withDetail("endpoint", "api.deepseek.com")
                    .withDetail("statusCode", code)
                    .withDetail("reason", "Unexpected status code")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("endpoint", "api.deepseek.com")
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
