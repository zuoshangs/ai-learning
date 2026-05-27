package com.ai.learning.knowledge.search;

import com.ai.learning.knowledge.model.SearchResultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 关键词检索服务（Day 24 继承）
 * <p>
 * 基于 PostgreSQL FTS (Full Text Search) 的 BM25 风格检索
 * 利用 PgVector 表中存储的文档文本
 */
@Service
public class KeywordSearchService {

    private static final Logger log = LoggerFactory.getLogger(KeywordSearchService.class);

    private final JdbcTemplate jdbcTemplate;

    public KeywordSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 关键词检索 — 使用 PostgreSQL tsvector
     */
    public List<SearchResultItem> search(String query, int topK) {
        long t0 = System.currentTimeMillis();
        List<SearchResultItem> results = new ArrayList<>();

        try {
            // 使用 PostgreSQL 的 to_tsvector + plainto_tsquery 实现 FTS
            String sql = """
                SELECT id, content, metadata->>'source' as source,
                       ts_rank(to_tsvector('simple', content),
                               plainto_tsquery('simple', ?)) as rank
                FROM vector_store
                WHERE to_tsvector('simple', content) @@ plainto_tsquery('simple', ?)
                ORDER BY rank DESC
                LIMIT ?
                """;

            jdbcTemplate.query(sql, (rs) -> {
                String chunkId = rs.getString("id");
                String content = rs.getString("content");
                String source = rs.getString("source");
                double rank = rs.getDouble("rank");

                results.add(new SearchResultItem()
                    .setScore(Math.round(rank * 1000.0) / 1000.0)
                    .setContent(content)
                    .setContentLength(content != null ? content.length() : 0)
                    .setSource(source != null ? source : "unknown")
                    .setMethod("keyword"));
            }, query, query, topK);

        } catch (Exception e) {
            log.warn("关键词检索降级（表结构可能不支持 FTS）: {}", e.getMessage());
            // 降级 — 用 SQL LIKE 匹配
            String sql = """
                SELECT id, content, metadata->>'source' as source
                FROM vector_store
                WHERE content ILIKE ?
                LIMIT ?
                """;
            jdbcTemplate.query(sql, (rs) -> {
                String content = rs.getString("content");
                String source = rs.getString("source");
                results.add(new SearchResultItem()
                    .setScore(0.5)
                    .setContent(content)
                    .setContentLength(content != null ? content.length() : 0)
                    .setSource(source != null ? source : "unknown")
                    .setMethod("keyword"));
            }, "%" + query + "%", topK);
        }

        // 排序
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setRank(i + 1);
        }

        long t1 = System.currentTimeMillis();
        log.info("🔤 关键词检索: {} 条结果 [{}ms]", results.size(), t1 - t0);
        return results;
    }
}
