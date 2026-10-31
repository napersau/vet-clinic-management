package com.vetclinic.auth.dto.request;

import com.vetclinic.auth.constant.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Người dùng tự cập nhật hồ sơ của mình. Trường nào null thì giữ nguyên.
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 150, message = "Họ tên từ 1 đến 150 ký tự")
        String fullName,

        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phoneNumber,

        @Email(message = "Email không hợp lệ")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address
) {
}
