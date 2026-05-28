package com.ai.cs.chat;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Chat REST controller supporting multi-turn conversations.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ConversationService conversationService;

    public ChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatResponse response = conversationService.processMessage(
                request.sessionId(), request.message());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<Map<String, Object>> history(@PathVariable String sessionId) {
        var history = conversationService.getHistory(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "history", history,
                "turnCount", history.size() / 2
        ));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        conversationService.clearSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
