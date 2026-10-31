package com.vetclinic.auth.dto.request;

import com.vetclinic.auth.constant.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Chủ nuôi tự đăng ký tài khoản (public). Luôn tạo vai trò PET_OWNER, tên đăng nhập là số điện thoại; không có trường vai trò
 * hay tên đăng nhập nên người ngoài không thể tự cấp quyền.
 */
public record RegisterRequest(
        @NotBlank(message = "Mật khẩu không được để trống")
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        String fullName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phoneNumber,

        @Email(message = "Email không hợp lệ")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address
) {
}
