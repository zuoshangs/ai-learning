package com.ai.llm.gateway.circuit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 3-state circuit breaker: CLOSED → OPEN → HALF_OPEN → CLOSED
 */
public class CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long resetTimeoutMillis;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;

    public CircuitBreakerService(int failureThreshold, int resetSeconds) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMillis = resetSeconds * 1000L;
    }

    public boolean isAvailable() {
        State current = state.get();
        if (current == State.CLOSED) return true;
        if (current == State.OPEN) {
            // Check if it's time to try half-open
            if (System.currentTimeMillis() - lastFailureTime >= resetTimeoutMillis) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("Circuit breaker HALF_OPEN: allowing probe request");
                    return true;
                }
            }
            return false;
        }
        // HALF_OPEN: allow one probe
        return true;
    }

    public void onSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            log.info("Circuit breaker CLOSED (recovered from half-open)");
            state.set(State.CLOSED);
            failureCount.set(0);
        }
    }

    public void onFailure() {
        lastFailureTime = System.currentTimeMillis();
        int failures = failureCount.incrementAndGet();

        State current = state.get();
        if (current == State.HALF_OPEN) {
            log.warn("Circuit breaker OPEN (probe failed, {}/{})", failures, failureThreshold);
            state.set(State.OPEN);
        } else if (current == State.CLOSED && failures >= failureThreshold) {
            log.warn("Circuit breaker OPEN (threshold {}/{})", failures, failureThreshold);
            state.set(State.OPEN);
        }
    }

    public State getState() { return state.get(); }
    public int getFailureCount() { return failureCount.get(); }
    public int getFailureThreshold() { return failureThreshold; }
}
