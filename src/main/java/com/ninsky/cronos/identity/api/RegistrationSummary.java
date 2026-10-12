package com.ninsky.cronos.identity.api;

import com.ninsky.cronos.iam.shared.RoleRef;
import com.ninsky.cronos.iam.shared.UserRef;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One self-registration for reviewers (§3.1).
 *
 * @param sameIpCount other registrations from the same IP in the 24 h before {@code submittedAt}
 */
public record RegistrationSummary(UUID id, String reference, UUID userId, String username, String email, String firstName,
                                  String lastName, String displayName, String phoneNumber, String businessName, String message,
                                  boolean emailVerified, String status, String locale, Instant submittedAt, String ipAddress,
                                  String userAgent, long sameIpCount, Instant reviewedAt, UserRef reviewedBy, String rejectReason,
                                  String reviewComment, List<RoleRef> grantedRoles, Instant expiresAt, long version) {

    public RegistrationSummary {
        grantedRoles = grantedRoles == null ? List.of() : List.copyOf(grantedRoles);
    }
}
