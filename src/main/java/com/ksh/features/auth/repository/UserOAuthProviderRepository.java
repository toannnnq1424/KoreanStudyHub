package com.ksh.features.auth.repository;

import com.ksh.entities.UserOAuthProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for managing {@link UserOAuthProvider} entities.
 *
 * <p>Provides data access operations for OAuth provider bindings associated with user accounts.
 * Extends {@link org.springframework.data.jpa.repository.JpaRepository} to inherit standard
 * CRUD operations.
 */
public interface UserOAuthProviderRepository extends JpaRepository<UserOAuthProvider, Long> {

    Optional<UserOAuthProvider> findByProviderAndProviderUserId(String provider, String providerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UserOAuthProvider p WHERE p.provider = :provider "
            + "AND p.providerUserId = :providerUserId")
    Optional<UserOAuthProvider> findByProviderAndProviderUserIdForUpdate(
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId);
}
