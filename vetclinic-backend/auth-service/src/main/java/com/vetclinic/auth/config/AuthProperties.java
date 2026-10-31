package com.vetclinic.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param maxLoginAttempts  số lần sai liên tiếp (theo username) trước khi tạm chặn đăng nhập
 * @param loginLockDuration thời gian chặn, tính từ lần sai gần nhất
 */
@ConfigurationProperties(prefix = "vetclinic.auth")
public record AuthProperties(
        @DefaultValue("5") int maxLoginAttempts,
        @DefaultValue("15m") Duration loginLockDuration) {
}
