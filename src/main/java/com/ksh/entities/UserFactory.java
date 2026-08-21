package com.ksh.entities;

import com.ksh.security.Role;

import java.time.LocalDateTime;

/**
 * Package-private factory for constructing new {@link User} instances.
 *
 * <p>{@link User} hides its no-arg constructor behind
 * {@code @NoArgsConstructor(access = PROTECTED)} to prevent ad-hoc external
 * instantiation. The admin Create flow needs to build a fresh user with all
 * mandatory fields populated — this factory provides exactly that surface and
 * lives in the entity's own package so it can call the protected constructor.
 */
public final class UserFactory {

    private UserFactory() {
        // utility holder
    }

    /**
     * Builds a fully-populated {@link User} for the admin Create flow.
     *
     * @param normalizedEmail email already trimmed and lower-cased by the service
     * @param passwordHash    a BCrypt hash; callers MUST encode before passing
     * @param fullName        display name
     * @param role            role to assign on creation
     * @param emailVerified   initial value for {@code is_email_verified}
     * @param phone           optional phone (already null-coerced when blank)
     * @param bio             optional bio (already null-coerced when blank)
     * @return a {@link User} entity ready for {@code repository.save(...)}
     */
    public static User newAdminCreated(String normalizedEmail,
                                       String passwordHash,
                                       String fullName,
                                       Role role,
                                       boolean emailVerified,
                                       String phone,
                                       String bio) {
        User u = new User(
                normalizedEmail,
                passwordHash,
                fullName,
                role,
                emailVerified,
                /* active  */ true,
                /* locked  */ false,
                /* deleted */ false,
                phone,
                bio
        );
        u.markActivated(LocalDateTime.now());
        return u;
    }

    /**
     * Builds an account whose owner has not yet activated it: {@code is_active}
     * is {@code 0} and {@code activated_at} is {@code NULL}. Used by the admin
     * roster import, where nobody — including the importing admin — knows a
     * password that authenticates the account until the owner sets one.
     *
     * @param normalizedEmail email already trimmed and lower-cased by the caller
     * @param passwordHash    BCrypt hash of an undisclosed random secret
     * @param fullName        display name
     * @param role            role to assign on creation
     * @param phone           optional phone (already null-coerced when blank)
     * @param departmentId    optional department ownership; {@code null} when none
     * @return a {@link User} entity ready for {@code repository.save(...)}
     */
    public static User newPendingActivation(String normalizedEmail,
                                            String passwordHash,
                                            String fullName,
                                            Role role,
                                            String phone,
                                            Long subjectId) {
        return new User(normalizedEmail, passwordHash, fullName, role, phone, subjectId);
    }
}
