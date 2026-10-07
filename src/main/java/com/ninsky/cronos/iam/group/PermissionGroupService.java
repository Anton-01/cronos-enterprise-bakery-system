package com.ninsky.cronos.iam.group;

import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.audit.AuditSeverity;
import com.ninsky.cronos.iam.access.AccessGuards;
import com.ninsky.cronos.iam.access.AccessVersions;
import com.ninsky.cronos.iam.access.SessionRevoker;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.group.api.PermissionGroupDetail;
import com.ninsky.cronos.iam.group.api.PermissionGroupRequest;
import com.ninsky.cronos.iam.group.api.PermissionGroupSummary;
import com.ninsky.cronos.iam.permission.PermissionCatalog;
import com.ninsky.cronos.iam.permission.PermissionDefinition;
import com.ninsky.cronos.iam.permission.PermissionRisk;
import com.ninsky.cronos.iam.role.RoleStatus;
import com.ninsky.cronos.iam.role.api.StatusRequest;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.iam.shared.IamRules;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.Violations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Permission groups (spec §6). */
@Service
@RequiredArgsConstructor
public class PermissionGroupService {

    private final PermissionGroupCustomRepository groups;
    private final AccessGuards guards;
    private final AccessVersions versions;
    private final SessionRevoker revoker;
    private final AuditRecorder recorder;
    private final ActorProvider actors;

    @Transactional(readOnly = true)
    public List<PermissionGroupSummary> list() {
        return groups.findAll().stream().map(PermissionGroupService::summary).toList();
    }

    @Transactional(readOnly = true)
    public PermissionGroupDetail detail(long id) {
        return detail(require(id));
    }

    @Transactional
    public PermissionGroupDetail create(PermissionGroupRequest request) {
        Actor actor = actors.require();
        Validated group = validate(request, false);
        requireUnique(group.code(), group.name(), null);
        guards.requireNoEscalation(actor, grants(request, Set.of()));
        long id = groups.insert(group.code(), group.name(), group.description(), actor.id());
        groups.replacePermissions(id, group.stored());

        Map<String, Object> changes = new LinkedHashMap<>();
        Changes.put(changes, "code", null, group.code());
        Changes.put(changes, "name", null, group.name());
        Changes.put(changes, "description", null, group.description());
        Changes.put(changes, "permissions", null, List.copyOf(group.stored()));
        recorder.record(AuditEvent.of(AuditAction.PERMISSION_GROUP_CREATED, AuditTargets.PERMISSION_GROUP, id, group.name())
                .severity(hasCritical(group.stored()) ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .changes(changes).build());
        return detail(require(id));
    }

    @Transactional
    public PermissionGroupDetail update(long id, PermissionGroupRequest request) {
        Actor actor = actors.require();
        GroupRow existing = groups.lock(id).orElseThrow(() -> ApiException.notFound("iam.group.notFound"));
        if (existing.system()) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, null, "iam.group.systemReadOnly");
        }
        Validated group = validate(request, true);
        requireUnique(group.code(), group.name(), id);
        Set<String> before = groups.permissions(id);
        guards.requireNoEscalation(actor, grants(request, before));
        if (!groups.update(id, group.code(), group.name(), group.description(), request.version(), actor.id())) {
            throw ApiException.concurrentModification();
        }
        groups.replacePermissions(id, group.stored());
        Set<String> added = minus(group.stored(), before);
        Set<String> removed = minus(before, group.stored());
        if (existing.status() == RoleStatus.ACTIVE && (!added.isEmpty() || !removed.isEmpty())) {
            propagate(id, !removed.isEmpty());
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        Changes.put(changes, "code", existing.code(), group.code());
        Changes.put(changes, "name", existing.name(), group.name());
        Changes.put(changes, "description", existing.description(), group.description());
        if (!added.isEmpty() || !removed.isEmpty()) {
            changes.put("permissions", Map.of("added", List.copyOf(new TreeSet<>(added)), "removed", List.copyOf(new TreeSet<>(removed))));
        }
        recorder.record(AuditEvent.of(AuditAction.PERMISSION_GROUP_UPDATED, AuditTargets.PERMISSION_GROUP, id, group.name())
                .severity(hasCritical(added) ? AuditSeverity.WARNING : AuditSeverity.NOTICE)
                .changes(changes).build());
        return detail(require(id));
    }

