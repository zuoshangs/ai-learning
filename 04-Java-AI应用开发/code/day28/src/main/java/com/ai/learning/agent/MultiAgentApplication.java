package com.ai.learning.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MultiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiAgentApplication.class, args);
        System.out.println("""
            ╔══════════════════════════════════════════════════════╗
            ║   Multi-Agent V2 — 多工具编排 + Agent 记忆系统       ║
            ║   端口: 8090                                         ║
            ║   API: /chat?msg=你的问题                            ║
            ║        /chat/new （清空记忆）                         ║
            ║        /chat/memory （查看历史）                      ║
            ╚══════════════════════════════════════════════════════╝
            """);
    }
}
