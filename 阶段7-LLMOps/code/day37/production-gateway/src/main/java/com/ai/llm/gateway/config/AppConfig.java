package com.ai.llm.gateway.config;

import com.ai.llm.gateway.cache.SemanticCacheService;
import com.ai.llm.gateway.circuit.CircuitBreakerService;
import com.ai.llm.gateway.cost.CostAnalyzerService;
import com.ai.llm.gateway.monitor.MetricsService;
import com.ai.llm.gateway.filter.RateLimitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AppConfig {

    @Value("${gateway.cache.max-size:1024}")
    private int cacheMaxSize;

    @Value("${gateway.cache.ttl-minutes:30}")
    private int cacheTtlMinutes;

    @Value("${gateway.cache.similarity-threshold:0.85}")
    private double similarityThreshold;

    @Value("${gateway.rate-limit.default-rpm:30}")
    private int defaultRpm;

    @Value("${gateway.circuit.failure-threshold:5}")
    private int circuitFailureThreshold;

    @Value("${gateway.circuit.reset-seconds:30}")
    private int circuitResetSeconds;

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    public SemanticCacheService semanticCacheService() {
        return new SemanticCacheService(cacheMaxSize, cacheTtlMinutes, similarityThreshold);
    }

    @Bean
    public RateLimitService rateLimitService() {
        return new RateLimitService(defaultRpm);
    }

    @Bean
    public CircuitBreakerService circuitBreakerService() {
        return new CircuitBreakerService(circuitFailureThreshold, circuitResetSeconds);
    }

    @Bean
    public CostAnalyzerService costAnalyzerService() {
        return new CostAnalyzerService();
    }

    @Bean
    public MetricsService metricsService() {
        return new MetricsService();
    }
}
