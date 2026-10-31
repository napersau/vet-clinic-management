package com.vetclinic.auth.controller;

import com.vetclinic.auth.dto.request.LoginRequest;
import com.vetclinic.auth.dto.request.LogoutRequest;
import com.vetclinic.auth.dto.request.RefreshTokenRequest;
import com.vetclinic.auth.dto.request.RegisterRequest;
import com.vetclinic.auth.dto.response.AuthResponse;
import com.vetclinic.auth.dto.response.TokenRefreshResponse;
import com.vetclinic.auth.dto.response.UserResponse;
import com.vetclinic.auth.service.AuthService;
import com.vetclinic.auth.service.UserService;
import com.vetclinic.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /** Public (không cần Access Token). */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", authService.login(request)));
    }

    /** Public: chủ nuôi tự đăng ký (luôn vai trò PET_OWNER). Đăng nhập sau đó bằng số điện thoại và mật khẩu vừa đặt. */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đăng ký tài khoản thành công", userService.register(request)));
    }

    /** Public: Access Token đã hết hạn nên client chỉ có Refresh Token. */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Làm mới phiên đăng nhập thành công",
                authService.refresh(request.refreshToken())));
    }

    /** Public: luôn thành công kể cả khi token không tồn tại / đã thu hồi (idempotent). */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.<Void>success("Đăng xuất thành công", null));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success(userService.getMe()));
    }
}
