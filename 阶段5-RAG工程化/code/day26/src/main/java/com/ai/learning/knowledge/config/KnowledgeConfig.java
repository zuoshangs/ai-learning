package com.ai.learning.knowledge.config;

import org.springframework.context.annotation.Configuration;

/**
 * 知识库 V2 配置
 * 继承 Day 24 的混合检索配置能力
 */
@Configuration
public class KnowledgeConfig {

    // Spring AI AutoConfiguration 自动处理：
    // - ChatClient.Builder → ChatClient
    // - VectorStore (PgVector)
    // - JdbcTemplate
    //
    // application.yml 中已配置：
    // - spring.ai.openai.* (DeepSeek API)
    // - spring.ai.vectorstore.pgvector.*
    // - spring.datasource.*
}
