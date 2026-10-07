package com.ninsky.cronos.iam.access;

import com.ninsky.cronos.iam.role.SystemRole;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Object-level rules (spec §3.4) and the privilege-escalation guard (§1.4.6). */
@Component
@RequiredArgsConstructor
public class AccessGuards {

    private final UserAccessCustomRepository repository;

    /** Admin endpoints never act on the caller's own account. */
    public void requireNotSelf(Actor actor, UUID target, String field) {
        if (actor.id().equals(target)) {
            throw ApiException.of(ApiErrorCode.SELF_MODIFICATION_FORBIDDEN, field, "iam.user.selfModification");
        }
    }

    /** Only SUPER_ADMIN may modify a user who holds SUPER_ADMIN. */
    public void requireCanModify(Actor actor, Collection<UUID> targets) {
        if (actor.superAdmin() || targets.isEmpty()) {
            return;
        }
        if (!superAdminsAmong(targets).isEmpty()) {
            throw ApiException.of(ApiErrorCode.ACCESS_DENIED, null, "iam.user.rootProtected");
        }
    }

    /** Users of {@code candidates} holding the SUPER_ADMIN role. */
    public Set<UUID> superAdminsAmong(Collection<UUID> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.holdersAmong(SystemRole.SUPER_ADMIN_CODE, candidates));
    }

    /** At least one ACTIVE user must keep an ACTIVE SUPER_ADMIN membership once {@code leaving} lose it. */
    public void requireRootRemains(Collection<UUID> leaving) {
        if (leaving.isEmpty()) {
            return;
        }
        int remaining = repository.activeHoldersExcluding(SystemRole.SUPER_ADMIN_CODE, leaving);
        if (remaining == 0) {
            throw ApiException.of(ApiErrorCode.SYSTEM_RESOURCE_CONFLICT, null, "iam.role.lastSuperAdmin");
        }
    }

    /** One grant attempt: the codes it hands out and whether it is the SUPER_ADMIN role itself. */
    public record Grant(String field, Set<String> codes, boolean superAdminRole) {
        public static Grant of(String field, Collection<String> codes) {
            return new Grant(field, Set.copyOf(codes), false);
        }
    }

    /** First grant the actor could not hand out → 403 PRIVILEGE_ESCALATION on its field. */
    public void requireNoEscalation(Actor actor, List<Grant> grants) {
        if (actor.superAdmin()) {
            return;
        }
        grants.stream()
                .filter(g -> g.superAdminRole() || !actor.permissions().containsAll(g.codes()))
                .findFirst()
                .ifPresent(g -> {
                    throw ApiException.of(ApiErrorCode.PRIVILEGE_ESCALATION, g.field(),
                            g.superAdminRole() ? "iam.escalation.superAdmin" : "iam.escalation.permissions");
                });
    }
}
