package com.ai.learning.cache.controller;

import com.ai.learning.cache.service.SemanticCache;
import com.ai.learning.cache.model.CacheResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for semantic cache.
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final SemanticCache cache;

    public CacheController(SemanticCache cache) {
        this.cache = cache;
    }

    /** Chat endpoint — uses cache. */
    @PostMapping("/chat")
    public CacheResponse chat(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        if (query.isEmpty()) {
            CacheResponse r = new CacheResponse();
            r.setHit(false);
            r.setSource("error");
            r.setResponse("Query cannot be empty");
            return r;
        }
        return cache.getOrGenerate(query);
    }

    /** Direct cache lookup (no LLM fallback). */
    @GetMapping("/lookup")
    public CacheResponse lookup(@RequestParam String q) {
        // This bypasses LLM — for testing cache hits only
        var vec = cache.getEmbedder().computeVector(q);
        return cache.semanticLookup(q, vec);
    }

    /** Cache statistics. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return cache.getStats();
    }

    /** List cache entries. */
    @GetMapping("/entries")
    public List<Map<String, Object>> entries(@RequestParam(defaultValue = "20") int limit) {
        return cache.listEntries(limit);
    }

    /** Clear cache. */
    @DeleteMapping("/clear")
    public Map<String, String> clear() {
        cache.clear();
        return Map.of("status", "cleared");
    }

    /** Warmup cache with default entries. */
    @PostMapping("/warmup")
    public Map<String, Object> warmup() {
        cache.doWarmup();
        return Map.of("status", "ok", "size", cache.getSize());
    }
}
