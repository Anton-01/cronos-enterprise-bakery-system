package com.ninsky.cronos.identity.application;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
import com.ninsky.cronos.iam.access.UserAccessChanges;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.IamRules;
import com.ninsky.cronos.iam.shared.RoleRef;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.user.StatusReason;
import com.ninsky.cronos.iam.user.UserReadCustomRepository;
import com.ninsky.cronos.iam.user.UserRow;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.iam.user.UserStatusWriter;
import com.ninsky.cronos.iam.user.api.BulkResult;
import com.ninsky.cronos.identity.api.ApproveRegistrationRequest;
import com.ninsky.cronos.identity.api.BulkRegistrationRequest;
import com.ninsky.cronos.identity.api.RegistrationStats;
import com.ninsky.cronos.identity.api.RegistrationSummary;
import com.ninsky.cronos.identity.api.RejectRegistrationRequest;
import com.ninsky.cronos.identity.domain.RegistrationEvents;
import com.ninsky.cronos.identity.domain.RegistrationStatus;
import com.ninsky.cronos.identity.domain.RejectReason;
import com.ninsky.cronos.identity.infrastructure.RegistrationCustomRepository;
import com.ninsky.cronos.identity.infrastructure.RegistrationCustomRepository.RegistrationRow;
import com.ninsky.cronos.identity.infrastructure.RegistrationQueryCustomRepository;
import com.ninsky.cronos.identity.infrastructure.SignInPolicyProvider;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Administrator review of self-registrations (§3): only PENDING can be decided, every decision is optimistic-locked,
 * audited and published; approving assigns roles through the IAM access rules (privilege-escalation guard included).
 */
@Slf4j
@Service
public class RegistrationReviewService {

    static final int COMMENT_MIN = 10;
    static final int COMMENT_MAX = 500;

    /** Outcome of a single decision: the summary and an optional warning for the envelope message. */
    public record Decision(RegistrationSummary registration, String warning) {
    }

    private final RegistrationCustomRepository registrations;
    private final RegistrationQueryCustomRepository queries;
    private final UserReadCustomRepository users;
    private final UserDirectory directory;
    private final UserStatusWriter statusWriter;
    private final UserAccessChanges accessChanges;
    private final SignInPolicyProvider policies;
    private final ActorProvider actors;
    private final AuditRecorder audit;
    private final DomainEventPublisher events;
    private final MessageSource messages;
    private final TransactionTemplate perItem;
    private final Clock clock;

    public RegistrationReviewService(RegistrationCustomRepository registrations, RegistrationQueryCustomRepository queries,
                                     UserReadCustomRepository users, UserDirectory directory, UserStatusWriter statusWriter,
                                     UserAccessChanges accessChanges, SignInPolicyProvider policies, ActorProvider actors,
                                     AuditRecorder audit, DomainEventPublisher events, MessageSource messages,
                                     PlatformTransactionManager transactions, Clock clock) {
        this.registrations = registrations;
        this.queries = queries;
        this.users = users;
        this.directory = directory;
        this.statusWriter = statusWriter;
        this.accessChanges = accessChanges;
        this.policies = policies;
        this.actors = actors;
        this.audit = audit;
        this.events = events;
        this.messages = messages;
        this.perItem = new TransactionTemplate(transactions);
        this.perItem.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** One page of the queue; default sort {@code submittedAt,asc} (oldest first). */
    @Transactional(readOnly = true)
    public CatalogPage<RegistrationSummary> page(String search, Set<RegistrationStatus> statuses, Boolean emailVerified,
                                                 Integer page, Integer size, String sort) {
        PageQuery query = PageQuery.of(page, size, sort, RegistrationQueryCustomRepository.SORTS, "submittedAt,asc");
        RegistrationQueryCustomRepository.Filter filter = new RegistrationQueryCustomRepository.Filter(search,
                statuses == null ? Set.of() : statuses.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()), emailVerified);
        return CatalogPage.of(summaries(queries.page(filter, query)), query, queries.count(filter));
    }

