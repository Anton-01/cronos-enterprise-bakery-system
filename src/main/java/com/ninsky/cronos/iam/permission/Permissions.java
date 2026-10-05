package com.ninsky.cronos.iam.permission;

/**
 * Permission codes as compile-time constants, usable inside {@code @PreAuthorize}.
 * {@link PermissionCatalog} is the registry that gives each one its risk and dependencies.
 */
public final class Permissions {

    public static final String DASHBOARD_HOME_READ = "DASHBOARD.HOME.READ";

    public static final String RECIPE_READ = "RECIPE.RECIPE.READ";
    public static final String RECIPE_CREATE = "RECIPE.RECIPE.CREATE";
    public static final String RECIPE_UPDATE = "RECIPE.RECIPE.UPDATE";
    public static final String RECIPE_DELETE = "RECIPE.RECIPE.DELETE";
    public static final String RECIPE_SHARE = "RECIPE.RECIPE.SHARE";

    public static final String QUOTE_READ = "QUOTE.QUOTE.READ";
    public static final String QUOTE_CREATE = "QUOTE.QUOTE.CREATE";
    public static final String QUOTE_UPDATE = "QUOTE.QUOTE.UPDATE";
    public static final String QUOTE_DELETE = "QUOTE.QUOTE.DELETE";
    public static final String QUOTE_SHARE = "QUOTE.QUOTE.SHARE";
    public static final String QUOTE_APPROVE = "QUOTE.QUOTE.APPROVE";

    public static final String INGREDIENT_READ = "INGREDIENT.INGREDIENT.READ";
    public static final String INGREDIENT_CREATE = "INGREDIENT.INGREDIENT.CREATE";
    public static final String INGREDIENT_UPDATE = "INGREDIENT.INGREDIENT.UPDATE";
    public static final String INGREDIENT_DELETE = "INGREDIENT.INGREDIENT.DELETE";

    public static final String FIXED_COST_READ = "FIXED_COST.FIXED_COST.READ";
    public static final String FIXED_COST_MANAGE = "FIXED_COST.FIXED_COST.MANAGE";

    public static final String CATALOG_UNIT_TYPE_READ = "CATALOG.UNIT_TYPE.READ";
    public static final String CATALOG_UNIT_TYPE_MANAGE = "CATALOG.UNIT_TYPE.MANAGE";
    public static final String CATALOG_MEASUREMENT_UNIT_READ = "CATALOG.MEASUREMENT_UNIT.READ";
    public static final String CATALOG_MEASUREMENT_UNIT_MANAGE = "CATALOG.MEASUREMENT_UNIT.MANAGE";
    public static final String CATALOG_CATEGORY_READ = "CATALOG.CATEGORY.READ";
    public static final String CATALOG_CATEGORY_MANAGE = "CATALOG.CATEGORY.MANAGE";
    public static final String CATALOG_ALLERGEN_READ = "CATALOG.ALLERGEN.READ";
    public static final String CATALOG_ALLERGEN_MANAGE = "CATALOG.ALLERGEN.MANAGE";
    public static final String CATALOG_IMPORT_READ = "CATALOG.IMPORT.READ";
    public static final String CATALOG_IMPORT_EXECUTE = "CATALOG.IMPORT.EXECUTE";

    public static final String FINANCE_CURRENCY_READ = "FINANCE.CURRENCY.READ";
    public static final String FINANCE_CURRENCY_MANAGE = "FINANCE.CURRENCY.MANAGE";
    public static final String FINANCE_TAX_RATE_READ = "FINANCE.TAX_RATE.READ";
    public static final String FINANCE_TAX_RATE_MANAGE = "FINANCE.TAX_RATE.MANAGE";
    public static final String FINANCE_SETTINGS_UPDATE = "FINANCE.SETTINGS.UPDATE";

    public static final String IAM_USER_READ = "IAM.USER.READ";
    public static final String IAM_USER_CREATE = "IAM.USER.CREATE";
    public static final String IAM_USER_UPDATE = "IAM.USER.UPDATE";
    public static final String IAM_USER_CHANGE_STATUS = "IAM.USER.CHANGE_STATUS";
    public static final String IAM_USER_RESET_CREDENTIALS = "IAM.USER.RESET_CREDENTIALS";
    public static final String IAM_USER_MANAGE_ACCESS = "IAM.USER.MANAGE_ACCESS";
    public static final String IAM_USER_MANAGE_SESSIONS = "IAM.USER.MANAGE_SESSIONS";
    public static final String IAM_USER_EXPORT = "IAM.USER.EXPORT";

    public static final String IAM_ROLE_READ = "IAM.ROLE.READ";
    public static final String IAM_ROLE_CREATE = "IAM.ROLE.CREATE";
    public static final String IAM_ROLE_UPDATE = "IAM.ROLE.UPDATE";
    public static final String IAM_ROLE_DELETE = "IAM.ROLE.DELETE";
    public static final String IAM_ROLE_MANAGE_MEMBERS = "IAM.ROLE.MANAGE_MEMBERS";

    public static final String IAM_PERMISSION_GROUP_READ = "IAM.PERMISSION_GROUP.READ";
    public static final String IAM_PERMISSION_GROUP_CREATE = "IAM.PERMISSION_GROUP.CREATE";
    public static final String IAM_PERMISSION_GROUP_UPDATE = "IAM.PERMISSION_GROUP.UPDATE";
    public static final String IAM_PERMISSION_GROUP_DELETE = "IAM.PERMISSION_GROUP.DELETE";

    public static final String IAM_AUDIT_READ = "IAM.AUDIT.READ";
    public static final String IAM_AUDIT_EXPORT = "IAM.AUDIT.EXPORT";

    public static final String IAM_SECURITY_POLICY_READ = "IAM.SECURITY_POLICY.READ";
    public static final String IAM_SECURITY_POLICY_UPDATE = "IAM.SECURITY_POLICY.UPDATE";

    /** Transitional alias kept in the token while the SPA still checks it (spec §1.4.4). */
    public static final String LEGACY_MANAGE_CATALOGS = "MANAGE_CATALOGS";

    private Permissions() {
    }
}
