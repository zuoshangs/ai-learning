package com.ai.learning.advanced.controller;

import com.ai.learning.advanced.model.RagResult;
import com.ai.learning.advanced.service.HydeSearchService;
import com.ai.learning.advanced.service.ParentDocService;
import com.ai.learning.advanced.service.QueryRewriteService;
import com.ai.learning.advanced.service.RagCompareService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rag")
public class AdvancedRagController {

    private final QueryRewriteService rewriteService;
    private final HydeSearchService hydeService;
    private final ParentDocService parentDocService;
    private final RagCompareService compareService;

    public AdvancedRagController(QueryRewriteService rewriteService,
                                  HydeSearchService hydeService,
                                  ParentDocService parentDocService,
                                  RagCompareService compareService) {
        this.rewriteService = rewriteService;
        this.hydeService = hydeService;
        this.parentDocService = parentDocService;
        this.compareService = compareService;
    }

    @GetMapping("/rewrite")
    public ResponseEntity<?> rewrite(@RequestParam(defaultValue = "向量数据库怎么用") String q,
                                      @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(rewriteService.search(q, topK));
    }

    @GetMapping("/hyde")
    public ResponseEntity<?> hyde(@RequestParam(defaultValue = "Spring AI 支持哪些向量数据库") String q,
                                   @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(hydeService.search(q, topK));
    }

    @GetMapping("/parent-doc")
    public ResponseEntity<?> parentDoc(@RequestParam(defaultValue = "PgVector") String q,
                                        @RequestParam(defaultValue = "3") int topK) {
        return ResponseEntity.ok(parentDocService.search(q, topK));
    }

    @GetMapping("/compare")
    public ResponseEntity<?> compare(@RequestParam(defaultValue = "Spring AI 向量数据库") String q,
                                      @RequestParam(defaultValue = "3") int topK) {
        return ResponseEntity.ok(compareService.compare(q, topK));
    }

    @GetMapping
    public ResponseEntity<?> info() {
        return ResponseEntity.ok(Map.of(
            "endpoints", List.of(
                "GET /rag/rewrite?q=...    — 查询重写 + 检索",
                "GET /rag/hyde?q=...       — HyDE 假设回答检索",
                "GET /rag/parent-doc?q=... — 父文档检索",
                "GET /rag/compare?q=...    — 四种技术对比"
            )
        ));
    }
}
