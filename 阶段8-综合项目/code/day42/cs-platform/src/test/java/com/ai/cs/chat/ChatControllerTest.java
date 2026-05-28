package com.ai.cs.chat;

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
 * Integration tests for ChatController.
 * Uses random port (server.port=0) to avoid conflicts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void chatEndpointResponds() {
        Map<String, Object> request = Map.of(
                "sessionId", "test-session",
                "message", "你好"
        );

        ResponseEntity<Map> response = rest.postForEntity("/api/chat", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.get("sessionId"));
        assertNotNull(body.get("reply"));
        assertNotNull(body.get("historySize"));
        assertTrue((Integer) body.get("historySize") >= 1);
    }

    @Test
    void chatEndpointAutoGeneratesSessionId() {
        Map<String, Object> request = Map.of(
                "message", "测试消息"
        );

        ResponseEntity<Map> response = rest.postForEntity("/api/chat", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("sessionId"));
    }

    @Test
    void chatEndpointReturnsHistory() {
        // Send a message first
        Map<String, Object> req1 = Map.of("sessionId", "hist-test", "message", "第一条消息");
        rest.postForEntity("/api/chat", req1, Map.class);

        // Check history
        ResponseEntity<Map> historyResp = rest.getForEntity(
                "/api/chat/history/hist-test", Map.class);

        assertEquals(HttpStatus.OK, historyResp.getStatusCode());
        Map<String, Object> history = historyResp.getBody();
        assertNotNull(history);
        assertTrue(((Number) history.get("turnCount")).intValue() >= 1);
    }

    @Test
    void chatEndpointClearsSession() {
        ResponseEntity<Void> response = rest.exchange(
                "/api/chat/clear-session-test",
                org.springframework.http.HttpMethod.DELETE,
                null,
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
