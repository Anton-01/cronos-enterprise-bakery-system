package com.ninsky.cronos.account.profile.application.port;

import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.profile.domain.ProfileUpdate;
import com.ninsky.cronos.account.profile.domain.UserAccount;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the account-settings view of a user. Every mutation bumps the user's
 * optimistic-lock version, so the returned {@link UserAccount#version()} is the new ETag.
 */
public interface UserAccountRepository {

    Optional<UserAccount> findById(UUID userId);

    /** Case-insensitive: "Admin" is taken if another account is "admin". */
    boolean isUsernameTakenByOther(String username, UUID userId);

    /**
     * Full replace of username / first / last name / phone ({@code null} clears).
     * @throws com.ninsky.cronos.account.shared.domain.AccountDomainException with DuplicateUsername when a
     *         concurrent writer claimed the username first (unique index race)
     */
    UserAccount applyProfile(UUID userId, ProfileUpdate update);

    /** @param avatarKey the new key, or null to clear */
    UserAccount replaceAvatar(UUID userId, AvatarKey avatarKey);
}
