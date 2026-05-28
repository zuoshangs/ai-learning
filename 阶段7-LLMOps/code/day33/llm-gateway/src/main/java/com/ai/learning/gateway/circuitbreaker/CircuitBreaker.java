package com.ai.learning.gateway.circuitbreaker;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit Breaker for LLM API calls.
 * Protects upstream LLM provider from overload and fails fast when it's unhealthy.
 */
public class CircuitBreaker {

    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final int halfOpenMaxRequests;

    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger rejectedRequests = new AtomicInteger(0);
    private volatile Instant lastFailureTime = Instant.MIN;
    private volatile Instant lastStateChange = Instant.now();

    public CircuitBreaker(int failureThreshold, long resetTimeoutMs, int halfOpenMaxRequests) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.halfOpenMaxRequests = halfOpenMaxRequests;
    }

    /**
     * Check if the request is allowed through.
     * @return true if request can proceed
     */
    public boolean allowRequest() {
        CircuitState current = state.get();
        switch (current) {
            case CLOSED:
                totalRequests.incrementAndGet();
                return true;
            case OPEN:
                // Check if reset timeout has elapsed
                if (Instant.now().isAfter(lastStateChange.plusMillis(resetTimeoutMs))) {
                    if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                        lastStateChange = Instant.now();
                        successCount.set(0);
                        totalRequests.incrementAndGet();
                        return true;
                    }
                }
                rejectedRequests.incrementAndGet();
                return false;
            case HALF_OPEN:
                // Allow limited requests to test the waters
                if (successCount.get() < halfOpenMaxRequests) {
                    totalRequests.incrementAndGet();
                    return true;
                }
                rejectedRequests.incrementAndGet();
                return false;
            default:
                return true;
        }
    }

    /** Record a successful call. */
    public void onSuccess() {
        CircuitState current = state.get();
        if (current == CircuitState.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= halfOpenMaxRequests) {
                // Enough successful trials — close the circuit
                if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
                    lastStateChange = Instant.now();
                    failureCount.set(0);
                    successCount.set(0);
                }
            }
        } else if (current == CircuitState.CLOSED) {
            // Reset failure count on success
            failureCount.set(0);
        }
    }

    /** Record a failed call. */
    public void onFailure() {
        lastFailureTime = Instant.now();
        CircuitState current = state.get();
        if (current == CircuitState.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= failureThreshold) {
                if (state.compareAndSet(CircuitState.CLOSED, CircuitState.OPEN)) {
                    lastStateChange = Instant.now();
                }
            }
        } else if (current == CircuitState.HALF_OPEN) {
            // Failed in half-open state — back to open
            if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.OPEN)) {
                lastStateChange = Instant.now();
            }
        }
    }

    public CircuitState getState() { return state.get(); }
    public int getFailureCount() { return failureCount.get(); }
    public int getTotalRequests() { return totalRequests.get(); }
    public int getRejectedRequests() { return rejectedRequests.get(); }
    public int getSuccessCount() { return successCount.get(); }
    public Instant getLastFailureTime() { return lastFailureTime; }
    public Instant getLastStateChange() { return lastStateChange; }

    /** Reset circuit breaker to closed state. */
    public void reset() {
        state.set(CircuitState.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        totalRequests.set(0);
        rejectedRequests.set(0);
        lastFailureTime = Instant.MIN;
        lastStateChange = Instant.now();
    }
}
