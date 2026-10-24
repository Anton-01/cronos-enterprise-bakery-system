package com.ninsky.cronos.mail.domain;

/**
 * How the catalog screen groups templates. Stored in {@code mail/catalog.json} as the enum value; never derived from
 * the template key at runtime ({@code auth.*} templates are split between {@link #ACCOUNT} and {@link #SECURITY}).
 */
public enum EmailCategory {
    ACCOUNT,
    SECURITY,
    ADMIN,
    QUOTES,
    ORDERS,
    RECIPES
}
