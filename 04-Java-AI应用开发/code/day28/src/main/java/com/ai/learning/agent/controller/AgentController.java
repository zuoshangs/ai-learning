package com.ai.learning.agent.controller;

import com.ai.learning.agent.memory.ConversationMemory;
import com.ai.learning.agent.memory.MemoryService;
import com.ai.learning.agent.orchestrator.OrchestratorAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent API 控制器
 * 提供带记忆的对话接口
 */
@RestController
@RequestMapping("/chat")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final OrchestratorAgent orchestratorAgent;
    private final MemoryService memoryService;

    /** 默认会话ID（简单模式下使用） */
    private static final String DEFAULT_SESSION = "default-session";

    public AgentController(OrchestratorAgent orchestratorAgent, MemoryService memoryService) {
        this.orchestratorAgent = orchestratorAgent;
        this.memoryService = memoryService;
    }

    /**
     * 对话接口（带记忆）
     * GET /chat?msg=你好
     * GET /chat?msg=北京天气&session=my-session
     */
    @GetMapping
    public Map<String, Object> chat(
            @RequestParam("msg") String message,
            @RequestParam(value = "session", defaultValue = DEFAULT_SESSION) String sessionId) {

        log.info("收到请求 [会话:{}]: {}", sessionId, message);

        if (message == null || message.trim().isEmpty()) {
            return Map.of("error", "消息不能为空");
        }

        long startTime = System.currentTimeMillis();
        try {
            String response = orchestratorAgent.processMessage(sessionId, message);
            long elapsed = System.currentTimeMillis() - startTime;

            return Map.of(
                "sessionId", sessionId,
                "response", response,
                "elapsed_ms", elapsed
            );
        } catch (Exception e) {
            log.error("处理消息失败: {}", e.getMessage(), e);
            return Map.of(
                "sessionId", sessionId,
                "error", "处理失败: " + e.getMessage(),
                "elapsed_ms", System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * 清空记忆
     * GET /chat/new
     */
    @GetMapping("/new")
    public Map<String, Object> clearMemory(
            @RequestParam(value = "session", defaultValue = DEFAULT_SESSION) String sessionId) {
        memoryService.clearShortTerm(sessionId);
        log.info("已清空会话记忆: {}", sessionId);
        return Map.of("status", "ok", "message", "记忆已清空", "sessionId", sessionId);
    }

    /**
     * 查看历史记忆
     * GET /chat/memory
     */
    @GetMapping("/memory")
    public Map<String, Object> viewMemory(
            @RequestParam(value = "session", defaultValue = DEFAULT_SESSION) String sessionId) {
        List<ConversationMemory.Message> messages = memoryService.getFullContext(sessionId);

        List<Map<String, String>> history = messages.stream()
            .map(msg -> Map.of("role", msg.getRole(), "content", msg.getContent()))
            .collect(Collectors.toList());

        return Map.of(
            "sessionId", sessionId,
            "totalMessages", history.size(),
            "messages", history
        );
    }

    /**
     * 列出所有会话
     * GET /chat/sessions
     */
    @GetMapping("/sessions")
    public Map<String, Object> listSessions() {
        List<String> sessions = memoryService.getAllSessionIds();
        return Map.of("sessions", sessions, "total", sessions.size());
    }
}
