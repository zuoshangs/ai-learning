package com.ai.learning.service;

import com.ai.learning.dto.BookInfo;
import com.ai.learning.dto.CodeReviewResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 对话服务 — 封装提示词模板 + 结构化输出
 * 
 * 对比 Day 16 的 ChatController，这里把 AI 调用逻辑抽到 Service 层，
 * 并提供三种不同的调用模式。
 */
@Service
public class AiChatService {

    private final ChatClient chatClient;

    public AiChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // =========================================================
    // 模式1：普通对话（与Day 16相同，对比用）
    // =========================================================
    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    // =========================================================
    // 模式2：提示词模板 — 参数化注入
    // =========================================================
    
    /** 代码审查模板 */
    public String codeReview(String language, String code) {
        String template = """
                你是一个资深的%s代码审查专家。
                请审查以下代码，指出所有问题，包括：
                1. 安全漏洞
                2. 性能问题
                3. 代码规范
                4. 可维护性
                5. 潜在Bug
                
                代码：
                ```%s
                %s
                ```
                
                请按严重程度排列，每个问题标注行号和修改建议。
                """.formatted(language, language, code);
        
        return chatClient.prompt()
                .user(template)
                .call()
                .content();
    }

    /** 翻译模板 */
    public String translate(String sourceLang, String targetLang, String text) {
        String template = """
                你是一个专业%s译%s专家。
                
                翻译规则：
                - 保持原文格式和风格
                - 专业术语保留原文并括号标注
                - 不要添加原文没有的内容
                - 只需要输出翻译结果，不要解释
                
                原文（%s）：
                %s
                """.formatted(sourceLang, targetLang, sourceLang, text);
        
        return chatClient.prompt()
                .user(template)
                .call()
                .content();
    }

    /** SQL 生成模板 */
    public String generateSql(String tableSchema, String queryRequest) {
        String template = """
                数据库表结构：
                %s
                
                查询需求：
                %s
                
                请只输出 SQL 语句，不要任何解释。
                考虑使用索引、避免全表扫描。
                """.formatted(tableSchema, queryRequest);
        
        return chatClient.prompt()
                .user(template)
                .call()
                .content();
    }

    // =========================================================
    // 模式3：结构化输出 — AI返回JSON自动映射为Java对象
    // =========================================================

    /**
     * 获取图书信息 — 结构化输出
     * AI返回JSON → Spring AI自动反序列化为 BookInfo 对象
     */
    public BookInfo getBookInfo(String bookName) {
        // BeanOutputConverter 根据BookInfo类的结构生成JSON Schema，
        // 告诉AI需要输出什么格式的JSON
        var converter = new BeanOutputConverter<>(BookInfo.class);
        
        String template = """
                请提供以下书籍的详细信息：
                书名：%s
                
                请按以下 JSON 格式输出（包含所有字段）：
                %s
                """;
        
        String userMessage = template.formatted(bookName, converter.getFormat());
        
        String json = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        return converter.convert(json);
    }

    /**
     * 代码审查 — 结构化输出
     */
    public CodeReviewResult structuredCodeReview(String code) {
        var converter = new BeanOutputConverter<>(CodeReviewResult.class);
        
        String template = """
                请审查以下 Java 代码，按 JSON 格式输出审查结果：
                
                ```java
                %s
                ```
                
                请按以下格式输出：
                %s
                
                注意事项：
                - totalScore: 0-100 分
                - verdict: PASS / MINOR / MAJOR / CRITICAL
                - issues 数组包含所有问题，按 severity 降序排列
                """;
        
        String userMessage = template.formatted(code, converter.getFormat());
        
        String json = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        return converter.convert(json);
    }
}
