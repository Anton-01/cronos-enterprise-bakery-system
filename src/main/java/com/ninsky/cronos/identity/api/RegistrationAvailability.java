package com.ninsky.cronos.identity.api;

/**
 * {@code GET /auth/availability}: null for the parameter not sent. Email is only ever {@code true} or null for
 * anonymous callers (registration is the authority), username is a real true/false.
 */
public record RegistrationAvailability(Boolean usernameAvailable, Boolean emailAvailable) {
}
