package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.shared.UserRef;

import java.time.LocalDate;

/** Normalised profile fields shared by create and update (spec §3.2). */
public record UserProfile(
        String username, String email, String firstName, String lastName, String phoneNumber, String jobTitle,
        String department, String employeeNumber, String locale, LocalDate accessExpiresAt, boolean requireTwoFactor
) {
    public String displayName() {
        return UserRef.displayName(firstName, lastName, username);
    }
}
