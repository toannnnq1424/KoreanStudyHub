package com.ksh.features.admin.users.imports.dto;

/**
 * Status code attached to every parsed row of an admin roster import.
 *
 * <p>Three categories, mirroring the shape of
 * {@code com.ulp.features.classes.imports.dto.ImportRowStatus} while inverting
 * its rules — there, a missing account is an error; here it is the whole point:
 * <ul>
 *   <li>{@code CREATABLE}: a new account will be created on confirm.</li>
 *   <li>{@code ALREADY_EXISTS}: an account with that email already exists and
 *       is left completely untouched — name, role, subject, phone,
 *       password, and status all stay as they are.</li>
 *   <li>Everything else: hard error — the row is shown in red and never
 *       creates an account.</li>
 * </ul>
 *
 * <p>{@link #message()} returns the short Vietnamese label used in the preview
 * table, keeping Vietnamese strings out of the controllers and services.
 */
public enum UserImportRowStatus {

    /** Email is well-formed, unique in the file, and belongs to no account yet. */
    CREATABLE("Sẽ tạo tài khoản mới"),

    /** An account already owns this email; the row is skipped without any write. */
    ALREADY_EXISTS("Đã có tài khoản — bỏ qua"),

    /** The email cell is blank. */
    MISSING_EMAIL("Thiếu email"),

    /** Email present but does not match the basic email pattern. */
    INVALID_EMAIL("Email không hợp lệ"),

    /** Role cell names something outside STUDENT / LECTURER / HEAD / ADMIN. */
    INVALID_ROLE("Vai trò không hợp lệ"),

    /** Subject cell names a subject that does not exist. */
    UNKNOWN_SUBJECT("Khoa/bộ môn không tồn tại"),

    /** The same email appears in an earlier row of the same uploaded file. */
    DUPLICATE_IN_FILE("Trùng email với dòng trước"),

    /** Persisting the account failed at the row level (DB error mid-confirm). */
    PERSISTENCE_FAILED("Không lưu được, vui lòng thử lại");

    private final String message;

    UserImportRowStatus(String message) {
        this.message = message;
    }

    /** Short Vietnamese label rendered in the preview table badge. */
    public String message() {
        return message;
    }

    /** {@code true} when confirming the import will create an account for this row. */
    public boolean isCreatable() {
        return this == CREATABLE;
    }

    /** {@code true} for hard errors that block the row from creating anything. */
    public boolean isError() {
        return switch (this) {
            case MISSING_EMAIL, INVALID_EMAIL, INVALID_ROLE, UNKNOWN_SUBJECT,
                 DUPLICATE_IN_FILE, PERSISTENCE_FAILED -> true;
            default -> false;
        };
    }

    /** {@code true} for the informational skip: the account already exists. */
    public boolean isSkipped() {
        return this == ALREADY_EXISTS;
    }
}