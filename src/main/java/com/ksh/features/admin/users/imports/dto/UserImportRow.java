package com.ksh.features.admin.users.imports.dto;

import com.ksh.security.Role;

/**
 * Mutable holder describing a single row of an uploaded admin roster file.
 *
 * <p>Built in two stages, mirroring the existing class-import pipeline:
 * <ol>
 *   <li>{@code UserRosterParser} produces it from a spreadsheet row, populating
 *       the raw cell values.</li>
 *   <li>{@code UserRowValidator} fills in {@code status}, {@code detail}, the
 *       resolved {@code role} / {@code subjectId}, and — for rows matching
 *       an existing account — that account's {@code existingStatusLabel}.</li>
 * </ol>
 *
 * <p>A JavaBean rather than a record so the validator can populate fields one
 * at a time while the row identity ({@code rowNumber}) stays stable.
 */
public class UserImportRow {

    private final int rowNumber;
    private final String email;
    private final String fullName;
    private final String rawRole;
    private final String rawSubjectId;
    private final String phone;

    private UserImportRowStatus status;
    private String detail;
    private Role role;
    private Long subjectId;
    private String existingStatusLabel;
    private boolean roleDefaulted;

    public UserImportRow(int rowNumber, String email, String fullName,
                         String rawRole, String subjectId, String phone) {
        this.rowNumber = rowNumber;
        this.email = email;
        this.fullName = fullName;
        this.rawRole = rawRole;
        this.rawSubjectId = subjectId;
        this.phone = phone;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRawRole() {
        return rawRole;
    }

    public String getRawSubjectId() {
        return rawSubjectId;
    }

    public String getPhone() {
        return phone;
    }

    public UserImportRowStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    /** Role resolved from {@link #getRawRole()}; {@code null} until validated. */
    public Role getRole() {
        return role;
    }

    /** subject resolved from {@link #getRawSubjectId()}; {@code null} when none was named. */
    public Long getSubjectId() {
        return subjectId;
    }

    /**
     * Current status label of the account this row collided with, so the
     * preview can explain why the row was skipped. {@code null} unless the
     * status is {@link UserImportRowStatus#ALREADY_EXISTS}.
     */
    public String getExistingStatusLabel() {
        return existingStatusLabel;
    }

    /** Marks the row with a final status and a short Vietnamese tooltip (≤80 chars). */
    public void mark(UserImportRowStatus status, String detail) {
        this.status = status;
        this.detail = truncate(detail);
    }

    public void mark(UserImportRowStatus status) {
        mark(status, null);
    }

    /**
     * {@code true} when the role cell was blank and {@link #getRole()} came from
     * the import default rather than the file.
     *
     * <p>Orthogonal to {@link #getStatus()}: such a row is still
     * {@link UserImportRowStatus#CREATABLE}, not an error. Assigning a role the
     * admin never typed is security-sensitive, so the preview marks it visibly
     * instead of letting it pass silently.
     */
    public boolean isRoleDefaulted() {
        return roleDefaulted;
    }

    /** Attaches the values the confirm step needs so it does not re-resolve them. */
    public void resolve(Role role, Long subjectId, boolean roleDefaulted) {
        this.role = role;
        this.subjectId = subjectId;
        this.roleDefaulted = roleDefaulted;
    }

    /** Records the colliding account's displayed status for the preview table. */
    public void attachExistingStatusLabel(String label) {
        this.existingStatusLabel = label;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }
}
