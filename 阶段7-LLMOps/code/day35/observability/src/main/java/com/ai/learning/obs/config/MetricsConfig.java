package com.ai.learning.obs.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics configuration.
 * Sets up Micrometer registries for collecting application metrics.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        // SimpleMeterRegistry is always available; production would use
        // PrometheusMeterRegistry, DatadogMeterRegistry, etc.
        return new CompositeMeterRegistry();
    }
}
