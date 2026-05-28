package com.ai.learning.knowledge.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 向量化服务 — 将块存入 PgVector
 * 继承 Day 23 的入库能力
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final VectorStore vectorStore;

    public EmbeddingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 批量将分块写入向量库
     */
    public int storeChunks(List<com.ai.learning.knowledge.model.DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("没有块需要入库");
            return 0;
        }

        List<Document> docs = chunks.stream()
            .map(chunk -> {
                Map<String, Object> metadata = new java.util.HashMap<>();
                metadata.put("docId", chunk.getDocId());
                metadata.put("chunkIndex", chunk.getChunkIndex());
                metadata.put("parentDocId", chunk.getParentDocId());
                metadata.put("totalParentChunks", chunk.getTotalParentChunks());
                metadata.put("source", chunk.getSource());
                metadata.put("id", chunk.getId());

                return new Document(chunk.getContent(), metadata);
            })
            .toList();

        vectorStore.add(docs);
        log.info("💾 入库完成: {} 个块", docs.size());
        return docs.size();
    }

    /**
     * 语义检索
     */
    public List<Document> search(String query, int topK) {
        long t0 = System.currentTimeMillis();
        var docs = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(topK).build());
        log.info("🔍 语义检索: {} 结果 [{}ms]", docs.size(), System.currentTimeMillis() - t0);
        return docs;
    }

    /**
     * 获取父文档的内容（收集同一 parent 下的所有 chunks）
     * 简易实现：重新检索 parent 下的所有块并拼接
     */
    public String getParentContent(String parentDocId, List<Document> children) {
        StringBuilder sb = new StringBuilder();
        for (Document doc : children) {
            String pdid = (String) doc.getMetadata().getOrDefault("parentDocId", "");
            if (pdid.equals(parentDocId)) {
                if (sb.length() > 0) sb.append("\n\n---\n\n");
                sb.append(doc.getText());
            }
        }
        return sb.toString();
    }
}
