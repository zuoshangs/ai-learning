package com.ai.learning.knowledge.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 查询重写服务（Day 25 继承）
 * <p>
 * 用户口语化查询 → LLM 转为结构化关键词
 * 例如: "那个向量数据库怎么用来着？" → "向量数据库 使用 配置"
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
        用户：那个数据库怎么用？ → 数据库 使用 配置
        用户：什么是 RAG → RAG 概念 原理
        用户：PgVector 能干啥 → PgVector 功能 用途

        用户：%s
        """;

    private final ChatClient chatClient;

    public QueryRewriteService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 重写查询
     *
     * @param query 原始查询
     * @return 重写后的查询（失败则返回原始查询）
     */
    public String rewrite(String query) {
        try {
            String rewritten = chatClient.prompt()
                .user(String.format(REWRITE_PROMPT, query))
                .call()
                .content();

            if (rewritten == null || rewritten.isBlank()) {
                return query;
            }

            rewritten = rewritten.trim().replaceAll("[\"\"'']", "");
            log.info("✏️  查询重写: '{}' → '{}'", query, rewritten);
            return rewritten;

        } catch (Exception e) {
            log.warn("查询重写失败，使用原始查询: {}", e.getMessage());
            return query;
        }
    }
}
