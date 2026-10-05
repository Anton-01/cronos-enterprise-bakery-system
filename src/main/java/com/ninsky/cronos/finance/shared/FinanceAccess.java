package com.ninsky.cronos.finance.shared;

import com.ninsky.cronos.iam.permission.Permissions;

/** {@code @PreAuthorize} expressions of the finance endpoints (spec §1.4.7). */
public final class FinanceAccess {

    public static final String AUTHENTICATED = "isAuthenticated()";
    public static final String CURRENCY_READ = "hasAuthority('" + Permissions.FINANCE_CURRENCY_READ + "')";
    public static final String CURRENCY_MANAGE = "hasAuthority('" + Permissions.FINANCE_CURRENCY_MANAGE + "')";
    public static final String TAX_RATE_READ = "hasAuthority('" + Permissions.FINANCE_TAX_RATE_READ + "')";
    public static final String TAX_RATE_MANAGE = "hasAuthority('" + Permissions.FINANCE_TAX_RATE_MANAGE + "')";
    public static final String SETTINGS_UPDATE = "hasAuthority('" + Permissions.FINANCE_SETTINGS_UPDATE + "')";

    private FinanceAccess() {
    }
}
