package com.ai.learning.tools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
        System.out.println("===========================================");
        System.out.println("  Day 19 - Function Calling / Tool Use");
        System.out.println("  http://localhost:8080/chat?msg=北京天气");
        System.out.println("  http://localhost:8080/chat?msg=计算 123x456");
        System.out.println("===========================================");
    }
}
