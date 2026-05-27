package com.ai.learning.vector.service;

import com.ai.learning.vector.model.SearchResponse;
import com.ai.learning.vector.model.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量检索服务
 *
 * 四种检索模式：
 *   1. top-k     — 标准 Top-K 语义检索
 *   2. threshold — 带相似度阈值的检索
 *   3. window    — 上下文窗口（命中块 + 前后块）
 *   4. compare   — 策略对比（不同策略的结果并列展示）
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public VectorSearchService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    /**
     * 检索主入口
     *
     * @param query 查询文本
     * @param mode  检索模式: top-k / threshold / window / compare / rag
     * @param topK  返回结果数
     */
    public SearchResponse search(String query, String mode, int topK, double threshold) {
        // 1. 执行检索
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build());

        // 2. 根据模式处理
        return switch (mode) {
            case "threshold" -> buildResponse(query, mode, docs, threshold, topK);
            case "window" -> windowSearch(query, docs, topK);
            case "compare" -> compareStrategies(query, topK);
            case "rag" -> ragSearch(query, docs, topK);
            default -> buildResponse(query, "top-k", docs, 0, topK);
        };
    }

    // ====== 模式 1: top-k 标准检索 ======

    private SearchResponse buildResponse(String query, String mode,
                                          List<Document> docs, double threshold, int topK) {
        SearchResponse resp = new SearchResponse()
            .setQuery(query)
            .setMode(mode)
            .setTotalResults(docs.size());

        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            results.add(new SearchResult()
                .setRank(i + 1)
                .setScore(similarityScore(doc))
                .setContent(doc.getText())
                .setContentLength(doc.getText().length())
                .setSource((String) doc.getMetadata().getOrDefault("source", "unknown"))
                .setChunkStrategy((String) doc.getMetadata().getOrDefault("chunkStrategy", "default"))
                .setChunkId("chunk-" + doc.getMetadata().getOrDefault("chunkIndex", i)));
        }
        resp.setResults(results);
        return resp;
    }

    // ====== 模式 2: 带阈值 ======
    // 与 top-k 相同但由前端传入 threshold，实际在 search() 里已应用

    // ====== 模式 3: 上下文窗口 ======

    private SearchResponse windowSearch(String query, List<Document> initialDocs, int windowSize) {
        // 先用少量结果找到 best match，然后展示它附近的块
        // 简化实现：只需要 Top-K 结果及每个结果后拼接相邻块信息
        return buildResponse(query, "window", initialDocs, 0, initialDocs.size());
    }

    // ====== 模式 4: 策略对比 ======

    private SearchResponse compareStrategies(String query, int topK) {
        // 1. 检索 topK * 3 确保覆盖所有策略
        List<Document> allDocs = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(topK * 3).build());

        // 2. 按策略分组
        Map<String, List<Document>> grouped = new LinkedHashMap<>();
        for (Document doc : allDocs) {
            String strategy = (String) doc.getMetadata().getOrDefault("chunkStrategy", "unknown");
            grouped.computeIfAbsent(strategy, k -> new ArrayList<>()).add(doc);
        }

        // 3. 每组取 topK
        List<SearchResult> allResults = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<Document> docs = entry.getValue().stream().limit(topK).toList();
            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                allResults.add(new SearchResult()
                    .setRank(i + 1)
                    .setScore(similarityScore(doc))
                    .setContent(doc.getText())
                    .setContentLength(doc.getText().length())
                    .setSource((String) doc.getMetadata().getOrDefault("source", "unknown"))
                    .setChunkStrategy(entry.getKey())
                    .setChunkId("chunk-" + doc.getMetadata().getOrDefault("chunkIndex", 0)));
            }
        }

        return new SearchResponse()
            .setQuery(query)
            .setMode("compare")
            .setTotalResults(allResults.size())
            .setResults(allResults);
    }

    // ====== 模式 5: RAG 增强 ======

    private SearchResponse ragSearch(String query, List<Document> docs, int topK) {
        // 先用标准 top-k 检索
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            results.add(new SearchResult()
                .setRank(i + 1)
                .setScore(similarityScore(doc))
                .setContent(doc.getText())
                .setContentLength(doc.getText().length())
                .setSource((String) doc.getMetadata().getOrDefault("source", "unknown"))
                .setChunkStrategy((String) doc.getMetadata().getOrDefault("chunkStrategy", "default"))
                .setChunkId("chunk-" + doc.getMetadata().getOrDefault("chunkIndex", i)));
        }

        // 生成 RAG 回答
        String answer = chatClient.prompt()
            .user(query)
            .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(0.3)
                .build()))
            .call()
            .content();

        return new SearchResponse()
            .setQuery(query)
            .setMode("rag")
            .setTotalResults(results.size())
            .setResults(results)
            .setRagAnswer(answer);
    }

    // ====== 辅助 ======

    /**
     * 从文档元数据提取相似度分数
     * PgVector 使用余弦距离（0=最相似，2=最不相似）
     * 转换为相似度（1=最相似，0=最不相似）
     */
    private double similarityScore(Document doc) {
        Object dist = doc.getMetadata().get("distance");
        if (dist instanceof Number n) {
            return Math.round((1 - n.doubleValue() / 2.0) * 1000.0) / 1000.0;
        }
        return 0;
    }
}
