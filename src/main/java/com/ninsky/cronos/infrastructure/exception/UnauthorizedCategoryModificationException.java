package com.ninsky.cronos.infrastructure.exception;

/**
 * Thrown when the caller attempts to edit/trash a {@code USER}-scope category they don't own
 * (IDOR prevention — business rule 3). Deliberately distinct from {@link SystemResourceException}
 * (which covers the "nobody may edit a SYSTEM row" case): this one is about a specific other
 * user's row, not a global-immutability rule.
 */
public class UnauthorizedCategoryModificationException extends RuntimeException {
    public UnauthorizedCategoryModificationException(String message) {
        super(message);
    }
}
