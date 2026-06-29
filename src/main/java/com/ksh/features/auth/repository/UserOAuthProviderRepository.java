package com.ksh.features.auth.repository;

import com.ksh.entities.UserOAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Repository cho {@link UserOAuthProvider}. */
public interface UserOAuthProviderRepository extends JpaRepository<UserOAuthProvider, Long> {

    Optional<UserOAuthProvider> findByProviderAndProviderUserId(String provider, String providerUserId);
}
