package com.ksh.features.admin.users.imports.dto;

import java.util.List;

/**
 * Outcome of a confirmed roster import.
 *
 * <p>The three counts are mutually exclusive and together cover every row:
 * an account was created, the row was skipped because an account already
 * existed, or the row was blocked by an error (including a row that failed at
 * persist time).
 *
 * <p>{@code roleDefaulted} cuts across those three rather than joining them: it
 * counts how many of the {@code created} accounts got their role from the
 * import default because the file left the cell blank. Creating hundreds of
 * accounts at a role nobody typed should be visible in the summary, not just
 * in the preview the admin already clicked past.
 *
 * @param totalProcessed rows considered during this confirm
 * @param created        accounts newly created
 * @param alreadyExisted rows skipped because the email already belonged to an account
 * @param errors         rows blocked by a validation or persistence error
 * @param roleDefaulted  created accounts whose role came from the default
 * @param rows           full per-row report for the UI
 */
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