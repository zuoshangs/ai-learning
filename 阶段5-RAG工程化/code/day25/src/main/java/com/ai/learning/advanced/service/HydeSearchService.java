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
 * HyDE (Hypothetical Document Embedding) 服务
 *
 * 核心思想：
 *   1. 让 LLM 根据用户问题先生成一个"假设的理想回答"
 *   2. 用这个假设回答去向量库检索
 *   3. 因为假设回答与真实文档更相似，检索准确率更高
 *
 * 原理：假设回答的向量空间位置更接近真实相关文档，
 * 比直接用问题检索更容易命中目标。
 */
@Service
public class HydeSearchService {

    private static final Logger log = LoggerFactory.getLogger(HydeSearchService.class);

    private static final String HYDE_PROMPT = """
        你是一个领域专家。根据用户的问题，假设你有一份完美的参考文档，
        请写出这篇文档中可能包含的段落。

        要求：
        1. 假设回答的语气要像真实的参考文档（用第三人称、客观陈述）
        2. 包含具体的技术名词和术语
        3. 长度在 100-200 字之间
        4. 只输出假设文档内容，不要额外说明

        用户问题：%s
        """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public HydeSearchService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    public RagResult search(String query, int topK) {
        // 1. 生成假设回答
        String hypothesis = chatClient.prompt()
            .user(String.format(HYDE_PROMPT, query))
            .call()
            .content();

        if (hypothesis == null || hypothesis.isBlank()) {
            hypothesis = query;
        }
        hypothesis = hypothesis.trim();

        log.info("🤔 HyDE 假设回答 ({} 字): {}...",
            hypothesis.length(),
            hypothesis.substring(0, Math.min(60, hypothesis.length())));

        // 2. 用假设回答检索
        var docs = vectorStore.similaritySearch(
            SearchRequest.builder().query(hypothesis).topK(topK).build());

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
            .setTechnique("hyde")
            .setHydeHypothesis(hypothesis)
            .setChunkCount(hits.size())
            .setChunks(hits);
    }
}
