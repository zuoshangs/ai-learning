package com.ai.learning.obs.tracing;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that injects a trace ID into every request.
 * The trace ID is logged via SLF4J MDC and returned in the response header.
 */
@Component
@Order(0)
public class TraceFilter implements Filter {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Extract existing trace ID from header, or generate new one
        String traceId = req.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
        }

        // Inject into MDC for logging
        MDC.put(MDC_KEY, traceId);

        // Inject into response header
        res.setHeader(TRACE_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
