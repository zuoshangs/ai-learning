package com.ai.learning.advanced.service;

import com.ai.learning.advanced.model.RagResult;
import com.ai.learning.advanced.model.RagResult.ChunkHit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 父文档检索服务（Parent Document Retrieval）
 *
 * 核心思想：
 *   检索时用小块（child chunks）做精确匹配
 *   返回时拼接成完整大块（parent chunks）做上下文
 *
 * 优点：
 *   - 小块 → 精确匹配关键词/语义（高精度）
 *   - 大块 → 完整上下文给 LLM（高召回）
 *
 * 实现方式：
 *   PgVector 存的是 child chunks（经过切分的小块）。
 *   我们通过 metadata 中的 "source" 字段找到同一来源的所有块，
 *   并拼接成 parent chunk 返回。
 */
@Service
public class ParentDocService {

    private static final Logger log = LoggerFactory.getLogger(ParentDocService.class);

    private final VectorStore vectorStore;

    public ParentDocService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 父文档检索
     *
     * 1. 先用小块检索（topK 小块）
     * 2. 按 source 分组
     * 3. 每个 source 拼接出完整上下文
     * 4. 返回 parent chunks
     */
    public RagResult search(String query, int topK) {
        // 1. 检索小块（检索更多以保证覆盖所有相关 source）
        int childTopK = Math.max(topK * 3, 10);
        var childDocs = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(childTopK).build());

        // 2. 按 source 分组
        Map<String, List<Document>> grouped = new LinkedHashMap<>();
        for (Document doc : childDocs) {
            String source = (String) doc.getMetadata().getOrDefault("source", "unknown");
            grouped.computeIfAbsent(source, k -> new ArrayList<>()).add(doc);
        }

        log.info("📂 父文档检索: {} 个小块 → {} 个来源", childDocs.size(), grouped.size());

        // 3. 从 PgVector 的 vector_store 表中获取每个 source 的完整内容
        // 为了简化，我们从 results 中拼接
        List<ChunkHit> parentHits = new ArrayList<>();
        int rank = 0;

        for (var entry : grouped.entrySet()) {
            String source = entry.getKey();
            List<Document> docs = entry.getValue();

            // 计算这个 source 的最佳得分
            double bestScore = docs.stream()
                .mapToDouble(d -> {
                    Object dist = d.getMetadata().get("distance");
                    double score = 1 - (dist instanceof Number n ? n.doubleValue() : 1.0) / 2.0;
                    return score;
                })
                .max()
                .orElse(0);

            bestScore = Math.round(bestScore * 1000.0) / 1000.0;

            // 拼接该 source 的所有块作为父文档
            String parentContent = docs.stream()
                .map(Document::getText)
                .distinct()
                .collect(Collectors.joining("\n\n---\n\n"));

            rank++;
            parentHits.add(new ChunkHit(rank, bestScore, parentContent, source));
        }

        // 按得分降序排列
        parentHits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        for (int i = 0; i < parentHits.size(); i++) {
            parentHits.get(i).setRank(i + 1);
        }

        // 截取 topK
        List<ChunkHit> top = parentHits.stream().limit(topK).toList();

        return new RagResult()
            .setOriginalQuery(query)
            .setTechnique("parent-doc")
            .setChunkCount(childDocs.size())
            .setUsedStrategies(grouped.keySet().stream().toList())
            .setChunks(top);
    }
}
