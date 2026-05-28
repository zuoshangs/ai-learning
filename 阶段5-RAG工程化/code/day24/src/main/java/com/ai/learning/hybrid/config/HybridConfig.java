package com.ai.learning.hybrid.config;

import com.ai.learning.hybrid.service.KeywordSearchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HybridConfig {

    private static final Logger log = LoggerFactory.getLogger(HybridConfig.class);

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * 启动时自动初始化关键词索引
     */
    @Bean
    public CommandLineRunner initKeywordIndex(KeywordSearchService keywordSearch) {
        return args -> {
            keywordSearch.initKeywordIndex();
            int count = keywordSearch.syncFromVectorStore();
            log.info("✅ 关键词索引初始化完成: {} 条文档", count);
        };
    }
}
