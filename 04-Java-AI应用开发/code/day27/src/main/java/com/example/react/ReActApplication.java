package com.example.react;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReActApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReActApplication.class, args);
        System.out.println("""
            ============================================
              Day 27 - ReAct Agent 手动循环
              演示：http://localhost:8089/react?msg=北京天气
              API: GET /react?msg=<你的问题>
            ============================================
            """);
    }
}
