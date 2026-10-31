package com.vetclinic.auth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vetclinic.auth.config.AuthProperties;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Chống dò mật khẩu: sai quá maxLoginAttempts lần liên tiếp thì tạm chặn username đó.
 * Lưu trong bộ nhớ của từng instance (đủ cho phạm vi đồ án, 1 instance auth-service). Giới hạn 10.000 khóa nên
 * kẻ tấn công thử username ngẫu nhiên cũng không làm phình bộ nhớ. Không khóa vĩnh viễn trong DB để
 * tránh bị lợi dụng khóa tài khoản admin; cái giá là người lạ có thể gây chặn tạm thời 15 phút cho một username.
 */
@Component
public class LoginAttemptService {

    private final int maxAttempts;
    private final Cache<String, Integer> failures;

    public LoginAttemptService(AuthProperties properties) {
        this.maxAttempts = properties.maxLoginAttempts();
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(properties.loginLockDuration())
                .maximumSize(10_000)
                .build();
    }

    public void assertNotBlocked(String username) {
        Integer count = failures.getIfPresent(key(username));
        if (count != null && count >= maxAttempts) {
            throw new AppException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    public void recordFailure(String username) {
        failures.asMap().merge(key(username), 1, Integer::sum);
    }

    public void reset(String username) {
        failures.invalidate(key(username));
    }

    // MySQL so sánh username không phân biệt hoa thường nên "Admin" và "admin" là cùng một tài khoản
    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
