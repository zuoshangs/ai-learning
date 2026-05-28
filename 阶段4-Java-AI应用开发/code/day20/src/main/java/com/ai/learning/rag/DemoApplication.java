package com.ai.learning.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("============================================");
        System.out.println("  Day 20 - Spring AI RAG 实战");
        System.out.println("  PgVector + DeepSeek Embedding + RAG");
        System.out.println("  ");
        System.out.println("  POST /rag/load   — 加载文档到向量库");
        System.out.println("  GET  /rag/ask    — RAG 问答");
        System.out.println("  GET  /rag/search — 纯检索（不含生成）");
        System.out.println("============================================");
    }
}
