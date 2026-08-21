package com.ksh.features.admin.users.imports.session;

import com.ksh.features.admin.users.imports.dto.UserImportRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * In-memory session holding one staged roster preview.
 *
 * <p>Created after parsing and validation finish, stored in
 * {@link UserImportSessionStore}, and replayed when the admin confirms. Unlike
 * the class-roster session this carries no class: an admin import is scoped to
 * the acting administrator alone.
 *
 * <p>Sessions expire after {@link #TTL_MINUTES} minutes; an expired session
 * must be re-uploaded.
 */
public final class UserImportSession {

    /** TTL after which the sweeper discards the session. */
    public static final long TTL_MINUTES = 10L;

    private final UUID id;
    private final Long adminId;
    private final Instant uploadedAt;
    private final String fileName;
    private final List<UserImportRow> rows;

    public UserImportSession(UUID id, Long adminId, Instant uploadedAt,
                             String fileName, List<UserImportRow> rows) {
        this.id = id;
        this.adminId = adminId;
        this.uploadedAt = uploadedAt;
        this.fileName = fileName;
        this.rows = List.copyOf(rows);
    }

    public UUID getId() { return id; }

    /** The administrator who uploaded this preview; the only one who may confirm it. */
    public Long getAdminId() { return adminId; }

    public Instant getUploadedAt() { return uploadedAt; }
    public String getFileName() { return fileName; }
    public List<UserImportRow> getRows() { return rows; }

    public int totalRows() {
        return rows.size();
    }

    /** Rows that will create an account on confirm. */
    public long creatableCount() {
        return rows.stream().filter(r -> r.getStatus() != null && r.getStatus().isCreatable()).count();
    }

    /** Rows skipped because an account already owns the email. */
    public long existingCount() {
        return rows.stream().filter(r -> r.getStatus() != null && r.getStatus().isSkipped()).count();
    }

    /**
     * Creatable rows whose role came from the import default rather than the
     * file. A subset of {@link #creatableCount()}, not a separate category.
     */
    public long roleDefaultedCount() {
        return rows.stream()
                .filter(r -> r.getStatus() != null && r.getStatus().isCreatable())
                .filter(UserImportRow::isRoleDefaulted)
                .count();
    }

    /** Rows blocked by a hard error. */
    public long errorCount() {
        return rows.stream().filter(r -> r.getStatus() != null && r.getStatus().isError()).count();
    }

    /** Whether the session is older than {@link #TTL_MINUTES} minutes. */
    public boolean isExpired(Instant now) {
        return uploadedAt.plusSeconds(TTL_MINUTES * 60).isBefore(now);
    }
}
