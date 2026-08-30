package com.ksh.features.admin.users.imports.dto;

public enum UserImportRowStatus {
    CREATABLE("Sẽ tạo tài khoản mới"),
    ALREADY_EXISTS("Đã có tài khoản — bỏ qua"),
    MISSING_EMAIL("Thiếu email"),
    INVALID_EMAIL("Email không hợp lệ"),
    INVALID_FULL_NAME("Họ tên quá dài"),
    INVALID_PHONE("Số điện thoại quá dài"),
    INVALID_ROLE("Vai trò không hợp lệ"),
    UNKNOWN_SUBJECT("Mã môn không tồn tại"),
    DUPLICATE_IN_FILE("Trùng email với dòng trước");

    private final String message;

    UserImportRowStatus(String message) {
        this.message = message;
    }

    public String message() { return message; }
    public boolean isCreatable() { return this == CREATABLE; }
    public boolean isSkipped() { return this == ALREADY_EXISTS; }
    public boolean isError() { return !isCreatable() && !isSkipped(); }
}
