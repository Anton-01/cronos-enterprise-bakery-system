package com.ninsky.cronos.iam.group.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.ninsky.cronos.iam.shared.RoleRef;

import java.util.List;

/** Group detail (spec §6). */
public record PermissionGroupDetail(@JsonUnwrapped PermissionGroupSummary summary, List<String> permissions, List<RoleRef> roles,
                                    long version) {
}