    @Transactional(readOnly = true)
    public RegistrationStats stats() {
        return queries.stats(TenantTime.now(clock));
    }

    /** @throws ApiException RESOURCE_NOT_FOUND */
    @Transactional(readOnly = true)
    public RegistrationSummary get(UUID id) {
        return summaries(List.of(registrations.find(id).orElseThrow(RegistrationReviewService::notFound))).getFirst();
    }

    /**
     * Approves with roles: the account becomes ACTIVE.
     *
     * @throws ApiException RESOURCE_NOT_FOUND; INVALID_STATE_TRANSITION unless PENDING; CONCURRENT_MODIFICATION;
     *                      PRIVILEGE_ESCALATION when the actor lacks a granted permission
     */
    @Transactional
    public Decision approve(UUID id, ApproveRegistrationRequest request) {
        Actor actor = actors.require();
        Violations violations = new Violations();
        List<Long> roleIds = IamRules.distinctIds(violations, "roleIds", request.roleIds());
        violations.throwIfAny();
        return approve(actor, id, roleIds, request.comment(), request.version());
    }

    /**
     * Rejects: the account is DEACTIVATED; username and email stay reserved.
     *
     * @throws ApiException VALIDATION_ERROR on {@code comment} (OTHER needs 10–500 chars) and the errors of {@link #approve}
     */
    @Transactional
    public Decision reject(UUID id, RejectRegistrationRequest request) {
        Actor actor = actors.require();
        return reject(actor, id, request.reason(), request.comment(), Boolean.TRUE.equals(request.notifyApplicant()), request.version());
    }

    /**
     * The account of a registration (for the reviewer's "resend verification").
     *
     * @throws ApiException RESOURCE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public UUID accountOf(UUID id) {
        return registrations.find(id).orElseThrow(RegistrationReviewService::notFound).userId();
    }

    /**
     * Applies one decision to up to 100 registrations, each in its own transaction; non-pending ones fail with
     * INVALID_STATE_TRANSITION.
     */
    public BulkResult bulk(BulkRegistrationRequest request) {
        Actor actor = actors.require();
        Violations violations = new Violations();
        List<UUID> ids = request.ids().stream().distinct().toList();
        List<Long> roleIds = request.decision() == BulkRegistrationRequest.Decision.APPROVE
                ? IamRules.distinctIds(violations, "roleIds", request.roleIds()) : List.of();
        violations.invalidIf(request.decision() == BulkRegistrationRequest.Decision.APPROVE && roleIds.isEmpty(), "roleIds",
                "api.validation.required");
        violations.invalidIf(request.decision() == BulkRegistrationRequest.Decision.REJECT && request.reason() == null, "reason",
                "api.validation.required");
        violations.throwIfAny();
        Map<Boolean, List<Result>> outcome = ids.stream()
                .map(id -> attempt(id, () -> request.decision() == BulkRegistrationRequest.Decision.APPROVE
                        ? approve(actor, id, roleIds, request.comment(), null)
                        : reject(actor, id, request.reason(), request.comment(), Boolean.TRUE.equals(request.notifyApplicant()), null)))
                .collect(Collectors.partitioningBy(Result::succeeded));
        return new BulkResult(outcome.get(true).stream().map(Result::id).toList(),
                outcome.get(false).stream().map(Result::failure).toList());
    }

    /** Marks PENDING registrations past {@code expires_at} EXPIRED and deactivates their accounts; returns how many. */
    public int expireOverdue(int limit) {
        int expired = 0;
        for (RegistrationRow row : registrations.expiredPending(TenantTime.now(clock), limit)) {
            try {
                perItem.executeWithoutResult(status -> expire(row));
                expired++;
            } catch (RuntimeException e) {
                log.warn("Could not expire registration {}: {}", row.id(), e.getMessage());
            }
        }
        return expired;
    }

