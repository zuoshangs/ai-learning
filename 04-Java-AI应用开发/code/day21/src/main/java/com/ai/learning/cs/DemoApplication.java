package com.ai.learning.cs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("=" + "=".repeat(55));
        System.out.println("  Day 21 — 智能客服系统");
        System.out.println("  意图识别 + 流式输出 + 异常重试");
        System.out.println("  POST /api/chat     — 同步对话");
        System.out.println("  GET  /api/chat/stream — 流式对话");
        System.out.println("  GET  /api/sessions — 查看会话");
        System.out.println("  GET  /chat.html    — 聊天界面");
        System.out.println("=".repeat(56));
    }
}
