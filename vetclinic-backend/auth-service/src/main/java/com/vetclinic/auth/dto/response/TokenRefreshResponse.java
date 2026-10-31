package com.vetclinic.auth.dto.response;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
