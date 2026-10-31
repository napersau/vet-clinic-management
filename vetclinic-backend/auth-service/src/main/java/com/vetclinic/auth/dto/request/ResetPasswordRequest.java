package com.vetclinic.auth.dto.request;

import com.vetclinic.auth.constant.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Đặt lại mật khẩu cho người dùng quên mật khẩu (Admin: mọi tài khoản; Lễ tân: chỉ chủ nuôi). */
public record ResetPasswordRequest(
        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String newPassword
) {
}
