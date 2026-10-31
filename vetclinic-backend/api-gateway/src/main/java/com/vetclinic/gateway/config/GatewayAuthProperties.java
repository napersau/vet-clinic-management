package com.vetclinic.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @param jwtSecret   khóa HMAC-SHA256 dùng chung với auth-service (biến môi trường JWT_SECRET, tối thiểu 32 byte)
 * @param publicPaths các đường dẫn không cần JWT (login, refresh, ...), cú pháp PathPattern của Spring
 */
@ConfigurationProperties(prefix = "vetclinic.gateway")
public record GatewayAuthProperties(String jwtSecret, List<String> publicPaths) {

    public GatewayAuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("vetclinic.gateway.jwt-secret (JWT_SECRET) phải dài tối thiểu 32 byte");
        }
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
