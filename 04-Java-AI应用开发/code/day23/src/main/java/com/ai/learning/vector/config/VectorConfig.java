package com.ai.learning.vector.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置
 */
@Configuration
public class VectorConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("你是知识库问答助手。基于提供的参考文档回答用户问题。" +
                           "如果文档中没有足够信息，请明确告知。" +
                           "用简洁清晰的中文回答，引用文档中的具体内容。")
            .build();
    }
}
