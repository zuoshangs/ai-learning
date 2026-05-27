package com.ai.learning.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 企业知识库 V2 — 生产级 RAG 集成系统
 * <p>
 * 集成 Day 22-25 全部技术：
 * - 异步文档摄取 + 切分 + 向量化
 * - 查询重写（Day 25）
 * - HyDE 假设回答检索（Day 25）
 * - 混合检索 RRF 融合（Day 24）
 * - 父文档检索（Day 25）
 * - LLM Reranker（Day 24）
 * - V1 vs V2 评估对比
 */
@SpringBootApplication
public class KnowledgeBaseApplication {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeBaseApplication.class, args);
        log.info("""
            ╔════════════════════════════════════════╗
            ║  企业知识库 V2 启动完成                   ║
            ║  端口: 8088                             ║
            ║  集成: Rewrite+Hybrid+Rerank+ParentDoc  ║
            ║                                        ║
            ║  接口:                                  ║
            ║  GET  /api/knowledge/health             ║
            ║  POST /api/knowledge/ingest             ║
            ║  POST /api/knowledge/search/v2          ║
            ║  GET  /api/knowledge/search?mode=v2     ║
            ║  GET  /api/knowledge/evaluate/quick     ║
            ╚════════════════════════════════════════╝
            """);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
