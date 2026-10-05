package com.ninsky.cronos.iam.permission;

/** How much damage misuse of a permission can do; drives audit severity and UI warnings. */
public enum PermissionRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
