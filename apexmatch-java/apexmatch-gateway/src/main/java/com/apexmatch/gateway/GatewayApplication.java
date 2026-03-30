package com.apexmatch.gateway;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ApexMatch 网关启动类。
 *
 * @author luka
 * @since 2025-03-26
 */
@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(info = @Info(
        title = "ApexMatch Trading API",
        version = "1.0.0",
        description = "高性能合约交易平台 REST API"
))
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
