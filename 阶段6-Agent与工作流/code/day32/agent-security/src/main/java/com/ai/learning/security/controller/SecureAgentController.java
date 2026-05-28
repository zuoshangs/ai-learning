package com.ai.learning.security.controller;

import com.ai.learning.security.detector.InjectionDetector;
import com.ai.learning.security.detector.SensitiveDataDetector;
import com.ai.learning.security.evaluation.AgentEvaluator;
import com.ai.learning.security.evaluation.EvaluationReport;
import com.ai.learning.security.evaluation.TestCase;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 安全 Agent API 控制器
 */
@RestController
@RequestMapping("/api/agent")
public class SecureAgentController {

    private final ChatClient chatClient;
    private final InjectionDetector injectionDetector;

    public SecureAgentController(ChatClient.Builder chatBuilder,
                                 InjectionDetector injectionDetector) {
        this.chatClient = chatBuilder.build();
        this.injectionDetector = injectionDetector;
    }

    /**
     * 安全版对话 — 自动注入检测
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");

        // 1. 注入检测
        var detection = injectionDetector.analyze(message);
        if (detection.isAttack()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("blocked", true);
            resp.put("message", "检测到不安全输入，已拦截。");
            resp.put("detail", detection.getSummary());
            return ResponseEntity.ok(resp);
        }

        // 2. 正常 LLM 调用
        String response = chatClient.prompt()
                .user(message)
                .call()
                .content();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("blocked", false);
        resp.put("message", response);
        return ResponseEntity.ok(resp);
    }

    /**
     * 注入检测 API（无 LLM 调用，仅检测）
     */
    @PostMapping("/detect")
    public ResponseEntity<Map<String, Object>> detect(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        var result = injectionDetector.analyze(text);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("isAttack", result.isAttack());
        resp.put("summary", result.getSummary());
        if (result.isAttack()) {
            resp.put("findings", result.findings());
        }
        return ResponseEntity.ok(resp);
    }
}
