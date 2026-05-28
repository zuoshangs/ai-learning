package com.ai.llm.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ProductionGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductionGatewayApplication.class, args);
    }
}
