package com.ai.cs.ticket;

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
 * Integration tests for TicketController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TicketControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void listTicketsReturnsSeededData() {
        ResponseEntity<Map> response = rest.getForEntity("/api/tickets", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(((Number) body.get("total")).intValue() >= 4, "At least 4 seeded tickets");
    }

    @Test
    void createTicketWorks() {
        Map<String, Object> request = Map.of(
                "title", "测试工单",
                "description", "这是一个测试工单",
                "category", "售后",
                "priority", "HIGH",
                "creatorName", "测试用户"
        );

        ResponseEntity<Map> response = rest.postForEntity("/api/tickets", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> ticket = response.getBody();
        assertNotNull(ticket);
        assertEquals("测试工单", ticket.get("title"));
        assertNotNull(ticket.get("id"));
    }

    @Test
    void getTicketStats() {
        ResponseEntity<Map> response = rest.getForEntity("/api/tickets/stats", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> stats = response.getBody();
        assertNotNull(stats);
        assertNotNull(stats.get("total"));
        assertNotNull(stats.get("byPriority"));
    }

    @Test
    void getTicketCategories() {
        ResponseEntity<Map> response = rest.getForEntity("/api/tickets/categories", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void listWithStatusFilter() {
        ResponseEntity<Map> response = rest.getForEntity(
                "/api/tickets?status=PENDING", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("PENDING", ((java.util.List<Map>) body.get("tickets"))
                .get(0).get("status"));
    }
}
