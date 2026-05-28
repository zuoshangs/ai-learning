package com.ai.learning.doc.controller;

import com.ai.learning.doc.model.DocumentInfo;
import com.ai.learning.doc.service.DocumentProcessingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文档处理控制器
 *
 * POST /doc/process          — 处理单个文件（传入 absolutePath）
 * POST /doc/process/dir      — 处理目录下所有文档
 * GET  /doc/strategies       — 列出可用的切分策略
 */
@RestController
@RequestMapping("/doc")
public class DocProcessingController {

    private static final Logger log = LoggerFactory.getLogger(DocProcessingController.class);

    private final DocumentProcessingService docService;

    public DocProcessingController(DocumentProcessingService docService) {
        this.docService = docService;
    }

    @PostMapping("/process")
    public ResponseEntity<?> processFile(@RequestBody Map<String, String> body) {
        String filePath = body.get("filePath");
        if (filePath == null || filePath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供 filePath"));
        }
        try {
            DocumentInfo result = docService.processFile(filePath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("处理文件失败: {}", filePath, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/process/dir")
    public ResponseEntity<?> processDirectory(@RequestBody Map<String, String> body) {
        String dirPath = body.get("dirPath");
        if (dirPath == null || dirPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供 dirPath"));
        }
        try {
            List<DocumentInfo> results = docService.processDirectory(dirPath);
            return ResponseEntity.ok(Map.of(
                "count", results.size(),
                "results", results
            ));
        } catch (Exception e) {
            log.error("处理目录失败: {}", dirPath, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/strategies")
    public ResponseEntity<?> listStrategies() {
        // 直接从 context 获取 info 不可行，这里简单返回名称列表
        return ResponseEntity.ok(Map.of(
            "strategies", List.of(
                "TokenSplitter (Spring AI) — 按 Token 切分，支持重叠",
                "ParagraphSplitter (语义) — 按段落/句子切分，保留语义完整",
                "RecursiveSplitter (LangChain式) — 递归字符切分，兼顾语义与大小"
            )
        ));
    }
}
