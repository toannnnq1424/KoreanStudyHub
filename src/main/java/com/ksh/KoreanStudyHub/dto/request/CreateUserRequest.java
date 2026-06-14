package com.ksh.KoreanStudyHub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateUserRequest {
    @NotBlank private String fullName;
    @Email @NotBlank private String email;
    @NotBlank private String password;
    @NotBlank private String roleName;
}
