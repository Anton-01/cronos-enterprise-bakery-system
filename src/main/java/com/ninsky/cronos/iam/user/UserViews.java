package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.shared.RoleRef;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.token.TokenPurpose;
import com.ninsky.cronos.iam.token.UserTokens;
import com.ninsky.cronos.iam.user.api.IamUserDetail;
import com.ninsky.cronos.iam.user.api.IamUserSummary;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/** Builds the user read models with batched role and user-reference lookups. */
@Component
@RequiredArgsConstructor
public class UserViews {

    private final UserReadCustomRepository users;
    private final UserDirectory directory;
    private final UserTokens tokens;

    public CatalogPage<IamUserSummary> page(UserSearch search, PageQuery page) {
        UserReadCustomRepository.Page result = users.search(search, page);
        return CatalogPage.of(summaries(result.rows()), page, result.total());
    }

    public List<IamUserSummary> summaries(List<UserRow> rows) {
        Map<UUID, List<RoleRef>> roles = users.roles(rows.stream().map(UserRow::id).toList());
        return rows.stream().map(row -> summary(row, roles.getOrDefault(row.id(), List.of()))).toList();
    }

    public IamUserDetail detail(UserRow row) {
        List<RoleRef> roles = users.roles(List.of(row.id())).getOrDefault(row.id(), List.of());
        Map<UUID, UserRef> refs = directory.refs(Stream.of(row.statusChangedBy(), row.createdById(), row.updatedById())
                .filter(Objects::nonNull).toList());
        return new IamUserDetail(summary(row, roles), row.phoneNumber(), row.employeeNumber(), row.locale(), row.emailVerified(),
                row.failedLoginAttempts(), row.passwordChangedAt(), row.statusReason(), row.statusComment(), row.statusChangedAt(),
                ref(refs, row.statusChangedBy()),
                row.status() == UserStatus.PENDING_ACTIVATION ? tokens.pendingExpiry(row.id(), TokenPurpose.INVITATION).orElse(null) : null,
                ref(refs, row.createdById()), row.updatedAt(), ref(refs, row.updatedById()), row.requireTwoFactor(), row.version());
    }

    public IamUserSummary summary(UserRow row, List<RoleRef> roles) {
        return new IamUserSummary(row.id(), row.username(), row.email(), row.firstName(), row.lastName(), row.displayName(),
                directory.avatarUrl(row.avatarKey()), row.jobTitle(), row.department(), row.status(), roles,
                row.twoFactorEnabled(), row.mustChangePassword(), row.lastLoginAt(), row.statusUntil(), row.accessExpiresAt(),
                row.createdAt());
    }

    private static UserRef ref(Map<UUID, UserRef> refs, UUID id) {
        return id == null ? null : refs.get(id);
    }
}
