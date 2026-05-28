package com.ai.llm.gateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Trace ID filter: generates or propagates X-Trace-ID for request tracing.
 */
@Component
@Order(0)
public class TraceFilter implements Filter {

    static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String traceId = req.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        MDC.put("traceId", traceId);
        resp.setHeader(TRACE_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
