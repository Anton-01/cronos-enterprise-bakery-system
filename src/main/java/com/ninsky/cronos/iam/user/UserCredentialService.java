package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.access.AccessGuards;
import com.ninsky.cronos.iam.access.AccessVersions;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.policy.PasswordPolicy;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.IamRules;
import com.ninsky.cronos.iam.shared.KeyedRateLimiter;
import com.ninsky.cronos.iam.shared.Masking;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.token.CredentialDelivery;
import com.ninsky.cronos.iam.token.IssuedToken;
import com.ninsky.cronos.iam.token.TokenPurpose;
import com.ninsky.cronos.iam.twofactor.TwoFactorAccountService;
import com.ninsky.cronos.iam.token.UserTokens;
import com.ninsky.cronos.iam.user.api.IamUserDetail;
import com.ninsky.cronos.iam.user.api.PasswordResetIssued;
import com.ninsky.cronos.iam.user.api.PasswordResetRequest;
import com.ninsky.cronos.iam.user.api.PasswordResetRequest.Mode;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Admin credential actions (spec §3.5 "Credentials"); never on the caller's own account. */
@Service
public class UserCredentialService {

    private static final Set<UserStatus> RESETTABLE = Set.of(UserStatus.ACTIVE, UserStatus.LOCKED);

    private final UserReadCustomRepository users;
    private final UserWriteCustomRepository writes;
    private final UserViews views;
    private final AccessGuards guards;
    private final AccessVersions versions;
    private final SessionRevoker revoker;
    private final ActorProvider actors;
    private final AuditRecorder recorder;
    private final UserTokens tokens;
    private final PasswordPolicy passwordPolicy;
    private final SecurityPolicyProvider policies;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final TwoFactorAccountService twoFactor;
    private final Clock clock;
    private final KeyedRateLimiter resetLimiter = new KeyedRateLimiter(5, Duration.ofHours(1));
    private final KeyedRateLimiter invitationLimiter = new KeyedRateLimiter(5, Duration.ofHours(1));

    public UserCredentialService(UserReadCustomRepository users, UserWriteCustomRepository writes, UserViews views, AccessGuards guards,
                                 AccessVersions versions, SessionRevoker revoker, ActorProvider actors, AuditRecorder recorder,
                                 UserTokens tokens, PasswordPolicy passwordPolicy, SecurityPolicyProvider policies,
                                 PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
                                 TwoFactorAccountService twoFactor, Clock clock) {
        this.users = users;
        this.writes = writes;
        this.views = views;
        this.guards = guards;
        this.versions = versions;
        this.revoker = revoker;
        this.actors = actors;
        this.recorder = recorder;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.policies = policies;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.twoFactor = twoFactor;
        this.clock = clock;
    }

    @Transactional
    public PasswordResetIssued resetPassword(UUID id, PasswordResetRequest request) {
        Actor actor = actors.require();
        UserRow row = target(actor, id);
        if (request == null || request.mode() == null) {
            throw ApiException.invalid("mode", "api.validation.required");
        }
        if (!RESETTABLE.contains(row.status())) {
            throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, "status", "iam.user.resetNotAllowed", row.status().name());
        }
        resetLimiter.acquire(id);
        Instant now = TenantTime.now(clock);
        Instant expiresAt = null;
        if (request.mode() == Mode.EMAIL_LINK) {
            IssuedToken token = tokens.issue(id, TokenPurpose.PASSWORD_RESET, UserStatusService.RESET_LINK_TTL);
            expiresAt = token.expiresAt();
            writes.touch(id, actor.id(), now);
            events.publishEvent(new CredentialDelivery.PasswordResetLink(id, row.email(), row.displayName(), row.locale(),
                    token.rawToken(), expiresAt));
        } else {
            tokens.invalidate(id, TokenPurpose.PASSWORD_RESET);
            String temporary = passwordPolicy.generateTemporary();
            writes.setTemporaryPassword(id, passwordEncoder.encode(temporary), actor.id(), now);
            events.publishEvent(new CredentialDelivery.TemporaryPassword(id, row.email(), row.displayName(), row.locale(), temporary));
        }
        if (!Boolean.FALSE.equals(request.revokeSessions())) {
            revoker.revokeAll(id, "PASSWORD_RESET");
        }
        recorder.record(AuditEvent.of(AuditAction.USER_PASSWORD_RESET_ISSUED, AuditTargets.USER, id, row.displayName())
                .severity(AuditSeverity.WARNING)
                .params(Map.of("mode", request.mode().name()))
                .build());
        return new PasswordResetIssued(request.mode(), Masking.email(row.email()), expiresAt);
    }

    @Transactional
    public IamUserDetail requirePasswordChange(UUID id) {
        Actor actor = actors.require();
        UserRow row = target(actor, id);
        writes.requirePasswordChange(id, actor.id(), TenantTime.now(clock));
        recorder.record(AuditEvent.of(AuditAction.USER_PASSWORD_CHANGE_REQUIRED, AuditTargets.USER, id, row.displayName()).build());
        return views.detail(users.find(id).orElseThrow());
    }

    @Transactional
    public IamUserDetail resetTwoFactor(UUID id, String rawReason) {
        Actor actor = actors.require();
        UserRow row = target(actor, id);
        Violations violations = new Violations();
        String reason = IamRules.reason(violations, "reason", rawReason);
        violations.throwIfAny();
        writes.resetTwoFactor(id, actor.id(), TenantTime.now(clock));
        twoFactor.reset(id);
        versions.bump(List.of(id));
        revoker.revokeAll(id, "TWO_FACTOR_RESET");
        recorder.record(AuditEvent.of(AuditAction.USER_2FA_RESET, AuditTargets.USER, id, row.displayName())
                .severity(AuditSeverity.WARNING).reason(reason).build());
        return views.detail(users.find(id).orElseThrow());
    }

    @Transactional
    public IamUserDetail resendInvitation(UUID id) {
        Actor actor = actors.require();
        UserRow row = target(actor, id);
        if (row.status() != UserStatus.PENDING_ACTIVATION) {
            throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, "status", "iam.user.notPending");
        }
        invitationLimiter.acquire(id);
        IssuedToken token = tokens.issue(id, TokenPurpose.INVITATION, Duration.ofHours(policies.current().invitationTtlHours()));
        writes.touch(id, actor.id(), TenantTime.now(clock));
        events.publishEvent(new CredentialDelivery.Invitation(id, row.email(), row.displayName(), row.locale(),
                token.rawToken(), token.expiresAt()));
        recorder.record(AuditEvent.of(AuditAction.INVITATION_RESENT, AuditTargets.USER, id, row.displayName()).build());
        return views.detail(users.find(id).orElseThrow());
    }

    private UserRow target(Actor actor, UUID id) {
        guards.requireNotSelf(actor, id, null);
        UserRow row = users.find(id).orElseThrow(() -> ApiException.notFound("iam.user.notFound"));
        guards.requireCanModify(actor, List.of(id));
        return row;
    }
}
