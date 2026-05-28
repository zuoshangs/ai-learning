package com.ai.learning.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 核心配置
 * 
 * Spring AI 的 PgVectorStore 通过 autoconfigure 自动创建（基于 application.yml），
 * EmbeddingModel 也由 spring-ai-openai-spring-boot-starter 自动配置。
 * 这里只需创建 ChatClient。
 */
@Configuration
public class RagConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("""
                你是一个知识库问答助手。你的工作流程：
                
                1. 系统会为你提供相关的参考文档片段（context）
                2. 基于这些文档片段回答用户的问题
                3. 如果文档中没有足够信息，明确告知用户你不知道
                4. 引用参考文档中的具体内容来支撑你的回答
                5. 用简洁清晰的中文回答
                
                参考文档：
                {question_answer_context}
                """)
            .build();
    }
}
