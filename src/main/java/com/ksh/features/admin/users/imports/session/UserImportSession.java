package com.ksh.features.admin.users.imports.session;

import com.ksh.features.admin.users.imports.dto.UserImportRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserImportSession {
    public static final long TTL_MINUTES = 10;
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
    public Long getAdminId() { return adminId; }
    public String getFileName() { return fileName; }
    public List<UserImportRow> getRows() { return rows; }
    public int totalRows() { return rows.size(); }
    public long creatableCount() { return rows.stream().filter(r -> r.getStatus().isCreatable()).count(); }
    public long existingCount() { return rows.stream().filter(r -> r.getStatus().isSkipped()).count(); }
    public long errorCount() { return rows.stream().filter(r -> r.getStatus().isError()).count(); }
    public long roleDefaultedCount() {
        return rows.stream().filter(r -> r.getStatus().isCreatable())
                .filter(UserImportRow::isRoleDefaulted).count();
    }
    public boolean isExpired(Instant now) {
        return uploadedAt.plusSeconds(TTL_MINUTES * 60).isBefore(now);
    }
}