    private void expire(RegistrationRow row) {
        Instant now = TenantTime.now(clock);
        if (!registrations.decide(row.id(), RegistrationStatus.EXPIRED, null, null, now, null, null, List.of())) {
            return;
        }
        statusWriter.apply(row.userId(), new UserStatusWriter.Change(UserStatus.DEACTIVATED, StatusReason.OFFBOARDING,
                "REGISTRATION_EXPIRED", null, null), null);
        audit.record(AuditEvent.of(AuditAction.REGISTRATION_EXPIRED, AuditTargets.USER, row.userId(), label(row.userId()))
                .params(Map.of("detail", row.reference())).build());
        events.publish(new RegistrationEvents.RegistrationExpired(row.id(), row.userId()));
    }

    private Decision approve(Actor actor, UUID id, List<Long> roleIds, String comment, Long version) {
        RegistrationRow row = lockPending(id, version);
        if (roleIds.isEmpty()) {
            throw ApiException.invalid("roleIds", "api.validation.required");
        }
        Locale locale = LocaleContextHolder.getLocale();
        accessChanges.assignInitial(row.userId(), new UserAccessChanges.Proposal(roleIds, List.of(), List.of(), List.of()),
                UserAccessChanges.Fields.ACCESS, actor, locale);
        if (!statusWriter.apply(row.userId(), new UserStatusWriter.Change(UserStatus.ACTIVE, null, "REGISTRATION_APPROVED", null,
                actor.id()), null)) {
            throw ApiException.concurrentModification();
        }
        Instant now = TenantTime.now(clock);
        String cleanComment = comment == null || comment.isBlank() ? null : comment.trim();
        if (!registrations.decide(id, RegistrationStatus.APPROVED, version, actor.id(), now, null, cleanComment, roleIds)) {
            throw ApiException.concurrentModification();
        }
        audit.record(AuditEvent.of(AuditAction.REGISTRATION_APPROVED, AuditTargets.USER, row.userId(), label(row.userId()))
                .params(Map.of("detail", row.reference(), "roles", roleIds)).reason(cleanComment).build());
        events.publish(new RegistrationEvents.RegistrationApproved(id, row.userId(), actor.id(), roleIds));
        boolean unverified = users.find(row.userId()).map(user -> !user.emailVerified()).orElse(false);
        String warning = unverified && policies.current().requireEmailVerification()
                ? messages.getMessage("registration.approvedUnverified", null, LocaleContextHolder.getLocale()) : null;
        return new Decision(summary(id), warning);
    }

    private Decision reject(Actor actor, UUID id, RejectReason reason, String comment, boolean notify, Long version) {
        RegistrationRow row = lockPending(id, version);
        Violations violations = new Violations();
        String clean = comment == null || comment.isBlank() ? null : comment.trim();
        violations.invalidIf(reason == null, "reason", "api.validation.required");
        violations.invalidIf(reason == RejectReason.OTHER && (clean == null || clean.length() < COMMENT_MIN), "comment",
                "registration.comment.required", COMMENT_MIN, COMMENT_MAX);
        violations.invalidIf(clean != null && clean.length() > COMMENT_MAX, "comment", "registration.comment.required", COMMENT_MIN,
                COMMENT_MAX);
        violations.throwIfAny();
        StatusReason statusReason = reason == RejectReason.POLICY_VIOLATION ? StatusReason.POLICY_VIOLATION : StatusReason.OTHER;
        if (!statusWriter.apply(row.userId(), new UserStatusWriter.Change(UserStatus.DEACTIVATED, statusReason, "REGISTRATION_REJECTED",
                null, actor.id()), null)) {
            throw ApiException.concurrentModification();
        }
        if (!registrations.decide(id, RegistrationStatus.REJECTED, version, actor.id(), TenantTime.now(clock), reason, clean, List.of())) {
            throw ApiException.concurrentModification();
        }
        audit.record(AuditEvent.of(AuditAction.REGISTRATION_REJECTED, AuditTargets.USER, row.userId(), label(row.userId()))
                .params(Map.of("detail", reason.name())).reason(clean).build());
        events.publish(new RegistrationEvents.RegistrationRejected(id, row.userId(), actor.id(), reason, clean, notify));
        return new Decision(summary(id), null);
    }

