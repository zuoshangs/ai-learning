package com.ai.learning.controller;

import com.ai.learning.dto.BookInfo;
import com.ai.learning.dto.CodeReviewResult;
import com.ai.learning.service.AiChatService;
import org.springframework.web.bind.annotation.*;

/**
 * Day 17 控制器 — 演示提示词模板和结构化输出
 */
@RestController
public class ChatController {

    private final AiChatService aiChatService;

    public ChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    // =========================================================
    // 模式1：普通对话（与Day 16相同）
    // =========================================================
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好") String message) {
        return aiChatService.chat(message);
    }

    // =========================================================
    // 模式2：提示词模板 — 参数化注入
    // =========================================================

    /** 代码审查（文本输出） */
    @GetMapping("/review")
    public String reviewCode(
            @RequestParam(defaultValue = "java") String language,
            @RequestParam(defaultValue = "public class Test {}") String code) {
        return aiChatService.codeReview(language, code);
    }

    /** 翻译 */
    @GetMapping("/translate")
    public String translate(
            @RequestParam(defaultValue = "英文") String sourceLang,
            @RequestParam(defaultValue = "中文") String targetLang,
            @RequestParam(defaultValue = "Hello World") String text) {
        return aiChatService.translate(sourceLang, targetLang, text);
    }

    /** SQL 生成 */
    @GetMapping("/sql")
    public String generateSql(
            @RequestParam(defaultValue = "users(id,name,email), orders(id,user_id,amount)") String schema,
            @RequestParam(defaultValue = "查询每个用户的订单总数") String query) {
        return aiChatService.generateSql(schema, query);
    }

    // =========================================================
    // 模式3：结构化输出 — AI返回JSON自动映射
    // =========================================================

    /** 图书信息（结构化） */
    @GetMapping("/book")
    public BookInfo getBookInfo(@RequestParam(defaultValue = "三体") String name) {
        return aiChatService.getBookInfo(name);
    }

    /** 代码审查（结构化） */
    @PostMapping("/review/structured")
    public CodeReviewResult structuredReview(@RequestBody String code) {
        return aiChatService.structuredCodeReview(code);
    }
}
