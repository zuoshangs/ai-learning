package com.ai.cs.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DashboardController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthEndpoint() {
        ResponseEntity<Map> response = rest.getForEntity("/api/dashboard/health", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("CS Platform Dashboard", response.getBody().get("service"));
    }

    @Test
    void statusEndpoint() {
        ResponseEntity<Map> response = rest.getForEntity("/api/dashboard/status", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.get("uptime"));
        assertNotNull(body.get("cacheHitRate"));
    }

    @Test
    void reportEndpointReturnsAllSections() {
        ResponseEntity<Map> response = rest.getForEntity("/api/dashboard/report", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("system"));
        assertTrue(body.containsKey("chat"));
        assertTrue(body.containsKey("cache"));
        assertTrue(body.containsKey("rateLimit"));
        assertTrue(body.containsKey("cost"));
        assertTrue(body.containsKey("tickets"));
        assertTrue(body.containsKey("knowledge"));
    }

    @Test
    void reportContainsSystemInfo() {
        ResponseEntity<Map> response = rest.getForEntity("/api/dashboard/report", Map.class);

        Map<String, Object> system = (Map<String, Object>) response.getBody().get("system");
        assertEquals("CS Platform", system.get("service"));
        assertEquals("running", system.get("status"));
    }

    @Test
    void reportContainsTicketStats() {
        ResponseEntity<Map> response = rest.getForEntity("/api/dashboard/report", Map.class);

        Map<String, Object> tickets = (Map<String, Object>) response.getBody().get("tickets");
        assertTrue(((Number) tickets.get("total")).intValue() >= 4);
    }

    @Test
    void clearCache() {
        ResponseEntity<Map> response = rest.postForEntity(
                "/api/dashboard/cache/clear", null, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("clear_cache", response.getBody().get("action"));
    }
}
