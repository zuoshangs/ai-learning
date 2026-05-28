package com.ai.learning.obs.controller;

import com.ai.learning.obs.service.LlmProxyService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Main LLM proxy controller with observability built in.
 */
@RestController
@RequestMapping("/api/v1")
public class LlmController {

    private final LlmProxyService llmService;

    public LlmController(LlmProxyService llmService) {
        this.llmService = llmService;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        if (prompt.isBlank()) {
            return Map.of("error", "prompt_required", "message", "Prompt cannot be empty");
        }

        try {
            String response = llmService.call(prompt);
            return Map.of("success", true, "response", response);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "pong", "service", "observability-demo");
    }
}
