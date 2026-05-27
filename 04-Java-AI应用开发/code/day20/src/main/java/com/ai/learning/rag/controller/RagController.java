package com.ai.learning.rag.controller;

import com.ai.learning.rag.service.RagService;

import org.springframework.web.bind.annotation.*;

/**
 * RAG 控制器
 * 
 * POST /rag/load   — 加载文档到向量库（先加载后问答）
 * GET  /rag/ask    — RAG 问答（检索+增强+生成）
 * GET  /rag/search — 纯检索（只看向量库返回的原文）
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/load")
    public String load() {
        return ragService.loadDocuments();
    }

    @GetMapping("/ask")
    public String ask(@RequestParam(defaultValue = "Spring AI 的核心特性有哪些？") String q) {
        return ragService.ask(q);
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "Spring AI") String q) {
        return ragService.search(q);
    }

    @PostMapping("/clear")
    public String clear() {
        return ragService.clearVectorStore();
    }
}
