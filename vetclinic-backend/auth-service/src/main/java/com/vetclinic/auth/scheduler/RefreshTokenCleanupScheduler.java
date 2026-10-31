package com.vetclinic.auth.scheduler;

import com.vetclinic.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Bảng refresh_tokens tăng theo mỗi lần đăng nhập và mỗi lần xoay vòng; dọn token đã hết hạn quá 1 ngày.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
        log.info("Đã dọn {} Refresh Token hết hạn", deleted);
    }
}
