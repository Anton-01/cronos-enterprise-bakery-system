package com.ninsky.cronos.application.request.core.auth;

import com.ninsky.cronos.application.request.core.auth.validation.PasswordChange;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@PasswordChange
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        String newPassword,

        @NotBlank(message = "Password confirmation is required")
        String confirmPassword
) { }
