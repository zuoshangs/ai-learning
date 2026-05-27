package com.ai.learning.tools.config;

import com.ai.learning.tools.tools.CalculatorTools;
import com.ai.learning.tools.tools.SearchTools;
import com.ai.learning.tools.tools.WeatherTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置 — 通过 MethodToolCallbackProvider 注册 @Tool 方法
 * 
 * Spring AI 1.0.0-M6 的 @Tool 注解需要 MethodToolCallbackProvider 来扫描
 * 并注册 @Component 类中的 @Tool 方法为 AI 可调用的工具。
 */
@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider weatherToolProvider(WeatherTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    public ToolCallbackProvider calculatorToolProvider(CalculatorTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    public ToolCallbackProvider searchToolProvider(SearchTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider... providers) {
        return builder
            .defaultSystem("""
                你是一个智能助手，可以使用多种工具来帮助用户。
                当你需要实时信息、精确计算或执行特定操作时，
                请主动使用对应工具。
                
                可用的工具：
                - getWeather：查询指定城市的天气
                - getForecast：获取未来3天的预报
                - calculate：执行数学计算，如 "123 * 456"
                - convertUnit：单位换算（长度/重量/温度）
                - searchWeb：搜索最新信息
                - getCurrentTime：获取当前日期时间
                
                根据用户的问题，选择最合适的工具来回答。
                如果用户问的问题不需要工具，直接用你的知识回答。
                """)
            .defaultTools(providers)
            .build();
    }
}
