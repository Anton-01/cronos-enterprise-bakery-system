package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.iam.access.AccessGuards;
import com.ninsky.cronos.iam.access.UserAccessChanges;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.policy.PasswordPolicy;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.iam.shared.IamRules;
import com.ninsky.cronos.iam.shared.KeyedRateLimiter;
import com.ninsky.cronos.iam.shared.Masking;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.token.CredentialDelivery;
import com.ninsky.cronos.iam.token.IssuedToken;
import com.ninsky.cronos.iam.token.TokenPurpose;
import com.ninsky.cronos.iam.token.UserTokens;
import com.ninsky.cronos.iam.user.api.CreateIamUserRequest;
import com.ninsky.cronos.iam.user.api.CreateIamUserRequest.ActivationMode;
import com.ninsky.cronos.iam.user.api.IamUserDetail;
import com.ninsky.cronos.iam.user.api.IamUserSummary;
import com.ninsky.cronos.iam.user.api.UpdateIamUserRequest;
import com.ninsky.cronos.iam.user.api.UserAvailability;
import com.ninsky.cronos.iam.user.api.UserStats;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import com.ninsky.cronos.iam.shared.AccountCreated;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** User profile lifecycle (spec §3.5): list, stats, availability, create, read, update. */
@Service
public class IamUserService {

    static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(48);

    private final UserReadCustomRepository reads;
    private final UserWriteCustomRepository writes;
    private final UserStatsCustomRepository stats;
    private final UserViews views;
    private final UserProfileRules rules;
    private final UserAccessChanges accessChanges;
    private final UserAvatarService avatars;
    private final AccessGuards guards;
    private final ActorProvider actors;
    private final AuditRecorder recorder;
    private final UserTokens tokens;
    private final PasswordPolicy passwordPolicy;
    private final SecurityPolicyProvider policies;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final KeyedRateLimiter availabilityLimiter = new KeyedRateLimiter(30, Duration.ofMinutes(1));

