package com.ksh.features.admin.users.imports.dto;

import com.ksh.security.Role;

/** Parsed row plus the validation result needed by preview and confirm. */
public class UserImportRow {
    private final int rowNumber;
    private final String email;
    private final String fullName;
    private final String rawRole;
    private final String rawSubject;
    private final String phone;
    private UserImportRowStatus status;
    private String detail;
    private Role role;
    private Long subjectId;
    private String existingStatusLabel;
    private boolean roleDefaulted;

    public UserImportRow(int rowNumber, String email, String fullName,
                         String rawRole, String rawSubject, String phone) {
        this.rowNumber = rowNumber;
        this.email = email;
        this.fullName = fullName;
        this.rawRole = rawRole;
        this.rawSubject = rawSubject;
        this.phone = phone;
    }

    public int getRowNumber() { return rowNumber; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRawRole() { return rawRole; }
    public String getRawSubject() { return rawSubject; }
    public String getPhone() { return phone; }
    public UserImportRowStatus getStatus() { return status; }
    public String getDetail() { return detail; }
    public Role getRole() { return role; }
    public Long getSubjectId() { return subjectId; }
    public String getExistingStatusLabel() { return existingStatusLabel; }
    public boolean isRoleDefaulted() { return roleDefaulted; }

    public void resolve(Role role, Long subjectId, boolean roleDefaulted) {
        this.role = role;
        this.subjectId = subjectId;
        this.roleDefaulted = roleDefaulted;
    }

    public void mark(UserImportRowStatus status) { mark(status, null); }

    public void mark(UserImportRowStatus status, String detail) {
        this.status = status;
        this.detail = compact(detail);
    }

    public void attachExistingStatusLabel(String label) {
        this.existingStatusLabel = label;
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 117) + "...";
    }
}
