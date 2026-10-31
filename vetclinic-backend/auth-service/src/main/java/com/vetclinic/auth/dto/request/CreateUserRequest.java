package com.vetclinic.auth.dto.request;

import com.vetclinic.auth.constant.ValidationPatterns;
import com.vetclinic.common.security.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ADMIN tạo được mọi vai trò; RECEPTIONIST chỉ tạo được PET_OWNER (UC-18).
 * username bỏ trống thì dùng số điện thoại làm tên đăng nhập.
 */
public record CreateUserRequest(
        @Size(max = 100, message = "Tên đăng nhập tối đa 100 ký tự")
        String username,

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
        String address,

        @Pattern(regexp = ValidationPatterns.ROOM_NUMBER, message = ValidationPatterns.ROOM_MESSAGE)
        String roomNumber,

        @NotNull(message = "Vai trò không được để trống")
        RoleCode roleCode
) {
}
