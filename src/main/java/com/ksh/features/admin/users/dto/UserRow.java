package com.ksh.features.admin.users.dto;

import java.time.LocalDateTime;

/**
 * Spring Data JPA native-query projection — one row per user surfaced on
 * the admin list page. The native SQL in
 * {@code UserRepository.searchUsersForAdmin} aliases columns to match
 * these accessor names.
 *
 * <p>Status fields are exposed as raw booleans rather than a pre-computed
 * label so the template can both render the pill colour AND decide which
 * kebab-menu actions to display per row (e.g. show "Unlock" only when
 * {@code locked = true}).
 */
public interface UserRow {
    Long getId();
    String getFullName();
    String getEmail();
    String getRole();
    boolean isActive();
    boolean isLocked();
    boolean isDeleted();
    Long getDepartmentId();
    String getSubjectId();
    LocalDateTime getLastLoginAt();
    LocalDateTime getCreatedAt();
    String getAvatarUrl();
    LocalDateTime getActivatedAt();

    /**
     * Computed status label used by the template for the status pill.
     * Delegates to {@link AccountStatusLabel} so this page and the admin edit
     * page can never report different statuses for the same account.
     *
     * @return one of {@code ACTIVE | INACTIVE | PENDING | LOCKED | DELETED}
     */
    default String statusLabel() {
        return AccountStatusLabel.resolve(isDeleted(), isLocked(), isActive(), getActivatedAt());
    }
}
