package com.vetclinic.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @param secret          khóa HMAC-SHA256, dùng chung với api-gateway (biến môi trường JWT_SECRET), tối thiểu 32 byte
 * @param accessTokenTtl  mặc định 30 phút
 * @param refreshTokenTtl mặc định 7 ngày
 */
@ConfigurationProperties(prefix = "vetclinic.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("30m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("vetclinic.jwt.secret (JWT_SECRET) phải dài tối thiểu 32 byte");
        }
    }
}
