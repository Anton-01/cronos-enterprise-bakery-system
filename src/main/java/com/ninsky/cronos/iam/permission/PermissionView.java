package com.ninsky.cronos.iam.permission;

import java.util.List;

/** Catalog entry as served to the UI (spec §5.1). */
public record PermissionView(String code, String module, String resource, String action, String moduleName,
                             String resourceName, String name, String description, PermissionRisk risk, List<String> dependsOn) {
}
