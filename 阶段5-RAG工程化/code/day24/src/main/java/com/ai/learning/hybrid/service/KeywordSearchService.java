package com.ai.learning.hybrid.service;

import com.ai.learning.hybrid.model.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 关键词检索 — 基于 PostgreSQL 内置全文搜索 (tsvector)
 *
 * 使用 PostgreSQL 的 to_tsvector / to_tsquery 做中文分词+关键词检索，
 * 无需安装 Elasticsearch。
 *
 * 核心查询：
 *   SELECT content, metadata->>'source' AS source,
 *          ts_rank(to_tsvector('chinese', content), to_tsquery('chinese', ?)) AS score
 *   FROM keyword_index
 *   WHERE to_tsvector('chinese', content) @@ to_tsquery('chinese', ?)
 *   ORDER BY score DESC LIMIT ?
 */
@Service
public class KeywordSearchService {

    private static final Logger log = LoggerFactory.getLogger(KeywordSearchService.class);

    private final JdbcTemplate jdbc;

    public KeywordSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 初始化关键词索引表
     * 从 vector_store 表同步数据到 keyword_index 表
     */
    public void initKeywordIndex() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS keyword_index (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                content TEXT NOT NULL,
                source TEXT,
                tsvector_col TSVECTOR
            )
        """);
        log.info("✅ keyword_index 表已就绪");
    }

    /**
     * 从 PgVector 的 vector_store 同步文档到 keyword_index
     */
    public int syncFromVectorStore() {
        // 清空重建（开发环境）
        jdbc.update("TRUNCATE keyword_index");

        var rows = jdbc.queryForList(
            "SELECT content, metadata->>'source' AS source FROM vector_store");
        int count = 0;

        for (var row : rows) {
            String content = (String) row.get("content");
            String source = (String) row.get("source");
            if (content == null || content.isBlank()) continue;

            jdbc.update(
                "INSERT INTO keyword_index (content, source, tsvector_col) VALUES (?, ?, to_tsvector('simple', ?))",
                content, source, content);
            count++;
        }

        // 创建 GIN 索引（加速 FTS）
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_keyword_tsv ON keyword_index USING GIN(tsvector_col)");

        log.info("📦 同步 {} 条文档到关键词索引", count);
        return count;
    }

    /**
     * 关键词检索
     */
    public List<SearchResult> search(String query, int topK) {
        long t0 = System.currentTimeMillis();

        // 将用户查询转为 tsquery 格式
        String tsQuery = toTsQuery(query);

        List<SearchResult> results = new ArrayList<>();

        try {
            var rows = jdbc.queryForList(
                """
                SELECT content, source,
                       ts_rank(tsvector_col, to_tsquery('simple', ?)) AS score
                FROM keyword_index
                WHERE tsvector_col @@ to_tsquery('simple', ?)
                ORDER BY score DESC
                LIMIT ?
                """,
                tsQuery, tsQuery, topK);

            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                double score = ((Number) row.get("score")).doubleValue();

                results.add(new SearchResult()
                    .setRank(i + 1)
                    .setScore(Math.round(score * 1000.0) / 1000.0)
                    .setContent((String) row.get("content"))
                    .setContentLength(((String) row.get("content")).length())
                    .setSource((String) row.get("source"))
                    .setMethod("keyword"));
            }
        } catch (Exception e) {
            log.warn("关键词检索出错: {}", e.getMessage());
        }

        long t1 = System.currentTimeMillis();
        log.info("🔑 关键词检索: {} 条结果 [{}ms]", results.size(), t1 - t0);
        return results;
    }

    /**
     * 将用户查询转换为 PostgreSQL tsquery 格式
     *
     * "PgVector 的优点" → "PgVector & 优点"
     * "Spring AI 支持哪些向量数据库" → "Spring & AI & 支持 & 向量 & 数据库"
     */
    private String toTsQuery(String query) {
        // 提取中英文单词
        var words = Pattern.compile("[\\w\\u4e00-\\u9fff]+")
            .matcher(query)
            .results()
            .map(m -> m.group().toLowerCase())
            .filter(w -> w.length() > 1)
            .toList();

        if (words.isEmpty()) return query;
        return String.join(" & ", words);
    }
}
