package com.ninsky.cronos.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Self-registration (§2.4.1); the account waits for an administrator. */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 32) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String password,
        @NotBlank @Size(min = 1, max = 80) String firstName,
        @NotBlank @Size(min = 1, max = 80) String lastName,
        @Size(max = 20) String phoneNumber,
        @Size(max = 120) String businessName,
        @Size(max = 500) String message,
        @NotBlank @Size(max = 20) String acceptedTermsVersion,
        @NotBlank @Pattern(regexp = "es-MX|en") String locale
) {
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + "]";
    }
}
