package com.ai.learning.cache.controller;

import com.ai.learning.cache.embedding.TfIdfEmbedder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin controller — test embedding, similarity, etc.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TfIdfEmbedder embedder;

    public AdminController(TfIdfEmbedder embedder) {
        this.embedder = embedder;
    }

    /** Tokenize a query. */
    @GetMapping("/tokenize")
    public Map<String, Object> tokenize(@RequestParam String q) {
        List<String> tokens = embedder.tokenize(q);
        return Map.of("query", q, "tokens", tokens, "count", tokens.size());
    }

    /** Compare similarity between two queries. */
    @GetMapping("/similarity")
    public Map<String, Object> similarity(@RequestParam String a, @RequestParam String b) {
        var va = embedder.computeVector(a);
        var vb = embedder.computeVector(b);
        double sim = embedder.cosineSimilarity(va, vb);
        return Map.of("queryA", a, "queryB", b, "similarity", sim);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "semantic-cache");
    }
}
