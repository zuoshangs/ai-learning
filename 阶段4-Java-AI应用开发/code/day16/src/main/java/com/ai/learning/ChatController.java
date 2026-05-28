package com.ai.learning;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

/**
 * AI 对话控制器
 * 
 * 这是 Spring AI 最简单的入口 —— 一行代码调通 AI 对话。
 * ChatClient.Builder 由 spring-ai-openai 自动配置，
 * 基于 application.yml 中的 api-key / base-url / model 等设置。
 */
@RestController
public class ChatController {

    private final ChatClient chatClient;

    /**
     * 构造注入 ChatClient
     * 
     * Spring AI 的自动配置会创建 ChatClient.Builder，
     * 我们只需要注入它并调用 build() 即可。
     */
    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 最简单的对话接口
     * 
     * 使用 GET 方便测试，生产环境建议用 POST。
     * 
     * 测试方法：
     *   curl "http://localhost:8080/chat?message=你好"
     * 
     * @param message 用户输入的消息
     * @return AI 的文本回复
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好，请介绍一下你自己") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
