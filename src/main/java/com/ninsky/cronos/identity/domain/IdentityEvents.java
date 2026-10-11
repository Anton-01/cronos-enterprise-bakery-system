package com.ninsky.cronos.identity.domain;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events of the identity context (§8.2). Each carries ids and the minimum facts consumers need; never a
 * password, token or secret. Aggregate: the user account ({@code USER}) or the server session ({@code SESSION}).
 */
public final class IdentityEvents {

    private static final String USER = "USER";
    private static final String SESSION = "SESSION";

    private IdentityEvents() {
    }

    /** A sign-in completed (password and, when enabled, second factor). */
    @EventType("identity.user-signed-in")
    public record UserSignedIn(UUID userId, UUID sessionId, boolean rememberMe, boolean twoFactorUsed, String ipAddress)
            implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }

    /** Too many failed sign-ins locked the account until {@code lockedUntil}. */
    @EventType("identity.account-locked")
    public record AccountLocked(UUID userId, Instant lockedUntil, int lockCount, int failedAttempts) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }

    /** A lock ended (expired, lifted by a password reset or by an administrator). */
    @EventType("identity.account-unlocked")
    public record AccountUnlocked(UUID userId, String reason) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }

    /** Sign-in from a device fingerprint not seen for this user in 90 days. */
    @EventType("identity.new-device-sign-in")
    public record NewDeviceSignedIn(UUID userId, String browser, String operatingSystem, String device, String ipAddress,
                                    String location, Instant signedInAt) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }

    /** The screen of a session was locked; its access tokens answer 423 until unlocked. */
    @EventType("identity.session-locked")
    public record SessionLocked(UUID sessionId, UUID userId) implements DomainEvent {
        @Override public String aggregateType() { return SESSION; }
        @Override public UUID aggregateId() { return sessionId; }
    }

    /** A locked session was unlocked with the password. */
    @EventType("identity.session-unlocked")
    public record SessionUnlocked(UUID sessionId, UUID userId) implements DomainEvent {
        @Override public String aggregateType() { return SESSION; }
        @Override public UUID aggregateId() { return sessionId; }
    }

    /**
     * Sessions ended (sign-out, reset, admin, limits). {@code sessionId} null means every session of the user.
     */
    @EventType("identity.session-revoked")
    public record SessionRevoked(UUID userId, UUID sessionId, String reason) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }

    /** The password changed (self-service change, reset or activation). */
    @EventType("identity.password-changed")
    public record PasswordChanged(UUID userId, Instant changedAt, String via) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }

    /** The email address was confirmed through its verification link. */
    @EventType("identity.email-verified")
    public record EmailVerified(UUID userId, boolean pendingApproval) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }

    /** Someone tried to self-register with the address of an existing account (the caller never learns it). */
    @EventType("identity.registration-attempt-on-existing-email")
    public record RegistrationAttemptOnExistingEmail(UUID userId) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }

    /** An account was created (self-registration, an administrator, the seed); modules prepare their defaults. */
    @EventType("identity.account-created")
    public record AccountCreated(UUID userId) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }
}
