package com.ai.cs.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AdminController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void adminStatus() {
        ResponseEntity<java.util.Map> response = rest.getForEntity(
                "/api/admin/status", java.util.Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CS Platform", response.getBody().get("service"));
        assertNotNull(response.getBody().get("memory"));
    }

    @Test
    void adminHealth() {
        ResponseEntity<java.util.Map> response = rest.getForEntity(
                "/api/admin/health", java.util.Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }
}
