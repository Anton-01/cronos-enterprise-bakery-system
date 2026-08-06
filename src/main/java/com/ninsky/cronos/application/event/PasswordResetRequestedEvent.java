package com.ninsky.cronos.application.event;

import lombok.Builder;
import java.util.UUID;

/**
 * Published after a password-reset token has been generated and persisted, whether self-service
 * ({@code PasswordResetService}) or admin-initiated ({@code AdminUserService}) — see
 * {@code EmailNotificationListener}. Carries the raw token directly (a one-time value already in
 * hand at publish time, not something the listener should re-derive by guessing which token to
 * look up).
 */
@Builder
public record PasswordResetRequestedEvent(UUID userId, String resetToken, boolean requestedByAdmin) {}
