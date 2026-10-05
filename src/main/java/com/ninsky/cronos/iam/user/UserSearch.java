package com.ninsky.cronos.iam.user;

import java.util.List;

/** Filters of the user list, export and role members (spec §3.5). */
public record UserSearch(String search, List<UserStatus> statuses, List<Long> roleIds, Boolean twoFactorEnabled) {

    public static final int MIN_SEARCH = 2;

    public UserSearch {
        String trimmed = search == null ? null : search.trim();
        search = trimmed == null || trimmed.length() < MIN_SEARCH ? null : trimmed;
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
    }

    public static UserSearch ofRole(long roleId, String search) {
        return new UserSearch(search, List.of(), List.of(roleId), null);
    }
}
