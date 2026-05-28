package com.ai.llm.gateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * API Key authentication filter.
 * Validates the X-API-Key header against configured keys.
 */
@Component
@Order(1)
public class AuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    public static final String ATTR_API_KEY = "apiKey";
    public static final String ATTR_KEY_TIER = "keyTier";

    // In production, these come from a secure store (Vault, K8s Secret, etc.)
    private static final String GOLD_KEY = "sk-gold-001";
    private static final String SILVER_KEY = "sk-silver-001";
    private static final String FREE_KEY = "sk-free-001";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // Skip auth for actuator and health endpoints
        String path = req.getRequestURI();
        if (path.startsWith("/actuator/") || path.equals("/actuator")
                || path.startsWith("/admin/") && req.getMethod().equals("GET")) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = req.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            resp.setStatus(401);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"Missing X-API-Key header\",\"status\":401}");
            return;
        }

        String tier = switch (apiKey) {
            case GOLD_KEY -> "gold";
            case SILVER_KEY -> "silver";
            case FREE_KEY -> "free";
            default -> null;
        };

        if (tier == null) {
            resp.setStatus(401);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"Invalid API key\",\"status\":401}");
            return;
        }

        req.setAttribute(ATTR_API_KEY, apiKey);
        req.setAttribute(ATTR_KEY_TIER, tier);
        chain.doFilter(request, response);
    }
}
