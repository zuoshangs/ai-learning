package com.ai.learning.cs.controller;

import com.ai.learning.cs.model.ChatRequest;
import com.ai.learning.cs.model.ChatResponse;
import com.ai.learning.cs.service.AiService;
import com.ai.learning.cs.service.SessionManager;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 智能客服 REST API
 */
@RestController
@RequestMapping("/api")
public class CsController {

    private final AiService aiService;
    private final SessionManager sessionManager;

    public CsController(AiService aiService, SessionManager sessionManager) {
        this.aiService = aiService;
        this.sessionManager = sessionManager;
    }

    /**
     * 同步对话
     * POST /api/chat  { "sessionId": "xxx", "message": "我的订单怎么了" }
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest req) {
        return aiService.chat(req.getSessionId(), req.getMessage());
    }

    /**
     * 简单 GET 调用
     * GET /api/chat?sessionId=xxx&message=xxx
     */
    @GetMapping("/chat")
    public ChatResponse chatGet(
            @RequestParam(required = false) String sessionId,
            @RequestParam String message) {
        return aiService.chat(sessionId, message);
    }

    /**
     * 流式对话 SSE
     * POST /api/chat/stream  { "sessionId": "xxx", "message": "..." }
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest req) {
        return aiService.chatStream(req.getSessionId(), req.getMessage());
    }

    /**
     * 查看所有活跃会话
     */
    @GetMapping("/sessions")
    public Map<String, Object> listSessions() {
        return sessionManager.listSessions();
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public Map<String, String> newSession() {
        String id = sessionManager.createSession();
        return Map.of("sessionId", id);
    }
}
