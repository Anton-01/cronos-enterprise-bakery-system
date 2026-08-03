package com.ninsky.cronos.application.request.core.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record VerifyTwoFactorRequest(
        @NotNull(message = "Verification code is required")
        @Min(value = 100000, message = "Code must be 6 digits")
        @Max(value = 999999, message = "Code must be 6 digits")
        Integer code
) { }
