package com.ai.learning.controller;

import com.ai.learning.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Day 18 控制器 — 多轮对话记忆 + SSE 流式输出
 * 
 * 三条核心API：
 *   POST /session              — 创建新会话
 *   GET  /chat?session=X&msg=Y — 同步对话（有记忆，无流式）
 *   GET  /chat/stream?session=X&msg=Y — 流式对话（有记忆+SSE打字机）
 */
@RestController
public class ChatController {

    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 创建新会话
     * 每个会话拥有独立的对话记忆
     */
    @PostMapping("/session")
    public Map<String, String> createSession() {
        String sessionId = chatService.createSession();
        return Map.of("sessionId", sessionId, "message", "新会话已创建");
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/session/{sessionId}/history")
    public List<Map<String, String>> getHistory(@PathVariable String sessionId) {
        return chatService.getHistory(sessionId);
    }

    /**
     * 清空会话历史
     */
    @DeleteMapping("/session/{sessionId}")
    public Map<String, String> clearSession(@PathVariable String sessionId) {
        chatService.clearHistory(sessionId);
        return Map.of("message", "会话已清空");
    }

    // =========================================================
    // 同步对话（含多轮记忆）
    // =========================================================

    /**
     * 同步对话 — 带多轮对话记忆
     */
    @GetMapping("/chat")
    public String chat(
            @RequestParam String session,
            @RequestParam(defaultValue = "你好") String msg) {
        return chatService.chat(session, msg);
    }

    // =========================================================
    // 流式对话（SSE 打字机效果）
    // =========================================================

    /**
     * 流式对话 — SSE 逐字输出（打字机效果）
     * 
     * 使用 SseEmitter 实现，不需要 WebFlux 依赖。
     * 每个 chunk 通过 emitter.send() 推送到前端。
     */
    @GetMapping("/chat/stream")
    public SseEmitter streamChat(
            @RequestParam String session,
            @RequestParam String msg) {
        // 超时时间：5分钟
        SseEmitter emitter = new SseEmitter(300_000L);

        executor.execute(() -> {
            try {
                chatService.streamChat(session, msg)
                    .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> emitter.completeWithError(error),
                        () -> emitter.complete()
                    );
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
