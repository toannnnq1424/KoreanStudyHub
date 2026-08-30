package com.ksh.features.admin.users.imports.dto;

import com.ksh.features.admin.users.imports.session.UserImportSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UserImportPayloads {
    private UserImportPayloads() {}

    public static Map<String, Object> preview(UserImportSession session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId().toString());
        payload.put("fileName", session.getFileName());
        payload.put("totalRows", session.totalRows());
        payload.put("creatableCount", session.creatableCount());
        payload.put("existingCount", session.existingCount());
        payload.put("errorCount", session.errorCount());
        payload.put("roleDefaultedCount", session.roleDefaultedCount());
        payload.put("rows", rows(session.getRows()));
        return payload;
    }

    public static Map<String, Object> result(UserImportResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalProcessed", result.totalProcessed());
        payload.put("created", result.created());
        payload.put("alreadyExisted", result.alreadyExisted());
        payload.put("errors", result.errors());
        payload.put("roleDefaulted", result.roleDefaulted());
        payload.put("rows", rows(result.rows()));
        return payload;
    }

    private static List<Map<String, Object>> rows(List<UserImportRow> source) {
        return source.stream().map(row -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("rowNumber", row.getRowNumber());
            value.put("email", row.getEmail());
            value.put("fullName", row.getFullName());
            value.put("role", row.getRole() == null ? row.getRawRole() : row.getRole().name());
            value.put("subject", row.getRawSubject());
            value.put("phone", row.getPhone());
            value.put("status", row.getStatus() == null ? null : row.getStatus().name());
            value.put("statusMessage", row.getStatus() == null ? null : row.getStatus().message());
            value.put("error", row.getStatus() != null && row.getStatus().isError());
            value.put("skipped", row.getStatus() != null && row.getStatus().isSkipped());
            value.put("creatable", row.getStatus() != null && row.getStatus().isCreatable());
            value.put("roleDefaulted", row.isRoleDefaulted());
            value.put("existingStatusLabel", row.getExistingStatusLabel());
            value.put("detail", row.getDetail());
            return value;
        }).toList();
    }
}
