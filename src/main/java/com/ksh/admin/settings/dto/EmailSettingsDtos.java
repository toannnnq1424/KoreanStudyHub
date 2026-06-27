package com.ksh.admin.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTOs for the /admin/settings/email page.
 *
 * <p>{@link EmailSettingsForm} is the form binding for GET/POST. The
 * {@code password} field is a {@link String} without constraints — empty or
 * the placeholder {@link #MASKED} means "keep the old password".
 *
 * <p>{@link TestResult} is the JSON response of POST /test.
 */
public class EmailSettingsDtos {

    /**
     * Placeholder displayed in place of the actual value of secret settings.
     * Shared constant — referenced by service and tests to avoid duplicate
     * literal {@code "********"}.
     */
    public static final String MASKED = "********";

    /** Form binding for /admin/settings/email (GET re-render + POST save). */
    public record EmailSettingsForm(
            @NotBlank(message = "Host là bắt buộc")
            @Size(max = 255)
            String host,

            @NotNull(message = "Port là bắt buộc")
            @Min(value = 1, message = "Port phải từ 1 đến 65535")
            @Max(value = 65535, message = "Port phải từ 1 đến 65535")
            Integer port,

            @NotBlank(message = "Encryption là bắt buộc")
            @Pattern(regexp = "none|tls|ssl", message = "Encryption phải là none, tls hoặc ssl")
            String encryption,

            @NotBlank(message = "Username là bắt buộc")
            @Size(max = 255)
            String username,

            /** No validation — empty or MASKED means "keep unchanged". */
            String password,

            @NotBlank(message = "From Name là bắt buộc")
            @Size(max = 100)
            String fromName,

            @NotBlank(message = "From Email là bắt buộc")
            @Email(message = "From Email không hợp lệ")
            String fromEmail,

            /** Optional — accepts empty; if provided, it must be a valid email. */
            @Email(message = "Reply-To không hợp lệ")
            String replyTo
    ) {
    }

    /**
     * Result of POST /admin/settings/email/test — serialized to JSON.
     * {@code @JsonInclude(NON_NULL)} so that in the "ok=true" scenario it returns
     * {@code {"ok":true}} instead of {@code {"ok":true,"error":null}}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TestResult(boolean ok, String error) {
    }
}