package com.ai.learning.tools.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * 工具调用控制器
 * 
 * 提供 REST API 让用户通过自然语言调用各种工具。
 * AI 自动根据用户的问题选择合适的工具并调用。
 */
@RestController
public class ToolController {

    private final ChatClient chatClient;

    public ToolController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 同步对话 — AI 根据问题自动调用工具
     * 
     * 示例请求：
     *   GET /chat?msg=北京今天天气怎么样
     *   GET /chat?msg=计算 12345 × 6789
     *   GET /chat?msg=搜索最新的AI新闻
     *   GET /chat?msg=5公里等于多少米
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好") String msg) {
        return chatClient.prompt()
            .user(msg)
            .call()
            .content();
    }

    /**
     * 流式对话 — AI 逐字输出回答（含工具调用过程）
     * 
     * 示例：GET /chat/stream?msg=北京和上海天气对比
     */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam(defaultValue = "你好") String msg) {
        return chatClient.prompt()
            .user(msg)
            .stream()
            .content()
            .map(chunk -> "data:" + chunk + "\n\n");
    }
}
