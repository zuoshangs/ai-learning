package com.ai.cs.knowledge;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Knowledge base REST controller.
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeBaseService kb;

    public KnowledgeController(KnowledgeBaseService kb) {
        this.kb = kb;
    }

    /** Add a document */
    @PostMapping("/docs")
    public ResponseEntity<Document> addDoc(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String content = body.get("content");
        String category = body.getOrDefault("category", "默认");
        if (title == null || content == null) {
            return ResponseEntity.badRequest().build();
        }
        Document doc = kb.addDocument(title, content, category);
        return ResponseEntity.ok(doc);
    }

    /** List all documents */
    @GetMapping("/docs")
    public ResponseEntity<Map<String, Object>> listDocs(
            @RequestParam(required = false) String category) {
        List<Document> docs;
        if (category != null && !category.isBlank()) {
            docs = kb.getDocumentsByCategory(category);
        } else {
            docs = kb.getAllDocuments();
        }
        return ResponseEntity.ok(Map.of(
                "total", docs.size(),
                "documents", docs
        ));
    }

    /** Get a single document */
    @GetMapping("/docs/{id}")
    public ResponseEntity<Document> getDoc(@PathVariable String id) {
        Document doc = kb.getDocument(id);
        if (doc == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(doc);
    }

    /** Delete a document */
    @DeleteMapping("/docs/{id}")
    public ResponseEntity<Void> deleteDoc(@PathVariable String id) {
        kb.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /** Semantic search */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String category) {

        List<KnowledgeBaseService.SearchResult> results;
        if (category != null && !category.isBlank()) {
            results = kb.search(q, category, topK);
        } else {
            results = kb.search(q, topK);
        }

        List<Map<String, Object>> items = results.stream()
                .map(KnowledgeBaseService.SearchResult::toMap)
                .toList();

        return ResponseEntity.ok(Map.of(
                "query", q,
                "results", items,
                "total", items.size()
        ));
    }

    /** Get RAG context (for internal use / API clients) */
    @GetMapping("/context")
    public ResponseEntity<Map<String, Object>> context(@RequestParam String q) {
        String ragContext = kb.buildRagContext(q, 3);
        return ResponseEntity.ok(Map.of(
                "query", q,
                "context", ragContext,
                "hasContext", !ragContext.isEmpty()
        ));
    }

    /** Get categories */
    @GetMapping("/categories")
    public ResponseEntity<Set<String>> categories() {
        return ResponseEntity.ok(kb.getCategories());
    }

    /** Stats */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "totalDocs", kb.documentCount(),
                "categories", kb.getCategories()
        ));
    }
}
