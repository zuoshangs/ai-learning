package com.ai.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI Demo — 第一个 AI 对话应用
 * 
 * Day 16：Spring AI 环境搭建
 * 启动后访问 http://localhost:8080/chat?message=你好
 */
@SpringBootApplication
public class SpringAiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDemoApplication.class, args);
    }
}
