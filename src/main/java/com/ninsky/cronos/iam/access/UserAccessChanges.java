package com.ninsky.cronos.iam.access;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditOutcome;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.permission.PermissionCatalog;
import com.ninsky.cronos.iam.permission.PermissionDefinition;
import com.ninsky.cronos.iam.permission.PermissionRisk;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.sod.SodConflict;
import com.ninsky.cronos.iam.sod.SodEvaluator;
import com.ninsky.cronos.iam.sod.SodRuleRepository;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validates, guards, persists and audits a change of one user's roles, groups, grants and denials
 * (spec §3.6, §3.7, §1.4.5, §1.4.6). Shared by the access editor, bulk roles and role membership.
 */
@Service
@RequiredArgsConstructor
public class UserAccessChanges {

    private final AccessSnapshotLoader loader;
    private final NamedParameterJdbcTemplate jdbc;
    private final AccessVersions versions;
    private final SessionRevoker revoker;
    private final AccessGuards guards;
    private final SodRuleRepository sodRules;
    private final AuditRecorder recorder;
    private final Clock clock;

    /** Requested assignment; list order defines the {@code field[i]} of errors. */
    public record Proposal(List<Long> roleIds, List<Long> groupIds, List<String> grants, List<String> denials) {
        public Proposal {
            roleIds = List.copyOf(roleIds);
            groupIds = List.copyOf(groupIds);
            grants = List.copyOf(grants);
            denials = List.copyOf(denials);
        }

        public static Proposal of(AccessSnapshot snapshot) {
            return new Proposal(snapshot.roles().stream().map(RoleGrant::id).toList(),
                    snapshot.groups().stream().map(GroupGrant::id).toList(),
                    List.copyOf(snapshot.grants()), List.copyOf(snapshot.denials()));
        }
    }

    /** Request field names used in error paths. */
    public record Fields(String roles, String groups, String grants) {
        public static final Fields ACCESS = new Fields("roleIds", "permissionGroupIds", "grants");
        /** Membership changes made from elsewhere: errors carry no field. */
        public static final Fields NONE = new Fields(null, null, null);
    }

    public record Evaluation(AccessSnapshot before, AccessSnapshot after, EffectiveAccess beforeAccess,
                             EffectiveAccess afterAccess, List<SodConflict> conflicts) {
        public Set<String> added() {
            return EffectiveAccess.added(beforeAccess, afterAccess);
        }

        public Set<String> removed() {
            return EffectiveAccess.removed(beforeAccess, afterAccess);
        }

        public boolean assignmentChanged() {
            return !ids(before.roles(), RoleGrant::id).equals(ids(after.roles(), RoleGrant::id))
                    || !ids(before.groups(), GroupGrant::id).equals(ids(after.groups(), GroupGrant::id))
                    || !before.grants().equals(after.grants()) || !before.denials().equals(after.denials());
        }
    }

    /** Loads both states and validates ids/codes; nothing is persisted. */
    @Transactional(readOnly = true)
    public Evaluation evaluate(UUID userId, Proposal proposal, Fields fields, Locale locale) {
        AccessSnapshot before = loader.load(userId);
        Set<Long> currentRoles = ids(before.roles(), RoleGrant::id);
        Set<Long> currentGroups = ids(before.groups(), GroupGrant::id);
        Violations violations = new Violations();

        Map<Long, RoleGrant> roles = loader.loadRoles(proposal.roleIds()).stream()
                .collect(Collectors.toMap(RoleGrant::id, Function.identity()));
        for (int i = 0; i < proposal.roleIds().size(); i++) {
            Long id = proposal.roleIds().get(i);
            RoleGrant role = roles.get(id);
            violations.invalidIf(role == null || (!role.active() && !currentRoles.contains(id)),
                    at(fields.roles(), i), "api.validation.unknownRole", id);
        }
        Map<Long, GroupGrant> groups = loader.loadGroups(proposal.groupIds()).stream()
                .collect(Collectors.toMap(GroupGrant::id, Function.identity()));
        for (int i = 0; i < proposal.groupIds().size(); i++) {
            Long id = proposal.groupIds().get(i);
            GroupGrant group = groups.get(id);
            violations.invalidIf(group == null || (!group.active() && !currentGroups.contains(id)),
                    at(fields.groups(), i), "api.validation.unknownGroup", id);
        }
        for (int i = 0; i < proposal.grants().size(); i++) {
            violations.invalidIf(proposal.denials().contains(proposal.grants().get(i)),
                    at(fields.grants(), i), "iam.access.grantAndDeny", proposal.grants().get(i));
        }
        violations.throwIfAny();

        AccessSnapshot after = before.withAssignments(
                proposal.roleIds().stream().map(roles::get).toList(),
                proposal.groupIds().stream().map(groups::get).toList(),
                Set.copyOf(proposal.grants()), Set.copyOf(proposal.denials()));
        EffectiveAccess afterAccess = EffectivePermissionResolver.resolve(after);
        return new Evaluation(before, after, EffectivePermissionResolver.resolve(before), afterAccess,
                SodEvaluator.evaluate(sodRules.findActive(), afterAccess.granted(), locale));
    }

