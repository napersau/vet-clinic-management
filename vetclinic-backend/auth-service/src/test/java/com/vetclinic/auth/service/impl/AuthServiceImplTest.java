package com.vetclinic.auth.service.impl;

import com.vetclinic.auth.config.AuthProperties;
import com.vetclinic.auth.config.JwtProperties;
import com.vetclinic.auth.constant.UserStatus;
import com.vetclinic.auth.dto.request.LoginRequest;
import com.vetclinic.auth.dto.response.AuthResponse;
import com.vetclinic.auth.dto.response.TokenRefreshResponse;
import com.vetclinic.auth.entity.RefreshToken;
import com.vetclinic.auth.entity.Role;
import com.vetclinic.auth.entity.User;
import com.vetclinic.auth.mapper.UserMapperImpl;
import com.vetclinic.auth.repository.RefreshTokenRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.JwtTokenProvider;
import com.vetclinic.auth.security.LoginAttemptService;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PASSWORD = "Admin@123";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // strength thấp cho test nhanh
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "0123456789abcdef0123456789abcdef", Duration.ofMinutes(30), Duration.ofDays(7));
        service = new AuthServiceImpl(
                userRepository,
                refreshTokenRepository,
                encoder,
                new JwtTokenProvider(jwtProperties),
                jwtProperties,
                new LoginAttemptService(new AuthProperties(3, Duration.ofMinutes(15))),
                new UserMapperImpl());
    }

    // ------------------------------------------------------------------ login

    @Test
    void login_withValidCredentials_returnsTokensAndStoresRefreshToken() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthResponse response = service.login(new LoginRequest("admin", PASSWORD));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
        assertThat(response.user().role()).isEqualTo(RoleCode.ADMIN);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getToken()).isEqualTo(response.refreshToken());
        assertThat(saved.getValue().isRevoked()).isFalse();
        assertThat(saved.getValue().getExpiryDate()).isAfter(Instant.now().plus(Duration.ofDays(6)));
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentials() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "sai-mat-khau-1")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_withUnknownUser_throwsSameErrorAsWrongPassword() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", PASSWORD)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_withPasswordOver72Bytes_throwsInvalidCredentialsInsteadOfCrashing() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "a1".repeat(60))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_lockedAccountWithCorrectPassword_throwsAccountLocked() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(UserStatus.LOCKED)));

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", PASSWORD)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void login_lockedAccountWithWrongPassword_doesNotRevealStatus() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(UserStatus.LOCKED)));

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "sai-mat-khau-1")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_afterTooManyFailures_blocksEvenCorrectPassword() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("admin", "sai-mat-khau-1")))
                    .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        assertThatThrownBy(() -> service.login(new LoginRequest("Admin", PASSWORD)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
    }

    // ------------------------------------------------------------------ refresh

    @Test
    void refresh_withActiveToken_rotatesAndIssuesNewPair() {
        RefreshToken old = refreshToken("old-token", false, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenWithUser("old-token")).thenReturn(Optional.of(old));
        when(refreshTokenRepository.revokeIfActive(old.getId())).thenReturn(1);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        TokenRefreshResponse response = service.refresh("old-token");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank().isNotEqualTo("old-token");
        assertThat(response.expiresIn()).isEqualTo(1800);
        verify(refreshTokenRepository).revokeIfActive(old.getId());
        verify(refreshTokenRepository, never()).revokeAllUserTokens(any());
    }

    @Test
    void refresh_withRevokedToken_revokesAllSessionsAndThrows() {
        RefreshToken stolen = refreshToken("stolen", true, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenWithUser("stolen")).thenReturn(Optional.of(stolen));

        assertThatThrownBy(() -> service.refresh("stolen"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REVOKED);

        verify(refreshTokenRepository).revokeAllUserTokens(stolen.getUser().getId());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_whenLosingRaceToConcurrentRequest_isTreatedAsReuse() {
        RefreshToken token = refreshToken("raced", false, Instant.now().plus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenWithUser("raced")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.revokeIfActive(token.getId())).thenReturn(0);

        assertThatThrownBy(() -> service.refresh("raced"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REVOKED);

        verify(refreshTokenRepository).revokeAllUserTokens(token.getUser().getId());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_withExpiredToken_throwsExpired() {
        RefreshToken expired = refreshToken("expired", false, Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenWithUser("expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh("expired"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_withUnknownToken_throwsNotFound() {
        when(refreshTokenRepository.findByTokenWithUser("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("nope"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    void refresh_whenAccountWasLockedAfterIssuing_revokesAllAndRefuses() {
        RefreshToken token = refreshToken("t", false, Instant.now().plus(Duration.ofDays(1)));
        token.getUser().setStatus(UserStatus.LOCKED);
        when(refreshTokenRepository.findByTokenWithUser("t")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.revokeIfActive(token.getId())).thenReturn(1);

        assertThatThrownBy(() -> service.refresh("t"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);

        verify(refreshTokenRepository).revokeAllUserTokens(token.getUser().getId());
        verify(refreshTokenRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ logout

    @Test
    void logout_revokesGivenToken() {
        service.logout("abc");

        verify(refreshTokenRepository).revokeByToken("abc");
    }

    // ------------------------------------------------------------------ fixtures

    private User user(UserStatus status) {
        Role role = Role.builder().id(1L).code(RoleCode.ADMIN).name("Quản trị viên").build();
        return User.builder()
                .id(1L)
                .role(role)
                .username("admin")
                .passwordHash(encoder.encode(PASSWORD))
                .fullName("Quản trị viên hệ thống")
                .phoneNumber("0901234567")
                .status(status)
                .build();
    }

    private RefreshToken refreshToken(String value, boolean revoked, Instant expiry) {
        return RefreshToken.builder()
                .id(10L)
                .user(user(UserStatus.ACTIVE))
                .token(value)
                .revoked(revoked)
                .expiryDate(expiry)
                .build();
    }
}
