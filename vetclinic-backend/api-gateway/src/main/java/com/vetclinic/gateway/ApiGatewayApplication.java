package com.vetclinic.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Quét cả com.vetclinic để dùng chung ApiResponse/ErrorCode; các bean servlet/feign/rabbit trong common-library
 * tự bỏ qua nhờ @ConditionalOnWebApplication(SERVLET) / @ConditionalOnClass.
 */
@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = "com.vetclinic")
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