    /** Full save: guards, optimistic lock on {@code users.version}, persistence, revocation, audit. */
    @Transactional
    public Evaluation apply(UUID userId, Proposal proposal, Fields fields, String reason, Actor actor,
                            Long expectedVersion, Locale locale) {
        guards.requireNotSelf(actor, userId, null);
        requireUser(userId);
        guards.requireCanModify(actor, List.of(userId));
        Evaluation evaluation = evaluate(userId, proposal, fields, locale);
        requireNoEscalation(actor, proposal, fields, evaluation);
        if (evaluation.before().holdsActiveSuperAdmin() && !evaluation.after().holdsActiveSuperAdmin()) {
            guards.requireRootRemains(List.of(userId));
        }
        evaluation.conflicts().stream()
                .filter(SodConflict::blocking)
                .filter(c -> !evaluation.afterAccess().superAdmin())
                .findFirst()
                .ifPresent(c -> {
                    throw ApiException.of(ApiErrorCode.SOD_CONFLICT, null, "iam.sod.blocking", c.name());
                });

        touchUser(userId, actor, expectedVersion);
        if (!evaluation.assignmentChanged()) {
            return evaluation;
        }
        persist(userId, evaluation, actor);
        versions.bump(List.of(userId));
        if (!evaluation.removed().isEmpty()) {
            revoker.revokeRefreshTokens(userId);
        }
        audit(userId, evaluation, reason);
        return evaluation;
    }

    private void requireNoEscalation(Actor actor, Proposal proposal, Fields fields, Evaluation evaluation) {
        Set<Long> currentRoles = ids(evaluation.before().roles(), RoleGrant::id);
        Set<Long> currentGroups = ids(evaluation.before().groups(), GroupGrant::id);
        Map<Long, RoleGrant> roles = evaluation.after().roles().stream().collect(Collectors.toMap(RoleGrant::id, Function.identity()));
        Map<Long, GroupGrant> groups = evaluation.after().groups().stream().collect(Collectors.toMap(GroupGrant::id, Function.identity()));
        List<AccessGuards.Grant> grants = new ArrayList<>();
        for (int i = 0; i < proposal.roleIds().size(); i++) {
            RoleGrant role = roles.get(proposal.roleIds().get(i));
            if (!currentRoles.contains(role.id())) {
                grants.add(new AccessGuards.Grant(at(fields.roles(), i),
                        EffectivePermissionResolver.resolveRole(role), role.isSuperAdmin()));
            }
        }
        for (int i = 0; i < proposal.groupIds().size(); i++) {
            GroupGrant group = groups.get(proposal.groupIds().get(i));
            if (!currentGroups.contains(group.id())) {
                grants.add(AccessGuards.Grant.of(at(fields.groups(), i), catalogOnly(group.permissions())));
            }
        }
        for (int i = 0; i < proposal.grants().size(); i++) {
            String code = proposal.grants().get(i);
            if (!evaluation.before().grants().contains(code)) {
                grants.add(AccessGuards.Grant.of(at(fields.grants(), i), Set.of(code)));
            }
        }
        guards.requireNoEscalation(actor, grants);
    }

