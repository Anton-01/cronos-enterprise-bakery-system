package com.ninsky.cronos.domain.entity.enums;

/**
 * Who owns a category. {@code SYSTEM} rows are seeded, global, and immutable through the normal
 * API (see {@code CategoryServiceImplementation}); {@code USER} rows belong to exactly one user
 * ({@code Category.userId}) and are invisible to everyone else.
 */
public enum CategoryScope {
    SYSTEM, USER
}
