package com.ai.cs.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Admin status endpoint for monitoring the platform.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final Instant startTime = Instant.now();

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        Runtime rt = Runtime.getRuntime();
        long uptime = Duration.between(startTime, Instant.now()).getSeconds();

        return ResponseEntity.ok(Map.of(
                "service", "CS Platform",
                "status", "running",
                "uptime", uptime,
                "version", "1.0.0",
                "memory", Map.of(
                        "used", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024 + "MB",
                        "total", rt.totalMemory() / 1024 / 1024 + "MB",
                        "max", rt.maxMemory() / 1024 / 1024 + "MB"
                ),
                "threads", ManagementFactory.getThreadMXBean().getThreadCount()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
