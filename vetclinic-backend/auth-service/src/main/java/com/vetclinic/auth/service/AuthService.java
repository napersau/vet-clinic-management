package com.vetclinic.auth.service;

import com.vetclinic.auth.dto.request.LoginRequest;
import com.vetclinic.auth.dto.response.AuthResponse;
import com.vetclinic.auth.dto.response.TokenRefreshResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    /**
     * Xoay vòng Refresh Token: token cũ bị thu hồi, cấp cặp Access + Refresh mới.
     * Dùng lại token đã thu hồi được coi là bị đánh cắp và thu hồi toàn bộ phiên của người dùng.
     */
    TokenRefreshResponse refresh(String refreshToken);

    /**
     * Thu hồi Refresh Token. Idempotent: token không tồn tại hoặc đã thu hồi vẫn trả thành công.
     */
    void logout(String refreshToken);
}
