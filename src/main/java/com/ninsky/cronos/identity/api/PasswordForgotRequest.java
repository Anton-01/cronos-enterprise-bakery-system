package com.ninsky.cronos.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /auth/password/forgot}: a username or an email; always answered 200. */
public record PasswordForgotRequest(@NotBlank @Size(max = 254) String identifier, @Pattern(regexp = "es-MX|en") String locale) {
}
