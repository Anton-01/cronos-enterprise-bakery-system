package com.ninsky.cronos.application.response.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * {@code avatarUrl} is content-addressed (it changes whenever the image does), null without an avatar.
 * Nulls are always serialized: the account-settings form binds every key.
 */
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String avatarUrl,
        Boolean enabled,
        Boolean accountNonLocked,
        Boolean twoFactorEnabled,
        Integer failedLoginAttempts,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt,
        LocalDateTime passwordChangedAt,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
