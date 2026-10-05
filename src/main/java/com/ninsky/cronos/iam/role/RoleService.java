package com.ninsky.cronos.iam.role;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.access.AccessGuards;
import com.ninsky.cronos.iam.access.AccessSnapshotLoader;
import com.ninsky.cronos.iam.access.AccessVersions;
import com.ninsky.cronos.iam.access.EffectivePermissionResolver;
import com.ninsky.cronos.iam.access.GroupGrant;
import com.ninsky.cronos.iam.access.RoleGrant;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.access.UserAccessChanges;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.permission.PermissionCatalog;
import com.ninsky.cronos.iam.permission.PermissionDefinition;
import com.ninsky.cronos.iam.permission.PermissionRisk;
import com.ninsky.cronos.iam.role.api.CloneRoleRequest;
import com.ninsky.cronos.iam.role.api.IamRoleDetail;
import com.ninsky.cronos.iam.role.api.IamRoleSummary;
import com.ninsky.cronos.iam.role.api.MembersAdded;
import com.ninsky.cronos.iam.role.api.MembersRemoved;
import com.ninsky.cronos.iam.role.api.MembersRequest;
import com.ninsky.cronos.iam.role.api.RoleRequest;
import com.ninsky.cronos.iam.role.api.StatusRequest;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.iam.shared.IamRules;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.sod.SodEvaluator;
import com.ninsky.cronos.iam.sod.SodRuleRepository;
import com.ninsky.cronos.iam.user.UserSearch;
import com.ninsky.cronos.iam.user.UserViews;
import com.ninsky.cronos.iam.user.api.IamUserSummary;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Roles (spec §4): CRUD, status, clone and membership, with escalation guard, revocation and audit. */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final int MAX_MEMBERS = 200;

    private final RoleRepository roles;
    private final AccessSnapshotLoader loader;
    private final UserAccessChanges accessChanges;
    private final AccessVersions versions;
    private final SessionRevoker revoker;
    private final AccessGuards guards;
    private final SodRuleRepository sodRules;
    private final AuditRecorder recorder;
    private final ActorProvider actors;
    private final UserDirectory directory;
    private final UserViews userViews;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<IamRoleSummary> list(String search, RoleStatus status) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return roles.search(term, status).stream().map(RoleService::summary).toList();
    }

    @Transactional(readOnly = true)
    public IamRoleDetail detail(long id, Locale locale) {
        return detail(require(id), locale);
    }

    @Transactional
    public IamRoleDetail create(RoleRequest request, Locale locale) {
        Actor actor = actors.require();
        Validated role = validate(request, null);
        requireUnique(role.code(), role.name(), null);
        guards.requireNoEscalation(actor, grantsOf(role, Set.of(), Set.of(), request));

        long id = roles.insert(role.code(), role.name(), role.description(), role.color(), actor.id(), actor.username(),
                TenantTime.nowLocal(clock));
        roles.replacePermissions(id, role.storedPermissions());
        roles.replaceGroups(id, role.groupIds());

        Map<String, Object> changes = new LinkedHashMap<>();
        Changes.put(changes, "code", null, role.code());
        Changes.put(changes, "name", null, role.name());
        Changes.put(changes, "description", null, role.description());
        Changes.put(changes, "color", null, role.color());
        Changes.put(changes, "permissions", null, List.copyOf(role.storedPermissions()));
        Changes.put(changes, "permissionGroups", null, role.groups().stream().map(GroupGrant::name).sorted().toList());
        recorder.record(AuditEvent.of(AuditAction.ROLE_CREATED, AuditTargets.ROLE, id, role.name())
                .severity(hasCritical(role.storedPermissions()) ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .changes(changes).build());
        return detail(require(id), locale);
    }

    @Transactional
    public IamRoleDetail update(long id, RoleRequest request, Locale locale) {
        Actor actor = actors.require();
        RoleRow existing = roles.lock(id).orElseThrow(() -> ApiException.notFound("iam.role.notFound"));
        if (existing.isSuperAdmin()) {
            return updateSuperAdmin(existing, request, actor, locale);
        }
        Validated role = validate(request, existing);
        if (existing.system() && !existing.code().equals(role.code())) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, "code", "iam.role.systemCode");
        }
        requireUnique(role.code(), role.name(), id);
        RoleGrant before = grant(id);
        guards.requireNoEscalation(actor, grantsOf(role, before.permissions(),
                before.groups().stream().map(GroupGrant::id).collect(Collectors.toSet()), request));

        if (!roles.update(id, role.code(), role.name(), role.description(), role.color(), request.version(),
                actor.id(), actor.username(), TenantTime.nowLocal(clock))) {
            throw ApiException.concurrentModification();
        }
        roles.replacePermissions(id, role.storedPermissions());
        roles.replaceGroups(id, role.groupIds());
        RoleGrant after = grant(id);
        propagate(id, before, after);

        Map<String, Object> changes = new LinkedHashMap<>();
        Changes.put(changes, "code", existing.code(), role.code());
        Changes.put(changes, "name", existing.name(), role.name());
        Changes.put(changes, "description", existing.description(), role.description());
        Changes.put(changes, "color", existing.color(), role.color());
        Set<String> added = minus(after.permissions(), before.permissions());
        Set<String> removed = minus(before.permissions(), after.permissions());
        if (!added.isEmpty() || !removed.isEmpty()) {
            changes.put("permissions", Map.of("added", List.copyOf(new TreeSet<>(added)), "removed", List.copyOf(new TreeSet<>(removed))));
        }
        Changes.put(changes, "permissionGroups", groupNames(before), groupNames(after));
        recorder.record(AuditEvent.of(AuditAction.ROLE_UPDATED, AuditTargets.ROLE, id, role.name())
                .severity(hasCritical(added) ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .changes(changes).build());
        return detail(require(id), locale);
    }

    @Transactional
    public IamRoleDetail changeStatus(long id, StatusRequest request, Locale locale) {
        Actor actor = actors.require();
        Violations violations = new Violations()
                .invalidIf(request.status() == null, "status", "api.validation.required")
                .invalidIf(request.version() == null, "version", "api.validation.required");
        violations.throwIfAny();
        RoleRow existing = roles.lock(id).orElseThrow(() -> ApiException.notFound("iam.role.notFound"));
        if (existing.system() && request.status() == RoleStatus.INACTIVE) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, "status", "iam.role.systemStatus");
        }
        if (!roles.updateStatus(id, request.status(), request.version(), actor.id(), actor.username(), TenantTime.nowLocal(clock))) {
            throw ApiException.concurrentModification();
        }
        if (existing.status() != request.status()) {
            List<UUID> members = roles.memberIds(id);
            versions.bump(members);
            if (request.status() == RoleStatus.INACTIVE) {
                members.forEach(revoker::revokeRefreshTokens);
            }
            recorder.record(AuditEvent.of(AuditAction.ROLE_STATUS_CHANGED, AuditTargets.ROLE, id, existing.name())
                    .changes(Changes.of("status", existing.status().name(), request.status().name()))
                    .params(Map.of("detail", request.status().name()))
                    .build());
        }
        return detail(require(id), locale);
    }

    @Transactional
    public IamRoleDetail cloneRole(long id, CloneRoleRequest request, Locale locale) {
        Actor actor = actors.require();
        RoleRow source = require(id);
        if (source.isSuperAdmin()) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, null, "iam.role.superAdminClone");
        }
        Violations violations = new Violations();
        String code = IamRules.code(violations, "code", request.code());
        String name = IamRules.requiredText(violations, "name", request.name(), 100);
        violations.throwIfAny();
        requireUnique(code, name, null);
        RoleGrant sourceGrant = grant(id);
        guards.requireNoEscalation(actor, List.of(AccessGuards.Grant.of(null, EffectivePermissionResolver.resolveRole(sourceGrant))));

        long cloneId = roles.insert(code, name, source.description(), source.color(), actor.id(), actor.username(), TenantTime.nowLocal(clock));
        roles.replacePermissions(cloneId, sourceGrant.permissions());
        roles.replaceGroups(cloneId, sourceGrant.groups().stream().map(GroupGrant::id).toList());
        recorder.record(AuditEvent.of(AuditAction.ROLE_CLONED, AuditTargets.ROLE, cloneId, name)
                .params(Map.of("detail", source.name(), "sourceId", id))
                .changes(Changes.of("code", null, code))
                .build());
        return detail(require(cloneId), locale);
    }

    @Transactional
    public void delete(long id) {
        RoleRow role = roles.lock(id).orElseThrow(() -> ApiException.notFound("iam.role.notFound"));
        if (role.system()) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, null, "iam.role.systemDelete");
        }
        if (role.userCount() > 0) {
            throw ApiException.of(ApiErrorCode.RESOURCE_IN_USE, null, "iam.role.inUse", role.userCount());
        }
        roles.delete(id);
        recorder.record(AuditEvent.of(AuditAction.ROLE_DELETED, AuditTargets.ROLE, id, role.name())
                .changes(Changes.of("code", role.code(), null)).build());
    }

    @Transactional(readOnly = true)
    public CatalogPage<IamUserSummary> members(long id, String search, PageQuery page) {
        require(id);
        return userViews.page(UserSearch.ofRole(id, search), page);
    }

    @Transactional
    public MembersAdded addMembers(long id, MembersRequest request, Locale locale) {
        Actor actor = actors.require();
        RoleRow role = roles.lock(id).orElseThrow(() -> ApiException.notFound("iam.role.notFound"));
        List<UUID> userIds = validateMembers(request, actor);
        if (role.status() != RoleStatus.ACTIVE) {
            throw ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, null, "iam.role.inactive");
        }
        Set<UUID> current = new HashSet<>(roles.memberIds(id));
        String reason = request.reason().trim();
        int added = 0;
        for (UUID userId : userIds) {
            if (current.contains(userId)) {
                continue;
            }
            var proposal = UserAccessChanges.Proposal.of(loader.load(userId));
            List<Long> roleIds = new ArrayList<>(proposal.roleIds());
            roleIds.add(id);
            accessChanges.apply(userId, new UserAccessChanges.Proposal(roleIds, proposal.groupIds(), proposal.grants(), proposal.denials()),
                    UserAccessChanges.Fields.NONE, reason, actor, null, locale);
            added++;
        }
        return new MembersAdded(added);
    }

    @Transactional
    public MembersRemoved removeMembers(long id, MembersRequest request, Locale locale) {
        Actor actor = actors.require();
        roles.lock(id).orElseThrow(() -> ApiException.notFound("iam.role.notFound"));
        List<UUID> userIds = validateMembers(request, actor);
        Set<UUID> current = new HashSet<>(roles.memberIds(id));
        List<UUID> leaving = userIds.stream().filter(current::contains).toList();
        if (SystemRole.SUPER_ADMIN_CODE.equals(require(id).code())) {
            guards.requireRootRemains(leaving);
        }
        String reason = request.reason().trim();
        for (UUID userId : leaving) {
            var proposal = UserAccessChanges.Proposal.of(loader.load(userId));
            accessChanges.apply(userId, new UserAccessChanges.Proposal(
                            proposal.roleIds().stream().filter(r -> r != id).toList(), proposal.groupIds(), proposal.grants(), proposal.denials()),
                    UserAccessChanges.Fields.NONE, reason, actor, null, locale);
        }
        return new MembersRemoved(leaving.size());
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────────

    private record Validated(String code, String name, String description, String color, List<String> permissions,
                             SortedSet<String> storedPermissions, List<Long> groupIds, List<GroupGrant> groups) {
    }

    private Validated validate(RoleRequest request, RoleRow existing) {
        Violations violations = new Violations();
        String code = IamRules.code(violations, "code", request.code());
        String name = IamRules.requiredText(violations, "name", request.name(), 100);
        String description = IamRules.optionalText(violations, "description", request.description(), 500);
        String color = IamRules.color(violations, "color", request.color());
        List<String> permissions = IamRules.permissionCodes(violations, "permissions", request.permissions());
        List<Long> groupIds = IamRules.distinctIds(violations, "permissionGroupIds", request.permissionGroupIds());
        Set<Long> attached = existing == null ? Set.of()
                : roles.groups(existing.id()).stream().map(g -> g.id()).collect(Collectors.toSet());
        Map<Long, GroupGrant> groups = loader.loadGroups(groupIds).stream().collect(Collectors.toMap(GroupGrant::id, Function.identity()));
        List<Long> requested = request.permissionGroupIds() == null ? List.of() : request.permissionGroupIds();
        IntStream.range(0, requested.size()).forEach(i -> {
            GroupGrant group = groups.get(requested.get(i));
            violations.invalidIf(requested.get(i) != null && (group == null || (!group.active() && !attached.contains(group.id()))),
                    "permissionGroupIds[" + i + "]", "api.validation.unknownGroup", requested.get(i));
        });
        violations.invalidIf(permissions.isEmpty() && groupIds.isEmpty() && !violations.hasField("permissions"),
                "permissions", "iam.role.permissionsRequired");
        violations.invalidIf(existing != null && request.version() == null, "version", "api.validation.required");
        violations.throwIfAny();
        return new Validated(code, name, description, color, permissions, PermissionCatalog.withDependencies(permissions),
                groupIds, groupIds.stream().map(groups::get).filter(Objects::nonNull).toList());
    }

    private IamRoleDetail updateSuperAdmin(RoleRow existing, RoleRequest request, Actor actor, Locale locale) {
        Violations violations = new Violations();
        String description = IamRules.optionalText(violations, "description", request.description(), 500);
        String color = IamRules.color(violations, "color", request.color());
        violations.invalidIf(request.version() == null, "version", "api.validation.required");
        violations.throwIfAny();
        if (request.code() != null && !existing.code().equals(request.code().trim())) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, "code", "iam.role.systemCode");
        }
        if (request.name() != null && !existing.name().equals(request.name().trim())) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, "name", "iam.role.superAdminLocked");
        }
        if (!roles.update(existing.id(), existing.code(), existing.name(), description, color, request.version(),
                actor.id(), actor.username(), TenantTime.nowLocal(clock))) {
            throw ApiException.concurrentModification();
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        Changes.put(changes, "description", existing.description(), description);
        Changes.put(changes, "color", existing.color(), color);
        recorder.record(AuditEvent.of(AuditAction.ROLE_UPDATED, AuditTargets.ROLE, existing.id(), existing.name()).changes(changes).build());
        return detail(require(existing.id()), locale);
    }

    private List<AccessGuards.Grant> grantsOf(Validated role, Set<String> currentPermissions, Set<Long> currentGroups, RoleRequest request) {
        List<AccessGuards.Grant> grants = new ArrayList<>();
        List<String> permissions = request.permissions() == null ? List.of() : request.permissions();
        IntStream.range(0, permissions.size())
                .filter(i -> !currentPermissions.contains(permissions.get(i)))
                .forEach(i -> grants.add(AccessGuards.Grant.of("permissions[" + i + "]",
                        PermissionCatalog.withDependencies(List.of(permissions.get(i))))));
        Map<Long, GroupGrant> groups = role.groups().stream().collect(Collectors.toMap(GroupGrant::id, Function.identity()));
        List<Long> groupIds = request.permissionGroupIds() == null ? List.of() : request.permissionGroupIds();
        IntStream.range(0, groupIds.size())
                .filter(i -> groups.containsKey(groupIds.get(i)) && !currentGroups.contains(groupIds.get(i)))
                .forEach(i -> grants.add(AccessGuards.Grant.of("permissionGroupIds[" + i + "]",
                        groups.get(groupIds.get(i)).permissions().stream().filter(PermissionCatalog::contains).toList())));
        return grants;
    }

    /** Members get a new access version on any change; shrinking also revokes their refresh tokens. */
    private void propagate(long roleId, RoleGrant before, RoleGrant after) {
        Set<String> beforeSet = EffectivePermissionResolver.resolveRole(before);
        Set<String> afterSet = EffectivePermissionResolver.resolveRole(after);
        if (beforeSet.equals(afterSet)) {
            return;
        }
        List<UUID> members = roles.memberIds(roleId);
        versions.bump(members);
        if (!afterSet.containsAll(beforeSet)) {
            members.forEach(revoker::revokeRefreshTokens);
        }
    }

    private void requireUnique(String code, String name, Long excludeId) {
        Violations violations = new Violations();
        if (roles.codeTaken(code, excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "code", "iam.role.codeTaken");
        }
        if (roles.nameTaken(name, excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "name", "iam.role.nameTaken");
        }
        violations.throwIfAny();
    }

    private List<UUID> validateMembers(MembersRequest request, Actor actor) {
        Violations violations = new Violations();
        List<UUID> userIds = IamRules.distinctIds(violations, "userIds", request.userIds());
        violations.invalidIf(userIds.isEmpty() || userIds.size() > MAX_MEMBERS, "userIds", "api.validation.listSize", 1, MAX_MEMBERS);
        IamRules.reason(violations, "reason", request.reason());
        violations.throwIfAny();
        Set<UUID> known = Set.copyOf(jdbc.queryForList("SELECT id FROM users WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", userIds), UUID.class));
        List<UUID> raw = request.userIds();
        IntStream.range(0, raw.size()).forEach(i ->
                violations.invalidIf(raw.get(i) != null && !known.contains(raw.get(i)), "userIds[" + i + "]", "iam.user.unknown"));
        violations.throwIfAny();
        int self = raw.indexOf(actor.id());
        if (self >= 0) {
            guards.requireNotSelf(actor, actor.id(), "userIds[" + self + "]");
        }
        guards.requireCanModify(actor, userIds);
        return userIds;
    }

    private RoleRow require(long id) {
        return roles.find(id).orElseThrow(() -> ApiException.notFound("iam.role.notFound"));
    }

    private RoleGrant grant(long id) {
        return loader.loadRoles(List.of(id)).getFirst();
    }

    private IamRoleDetail detail(RoleRow row, Locale locale) {
        RoleGrant grant = grant(row.id());
        Set<String> effective = EffectivePermissionResolver.resolveRole(grant);
        List<String> ordered = canonical(effective);
        Map<UUID, UserRef> refs = directory.refs(Stream.of(row.createdById(), row.updatedById()).filter(Objects::nonNull).toList());
        return new IamRoleDetail(summary(row), row.isSuperAdmin() ? ordered : canonical(grant.permissions()), roles.groups(row.id()),
                ordered, SodEvaluator.evaluate(sodRules.findActive(), effective, locale), row.createdAt(),
                refs.get(row.createdById()), refs.get(row.updatedById()), row.version());
    }

    private static IamRoleSummary summary(RoleRow row) {
        return new IamRoleSummary(row.id(), row.code(), row.name(), row.description(), row.color(), row.system(), row.status(),
                row.userCount(), row.isSuperAdmin() ? PermissionCatalog.codes().size() : row.permissionCount(),
                row.permissionGroupCount(), Optional.ofNullable(row.updatedAt()).orElse(row.createdAt()));
    }

    private static List<String> canonical(Collection<String> codes) {
        return PermissionCatalog.all().stream().map(PermissionDefinition::code).filter(codes::contains).toList();
    }

    private static List<String> groupNames(RoleGrant role) {
        return role.groups().stream().map(GroupGrant::name).sorted().toList();
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        return a.stream().filter(code -> !b.contains(code)).collect(Collectors.toSet());
    }

    private static boolean hasCritical(Collection<String> codes) {
        return codes.stream().map(PermissionCatalog::find).flatMap(Optional::stream)
                .anyMatch(d -> d.risk() == PermissionRisk.CRITICAL);
    }
}
