package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.auth.PasswordResetToken;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.port.auth.PasswordResetTokenRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.policy.PasswordPolicy;
import com.ninsky.cronos.iam.policy.PasswordRules;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.signin.AccountStanding;
import com.ninsky.cronos.iam.signin.AccountStandingCustomRepository;
import com.ninsky.cronos.iam.signin.CredentialWriter;
import com.ninsky.cronos.iam.token.CredentialDelivery;
import com.ninsky.cronos.iam.token.IssuedToken;
import com.ninsky.cronos.iam.token.TokenPurpose;
import com.ninsky.cronos.iam.token.UserTokens;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.iam.user.UserStatusWriter;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Self-service password recovery. Links are {@code user_tokens} PASSWORD_RESET tokens (hash stored,
 * 60 min, single use); completion still accepts legacy {@code password_reset_tokens} as a fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration LINK_TTL = Duration.ofMinutes(60);
    private static final Set<UserStatus> RESETTABLE = EnumSet.of(UserStatus.ACTIVE, UserStatus.LOCKED);

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final UserTokens userTokens;
    private final PasswordPolicy passwordPolicy;
    private final SecurityPolicyProvider policies;
    private final CredentialWriter credentials;
    private final UserStatusWriter statusWriter;
    private final SessionRevoker sessionRevoker;
    private final AccountStandingCustomRepository standings;
    private final UserDirectory userDirectory;
    private final AuditRecorder recorder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** Silent for unknown or non-resettable accounts (no user enumeration). */
    @Transactional
    public void processForgotPassword(String email) {
        log.info("Processing forgot password request");
        userRepository.findByEmail(email).ifPresent(user -> standings.find(user.getId())
                .filter(standing -> RESETTABLE.contains(standing.status()))
                .ifPresent(standing -> {
                    IssuedToken token = userTokens.issue(user.getId(), TokenPurpose.PASSWORD_RESET, LINK_TTL);
                    eventPublisher.publishEvent(new CredentialDelivery.PasswordResetLink(user.getId(), user.getEmail(),
                            labelOf(user), standing.locale(), token.rawToken(), token.expiresAt()));
                    log.info("Password reset link issued for user {}", user.getId());
                }));
    }

    @Transactional
    public void resetPasswordWithToken(String tokenStr, String newPassword) {
        UUID userId = userTokens.consume(tokenStr, TokenPurpose.PASSWORD_RESET)
                .or(() -> consumeLegacy(tokenStr))
                .orElseThrow(() -> ApiException.invalid("token", "security.token.invalid"));
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));

        List<String> broken = passwordPolicy.violations(newPassword, user.getUsername(), user.getEmail(), userId);
        if (!broken.isEmpty()) {
            PasswordRules.toViolations(new Violations(), "newPassword", broken, policies.current()).throwIfAny();
        }
        credentials.setPassword(userId, newPassword, false);
        // A completed reset proves control of the mailbox: lift an (automatic or manual) lock.
        standings.find(userId).filter(standing -> standing.status() == UserStatus.LOCKED).map(AccountStanding::userId)
                .ifPresent(id -> statusWriter.apply(id, new UserStatusWriter.Change(UserStatus.ACTIVE, null, "PASSWORD_RESET", null, null), null));
        sessionRevoker.revokeAll(userId, "PASSWORD_RESET");
        recorder.record(AuditEvent.of(AuditAction.PASSWORD_RESET_COMPLETED, AuditTargets.USER, userId, labelOf(user)).build());
        log.info("Password reset completed for user {}", userId);
    }

    /** Pre-IAM raw tokens still in flight. */
    private Optional<UUID> consumeLegacy(String tokenStr) {
        LocalDateTime now = TenantTime.nowLocal(clock);
        return tokenRepository.findByToken(tokenStr)
                .filter(token -> !token.isUsed() && token.getExpiresAt().isAfter(now))
                .map(token -> {
                    token.setUsed(true);
                    tokenRepository.save(token);
                    return token;
                })
                .map(PasswordResetToken::getUserId);
    }

    private String labelOf(User user) {
        return userDirectory.ref(user.getId()).map(UserRef::displayName).orElse(user.getUsername());
    }
}
