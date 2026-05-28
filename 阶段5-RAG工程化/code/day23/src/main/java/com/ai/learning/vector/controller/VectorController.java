package com.ai.learning.vector.controller;

import com.ai.learning.vector.model.IngestionResult;
import com.ai.learning.vector.model.SearchResponse;
import com.ai.learning.vector.service.VectorIngestionService;
import com.ai.learning.vector.service.VectorSearchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 向量检索控制器
 *
 * POST /vector/ingest          — 摄入单个文件
 * POST /vector/ingest/dir      — 批量摄入目录
 * GET  /vector/search          — 语义检索
 * POST /vector/clear           — 清空向量库
 * GET  /vector/strategies      — 列出可用策略
 */
@RestController
@RequestMapping("/vector")
public class VectorController {

    private static final Logger log = LoggerFactory.getLogger(VectorController.class);

    private final VectorIngestionService ingestionService;
    private final VectorSearchService searchService;

    public VectorController(VectorIngestionService ingestionService,
                            VectorSearchService searchService) {
        this.ingestionService = ingestionService;
        this.searchService = searchService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestBody Map<String, String> body) {
        String filePath = body.get("filePath");
        String strategy = body.getOrDefault("strategy", "");
        if (filePath == null || filePath.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "请提供 filePath"));
        try {
            IngestionResult result = ingestionService.ingestFile(filePath, strategy);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("摄入失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/ingest/dir")
    public ResponseEntity<?> ingestDir(@RequestBody Map<String, String> body) {
        String dirPath = body.get("dirPath");
        String strategy = body.getOrDefault("strategy", "");
        if (dirPath == null || dirPath.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "请提供 dirPath"));
        try {
            List<IngestionResult> results = ingestionService.ingestDirectory(dirPath, strategy);
            return ResponseEntity.ok(Map.of("count", results.size(), "results", results));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(defaultValue = "Spring AI") String q,
            @RequestParam(defaultValue = "top-k") String mode,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.0") double threshold) {

        if (!List.of("top-k", "threshold", "window", "compare", "rag").contains(mode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不支持的模式: " + mode));
        }

        SearchResponse response = searchService.search(q, mode, topK, threshold);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/clear")
    public ResponseEntity<?> clear() {
        ingestionService.clearVectorStore();
        return ResponseEntity.ok(Map.of("message", "✅ 向量库已清空"));
    }

    @GetMapping("/strategies")
    public ResponseEntity<?> listStrategies() {
        return ResponseEntity.ok(Map.of(
            "modes", List.of(
                "top-k     — 标准 Top-K 语义检索",
                "threshold — 带相似度阈值过滤",
                "window    — 上下文窗口（命中块 + 前后文）",
                "compare   — 策略对比（各切分策略结果并列）",
                "rag       — 检索增强生成（含 AI 回答）"
            ),
            "tips", "检索参数: topK(默认5), threshold(默认0.0), mode(默认top-k)"
        ));
    }
}
