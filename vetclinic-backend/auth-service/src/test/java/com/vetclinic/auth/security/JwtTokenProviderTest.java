package com.vetclinic.auth.security;

import com.vetclinic.auth.config.JwtProperties;
import com.vetclinic.auth.entity.Role;
import com.vetclinic.auth.entity.User;
import com.vetclinic.common.security.RoleCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token do auth-service ký phải đọc được đúng như api-gateway (JwtValidator) sẽ đọc.
 */
class JwtTokenProviderTest {

    // 64 ký tự (giống `openssl rand -hex 32`): nếu không chỉ định HS256 tường minh, jjwt sẽ tự chọn HS512
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(7));
    private final JwtTokenProvider provider = new JwtTokenProvider(properties);
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    void accessToken_isSignedWithHs256AndCarriesGatewayClaims() {
        String token = provider.generateAccessToken(vet());

        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        Claims claims = jws.getPayload();
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("userId", String.class)).isEqualTo("7"); // chuỗi, không phải số
        assertThat(claims.get("role", String.class)).isEqualTo("VETERINARIAN");
        assertThat(claims.get("username", String.class)).isEqualTo("bs.nguyen");
        assertThat(claims.get("roomNumber", String.class)).isEqualTo("P01");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isBetween(1_799_000L, 1_801_000L);
    }

    @Test
    void accessToken_withoutRoom_omitsRoomClaim() {
        User user = vet();
        user.setRoomNumber(null);

        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(provider.generateAccessToken(user)).getPayload();

        assertThat(claims).doesNotContainKey("roomNumber");
    }

    @Test
    void accessToken_signedWithDifferentKey_isRejected() {
        String token = provider.generateAccessToken(vet());
        SecretKey otherKey = Keys.hmacShaKeyFor("ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Jwts.parser().verifyWith(otherKey).build().parseSignedClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void properties_rejectSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtProperties("short", Duration.ofMinutes(30), Duration.ofDays(7)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static User vet() {
        return User.builder()
                .id(7L)
                .role(Role.builder().id(2L).code(RoleCode.VETERINARIAN).name("Bác sĩ").build())
                .username("bs.nguyen")
                .roomNumber("P01")
                .build();
    }
}
