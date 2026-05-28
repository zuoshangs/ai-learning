package com.ai.learning.gateway.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter for API key authentication.
 * Blocks requests without valid API key, except for public endpoints.
 */
@Component
@Order(1)
public class AuthFilter implements Filter {

    private final ApiKeyManager apiKeyManager;

    public AuthFilter(ApiKeyManager apiKeyManager) {
        this.apiKeyManager = apiKeyManager;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();

        // Skip auth for public endpoints
        if (path.equals("/actuator/health") || path.equals("/api/admin/keys/test") || path.startsWith("/api/admin")) {
            chain.doFilter(request, response);
            return;
        }

        // Extract API key from header
        String apiKey = req.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isEmpty() || !apiKeyManager.isValid(apiKey)) {
            res.setStatus(401);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"invalid_api_key\",\"message\":\"Missing or invalid API key. Provide via X-API-Key header.\"}");
            return;
        }

        // Attach API key info as request attributes
        req.setAttribute("apiKey", apiKey);
        req.setAttribute("owner", apiKeyManager.getOwner(apiKey));
        req.setAttribute("tier", apiKeyManager.getTier(apiKey));

        chain.doFilter(request, response);
    }
}
