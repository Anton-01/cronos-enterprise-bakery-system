package com.ninsky.cronos.identity.domain;

import com.ninsky.cronos.eventing.domain.DomainEvent;
import com.ninsky.cronos.eventing.domain.EventType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain events of the identity context (§8.2). Each carries ids and the minimum facts consumers need; never a
 * password, token or secret. Aggregate: the user account ({@code USER}) or the server session ({@code SESSION}).
 */
public final class IdentityEvents {

    private static final String USER = "USER";
    private static final String SESSION = "SESSION";
    /** Aggregate of failed attempts against identifiers that match no account. */
    private static final UUID UNKNOWN_USER = new UUID(0L, 0L);

    private IdentityEvents() {
    }

    /**
     * Where a sign-in attempt came from, captured on the request thread so outbox consumers (login history, device
     * recognition) need no request. {@code identifierHash} is the SHA-256 of the typed identifier, never the value.
     */
    public record SignInContext(String identifierHash, String label, String ipAddress, String userAgent, String browser,
                                String operatingSystem, String device, String location, String traceId, Instant at) {
    }

    /**
     * A sign-in completed (password and, when enabled, second factor). Stored atomically with the new session; its
     * consumers write the login history, the audit entry, the device fingerprint (and the new-device alert),
     * {@code last_login_at}, and enforce the concurrent-session limit (performance-latency P0-5).
     *
     * @param context null only for events stored before the context existed
     */
    @EventType("identity.user-signed-in")
    public record UserSignedIn(UUID userId, UUID sessionId, boolean rememberMe, boolean twoFactorUsed, String ipAddress,
                               SignInContext context) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        @Override public UUID aggregateId() { return userId; }
    }

    /**
     * A sign-in attempt that did not complete ({@code outcome} is never SUCCESS). Consumers write the login history and
     * the audit entry and keep {@code users.failed_login_attempts} in step with the Redis counter.
     *
     * @param userId         null for an unknown identifier
     * @param failedAttempts failures counted in the current window, null when this attempt was not counted
     */
    @EventType("identity.sign-in-failed")
    public record SignInFailed(UUID userId, SignInOutcome outcome, String failureReason, boolean twoFactorUsed,
                               Integer failedAttempts, SignInContext context) implements DomainEvent {
        @Override public String aggregateType() { return USER; }
        /** Unknown identifiers get one ordering lane each, so a flood against one name never delays the others. */
        @Override public UUID aggregateId() {
            if (userId != null) {
                return userId;
            }
            return context == null || context.identifierHash() == null ? UNKNOWN_USER
                    : UUID.nameUUIDFromBytes(context.identifierHash().getBytes(StandardCharsets.UTF_8));
        }
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
