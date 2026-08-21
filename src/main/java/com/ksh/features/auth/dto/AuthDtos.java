package com.ksh.features.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs for the authentication flow — forgot password, password reset, activation. */
public class AuthDtos {

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 6, max = 64) String newPassword
    ) {}


    /** Same password rules as a reset — activation adds no strength policy of its own. */
    public record ActivateAccountRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 6, max = 64) String newPassword
    ) {}
}
