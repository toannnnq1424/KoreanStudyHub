package com.ksh.features.auth.repository;

import com.ksh.entities.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing {@link PasswordResetToken} entities.
 *
 * <p>Provides standard CRUD operations via {@link JpaRepository} and a
 * custom lookup methods retrieve records by token digest and support bounded
 * invalidation and retention.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    @Query("select token.user.id from PasswordResetToken token where token.token = :token")
    Optional<Long> findUserIdByToken(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.token = :token")
    Optional<PasswordResetToken> findByTokenForUpdate(@Param("token") String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetToken token set token.usedAt = :usedAt "
            + "where token.user.id = :userId and token.usedAt is null")
    int invalidateUnusedForUser(@Param("userId") Long userId,
                                @Param("usedAt") LocalDateTime usedAt);

    @Query("select token.id from PasswordResetToken token "
            + "where token.expiresAt < :cutoff "
            + "or (token.usedAt is not null and token.usedAt < :cutoff) "
            + "order by token.id")
    List<Long> findRetentionIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
