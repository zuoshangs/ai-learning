package com.ai.llm.gateway.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized metrics collection using Micrometer.
 * Records: request count, latency, token usage, errors, cache hit rate.
 */
public class MetricsService {

    private final MeterRegistry registry;
    private final Counter requestCounter;
    private final Counter errorCounter;
    private final Counter tokenCounter;
    private final Counter costCounter;
    private final io.micrometer.core.instrument.Timer requestTimer;
    private final Gauge cacheHitGauge;

    // Per-model token tracking
    private final Map<String, AtomicLong> modelTokens = new ConcurrentHashMap<>();

    public MetricsService() {
        this.registry = new SimpleMeterRegistry();

        this.requestCounter = Counter.builder("gateway.requests.total")
                .description("Total requests")
                .register(registry);

        this.errorCounter = Counter.builder("gateway.errors.total")
                .description("Total errors")
                .register(registry);

        this.tokenCounter = Counter.builder("gateway.tokens.total")
                .description("Total tokens consumed")
                .register(registry);

        this.costCounter = Counter.builder("gateway.cost.usd")
                .description("Total cost in USD")
                .register(registry);

        this.requestTimer = io.micrometer.core.instrument.Timer.builder("gateway.request.duration")
                .description("Request duration in seconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.cacheHitGauge = Gauge.builder("gateway.cache.hit_rate", () -> 0.0)
                .description("Cache hit rate")
                .register(registry);
    }

    public void recordRequest() { requestCounter.increment(); }

    public void recordError() { errorCounter.increment(); }

    public void recordTokens(String model, int tokens) {
        tokenCounter.increment(tokens);
        modelTokens.computeIfAbsent(model, k -> new AtomicLong(0))
                .addAndGet(tokens);
    }

    public void recordCost(double usd) { costCounter.increment(usd); }

    public io.micrometer.core.instrument.Timer.Sample startTimer() {
        return io.micrometer.core.instrument.Timer.start(registry);
    }

    public void stopTimer(io.micrometer.core.instrument.Timer.Sample sample) {
        sample.stop(requestTimer);
    }

    public void updateCacheHitRate(double rate) {
        registry.gauge("gateway.cache.hit_rate", rate);
    }

    // ---- Query methods ----
    public double getRequestCount() { return requestCounter.count(); }
    public double getErrorCount() { return errorCounter.count(); }
    public double getTotalTokens() { return tokenCounter.count(); }
    public double getTotalCost() { return costCounter.count(); }
    public Map<String, Long> getModelTokens() {
        Map<String, Long> result = new HashMap<>();
        modelTokens.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public String getPrometheusMetrics() {
        StringBuilder sb = new StringBuilder();
        // Requests
        sb.append("# HELP gateway_requests_total Total requests\n");
        sb.append("# TYPE gateway_requests_total counter\n");
        sb.append("gateway_requests_total ").append((long) requestCounter.count()).append("\n");

        // Errors
        sb.append("# HELP gateway_errors_total Total errors\n");
        sb.append("# TYPE gateway_errors_total counter\n");
        sb.append("gateway_errors_total ").append((long) errorCounter.count()).append("\n");

        // Tokens
        sb.append("# HELP gateway_tokens_total Total tokens consumed\n");
        sb.append("# TYPE gateway_tokens_total counter\n");
        sb.append("gateway_tokens_total ").append((long) tokenCounter.count()).append("\n");

        // Model tokens
        sb.append("# HELP gateway_model_tokens Tokens per model\n");
        sb.append("# TYPE gateway_model_tokens gauge\n");
        modelTokens.forEach((model, count) ->
                sb.append("gateway_model_tokens{model=\"").append(model)
                        .append("\"} ").append(count.get()).append("\n"));

        // Cost
        sb.append("# HELP gateway_cost_usd Total cost in USD\n");
        sb.append("# TYPE gateway_cost_usd gauge\n");
        sb.append("gateway_cost_usd ").append(String.format("%.4f", costCounter.count())).append("\n");

        return sb.toString();
    }

    public MeterRegistry getRegistry() { return registry; }
}
