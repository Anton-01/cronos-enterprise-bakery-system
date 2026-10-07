package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.access.AccessGuards;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.iam.shared.IamRules;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.Texts;
import com.ninsky.cronos.iam.token.CredentialDelivery;
import com.ninsky.cronos.iam.token.IssuedToken;
import com.ninsky.cronos.iam.token.TokenPurpose;
import com.ninsky.cronos.iam.token.UserTokens;
import com.ninsky.cronos.iam.user.api.BulkResult;
import com.ninsky.cronos.iam.user.api.BulkStatusRequest;
import com.ninsky.cronos.iam.user.api.IamUserDetail;
import com.ninsky.cronos.iam.user.api.UserStatusRequest;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Status changes of one or many users (spec §3.3, §3.5, §3.9). */
@Service
public class UserStatusService {

    static final Duration RESET_LINK_TTL = Duration.ofMinutes(60);
    private static final Duration MAX_UNTIL = Duration.ofDays(365);
    private static final Set<UserStatus> WARNING_STATES = Set.of(UserStatus.LOCKED, UserStatus.DEACTIVATED);

    private final UserReadCustomRepository users;
    private final UserStatusWriter writer;
    private final UserViews views;
    private final AccessGuards guards;
    private final ActorProvider actors;
    private final AuditRecorder recorder;
    private final UserTokens tokens;
    private final ApplicationEventPublisher events;
    private final BulkRunner bulk;
    private final Clock clock;

    public UserStatusService(UserReadCustomRepository users, UserStatusWriter writer, UserViews views, AccessGuards guards,
                             ActorProvider actors, AuditRecorder recorder, UserTokens tokens,
                             ApplicationEventPublisher events, BulkRunner bulk, Clock clock) {
        this.users = users;
        this.writer = writer;
        this.views = views;
        this.guards = guards;
        this.actors = actors;
        this.recorder = recorder;
        this.tokens = tokens;
        this.events = events;
        this.bulk = bulk;
        this.clock = clock;
    }

    @Transactional
    public IamUserDetail change(UUID id, UserStatusRequest request) {
        Actor actor = actors.require();
        guards.requireNotSelf(actor, id, "status");
        Violations violations = new Violations();
        UserStatusWriter.Change change = validate(violations, request.status(), request.reason(), request.comment(),
                request.until(), actor);
        violations.invalidIf(request.version() == null, "version", "api.validation.required");
        violations.throwIfAny();
        apply(actor, id, change, request.version());
        return views.detail(users.find(id).orElseThrow());
    }

    public BulkResult changeMany(BulkStatusRequest request) {
        Actor actor = actors.require();
        Violations violations = new Violations();
        List<UUID> ids = bulk.userIds(violations, request.userIds());
        UserStatusWriter.Change change = validate(violations, request.status(), request.reason(), request.comment(), null, actor);
        violations.throwIfAny();
        return bulk.run(actor, ids, "STATUS", Map.of("status", request.status().name()),
                id -> apply(actor, id, change, null));
    }

    /** Guards, transition check, write, follow-ups and audit; {@code expectedVersion} null skips the lock. */
    void apply(Actor actor, UUID id, UserStatusWriter.Change change, Long expectedVersion) {
        if (actor != null) {
            guards.requireNotSelf(actor, id, "status");
        }
        UserRow row = users.find(id).orElseThrow(() -> ApiException.notFound("iam.user.notFound"));
        if (actor != null) {
            guards.requireCanModify(actor, List.of(id));
        }
        if (row.status() != change.status() && !row.status().canMoveTo(change.status())) {
            throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, "status", "iam.user.invalidTransition",
                    row.status().name(), change.status().name());
        }
        if (change.status() != UserStatus.ACTIVE && row.status() == UserStatus.ACTIVE
                && !guards.superAdminsAmong(List.of(id)).isEmpty()) {
            guards.requireRootRemains(List.of(id));
        }
        if (!writer.apply(id, change, expectedVersion)) {
            throw ApiException.concurrentModification();
        }
        if (row.status() == UserStatus.DEACTIVATED && change.status() == UserStatus.ACTIVE) {
            IssuedToken token = tokens.issue(id, TokenPurpose.PASSWORD_RESET, RESET_LINK_TTL);
            events.publishEvent(new CredentialDelivery.PasswordResetLink(id, row.email(), row.displayName(), row.locale(),
                    token.rawToken(), token.expiresAt()));
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        Changes.put(changes, "status", row.status().name(), change.status().name());
        Changes.put(changes, "statusReason", row.statusReason() == null ? null : row.statusReason().name(),
                change.reason() == null ? null : change.reason().name());
        Changes.put(changes, "statusUntil", row.statusUntil() == null ? null : row.statusUntil().toString(),
                change.until() == null ? null : change.until().toString());
        recorder.record(AuditEvent.of(AuditAction.USER_STATUS_CHANGED, AuditTargets.USER, id, row.displayName())
                .severity(WARNING_STATES.contains(change.status()) ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .changes(changes)
                .params(Map.of("status", change.status().name()))
                .reason(change.comment())
                .build());
    }

    private UserStatusWriter.Change validate(Violations violations, UserStatus status, StatusReason reason, String rawComment,
                                             Instant until, Actor actor) {
        violations.invalidIf(status == null, "status", "api.validation.required");
        violations.invalidIf(reason == null, "reason", "api.validation.required");
        String comment = reason == StatusReason.OTHER
                ? IamRules.reason(violations, "comment", rawComment)
                : IamRules.optionalText(violations, "comment", rawComment, IamRules.REASON_MAX);
        if (until != null) {
            Instant now = TenantTime.now(clock);
            if (status == null || !status.acceptsUntil()) {
                violations.invalid("until", "iam.user.untilNotAllowed");
            } else if (!until.isAfter(now) || until.isAfter(now.plus(MAX_UNTIL))) {
                violations.invalid("until", "iam.user.untilRange");
            }
        }
        return new UserStatusWriter.Change(status, reason, Texts.clean(comment), until, actor == null ? null : actor.id());
    }
}
