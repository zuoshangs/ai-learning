package com.ai.learning.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 频率限制过滤器 — 防止滥用
 */
@Component
public class RateLimitFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final int maxPerMinute = 30;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // 仅限制 /api/ 路径
        String path = req.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // 基于 IP 限流
        String ip = req.getRemoteAddr();
        WindowCounter counter = counters.computeIfAbsent(ip, k -> new WindowCounter());

        if (counter.incrementAndCheck(maxPerMinute)) {
            log.warn("🚫 频率限制触发: IP={}, 路径={}", ip, path);
            resp.setStatus(429);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"请求过于频繁，请稍后再试\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 滑动窗口计数器
     */
    static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        synchronized boolean incrementAndCheck(int max) {
            long now = System.currentTimeMillis();
            // 每分钟重置
            if (now - windowStart > 60_000) {
                count.set(0);
                windowStart = now;
            }
            return count.incrementAndGet() > max;
        }
    }
}
