package com.ai.learning.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 服务 — 文档加载、向量检索、增强生成
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Value("classpath:documents/*")
    private Resource[] documentResources;

    public RagService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    /**
     * 加载文档 → 切分 → 向量化 → 入库
     */
    public String loadDocuments() {
        try {
            List<Document> allDocs = new ArrayList<>();
            int fileCount = 0;

            for (Resource resource : documentResources) {
                String filename = resource.getFilename();
                if (filename == null) continue;

                log.info("加载文档: {}", filename);

                // 使用 TextReader 读取 .txt 文件
                if (filename.endsWith(".txt")) {
                    TextReader reader = new TextReader(resource);
                    reader.setCharset(java.nio.charset.StandardCharsets.UTF_8);
                    List<Document> docs = reader.get();
                    docs.forEach(doc -> doc.getMetadata().put("source", filename));
                    allDocs.addAll(docs);
                    fileCount++;
                } else {
                    log.warn("跳过不支持的文件类型: {}", filename);
                }
            }

            if (allDocs.isEmpty()) {
                return "❌ 未找到可加载的文档";
            }

            log.info("原始文档数: {}", allDocs.size());

            // 切分文档
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.apply(allDocs);

            log.info("切分后文档块数: {}", chunks.size());

            // 向量化 + 入库
            vectorStore.add(chunks);

            return """
                ✅ 文档加载完成
                ─────────────────
                加载文件数：%d 个
                原始文档：%d 篇
                切分块数：%d 块
                向量维度：1024
                距离算法：余弦相似度
                索引类型：HNSW
                """.formatted(fileCount, allDocs.size(), chunks.size());

        } catch (Exception e) {
            log.error("文档加载失败", e);
            return "❌ 文档加载失败: " + e.getMessage();
        }
    }

    /**
     * RAG 问答 — 检索 + 增强 + 生成
     */
    public String ask(String question) {
        // 使用 QuestionAnswerAdvisor 实现标准 RAG 流程
        return chatClient.prompt()
            .user(question)
            .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
                .topK(3)          // 返回 Top-3 最相关文档块
                .similarityThreshold(0.5)  // 相似度阈值
                .build()))
            .call()
            .content();
    }

    /**
     * 纯检索 — 查看向量库中与问题最相关的文档块
     */
    public String search(String query) {
        try {
            SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(5)
                .build();

            List<Document> results = vectorStore.similaritySearch(request);

            if (results.isEmpty()) {
                return "🔍 未找到相关文档";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("🔍 搜索结果（Top-%d）\n".formatted(results.size()));
            sb.append("─".repeat(50)).append("\n");

            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                String source = (String) doc.getMetadata().getOrDefault("source", "unknown");
                double score = (double) doc.getMetadata()
                    .getOrDefault("distance", 0.0);

                sb.append("\n").append(i + 1).append(". [").append(source).append("]");
                sb.append(" (相似度: ").append(String.format("%.2f", 1 - score)).append(")\n");
                sb.append(doc.getText().trim()).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("搜索失败", e);
            return "❌ 搜索失败: " + e.getMessage();
        }
    }

    /**
     * 清空向量库
     */
    public String clearVectorStore() {
        try {
            vectorStore.delete(new ArrayList<>());  // delete all
            return "✅ 向量库已清空";
        } catch (Exception e) {
            return "❌ 清空失败: " + e.getMessage();
        }
    }
}
