package com.ninsky.cronos.iam.permission;

/** {@code @PreAuthorize} expressions, one per permission code (spec §1.4.7). */
public final class Authorities {

    public static final String DASHBOARD_HOME_READ = "hasAuthority('DASHBOARD.HOME.READ')";
    public static final String RECIPE_READ = "hasAuthority('RECIPE.RECIPE.READ')";
    public static final String RECIPE_CREATE = "hasAuthority('RECIPE.RECIPE.CREATE')";
    public static final String RECIPE_UPDATE = "hasAuthority('RECIPE.RECIPE.UPDATE')";
    public static final String RECIPE_DELETE = "hasAuthority('RECIPE.RECIPE.DELETE')";
    public static final String RECIPE_SHARE = "hasAuthority('RECIPE.RECIPE.SHARE')";
    public static final String QUOTE_READ = "hasAuthority('QUOTE.QUOTE.READ')";
    public static final String QUOTE_CREATE = "hasAuthority('QUOTE.QUOTE.CREATE')";
    public static final String QUOTE_UPDATE = "hasAuthority('QUOTE.QUOTE.UPDATE')";
    public static final String QUOTE_DELETE = "hasAuthority('QUOTE.QUOTE.DELETE')";
    public static final String QUOTE_SHARE = "hasAuthority('QUOTE.QUOTE.SHARE')";
    public static final String QUOTE_APPROVE = "hasAuthority('QUOTE.QUOTE.APPROVE')";
    public static final String INGREDIENT_READ = "hasAuthority('INGREDIENT.INGREDIENT.READ')";
    public static final String INGREDIENT_CREATE = "hasAuthority('INGREDIENT.INGREDIENT.CREATE')";
    public static final String INGREDIENT_UPDATE = "hasAuthority('INGREDIENT.INGREDIENT.UPDATE')";
    public static final String INGREDIENT_DELETE = "hasAuthority('INGREDIENT.INGREDIENT.DELETE')";
    public static final String FIXED_COST_READ = "hasAuthority('FIXED_COST.FIXED_COST.READ')";
    public static final String FIXED_COST_MANAGE = "hasAuthority('FIXED_COST.FIXED_COST.MANAGE')";
    public static final String GUIDE_GUIDE_READ = "hasAuthority('GUIDE.GUIDE.READ')";
    public static final String GUIDE_PAN_MANAGE = "hasAuthority('GUIDE.PAN.MANAGE')";
    public static final String GUIDE_CONTENT_MANAGE = "hasAuthority('GUIDE.CONTENT.MANAGE')";
    public static final String CATALOG_UNIT_TYPE_READ = "hasAuthority('CATALOG.UNIT_TYPE.READ')";
    public static final String CATALOG_UNIT_TYPE_MANAGE = "hasAuthority('CATALOG.UNIT_TYPE.MANAGE')";
    public static final String CATALOG_MEASUREMENT_UNIT_READ = "hasAuthority('CATALOG.MEASUREMENT_UNIT.READ')";
    public static final String CATALOG_MEASUREMENT_UNIT_MANAGE = "hasAuthority('CATALOG.MEASUREMENT_UNIT.MANAGE')";
    public static final String CATALOG_CATEGORY_READ = "hasAuthority('CATALOG.CATEGORY.READ')";
    public static final String CATALOG_CATEGORY_MANAGE = "hasAuthority('CATALOG.CATEGORY.MANAGE')";
    public static final String CATALOG_ALLERGEN_READ = "hasAuthority('CATALOG.ALLERGEN.READ')";
    public static final String CATALOG_ALLERGEN_MANAGE = "hasAuthority('CATALOG.ALLERGEN.MANAGE')";
    public static final String CATALOG_IMPORT_READ = "hasAuthority('CATALOG.IMPORT.READ')";
    public static final String CATALOG_IMPORT_EXECUTE = "hasAuthority('CATALOG.IMPORT.EXECUTE')";
    public static final String FINANCE_CURRENCY_READ = "hasAuthority('FINANCE.CURRENCY.READ')";
    public static final String FINANCE_CURRENCY_MANAGE = "hasAuthority('FINANCE.CURRENCY.MANAGE')";
    public static final String FINANCE_TAX_RATE_READ = "hasAuthority('FINANCE.TAX_RATE.READ')";
    public static final String FINANCE_TAX_RATE_MANAGE = "hasAuthority('FINANCE.TAX_RATE.MANAGE')";
    public static final String FINANCE_SETTINGS_UPDATE = "hasAuthority('FINANCE.SETTINGS.UPDATE')";
    public static final String IAM_USER_READ = "hasAuthority('IAM.USER.READ')";
    public static final String IAM_USER_CREATE = "hasAuthority('IAM.USER.CREATE')";
    public static final String IAM_USER_UPDATE = "hasAuthority('IAM.USER.UPDATE')";
    public static final String IAM_USER_CHANGE_STATUS = "hasAuthority('IAM.USER.CHANGE_STATUS')";
    public static final String IAM_USER_RESET_CREDENTIALS = "hasAuthority('IAM.USER.RESET_CREDENTIALS')";
    public static final String IAM_USER_MANAGE_ACCESS = "hasAuthority('IAM.USER.MANAGE_ACCESS')";
    public static final String IAM_USER_MANAGE_SESSIONS = "hasAuthority('IAM.USER.MANAGE_SESSIONS')";
    public static final String IAM_USER_EXPORT = "hasAuthority('IAM.USER.EXPORT')";
    public static final String IAM_ROLE_READ = "hasAuthority('IAM.ROLE.READ')";
    public static final String IAM_ROLE_CREATE = "hasAuthority('IAM.ROLE.CREATE')";
    public static final String IAM_ROLE_UPDATE = "hasAuthority('IAM.ROLE.UPDATE')";
    public static final String IAM_ROLE_DELETE = "hasAuthority('IAM.ROLE.DELETE')";
    public static final String IAM_ROLE_MANAGE_MEMBERS = "hasAuthority('IAM.ROLE.MANAGE_MEMBERS')";
    public static final String IAM_PERMISSION_GROUP_READ = "hasAuthority('IAM.PERMISSION_GROUP.READ')";
    public static final String IAM_PERMISSION_GROUP_CREATE = "hasAuthority('IAM.PERMISSION_GROUP.CREATE')";
    public static final String IAM_PERMISSION_GROUP_UPDATE = "hasAuthority('IAM.PERMISSION_GROUP.UPDATE')";
    public static final String IAM_PERMISSION_GROUP_DELETE = "hasAuthority('IAM.PERMISSION_GROUP.DELETE')";
    public static final String IAM_AUDIT_READ = "hasAuthority('IAM.AUDIT.READ')";
    public static final String IAM_AUDIT_EXPORT = "hasAuthority('IAM.AUDIT.EXPORT')";
    public static final String IAM_SECURITY_POLICY_READ = "hasAuthority('IAM.SECURITY_POLICY.READ')";
    public static final String IAM_SECURITY_POLICY_UPDATE = "hasAuthority('IAM.SECURITY_POLICY.UPDATE')";

    /** Permission catalog readers (spec §1.4.7). */
    public static final String PERMISSION_CATALOG_READ =
            "hasAnyAuthority('IAM.ROLE.READ', 'IAM.PERMISSION_GROUP.READ', 'IAM.USER.READ')";
    public static final String AUTHENTICATED = "isAuthenticated()";

    private Authorities() {
    }
}
