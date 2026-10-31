package com.vetclinic.auth.service.impl;

import com.vetclinic.auth.config.JwtProperties;
import com.vetclinic.auth.dto.request.LoginRequest;
import com.vetclinic.auth.dto.response.AuthResponse;
import com.vetclinic.auth.dto.response.TokenRefreshResponse;
import com.vetclinic.auth.entity.RefreshToken;
import com.vetclinic.auth.entity.User;
import com.vetclinic.auth.mapper.UserMapper;
import com.vetclinic.auth.repository.RefreshTokenRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.JwtTokenProvider;
import com.vetclinic.auth.security.LoginAttemptService;
import com.vetclinic.auth.service.AccountRules;
import com.vetclinic.auth.service.AuthService;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final LoginAttemptService loginAttemptService;
    private final UserMapper userMapper;

    // Hash giả để username không tồn tại cũng tốn thời gian BCrypt như username có thật (chống dò tài khoản qua thời gian phản hồi)
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           JwtProperties jwtProperties,
                           LoginAttemptService loginAttemptService,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.loginAttemptService = loginAttemptService;
        this.userMapper = userMapper;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-constant-time");
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        loginAttemptService.assertNotBlocked(username);

        User user = userRepository.findByUsername(username).orElse(null);
        boolean passwordMatches = matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !passwordMatches) {
            loginAttemptService.recordFailure(username);
            // Cùng một thông báo cho "sai tên đăng nhập" và "sai mật khẩu"
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Kiểm tra trạng thái SAU khi mật khẩu đúng: người không biết mật khẩu không dò được trạng thái tài khoản
        AccountRules.assertUsable(user);
        loginAttemptService.reset(username);

        RefreshToken refreshToken = newRefreshToken(user);
        log.info("Đăng nhập thành công: userId={}, role={}", user.getId(), user.getRole().getCode());
        return new AuthResponse(
                jwtTokenProvider.generateAccessToken(user),
                refreshToken.getToken(),
                TOKEN_TYPE,
                jwtTokenProvider.accessTokenTtlSeconds(),
                userMapper.toResponse(user));
    }

    /**
     * noRollbackFor: các nhánh lỗi ở đây phải GIỮ lại việc thu hồi token (đặc biệt "thu hồi toàn bộ phiên" khi
     * phát hiện đánh cắp). Nếu để mặc định, AppException (RuntimeException) sẽ rollback luôn việc thu hồi đó.
     */
    @Override
    @Transactional(noRollbackFor = AppException.class)
    public TokenRefreshResponse refresh(String tokenStr) {
        RefreshToken token = refreshTokenRepository.findByTokenWithUser(tokenStr)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
        User user = token.getUser();

        // 1. Token đã bị thu hồi mà vẫn được đem ra dùng -> nghi bị đánh cắp (Replay Attack)
        if (token.isRevoked()) {
            throw reuseDetected(user);
        }

        // 2. Hết hạn
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.revokeIfActive(token.getId());
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // 3. Thu hồi nguyên tử. Hai request đồng thời cùng token: chỉ một request thắng, request còn lại
        //    nhận 0 dòng và bị coi là dùng lại token (không thể cùng sinh ra hai token hợp lệ).
        if (refreshTokenRepository.revokeIfActive(token.getId()) == 0) {
            throw reuseDetected(user);
        }

        // 4. Tài khoản bị khóa/ngừng sau khi cấp token thì không được gia hạn phiên
        if (!isUsable(user)) {
            refreshTokenRepository.revokeAllUserTokens(user.getId());
            AccountRules.assertUsable(user);
        }

        // 5. Xoay vòng: cấp token mới
        RefreshToken newToken = newRefreshToken(user);
        return new TokenRefreshResponse(
                jwtTokenProvider.generateAccessToken(user),
                newToken.getToken(),
                TOKEN_TYPE,
                jwtTokenProvider.accessTokenTtlSeconds());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.revokeByToken(refreshToken);
    }

    private AppException reuseDetected(User user) {
        log.warn("CẢNH BÁO AN NINH: Refresh Token đã thu hồi bị dùng lại, userId={}. Thu hồi toàn bộ phiên!", user.getId());
        refreshTokenRepository.revokeAllUserTokens(user.getId());
        return new AppException(ErrorCode.REFRESH_TOKEN_REVOKED);
    }

    private RefreshToken newRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plus(jwtProperties.refreshTokenTtl()))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    private boolean matches(String rawPassword, String hash) {
        if (AccountRules.exceedsBcryptLimit(rawPassword)) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private static boolean isUsable(User user) {
        try {
            AccountRules.assertUsable(user);
            return true;
        } catch (AppException e) {
            return false;
        }
    }
}
