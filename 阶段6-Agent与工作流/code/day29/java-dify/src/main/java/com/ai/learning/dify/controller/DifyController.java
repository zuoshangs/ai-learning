package com.ai.learning.dify.controller;

import com.ai.learning.dify.service.DifyClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.Map;

@RestController
@RequestMapping("/api/dify")
public class DifyController {

    private final DifyClient difyClient;

    public DifyController(DifyClient difyClient) {
        this.difyClient = difyClient;
    }

    /**
     * Java → Dify：调用工作流
     * POST /api/dify/workflow?query=...
     */
    @PostMapping("/workflow")
    public Mono<String> callWorkflow(@RequestParam String query,
                                     @RequestParam(required = false) String userId) {
        return difyClient.runWorkflow(query, userId);
    }

    /**
     * Java → Dify：发送聊天消息
     * POST /api/dify/chat?query=...
     */
    @PostMapping("/chat")
    public Mono<String> sendChat(@RequestParam String query,
                                 @RequestParam(required = false) String conversationId) {
        return difyClient.sendMessage(query, conversationId);
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "java-dify");
    }
}