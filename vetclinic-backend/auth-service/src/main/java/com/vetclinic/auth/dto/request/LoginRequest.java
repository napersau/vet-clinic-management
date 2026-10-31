package com.vetclinic.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(max = 100, message = "Tên đăng nhập tối đa 100 ký tự")
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(max = 128, message = "Mật khẩu tối đa 128 ký tự")
        String password
) {
}
