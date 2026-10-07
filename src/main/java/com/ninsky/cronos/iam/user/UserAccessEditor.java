package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.access.AccessSnapshot;
import com.ninsky.cronos.iam.access.AccessSnapshotCustomRepository;
import com.ninsky.cronos.iam.access.EffectiveAccess;
import com.ninsky.cronos.iam.access.EffectiveEntry;
import com.ninsky.cronos.iam.access.EffectivePermissionResolver;
import com.ninsky.cronos.iam.access.UserAccessChanges;
import com.ninsky.cronos.iam.access.UserAccessChanges.Fields;
import com.ninsky.cronos.iam.access.UserAccessChanges.Proposal;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.IamRules;
import com.ninsky.cronos.iam.shared.KeyedRateLimiter;
import com.ninsky.cronos.iam.shared.PermissionGroupRef;
import com.ninsky.cronos.iam.sod.SodEvaluator;
import com.ninsky.cronos.iam.sod.SodRuleCustomRepository;
import com.ninsky.cronos.iam.user.api.AccessPreview;
import com.ninsky.cronos.iam.user.api.AccessRequest;
import com.ninsky.cronos.iam.user.api.BulkResult;
import com.ninsky.cronos.iam.user.api.BulkRolesRequest;
import com.ninsky.cronos.iam.user.api.UserAccessView;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-user access editor and bulk role changes (spec §3.6, §3.9). */
@Service
public class UserAccessEditor {

    private final UserReadCustomRepository users;
    private final AccessSnapshotCustomRepository loader;
    private final UserAccessChanges changes;
    private final SodRuleCustomRepository sodRules;
    private final ActorProvider actors;
    private final BulkRunner bulk;
    private final KeyedRateLimiter previewLimiter = new KeyedRateLimiter(60, Duration.ofMinutes(1));

    public UserAccessEditor(UserReadCustomRepository users, AccessSnapshotCustomRepository loader, UserAccessChanges changes,
                            SodRuleCustomRepository sodRules, ActorProvider actors, BulkRunner bulk) {
        this.users = users;
        this.loader = loader;
        this.changes = changes;
        this.sodRules = sodRules;
        this.actors = actors;
        this.bulk = bulk;
    }

    @Transactional(readOnly = true)
    public UserAccessView get(UUID id, Locale locale) {
        UserRow row = require(id);
        AccessSnapshot snapshot = loader.load(id);
        EffectiveAccess access = EffectivePermissionResolver.resolve(snapshot);
        return new UserAccessView(
                users.roles(List.of(id)).getOrDefault(id, List.of()),
                snapshot.groups().stream().map(g -> new PermissionGroupRef(g.id(), g.code(), g.name()))
                        .sorted(Comparator.comparing(PermissionGroupRef::name, String.CASE_INSENSITIVE_ORDER)).toList(),
                snapshot.grants().stream().sorted().toList(),
                snapshot.denials().stream().sorted().toList(),
                visible(access),
                SodEvaluator.evaluate(sodRules.findActive(), access.granted(), locale),
                row.version());
    }

    /** Same validation as the save, without the escalation guard; nothing persisted. */
    @Transactional(readOnly = true)
    public AccessPreview preview(UUID id, AccessRequest request, Locale locale) {
        previewLimiter.acquire(actors.require().id());
        require(id);
        UserAccessChanges.Evaluation evaluation = changes.evaluate(id, proposal(request), Fields.ACCESS, locale);
        return new AccessPreview(visible(evaluation.afterAccess()), evaluation.conflicts(),
                List.copyOf(evaluation.added()), List.copyOf(evaluation.removed()));
    }

    @Transactional
    public UserAccessView save(UUID id, AccessRequest request, Locale locale) {
        Actor actor = actors.require();
        Violations violations = new Violations();
        String reason = IamRules.reason(violations, "reason", request.reason());
        violations.invalidIf(request.version() == null, "version", "api.validation.required");
        Proposal proposal = proposal(violations, request);
        violations.throwIfAny();
        changes.apply(id, proposal, Fields.ACCESS, reason, actor, request.version(), locale);
        return get(id, locale);
    }

    public BulkResult changeRoles(BulkRolesRequest request, Locale locale) {
        Actor actor = actors.require();
        Violations violations = new Violations();
        List<UUID> ids = bulk.userIds(violations, request.userIds());
        List<Long> add = IamRules.distinctIds(violations, "addRoleIds", request.addRoleIds());
        List<Long> remove = IamRules.distinctIds(violations, "removeRoleIds", request.removeRoleIds());
        violations.invalidIf(add.isEmpty() && remove.isEmpty(), "addRoleIds", "iam.bulk.rolesRequired");
        violations.invalidIf(add.stream().anyMatch(remove::contains), "removeRoleIds", "iam.bulk.rolesOverlap");
        String reason = IamRules.reason(violations, "reason", request.reason());
        violations.throwIfAny();
        return bulk.run(actor, ids, "ROLES", Map.of("addRoleIds", add, "removeRoleIds", remove), id -> {
            Proposal current = Proposal.of(loader.load(id));
            Set<Long> roles = new LinkedHashSet<>(current.roleIds());
            remove.forEach(roles::remove);
            roles.addAll(add);
            changes.apply(id, new Proposal(List.copyOf(roles), current.groupIds(), current.grants(), current.denials()),
                    Fields.NONE, reason, actor, null, locale);
        });
    }

    private UserRow require(UUID id) {
        return users.find(id).orElseThrow(() -> ApiException.notFound("iam.user.notFound"));
    }

    private static Proposal proposal(AccessRequest request) {
        Violations violations = new Violations();
        Proposal proposal = proposal(violations, request);
        violations.throwIfAny();
        return proposal;
    }

    private static Proposal proposal(Violations violations, AccessRequest request) {
        return new Proposal(
                IamRules.distinctIds(violations, "roleIds", request.roleIds()),
                IamRules.distinctIds(violations, "permissionGroupIds", request.permissionGroupIds()),
                IamRules.permissionCodes(violations, "grants", request.grants()),
                IamRules.permissionCodes(violations, "denials", request.denials()));
    }

    /** Codes with at least one source or an explicit denial (spec §3.6). */
    private static List<EffectiveEntry> visible(EffectiveAccess access) {
        return access.entries().stream()
                .filter(e -> !e.sources().isEmpty() || e.deniedExplicitly())
                .toList();
    }
}
