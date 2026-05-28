package com.ai.learning.gateway.controller;

import com.ai.learning.gateway.gateway.LLMGateway;
import com.ai.learning.gateway.model.ChatRequest;
import com.ai.learning.gateway.model.ChatResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * Main gateway controller — proxy for LLM chat completions.
 */
@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    private final LLMGateway gateway;

    public GatewayController(LLMGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest servletRequest) {
        String apiKey = (String) servletRequest.getAttribute("apiKey");
        String provider = servletRequest.getParameter("provider");
        if (provider == null || provider.isBlank()) provider = "deepseek";
        return gateway.process(apiKey, provider, request);
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
