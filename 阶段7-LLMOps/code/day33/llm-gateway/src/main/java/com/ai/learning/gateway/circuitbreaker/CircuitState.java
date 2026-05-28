package com.ai.learning.gateway.circuitbreaker;

/**
 * Circuit Breaker states:
 * - CLOSED: normal operation, requests pass through
 * - OPEN: failures exceed threshold, requests rejected fast
 * - HALF_OPEN: trial period after timeout, limited requests allowed
 */
public enum CircuitState {
    CLOSED,    // Normal
    OPEN,      // Failing — fast reject
    HALF_OPEN  // Testing — allow limited requests
}
