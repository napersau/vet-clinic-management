package com.vetclinic.auth.service;

import com.vetclinic.auth.entity.User;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;

/**
 * Quy tắc dùng chung cho tài khoản và mật khẩu.
 */
public final class AccountRules {

    /** BCrypt chỉ xử lý 72 byte đầu; Spring Security 7 từ chối mật khẩu dài hơn. */
    public static final int BCRYPT_MAX_BYTES = 72;

    private AccountRules() {
    }

    public static boolean exceedsBcryptLimit(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

    /**
     * Chỉ tài khoản ACTIVE mới được dùng hệ thống.
     */
    public static void assertUsable(User user) {
        switch (user.getStatus()) {
            case ACTIVE -> {
            }
            case LOCKED -> throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            case INACTIVE -> throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }
}
