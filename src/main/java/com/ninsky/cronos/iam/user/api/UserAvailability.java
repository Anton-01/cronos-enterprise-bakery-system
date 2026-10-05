package com.ninsky.cronos.iam.user.api;

/** {@code null} for the parameter not sent. */
public record UserAvailability(Boolean usernameAvailable, Boolean emailAvailable) {
}
