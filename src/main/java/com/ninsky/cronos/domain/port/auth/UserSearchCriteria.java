package com.ninsky.cronos.domain.port.auth;

/**
 * Dynamic admin user-search filter — plain domain value object. The adapter translates this into
 * whatever query mechanism it likes (JPA {@code Specification} today); the port stays framework-free.
 */
public record UserSearchCriteria(String roleName, Boolean enabled, String search) {
}
