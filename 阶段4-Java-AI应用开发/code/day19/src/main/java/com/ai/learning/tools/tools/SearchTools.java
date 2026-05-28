package com.ai.learning.tools.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 信息搜索工具 — 模拟搜索引擎
 * 
 * 大模型的知识有截止日期，无法获取最新信息。
 * 通过工具调用，可以搜索实时信息（如新闻、最新产品信息等）。
 * 
 * 此处为模拟实现，真实场景可对接 Google/Bing/百度搜索 API。
 */
@Component
public class SearchTools {

    /**
     * 搜索最新信息
     */
    @Tool(description = "搜索最新信息或新闻，返回指定查询的相关结果列表（模拟搜索）")
    public String searchWeb(String query) {
        String time = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        
        // 根据关键词返回模拟结果
        String results;
        if (query.contains("新闻") || query.contains("news")) {
            results = """
                📰 最新新闻搜索结果
                ─────────────────
                1. [AI行业] DeepSeek发布新一代大模型，性能对标GPT-4o
                2. [科技] Spring AI 1.0.0 正式发布，Java AI开发进入新阶段
                3. [国际] 全球AI治理框架达成初步共识
                4. [产业] 国内多家企业宣布接入国产大模型API
                5. [学术] 研究表明：工具调用可显著提升LLM推理准确率
                """;
        } else if (query.contains("Java") || query.contains("Spring") || query.contains("spring")) {
            results = """
                🔍 Java/Spring 搜索结果
                ──────────────────────
                1. Spring AI 1.0.0-M6 发布，新增 @Tool 注解支持
                2. Java 21 正式成为LTS版本，虚拟线程性能提升显著
                3. Spring Boot 3.4.4 发布，升级关键依赖
                4. Maven Central 新增AI工具库搜索量同比增长300%
                5. 企业级Java AI应用最佳实践发布白皮书
                """;
        } else if (query.contains("天气") || query.contains("weather")) {
            results = """
                🌤️ 天气相关搜索结果
                ─────────────────
                1. 中央气象台发布高温预警，南方多地气温超35°C
                2. 今年第3号台风\"格美\"预计周末登陆
                3. 北方地区未来一周降温明显，注意添衣
                4. 全国空气质量整体良好，局部轻度污染
                """;
        } else {
            results = """
                🔍 搜索：%s
                ─────────────────
                1. 找到 3 条相关结果（模拟数据）
                2. AI领域的工具调用技术正在快速发展
                3. 建议使用更具体的关键词获取更精准结果
                """.formatted(query);
        }
        
        return """
            🔎 搜索完成
            ─────────
            查询词：「%s」
            搜索时间：%s
            
            %s
            """.formatted(query, time, results);
    }

    /**
     * 获取当前时间
     */
    @Tool(description = "获取当前的日期和时间")
    public String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        return """
            🕐 当前时间
            ─────────
            日期：%s
            时间：%s
            星期：%s
            """.formatted(
                now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                switch (now.getDayOfWeek()) {
                    case MONDAY -> "一";
                    case TUESDAY -> "二";
                    case WEDNESDAY -> "三";
                    case THURSDAY -> "四";
                    case FRIDAY -> "五";
                    case SATURDAY -> "六";
                    case SUNDAY -> "日";
                }
            );
    }
}