    @Transactional
    public PermissionGroupDetail changeStatus(long id, StatusRequest request) {
        Actor actor = actors.require();
        new Violations()
                .invalidIf(request.status() == null, "status", "api.validation.required")
                .invalidIf(request.version() == null, "version", "api.validation.required")
                .throwIfAny();
        GroupRow existing = groups.lock(id).orElseThrow(() -> ApiException.notFound("iam.group.notFound"));
        if (existing.system()) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, "status", "iam.group.systemReadOnly");
        }
        if (!groups.updateStatus(id, request.status(), request.version(), actor.id())) {
            throw ApiException.concurrentModification();
        }
        if (existing.status() != request.status()) {
            propagate(id, request.status() == RoleStatus.INACTIVE);
            recorder.record(AuditEvent.of(AuditAction.PERMISSION_GROUP_STATUS_CHANGED, AuditTargets.PERMISSION_GROUP, id, existing.name())
                    .changes(Changes.of("status", existing.status().name(), request.status().name()))
                    .params(Map.of("detail", request.status().name()))
                    .build());
        }
        return detail(require(id));
    }

    @Transactional
    public void delete(long id) {
        GroupRow group = groups.lock(id).orElseThrow(() -> ApiException.notFound("iam.group.notFound"));
        if (group.system()) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, null, "iam.group.systemReadOnly");
        }
        if (group.roleCount() + group.userCount() > 0) {
            throw ApiException.of(ApiErrorCode.RESOURCE_IN_USE, null, "iam.group.inUse", group.roleCount(), group.userCount());
        }
        groups.delete(id);
        recorder.record(AuditEvent.of(AuditAction.PERMISSION_GROUP_DELETED, AuditTargets.PERMISSION_GROUP, id, group.name())
                .changes(Changes.of("code", group.code(), null)).build());
    }

    private record Validated(String code, String name, String description, SortedSet<String> stored) {
    }

    private Validated validate(PermissionGroupRequest request, boolean update) {
        Violations violations = new Violations();
        String code = IamRules.code(violations, "code", request.code());
        String name = IamRules.requiredText(violations, "name", request.name(), 100);
        String description = IamRules.optionalText(violations, "description", request.description(), 500);
        List<String> permissions = IamRules.permissionCodes(violations, "permissions", request.permissions());
        violations.invalidIf(permissions.isEmpty() && !violations.hasField("permissions"), "permissions", "iam.group.permissionsRequired");
        violations.invalidIf(update && request.version() == null, "version", "api.validation.required");
        violations.throwIfAny();
        return new Validated(code, name, description, PermissionCatalog.withDependencies(permissions));
    }

    private List<AccessGuards.Grant> grants(PermissionGroupRequest request, Set<String> current) {
        List<String> permissions = request.permissions() == null ? List.of() : request.permissions();
        return IntStream.range(0, permissions.size())
                .filter(i -> !current.contains(permissions.get(i)))
                .mapToObj(i -> AccessGuards.Grant.of("permissions[" + i + "]", PermissionCatalog.withDependencies(List.of(permissions.get(i)))))
                .toList();
    }

    /** Every holder (direct or via a role) gets a new access version; shrinking revokes refresh tokens. */
    private void propagate(long groupId, boolean shrink) {
        List<UUID> affected = versions.affectedByGroup(groupId);
        versions.bump(affected);
        if (shrink) {
            affected.forEach(revoker::revokeRefreshTokens);
        }
    }

    private void requireUnique(String code, String name, Long excludeId) {
        Violations violations = new Violations();
        if (groups.codeTaken(code, excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "code", "iam.group.codeTaken");
        }
        if (groups.nameTaken(name, excludeId)) {
            violations.add(ApiErrorCode.DUPLICATE_RESOURCE, "name", "iam.group.nameTaken");
        }
        violations.throwIfAny();
    }

    private GroupRow require(long id) {
        return groups.find(id).orElseThrow(() -> ApiException.notFound("iam.group.notFound"));
    }

    private PermissionGroupDetail detail(GroupRow row) {
        Set<String> permissions = groups.permissions(row.id());
        return new PermissionGroupDetail(summary(row),
                PermissionCatalog.all().stream().map(PermissionDefinition::code).filter(permissions::contains).toList(),
                groups.roles(row.id()), row.version());
    }

    private static PermissionGroupSummary summary(GroupRow row) {
        return new PermissionGroupSummary(row.id(), row.code(), row.name(), row.description(), row.system(), row.status(),
                row.permissionCount(), row.roleCount(), row.userCount(), Optional.ofNullable(row.updatedAt()).orElse(row.createdAt()));
    }

    private static Set<String> minus(Collection<String> a, Collection<String> b) {
        return a.stream().filter(code -> !b.contains(code)).collect(Collectors.toSet());
    }

    private static boolean hasCritical(Collection<String> codes) {
        return codes.stream().map(PermissionCatalog::find).flatMap(Optional::stream).anyMatch(d -> d.risk() == PermissionRisk.CRITICAL);
    }
}
