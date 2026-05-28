package com.ai.learning.advanced.service;

import com.ai.learning.advanced.model.RagResult;
import com.ai.learning.advanced.model.RagResult.ChunkHit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询重写服务
 *
 * 核心思想：用户口语化查询 → LLM 转为更精确的结构化查询
 *
 * "那个向量数据库怎么用来着？" → "Spring AI PgVector 向量数据库使用配置"
 * "帮我查一下PgVector有啥用" → "PgVector 功能 优势 特点"
 *
 * 重写后的查询通常检索质量更高。
 */
@Service
public class QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteService.class);

    private static final String REWRITE_PROMPT = """
        你是一个搜索查询优化专家。将用户的口语化查询重写为更精确的搜索关键词。

        规则：
        1. 提取核心实体和概念
        2. 去掉口语化词汇（那个、这个、怎么、来着、帮我）
        3. 补充隐含的关键词
        4. 用空格分隔关键词
        5. 保持原文语言（中文/英文）
        6. 只回复重写后的查询文本，不要任何额外说明

        示例：
        用户：那个向量数据库怎么用？ → 向量数据库 使用 配置
        用户：Spring AI 的 RAG pipeline 是啥 → Spring AI RAG pipeline 流程
        用户：帮我查一下PgVector有啥用 → PgVector 功能 优势

        用户：%s
        """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public QueryRewriteService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    public RagResult search(String query, int topK) {
        // 1. 重写查询
        String rewritten = chatClient.prompt()
            .user(String.format(REWRITE_PROMPT, query))
            .call()
            .content();

        if (rewritten == null || rewritten.isBlank()) {
            rewritten = query;
        }
        rewritten = rewritten.trim();

        log.info("✏️  查询重写: '{}' → '{}'", query, rewritten);

        // 2. 用重写后的查询检索
        var docs = vectorStore.similaritySearch(
            SearchRequest.builder().query(rewritten).topK(topK).build());

        List<ChunkHit> hits = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            double dist = doc.getMetadata().get("distance") instanceof Number n
                ? n.doubleValue() : 1.0;
            double score = Math.round((1 - dist / 2.0) * 1000.0) / 1000.0;
            hits.add(new ChunkHit(i + 1, score, doc.getText(),
                (String) doc.getMetadata().getOrDefault("source", "unknown")));
        }

        return new RagResult()
            .setOriginalQuery(query)
            .setTechnique("rewrite")
            .setRewrittenQuery(rewritten)
            .setChunkCount(hits.size())
            .setChunks(hits);
    }
}
