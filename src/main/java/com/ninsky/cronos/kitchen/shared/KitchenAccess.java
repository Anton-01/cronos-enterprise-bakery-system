package com.ninsky.cronos.kitchen.shared;

import com.ninsky.cronos.iam.permission.Permissions;

/** {@code @PreAuthorize} expressions of the kitchen endpoints (§11). */
public final class KitchenAccess {

    public static final String AUTHENTICATED = "isAuthenticated()";
    public static final String ALLERGEN_READ = "hasAuthority('" + Permissions.CATALOG_ALLERGEN_READ + "')";
    public static final String ALLERGEN_MANAGE = "hasAuthority('" + Permissions.CATALOG_ALLERGEN_MANAGE + "')";
    public static final String INGREDIENT_READ = "hasAuthority('" + Permissions.INGREDIENT_READ + "')";
    public static final String INGREDIENT_CREATE = "hasAuthority('" + Permissions.INGREDIENT_CREATE + "')";
    public static final String INGREDIENT_UPDATE = "hasAuthority('" + Permissions.INGREDIENT_UPDATE + "')";
    public static final String INGREDIENT_DELETE = "hasAuthority('" + Permissions.INGREDIENT_DELETE + "')";
    /** Fine-grained USER vs SYSTEM check happens in the service. */
    public static final String INGREDIENT_EDIT = "hasAnyAuthority('" + Permissions.INGREDIENT_UPDATE + "', '"
            + Permissions.CATALOG_INGREDIENT_MANAGE + "')";
    public static final String RECIPE_READ = "hasAuthority('" + Permissions.RECIPE_READ + "')";
    public static final String RECIPE_CREATE = "hasAuthority('" + Permissions.RECIPE_CREATE + "')";
    public static final String RECIPE_UPDATE = "hasAuthority('" + Permissions.RECIPE_UPDATE + "')";
    public static final String RECIPE_DELETE = "hasAuthority('" + Permissions.RECIPE_DELETE + "')";

    private KitchenAccess() {
    }
}
