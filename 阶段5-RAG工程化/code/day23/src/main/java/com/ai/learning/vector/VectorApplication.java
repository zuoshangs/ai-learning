package com.ai.learning.vector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Day 23: 向量化入库 + 相似度检索
 *
 * 核心管线：文档读取 → 策略化切分 → Embedding → PgVector 入库
 *          → 多模式检索（Top-K/阈值上下文窗口/策略对比）
 */
@SpringBootApplication
public class VectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(VectorApplication.class, args);
    }
}
