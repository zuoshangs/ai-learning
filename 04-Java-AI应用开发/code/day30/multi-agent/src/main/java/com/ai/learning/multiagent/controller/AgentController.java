package com.ai.learning.multiagent.controller;

import com.ai.learning.multiagent.orchestrator.OrchestratorAgent;
import com.ai.learning.multiagent.orchestrator.OrchestrationResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final OrchestratorAgent orchestrator;

    public AgentController(OrchestratorAgent orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/ask")
    public OrchestrationResult ask(@RequestParam String query) {
        return orchestrator.process(query);
    }

    @GetMapping("/health")
    public String health() {
        return "{\"status\": \"UP\", \"service\": \"multi-agent\"}";
    }
}