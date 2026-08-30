package com.ksh.features.admin.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin reset-password modal submission on {@code /admin/users}. */
public record ResetPasswordForm(
        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 6, max = 64, message = "Mật khẩu mới phải có từ 6 đến 64 ký tự")
        String newPassword
) {}
