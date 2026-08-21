package com.ksh.features.admin.users.imports.dto;

import com.ksh.features.admin.users.imports.session.UserImportSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON structures the roster-import controller returns for preview
 * and confirm responses, keeping the HTTP handlers focused on transport.
 */
public final class UserImportPayloads {

    private UserImportPayloads() {}

    /** Serializes a staged preview (upload response). */
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

    /** Serializes a confirm outcome. */
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

    /** Serializes per-row identity plus status into JSON-friendly maps. */
    public static List<Map<String, Object>> rows(List<UserImportRow> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (UserImportRow row : rows) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("rowNumber", row.getRowNumber());
            r.put("email", row.getEmail());
            r.put("fullName", row.getFullName());
            r.put("role", row.getRole() == null ? row.getRawRole() : row.getRole().name());
            r.put("department", row.getRawSubjectId());
            r.put("phone", row.getPhone());
            UserImportRowStatus s = row.getStatus();
            r.put("status", s == null ? null : s.name());
            r.put("statusMessage", s == null ? null : s.message());
            r.put("isError", s != null && s.isError());
            r.put("isSkipped", s != null && s.isSkipped());
            r.put("isCreatable", s != null && s.isCreatable());
            // Warning rides alongside the status rather than replacing it: the
            // row is creatable AND carries a caveat worth showing.
            r.put("isRoleDefaulted", s != null && s.isCreatable() && row.isRoleDefaulted());
            r.put("existingStatusLabel", row.getExistingStatusLabel());
            r.put("detail", row.getDetail());
            out.add(r);
        }
        return out;
    }
}