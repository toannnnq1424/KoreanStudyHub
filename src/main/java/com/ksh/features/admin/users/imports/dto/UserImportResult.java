package com.ksh.features.admin.users.imports.dto;

import java.util.List;

public record UserImportResult(int totalProcessed,
                               int created,
                               int alreadyExisted,
                               int errors,
                               int roleDefaulted,
                               List<UserImportRow> rows) {
    public UserImportResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