    private void requireUser(UUID userId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE id = :id)", Map.of("id", userId), Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw ApiException.notFound("iam.user.notFound");
        }
    }

    /** Bumps the user's version; a stale {@code expectedVersion} is a 409. */
    private void touchUser(UUID userId, Actor actor, Long expectedVersion) {
        int rows = jdbc.update("""
                        UPDATE users SET version = version + 1, updated_at = :now, updated_by_id = :actor
                        WHERE id = :id AND (CAST(:expected AS BIGINT) IS NULL OR version = :expected)""",
                new MapSqlParameterSource("id", userId).addValue("actor", actor.id())
                        .addValue("now", TenantTime.nowLocal(clock)).addValue("expected", expectedVersion));
        if (rows == 0) {
            throw ApiException.concurrentModification();
        }
    }

    private void persist(UUID userId, Evaluation evaluation, Actor actor) {
        var params = new MapSqlParameterSource("userId", userId).addValue("actor", actor.id());
        Set<Long> roleIds = ids(evaluation.after().roles(), RoleGrant::id);
        Set<Long> groupIds = ids(evaluation.after().groups(), GroupGrant::id);
        jdbc.update("DELETE FROM user_roles WHERE user_id = :userId AND NOT (role_id = ANY(:ids))",
                params.addValue("ids", roleIds.toArray(Long[]::new)));
        roleIds.forEach(id -> jdbc.update("""
                INSERT INTO user_roles (user_id, role_id, created_by_id) VALUES (:userId, :roleId, :actor)
                ON CONFLICT DO NOTHING""", new MapSqlParameterSource(params.getValues()).addValue("roleId", id)));
        jdbc.update("DELETE FROM user_permission_groups WHERE user_id = :userId AND NOT (group_id = ANY(:ids))",
                new MapSqlParameterSource(params.getValues()).addValue("ids", groupIds.toArray(Long[]::new)));
        groupIds.forEach(id -> jdbc.update("""
                INSERT INTO user_permission_groups (user_id, group_id, created_by_id) VALUES (:userId, :groupId, :actor)
                ON CONFLICT DO NOTHING""", new MapSqlParameterSource(params.getValues()).addValue("groupId", id)));

        Map<String, String> overrides = new LinkedHashMap<>();
        evaluation.after().grants().forEach(code -> overrides.put(code, "GRANT"));
        evaluation.after().denials().forEach(code -> overrides.put(code, "DENY"));
        jdbc.update("DELETE FROM user_permission_overrides WHERE user_id = :userId AND NOT (permission_code = ANY(:codes))",
                new MapSqlParameterSource(params.getValues()).addValue("codes", overrides.keySet().toArray(String[]::new)));
        overrides.forEach((code, effect) -> jdbc.update("""
                INSERT INTO user_permission_overrides (user_id, permission_code, effect, created_by_id)
                VALUES (:userId, :code, :effect, :actor)
                ON CONFLICT (user_id, permission_code) DO UPDATE SET effect = EXCLUDED.effect
                WHERE user_permission_overrides.effect <> EXCLUDED.effect""",
                new MapSqlParameterSource(params.getValues()).addValue("code", code).addValue("effect", effect)));
    }

    private void audit(UUID userId, Evaluation evaluation, String reason) {
        Map<String, Object> changes = new LinkedHashMap<>();
        diff(changes, "roles", names(evaluation.before().roles(), RoleGrant::name), names(evaluation.after().roles(), RoleGrant::name));
        diff(changes, "permissionGroups", names(evaluation.before().groups(), GroupGrant::name), names(evaluation.after().groups(), GroupGrant::name));
        diff(changes, "grants", sorted(evaluation.before().grants()), sorted(evaluation.after().grants()));
        diff(changes, "denials", sorted(evaluation.before().denials()), sorted(evaluation.after().denials()));
        changes.put("effectiveAdded", sorted(evaluation.added()));
        changes.put("effectiveRemoved", sorted(evaluation.removed()));
        boolean criticalAdded = evaluation.added().stream()
                .map(PermissionCatalog::find).flatMap(Optional::stream)
                .map(PermissionDefinition::risk).anyMatch(PermissionRisk.CRITICAL::equals);
        String label = targetLabel(userId);
        List<SodConflict> warnings = evaluation.conflicts().stream().filter(c -> !c.blocking()).toList();
        recorder.record(AuditEvent.of(AuditAction.USER_ACCESS_CHANGED, AuditTargets.USER, userId, label)
                .severity(criticalAdded ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .changes(changes)
                .params(warnings.isEmpty() ? Map.of() : Map.of("sodConflicts", warnings.stream().map(SodConflict::code).toList()))
                .reason(reason)
                .build());
        if (!warnings.isEmpty()) {
            recorder.record(AuditEvent.of(AuditAction.SOD_CONFLICT_ACCEPTED, AuditTargets.USER, userId, label)
                    .outcome(AuditOutcome.SUCCESS).severity(AuditSeverity.WARNING)
                    .params(Map.of("rules", warnings.stream().map(SodConflict::code).toList(),
                            "detail", warnings.stream().map(SodConflict::name).collect(Collectors.joining(", "))))
                    .reason(reason)
                    .build());
        }
    }

    private String targetLabel(UUID userId) {
        return jdbc.queryForObject("""
                SELECT coalesce(NULLIF(btrim(concat_ws(' ', p.first_name, p.last_name)), ''), u.username)
                FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id WHERE u.id = :id""", Map.of("id", userId), String.class);
    }

    private static void diff(Map<String, Object> changes, String key, List<String> from, List<String> to) {
        if (!from.equals(to)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("from", from);
            change.put("to", to);
            changes.put(key, change);
        }
    }

    private static <T> List<String> names(Collection<T> items, Function<T, String> name) {
        return items.stream().filter(Objects::nonNull).map(name).sorted().toList();
    }

    private static List<String> sorted(Collection<String> codes) {
        return codes.stream().sorted().toList();
    }

    private static Set<String> catalogOnly(Collection<String> codes) {
        return codes.stream().filter(PermissionCatalog::contains).collect(Collectors.toSet());
    }

    private static String at(String base, int index) {
        return base == null ? null : base + "[" + index + "]";
    }

    static <T> Set<Long> ids(Collection<T> items, Function<T, Long> id) {
        return items.stream().filter(Objects::nonNull).map(id).collect(Collectors.toCollection(HashSet::new));
    }
}
