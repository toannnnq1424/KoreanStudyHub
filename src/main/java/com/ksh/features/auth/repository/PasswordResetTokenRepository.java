package com.ksh.features.auth.repository;

import com.ksh.entities.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for managing {@link PasswordResetToken} entities.
 *
 * <p>Provides standard CRUD operations via {@link JpaRepository} and a
 * custom lookup methods for retrieving a record by its token digest.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetToken token where token.token = :token")
    Optional<PasswordResetToken> findByTokenForUpdate(@Param("token") String token);
}
