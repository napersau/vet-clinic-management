package com.vetclinic.gateway.security;

import com.vetclinic.gateway.config.GatewayAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Kiểm tra chữ ký HMAC-SHA256 và hạn dùng của Access Token do auth-service ký.
 */
@Slf4j
@Component
public class JwtValidator {

    private final SecretKey signingKey;

    public JwtValidator(GatewayAuthProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT không hợp lệ: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
