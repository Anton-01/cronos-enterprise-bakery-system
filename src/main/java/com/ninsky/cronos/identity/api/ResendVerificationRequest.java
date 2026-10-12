package com.ninsky.cronos.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /auth/email/verify/resend}: always answered 200. */
public record ResendVerificationRequest(@NotBlank @Email @Size(max = 254) String email, @Pattern(regexp = "es-MX|en") String locale) {
}
