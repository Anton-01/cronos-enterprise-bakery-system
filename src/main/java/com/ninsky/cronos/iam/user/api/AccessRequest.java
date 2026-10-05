package com.ninsky.cronos.iam.user.api;

import java.util.List;

/** Access save ({@code reason}, {@code version} required) and preview (both ignored). */
public record AccessRequest(List<Long> roleIds, List<Long> permissionGroupIds, List<String> grants,
                            List<String> denials, String reason, Long version) {
}
