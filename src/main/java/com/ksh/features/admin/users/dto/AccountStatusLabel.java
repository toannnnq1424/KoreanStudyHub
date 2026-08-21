package com.ksh.features.admin.users.dto;

import java.time.LocalDateTime;

/**
 * Single source of truth for an account's displayed status label.
 *
 * <p>Both the admin user list ({@link UserRow#statusLabel()}) and the admin
 * user edit page resolve labels here, so the two screens can never disagree
 * about the same account. Adding a branch to only one copy — which is what
 * existed before this class — produced exactly that silent inconsistency.
 *
 * <p>Precedence is {@code DELETED > LOCKED > PENDING > INACTIVE > ACTIVE}.
 * {@code PENDING} sits above {@code INACTIVE} because a never-activated
 * account is more usefully described as awaiting its owner than as disabled,
 * and below {@code LOCKED} because a deliberate lock is the more urgent fact.
 */
public final class AccountStatusLabel {

    public static final String ACTIVE   = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String PENDING  = "PENDING";
    public static final String LOCKED   = "LOCKED";
    public static final String DELETED  = "DELETED";

    private AccountStatusLabel() {
        // utility holder
    }

    /**
     * Resolves the status label for one account.
     *
     * @param deleted     value of {@code is_deleted}
     * @param locked      value of {@code is_locked}
     * @param active      value of {@code is_active}
     * @param activatedAt {@code activated_at}; {@code null} means the owner
     *                    has not completed activation
     * @return one of {@code DELETED | LOCKED | PENDING | INACTIVE | ACTIVE}
     */
    public static String resolve(boolean deleted, boolean locked, boolean active,
                                 LocalDateTime activatedAt) {
        if (deleted) return DELETED;
        if (locked)  return LOCKED;
        // Never self-activated outranks INACTIVE regardless of is_active: an
        // admin may have enabled the account, but no password exists yet.
        if (activatedAt == null) return PENDING;
        if (!active) return INACTIVE;
        return ACTIVE;
    }
}
