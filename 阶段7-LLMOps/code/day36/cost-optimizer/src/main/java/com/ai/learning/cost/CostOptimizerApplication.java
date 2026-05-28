package com.ai.learning.cost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CostOptimizerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CostOptimizerApplication.class, args);
    }
}
