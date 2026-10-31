package com.vetclinic.auth.repository;

import com.vetclinic.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("select rt from RefreshToken rt join fetch rt.user u join fetch u.role where rt.token = :token")
    Optional<RefreshToken> findByTokenWithUser(@Param("token") String token);

    /**
     * Thu hồi nguyên tử: chỉ một request thắng khi hai request cùng dùng một token.
     * Trả về 0 nghĩa là token đã bị thu hồi (do request khác vừa xoay vòng hoặc do logout).
     */
    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true where rt.id = :id and rt.revoked = false")
    int revokeIfActive(@Param("id") Long id);

    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true where rt.token = :token and rt.revoked = false")
    int revokeByToken(@Param("token") String token);

    /** Force logout mọi thiết bị: phát hiện đánh cắp token, đổi mật khẩu, khóa tài khoản. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken rt set rt.revoked = true where rt.user.id = :userId and rt.revoked = false")
    int revokeAllUserTokens(@Param("userId") Long userId);

    @Modifying
    @Query("delete from RefreshToken rt where rt.expiryDate < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
