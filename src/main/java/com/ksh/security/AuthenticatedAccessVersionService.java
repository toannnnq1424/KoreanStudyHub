package com.ksh.security;

import com.ksh.features.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compares a principal's captured security version with current durable account state. */
@Service
public class AuthenticatedAccessVersionService {

    private final UserRepository userRepository;

    public AuthenticatedAccessVersionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Returns false for stale, locked, inactive, deleted, or missing accounts. */
    @Transactional(readOnly = true)
    public boolean isCurrent(Long userId, long capturedVersion) {
        if (userId == null) {
            return false;
        }
        return userRepository.findLoginCapableSecurityVersion(userId)
                .filter(current -> current == capturedVersion)
                .isPresent();
    }
}
