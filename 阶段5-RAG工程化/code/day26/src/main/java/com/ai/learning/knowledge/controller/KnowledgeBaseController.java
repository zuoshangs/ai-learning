package com.ai.learning.knowledge.controller;

import com.ai.learning.knowledge.evaluation.RagEvaluator;
import com.ai.learning.knowledge.ingestion.DocumentIngestionService;
import com.ai.learning.knowledge.model.*;
import com.ai.learning.knowledge.search.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 知识库 REST 控制器
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final KnowledgeBaseService kbService;
    private final DocumentIngestionService ingestionService;
    private final RagEvaluator evaluator;

    public KnowledgeBaseController(KnowledgeBaseService kbService,
                                   DocumentIngestionService ingestionService,
                                   RagEvaluator evaluator) {
        this.kbService = kbService;
        this.ingestionService = ingestionService;
        this.evaluator = evaluator;
    }

    // ============================================
    //  文档摄入
    // ============================================

    /**
     * 异步摄入文档
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestDocument(@RequestBody IngestRequest req) {
        if (req.getContent() == null || req.getContent().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content 不能为空"));
        }

        String docId = UUID.randomUUID().toString();
        KnowledgeDocument doc = new KnowledgeDocument(docId, req.getTitle(),
            req.getContent(), req.getSource(), req.getDocType());

        String taskId = ingestionService.ingestAsync(doc);

        return ResponseEntity.ok(Map.of(
            "taskId", taskId,
            "docId", docId,
            "title", req.getTitle(),
            "status", "queued"
        ));
    }

    /**
     * 查询摄入任务状态
     */
    @GetMapping("/ingest/{taskId}")
    public ResponseEntity<?> getIngestStatus(@PathVariable String taskId) {
        DocumentIngestionService.TaskStatus status = ingestionService.getStatus(taskId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "taskId", status.taskId,
            "docId", status.docId,
            "status", status.status,
            "totalChunks", status.totalChunks,
            "error", status.error != null ? status.error : ""
        ));
    }

    /**
     * 获取摄入统计
     */
    @GetMapping("/ingest/stats")
    public ResponseEntity<Map<String, Object>> getIngestStats() {
        return ResponseEntity.ok(ingestionService.getStats());
    }

    // ============================================
    //  搜索
    // ============================================

    /**
     * V2 集成搜索（全管线）
     */
    @PostMapping("/search/v2")
    public ResponseEntity<SearchResponse> searchV2(@RequestBody SearchRequest req) {
        if (req.getQuery() == null || req.getQuery().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        SearchResponse resp = kbService.searchV2(req);
        return ResponseEntity.ok(resp);
    }

    /**
     * V1 基础搜索（对比基准）
     */
    @PostMapping("/search/v1")
    public ResponseEntity<SearchResponse> searchV1(@RequestParam String query,
                                                    @RequestParam(defaultValue = "5") int topK) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        SearchResponse resp = kbService.searchV1(query, topK);
        return ResponseEntity.ok(resp);
    }

    /**
     * GET 搜索接口（简单模式）
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "v2") String mode,
            @RequestParam(defaultValue = "5") int topK) {

        if ("v1".equalsIgnoreCase(mode)) {
            return ResponseEntity.ok(kbService.searchV1(query, topK));
        }

        SearchRequest req = new SearchRequest()
            .setQuery(query)
            .setTopK(topK)
            .setUseRewrite(true)
            .setUseHyde(false)
            .setUseParentDoc(true)
            .setUseHybridSearch(true)
            .setUseReranker(true);
        return ResponseEntity.ok(kbService.searchV2(req));
    }

    // ============================================
    //  评估
    // ============================================

    /**
     * 运行评估（自定义测试集）
     */
    @PostMapping("/evaluate")
    public ResponseEntity<EvaluationResult> evaluate(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        var queries = (java.util.List<String>) body.getOrDefault("queries", java.util.List.of());
        int topK = (int) body.getOrDefault("topK", 5);

        EvaluationResult result = evaluator.evaluate(queries, topK);
        return ResponseEntity.ok(result);
    }

    /**
     * 快速评估（预定义测试集）
     */
    @GetMapping("/evaluate/quick")
    public ResponseEntity<EvaluationResult> quickEval(
            @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(evaluator.quickEval(topK));
    }

    // ============================================
    //  健康检查
    // ============================================

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "version", "Enterprise Knowledge Base V2",
            "module", "Day 26 - 集成所有 RAG 技术"
        ));
    }
}
