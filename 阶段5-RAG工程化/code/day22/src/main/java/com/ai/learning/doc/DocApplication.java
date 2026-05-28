package com.ai.learning.doc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Day 22: 文档加载与智能切分 — 启动类
 *
 * Spring AI 文档处理管线：
 *   文档读取 (Tika/TextReader)
 *   → 策略化切分 (Token/Paragraph/Recursive)
 *   → 元数据提取
 *   → 统计对比
 */
@SpringBootApplication
public class DocApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocApplication.class, args);
    }
}
