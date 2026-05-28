package com.ai.learning.hybrid.service;

import com.ai.learning.hybrid.model.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义检索 — 基于 PgVector 向量相似度
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final VectorStore vectorStore;

    public VectorSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<SearchResult> search(String query, int topK) {
        long t0 = System.currentTimeMillis();
        var docs = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(topK * 2).build());
        long t1 = System.currentTimeMillis();

        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < Math.min(docs.size(), topK); i++) {
            var doc = docs.get(i);
            double dist = doc.getMetadata().get("distance") instanceof Number n
                ? n.doubleValue() : 1.0;
            double score = Math.round((1 - dist / 2.0) * 1000.0) / 1000.0;

            results.add(new SearchResult()
                .setRank(i + 1)
                .setScore(score)
                .setContent(doc.getText())
                .setContentLength(doc.getText().length())
                .setSource((String) doc.getMetadata().getOrDefault("source", "unknown"))
                .setMethod("semantic"));
        }

        log.info("🔍 语义检索: {} 条结果 [{}ms]", results.size(), t1 - t0);
        return results;
    }
}
