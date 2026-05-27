package com.ai.learning.knowledge.search;

import com.ai.learning.knowledge.model.SearchResultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义检索服务（Day 23 继承）
 * <p>
 * 基于 PgVector 的余弦相似度检索
 */
@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final VectorStore vectorStore;

    public VectorSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 语义检索
     */
    public List<SearchResultItem> search(String query, int topK) {
        long t0 = System.currentTimeMillis();
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(topK * 2).build());
        long t1 = System.currentTimeMillis();

        List<SearchResultItem> results = new ArrayList<>();
        for (int i = 0; i < Math.min(docs.size(), topK); i++) {
            Document doc = docs.get(i);
            double dist = doc.getMetadata().get("distance") instanceof Number n
                ? n.doubleValue() : 1.0;
            double score = Math.round((1 - dist / 2.0) * 1000.0) / 1000.0;

            results.add(new SearchResultItem()
                .setRank(i + 1)
                .setScore(score)
                .setContent(doc.getText())
                .setSource((String) doc.getMetadata().getOrDefault("source", "unknown"))
                .setMethod("semantic"));
        }

        log.info("🔍 语义检索: {} 条结果 [{}ms]", results.size(), t1 - t0);
        return results;
    }

    /**
     * 获取文档块的元数据（用于父文档检索）
     */
    public String getParentDocId(Document doc) {
        return (String) doc.getMetadata().getOrDefault("parentDocId", "");
    }

    public int getTotalParentChunks(Document doc) {
        return doc.getMetadata().get("totalParentChunks") instanceof Number n
            ? n.intValue() : 1;
    }
}
