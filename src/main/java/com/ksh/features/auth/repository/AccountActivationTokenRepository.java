package com.ksh.features.auth.repository;

import com.ksh.entities.AccountActivationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AccountActivationTokenRepository
        extends JpaRepository<AccountActivationToken, Long> {

    Optional<AccountActivationToken> findByTokenDigest(String tokenDigest);

    @Query("select token.user.id from AccountActivationToken token "
            + "where token.tokenDigest = :digest")
    Optional<Long> findUserIdByTokenDigest(@Param("digest") String digest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AccountActivationToken token "
            + "where token.tokenDigest = :digest")
    Optional<AccountActivationToken> findByTokenDigestForUpdate(
            @Param("digest") String digest);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AccountActivationToken token set token.usedAt = :usedAt "
            + "where token.user.id = :userId and token.usedAt is null")
    int invalidateUnusedForUser(@Param("userId") Long userId,
                                @Param("usedAt") LocalDateTime usedAt);
}
