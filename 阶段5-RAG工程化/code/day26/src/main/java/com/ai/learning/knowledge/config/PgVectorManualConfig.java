package com.ai.learning.knowledge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 手动配置的 PgVector 向量存储
 * <p>
 * 由于排除了自动配置，手动创建 Bean
 */
@Configuration
public class PgVectorManualConfig {

    private static final Logger log = LoggerFactory.getLogger(PgVectorManualConfig.class);

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return new VectorStore() {
            @Override
            public void add(List<Document> documents) {
                String sql = "INSERT INTO vector_store (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?::vector) " +
                    "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding";

                for (Document doc : documents) {
                    float[] embedding = embeddingModel.embed(doc);
                    String vectorStr = "[" + java.util.stream.IntStream.range(0, embedding.length)
                        .mapToObj(i -> String.format("%.6f", embedding[i]))
                        .collect(Collectors.joining(",")) + "]";

                    String id = (String) doc.getMetadata().getOrDefault("id", UUID.randomUUID().toString());
                    // 确保是有效 UUID（chunk ID 可能是 docId-chunk-N 格式）
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(id);
                    } catch (IllegalArgumentException e) {
                        uuid = UUID.nameUUIDFromBytes(id.getBytes());
                    }
                    String metaJson = "{}";
                    try {
                        // Build JSON string from metadata
                        StringBuilder meta = new StringBuilder("{");
                        boolean first = true;
                        for (Map.Entry<String, Object> e : doc.getMetadata().entrySet()) {
                            if (e.getValue() != null && !e.getKey().equals("id")) {
                                if (!first) meta.append(",");
                                meta.append("\"").append(escapeJson(e.getKey())).append("\":\"")
                                    .append(escapeJson(e.getValue().toString())).append("\"");
                                first = false;
                            }
                        }
                        meta.append("}");
                        metaJson = meta.toString();
                    } catch (Exception ex) {
                        log.warn("元数据 JSON 构建失败: {}", ex.getMessage());
                    }

                    jdbcTemplate.update(sql, uuid, doc.getText(), metaJson, vectorStr);
                }
                log.info("💾 PgVector 入库: {} 个文档", documents.size());
            }

            @Override
            public void delete(List<String> idList) {
                if (idList.isEmpty()) return;
                String placeholders = idList.stream().map(s -> "?").collect(Collectors.joining(","));
                jdbcTemplate.update("DELETE FROM vector_store WHERE id::text IN (" + placeholders + ")", idList.toArray());
                log.info("🗑️ 删除 {} 个文档", idList.size());
            }

            @Override
            public void delete(Filter.Expression filterExpression) {
                if (filterExpression != null) {
                    log.warn("Filter 表达式删除暂不支持");
                }
            }

            @Override
            public List<Document> similaritySearch(SearchRequest request) {
                String query = request.getQuery();
                int topK = request.getTopK();

                // 计算查询的嵌入向量
                float[] queryEmbedding = embeddingModel.embed(query);
                String vectorStr = "[" + java.util.stream.IntStream.range(0, queryEmbedding.length)
                    .mapToObj(i -> String.format("%.6f", queryEmbedding[i]))
                    .collect(Collectors.joining(",")) + "]";

                // 余弦距离搜索
                String sql = """
                    SELECT id, content, metadata, 
                           (embedding <=> ?::vector) as distance
                    FROM vector_store
                    ORDER BY distance ASC
                    LIMIT ?
                    """;

                List<Document> results = new ArrayList<>();
                jdbcTemplate.query(sql, (ResultSet rs) -> {
                    do {
                        String id = rs.getString("id");
                        String content = rs.getString("content");
                        String metaJson = rs.getString("metadata");
                        double distance = rs.getDouble("distance");

                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("distance", distance);
                        metadata.put("id", id);
                        if (metaJson != null && !metaJson.isEmpty() && !metaJson.equals("{}")) {
                            try {
                                // Simple JSON parsing
                                String json = metaJson.replaceAll("[{}\"]", "");
                                for (String pair : json.split(",")) {
                                    String[] kv = pair.split(":", 2);
                                    if (kv.length == 2) metadata.put(kv[0].trim(), kv[1].trim());
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                        }

                        results.add(new Document(content, metadata));
                    } while (rs.next());
                }, vectorStr, topK);

                log.info("🔍 PgVector 检索: {} 结果", results.size());
                return results;
            }

            private String escapeJson(String s) {
                return s.replace("\\", "\\\\").replace("\"", "\\\"");
            }
        };
    }
}