    public IamUserService(UserReadCustomRepository reads, UserWriteCustomRepository writes, UserStatsCustomRepository stats, UserViews views,
                       UserProfileRules rules, UserAccessChanges accessChanges, UserAvatarService avatars,
                       AccessGuards guards, ActorProvider actors, AuditRecorder recorder, UserTokens tokens,
                       PasswordPolicy passwordPolicy, SecurityPolicyProvider policies, PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher events,
                       PlatformTransactionManager transactions, Clock clock) {
        this.reads = reads;
        this.writes = writes;
        this.stats = stats;
        this.views = views;
        this.rules = rules;
        this.accessChanges = accessChanges;
        this.avatars = avatars;
        this.guards = guards;
        this.actors = actors;
        this.recorder = recorder;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.policies = policies;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CatalogPage<IamUserSummary> list(UserSearch search, PageQuery page) {
        return views.page(search, page);
    }

    @Transactional(readOnly = true)
    public UserStats stats() {
        return stats.stats();
    }

    /** Never reveals anything but a boolean; malformed values are reported as unavailable. */
    @Transactional(readOnly = true)
    public UserAvailability availability(String username, String email, UUID excludeId) {
        availabilityLimiter.acquire(actors.require().id());
        String normalizedUsername = UserProfileRules.normalizeUsername(username);
        String normalizedEmail = UserProfileRules.normalizeEmail(email);
        Boolean usernameAvailable = username == null ? null
                : normalizedUsername != null && !reads.usernameTaken(normalizedUsername, excludeId);
        Boolean emailAvailable = email == null ? null
                : normalizedEmail != null && !reads.emailTaken(normalizedEmail, excludeId);
        return new UserAvailability(usernameAvailable, emailAvailable);
    }

    @Transactional(readOnly = true)
    public IamUserDetail detail(UUID id) {
        return views.detail(require(id));
    }

    /** Image work and storage happen before the transaction; the stored object is dropped if it fails. */
    public IamUserDetail create(CreateIamUserRequest request, byte[] avatar, Locale locale) {
        Actor actor = actors.require();
        UUID id = UUID.randomUUID();
        AvatarKey avatarKey = avatar == null || avatar.length == 0 ? null : avatars.store(id, avatar, "avatar");
        try {
            UUID created = Objects.requireNonNull(tx.execute(status -> insert(id, request, avatarKey, actor, locale)));
            return detail(created);
        } catch (RuntimeException e) {
            if (avatarKey != null) {
                avatars.discard(avatarKey);
            }
            throw e;
        }
    }

    private UUID insert(UUID id, CreateIamUserRequest request, AvatarKey avatarKey, Actor actor, Locale locale) {
        Violations violations = new Violations();
        UserProfile profile = rules.validate(violations, input(request), null);
        List<Long> roleIds = IamRules.distinctIds(violations, "roleIds", request.roleIds());
        List<Long> groupIds = IamRules.distinctIds(violations, "permissionGroupIds", request.permissionGroupIds());
        violations.invalidIf(request.activationMode() == null, "activationMode", "api.validation.required");
        violations.throwIfAny();

        Instant now = TenantTime.now(clock);
        boolean temporary = request.activationMode() == ActivationMode.TEMPORARY_PASSWORD;
        String temporaryPassword = temporary ? passwordPolicy.generateTemporary() : null;
        writes.insert(id, profile, temporary ? passwordEncoder.encode(temporaryPassword) : null, temporary,
                actor.id(), actor.username(), now);
        events.publishEvent(new AccountCreated(id));
        if (avatarKey != null) {
            writes.setAvatarKey(id, avatarKey.value());
        }
        UserAccessChanges.Evaluation access = accessChanges.assignInitial(id,
                new UserAccessChanges.Proposal(roleIds, groupIds, List.of(), List.of()),
                UserAccessChanges.Fields.ACCESS, actor, locale);

        if (temporary) {
            events.publishEvent(new CredentialDelivery.TemporaryPassword(id, profile.email(), profile.displayName(),
                    profile.locale(), temporaryPassword));
        } else {
            IssuedToken token = tokens.issue(id, TokenPurpose.INVITATION, Duration.ofHours(policies.current().invitationTtlHours()));
            events.publishEvent(new CredentialDelivery.Invitation(id, profile.email(), profile.displayName(),
                    profile.locale(), token.rawToken(), token.expiresAt()));
        }

        Map<String, Object> changes = profileChanges(null, profile);
        Changes.put(changes, "roles", null, access.after().roles().stream().map(r -> r.name()).sorted().toList());
        Changes.put(changes, "permissionGroups", null, access.after().groups().stream().map(g -> g.name()).sorted().toList());
        Changes.put(changes, "activationMode", null, request.activationMode().name());
        recorder.record(AuditEvent.of(AuditAction.USER_CREATED, AuditTargets.USER, id, profile.displayName())
                .changes(changes).build());
        return id;
    }

    @Transactional
    public IamUserDetail update(UUID id, UpdateIamUserRequest request) {
        Actor actor = actors.require();
        UserRow existing = require(id);
        guards.requireCanModify(actor, List.of(id));
        Violations violations = new Violations();
        UserProfile profile = rules.validate(violations, input(request), existing);
        violations.invalidIf(request.version() == null, "version", "api.validation.required");
        violations.throwIfAny();

        boolean emailChanged = !Objects.equals(existing.email(), profile.email());
        Instant now = TenantTime.now(clock);
        if (!writes.update(id, profile, emailChanged, actor.id(), now, request.version())) {
            throw ApiException.concurrentModification();
        }
        String label = profile.displayName();
        if (!existing.username().equals(profile.username())) {
            recorder.record(AuditEvent.of(AuditAction.USER_RENAMED, AuditTargets.USER, id, label)
                    .changes(Changes.of("username", existing.username(), profile.username())).build());
        }
        if (emailChanged) {
            IssuedToken token = tokens.issue(id, TokenPurpose.EMAIL_VERIFICATION, EMAIL_VERIFICATION_TTL);
            events.publishEvent(new CredentialDelivery.EmailVerification(id, profile.email(), label, profile.locale(),
                    token.rawToken(), token.expiresAt()));
            if (existing.email() != null) {
                events.publishEvent(new CredentialDelivery.EmailChangedNotice(id, existing.email(), label, profile.locale(),
                        Masking.email(profile.email())));
            }
            recorder.record(AuditEvent.of(AuditAction.USER_EMAIL_CHANGED, AuditTargets.USER, id, label)
                    .changes(Changes.of("email", Masking.email(existing.email()), Masking.email(profile.email()))).build());
        }
        Map<String, Object> changes = profileChanges(existing, profile);
        if (!changes.isEmpty()) {
            recorder.record(AuditEvent.of(AuditAction.USER_UPDATED, AuditTargets.USER, id, label).changes(changes).build());
        }
        return views.detail(require(id));
    }

    private UserRow require(UUID id) {
        return reads.find(id).orElseThrow(() -> ApiException.notFound("iam.user.notFound"));
    }

    /** Per-field diff; contact data masked, never secrets. */
    private static Map<String, Object> profileChanges(UserRow from, UserProfile to) {
        Map<String, Object> changes = new LinkedHashMap<>();
        Changes.put(changes, "username", from == null ? null : from.username(), to.username());
        Changes.put(changes, "email", from == null ? null : Masking.email(from.email()), Masking.email(to.email()));
        Changes.put(changes, "firstName", from == null ? null : from.firstName(), to.firstName());
        Changes.put(changes, "lastName", from == null ? null : from.lastName(), to.lastName());
        Changes.put(changes, "phoneNumber", from == null ? null : Masking.phone(from.phoneNumber()), Masking.phone(to.phoneNumber()));
        Changes.put(changes, "jobTitle", from == null ? null : from.jobTitle(), to.jobTitle());
        Changes.put(changes, "department", from == null ? null : from.department(), to.department());
        Changes.put(changes, "employeeNumber", from == null ? null : from.employeeNumber(), to.employeeNumber());
        Changes.put(changes, "locale", from == null ? null : from.locale(), to.locale());
        Changes.put(changes, "accessExpiresAt", from == null || from.accessExpiresAt() == null ? null : from.accessExpiresAt().toString(),
                to.accessExpiresAt() == null ? null : to.accessExpiresAt().toString());
        Changes.put(changes, "requireTwoFactor", from == null ? null : from.requireTwoFactor(), to.requireTwoFactor());
        return changes;
    }

    private static UserProfileRules.Input input(CreateIamUserRequest r) {
        return new UserProfileRules.Input(r.username(), r.email(), r.firstName(), r.lastName(), r.phoneNumber(), r.jobTitle(),
                r.department(), r.employeeNumber(), r.locale(), r.accessExpiresAt(), r.requireTwoFactor());
    }

    private static UserProfileRules.Input input(UpdateIamUserRequest r) {
        return new UserProfileRules.Input(r.username(), r.email(), r.firstName(), r.lastName(), r.phoneNumber(), r.jobTitle(),
                r.department(), r.employeeNumber(), r.locale(), r.accessExpiresAt(), r.requireTwoFactor());
    }
}
