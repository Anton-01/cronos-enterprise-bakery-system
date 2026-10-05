package com.ninsky.cronos.iam.user.api;

import java.time.LocalDate;
import java.util.List;

/** {@code user} part of {@code POST /iam/users} (spec §3.5). */
public record CreateUserRequest(
        String username, String email, String firstName, String lastName, String phoneNumber, String jobTitle,
        String department, String employeeNumber, String locale, List<Long> roleIds, List<Long> permissionGroupIds,
        LocalDate accessExpiresAt, ActivationMode activationMode, Boolean requireTwoFactor
) {
    public enum ActivationMode {
        INVITATION,
        TEMPORARY_PASSWORD
    }
}
