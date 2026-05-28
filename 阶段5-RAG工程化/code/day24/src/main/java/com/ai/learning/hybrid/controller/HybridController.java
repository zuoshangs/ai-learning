package com.ai.learning.hybrid.controller;

import com.ai.learning.hybrid.model.HybridSearchResponse;
import com.ai.learning.hybrid.service.HybridSearchService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search")
public class HybridController {

    private final HybridSearchService searchService;

    public HybridController(HybridSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(defaultValue = "Spring AI") String q,
            @RequestParam(defaultValue = "hybrid") String mode,
            @RequestParam(defaultValue = "5") int topK) {

        if (!List.of("semantic", "keyword", "hybrid", "rerank", "compare", "rag").contains(mode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不支持的模式: " + mode));
        }

        HybridSearchResponse resp = searchService.search(q, mode, topK);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/modes")
    public ResponseEntity<?> listModes() {
        return ResponseEntity.ok(Map.of(
            "modes", List.of(
                "semantic — 纯语义检索 (PgVector)",
                "keyword  — 关键词检索 (PostgreSQL FTS)",
                "hybrid   — 混合检索 (RRF 融合) [默认]",
                "rerank   — LLM Reranker 重排",
                "compare  — 三方法并列对比",
                "rag      — 混合检索 + RAG 增强"
            )
        ));
    }
}
