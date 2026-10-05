package com.ninsky.cronos.iam.user.api;

import java.time.LocalDate;

/** Full replace of profile fields; {@code null} clears optional ones. */
public record UpdateUserRequest(
        String username, String email, String firstName, String lastName, String phoneNumber, String jobTitle,
        String department, String employeeNumber, String locale, LocalDate accessExpiresAt, Boolean requireTwoFactor,
        Long version
) {
}
