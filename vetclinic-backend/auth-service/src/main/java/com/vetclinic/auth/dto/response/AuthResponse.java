package com.vetclinic.auth.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,      // giây: thời hạn Access Token
        UserResponse user
) {
}