    /** PENDING and (when given) at {@code version}; row-locked. */
    private RegistrationRow lockPending(UUID id, Long version) {
        RegistrationRow row = registrations.lock(id).orElseThrow(RegistrationReviewService::notFound);
        if (row.status() != RegistrationStatus.PENDING) {
            throw ApiException.withDetails(ApiErrorCode.INVALID_STATE_TRANSITION, "status",
                    Map.of("from", row.status().name(), "to", "DECIDED"), "registration.notPending", row.status().name());
        }
        if (version != null && row.version() != version) {
            throw ApiException.withDetails(ApiErrorCode.CONCURRENT_MODIFICATION, "version", Map.of("currentVersion", row.version()),
                    "api.concurrentModification");
        }
        return row;
    }

    private record Result(UUID id, boolean succeeded, BulkResult.Failure failure) {
    }

    private Result attempt(UUID id, Supplier<Decision> decision) {
        try {
            perItem.execute(status -> decision.get());
            return new Result(id, true, null);
        } catch (ApiException e) {
            ApiException.Violation first = e.violations().getFirst();
            return new Result(id, false, new BulkResult.Failure(id, first.code().name(),
                    messages.getMessage(first.messageKey(), first.args().toArray(), first.messageKey(), LocaleContextHolder.getLocale())));
        } catch (RuntimeException e) {
            log.error("Bulk registration decision failed for {}", id, e);
            return new Result(id, false, new BulkResult.Failure(id, ApiErrorCode.INTERNAL_ERROR.name(),
                    messages.getMessage(ApiErrorCode.INTERNAL_ERROR.titleKey(), null, LocaleContextHolder.getLocale())));
        }
    }

    private RegistrationSummary summary(UUID id) {
        return summaries(List.of(registrations.find(id).orElseThrow(RegistrationReviewService::notFound))).getFirst();
    }

    private List<RegistrationSummary> summaries(List<RegistrationRow> rows) {
        List<UUID> userIds = rows.stream().map(RegistrationRow::userId).toList();
        Map<UUID, UserRow> accounts = users.findAll(userIds).stream().collect(Collectors.toMap(UserRow::id, Function.identity()));
        Map<UUID, List<RoleRef>> roles = users.roles(userIds);
        Map<UUID, UserRef> reviewers = directory.refs(rows.stream().map(RegistrationRow::reviewedBy).toList());
        Map<UUID, Long> sameIp = queries.sameIpCounts(rows.stream().map(RegistrationRow::id).toList());
        List<RegistrationSummary> summaries = new ArrayList<>(rows.size());
        for (RegistrationRow row : rows) {
            UserRow user = accounts.get(row.userId());
            summaries.add(new RegistrationSummary(row.id(), row.reference(), row.userId(),
                    user == null ? null : user.username(), user == null ? null : user.email(),
                    user == null ? null : user.firstName(), user == null ? null : user.lastName(),
                    user == null ? null : user.displayName(), user == null ? null : user.phoneNumber(),
                    row.businessName(), row.message(), user != null && user.emailVerified(), row.status().name(),
                    user == null ? null : user.locale(), row.submittedAt(), row.ipAddress(), row.userAgent(),
                    sameIp.getOrDefault(row.id(), 0L), row.reviewedAt(), row.reviewedBy() == null ? null : reviewers.get(row.reviewedBy()),
                    row.rejectReason() == null ? null : row.rejectReason().name(), row.reviewComment(),
                    row.status() == RegistrationStatus.APPROVED ? roles.getOrDefault(row.userId(), List.of()) : List.of(),
                    row.expiresAt(), row.version()));
        }
        return List.copyOf(summaries);
    }

    private String label(UUID userId) {
        return directory.ref(userId).map(UserRef::displayName).orElse(null);
    }

    private static ApiException notFound() {
        return ApiException.notFound("registration.notFound");
    }
}
