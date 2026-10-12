package com.ninsky.cronos.identity.domain;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;

import java.util.List;
import java.util.UUID;

/** Domain events of self-registration review (§3). Aggregate: the registration request. */
public final class RegistrationEvents {

    private static final String REGISTRATION = "REGISTRATION";

    private RegistrationEvents() {
    }

    /** A self-registration was submitted and waits for an administrator. */
    @EventType("registration.submitted")
    public record RegistrationSubmitted(UUID registrationId, UUID userId, String reference, boolean emailVerificationRequired)
            implements DomainEvent {
        @Override public String aggregateType() { return REGISTRATION; }
        @Override public UUID aggregateId() { return registrationId; }
    }

    /** Approved with roles: the account is ACTIVE. */
    @EventType("registration.approved")
    public record RegistrationApproved(UUID registrationId, UUID userId, UUID reviewerId, List<Long> roleIds) implements DomainEvent {
        public RegistrationApproved {
            roleIds = List.copyOf(roleIds);
        }

        @Override public String aggregateType() { return REGISTRATION; }
        @Override public UUID aggregateId() { return registrationId; }
    }

    /** Rejected: the account is DEACTIVATED; the applicant is emailed only when {@code notifyApplicant}. */
    @EventType("registration.rejected")
    public record RegistrationRejected(UUID registrationId, UUID userId, UUID reviewerId, RejectReason reason, String comment,
                                       boolean notifyApplicant) implements DomainEvent {
        @Override public String aggregateType() { return REGISTRATION; }
        @Override public UUID aggregateId() { return registrationId; }
    }

    /** Nobody decided before {@code expires_at}: the account is deactivated. */
    @EventType("registration.expired")
    public record RegistrationExpired(UUID registrationId, UUID userId) implements DomainEvent {
        @Override public String aggregateType() { return REGISTRATION; }
        @Override public UUID aggregateId() { return registrationId; }
    }
}
