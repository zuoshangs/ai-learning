package com.ai.learning.dify.service;

import com.ai.learning.dify.config.DifyConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * Java 客户端 —— 调用 Dify 发布的工作流 API。
 */
@Service
public class DifyClient {

    private final DifyConfig config;
    private final WebClient webClient;

    public DifyClient(DifyConfig config) {
        this.config = config;
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .build();
    }

    /**
     * 调用 Dify 工作流（同步运行）
     */
    public Mono<String> runWorkflow(String query, String userId) {
        return webClient.post()
                .uri("/v1/workflows/run")
                .bodyValue(Map.of(
                        "inputs", Map.of("query", query),
                        "response_mode", "blocking",
                        "user", userId != null ? userId : "java-client"
                ))
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 调用 Dify 对话型应用（流式）
     */
    public Mono<String> sendMessage(String query, String conversationId) {
        return webClient.post()
                .uri("/v1/chat-messages")
                .bodyValue(Map.of(
                        "inputs", Map.of(),
                        "query", query,
                        "response_mode", "blocking",
                        "conversation_id", conversationId != null ? conversationId : "",
                        "user", "java-client"
                ))
                .retrieve()
                .bodyToMono(String.class);
    }
}