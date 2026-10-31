package com.vetclinic.auth.security;

import com.vetclinic.auth.config.JwtProperties;
import com.vetclinic.auth.entity.User;
import com.vetclinic.common.security.SecurityConstants;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Ký Access Token JWT (HS256). api-gateway kiểm tra chữ ký bằng cùng khóa và gắn
 * X-User-Id / X-User-Role từ claims userId / role.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                // userId để dạng chuỗi: Gateway đọc claims.get("userId") rồi đưa nguyên vào header, tránh "5.0" khi parse số
                .claim(SecurityConstants.CLAIM_USER_ID, String.valueOf(user.getId()))
                .claim(SecurityConstants.CLAIM_ROLE, user.getRole().getCode().name())
                .claim("username", user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())));
        if (user.getRoomNumber() != null) {
            builder.claim(SecurityConstants.CLAIM_ROOM_NUMBER, user.getRoomNumber());
        }
        // Chỉ định HS256 tường minh: nếu không, khóa 64 byte sẽ bị tự chọn HS512
        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }
}
