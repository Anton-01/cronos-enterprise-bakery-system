package com.ninsky.cronos.account.profile.infrastructure;

import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.ProfileUpdate;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.DuplicateUsername;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.persistence.auth.UserJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.UserProfileJpaRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.RoleJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserJpaEntity;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserProfileJpaEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Works on MANAGED entities (load → mutate → flush) rather than the existing rebuild-and-merge
 * pattern, so Hibernate's {@code @Version} check covers the whole read-modify-write and the flushed
 * entity already carries the new version for the response ETag. Touching {@code updatedAt} on the
 * {@code users} row makes every account write bump the user's version, including writes that only
 * change {@code user_profiles} columns.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaUserAccountRepository implements UserAccountRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserProfileJpaRepository userProfileJpaRepository;
    private final UserProfileMapper userProfileMapper;

    @Override
    public Optional<UserAccount> findById(UUID userId) {
        return userJpaRepository.findById(userId)
                .map(user -> toAccount(user, userProfileJpaRepository.findByUserId(userId).orElse(null)));
    }

    @Override
    public boolean isUsernameTakenByOther(String username, UUID userId) {
        return userJpaRepository.existsByUsernameIgnoreCaseAndIdNot(username, userId);
    }

    @Override
    public UserAccount applyProfile(UUID userId, ProfileUpdate update) {
        UserJpaEntity user = loadUser(userId);
        UserProfileJpaEntity profile = userProfileJpaRepository.findByUserId(userId)
                .orElseGet(() -> UserProfileJpaEntity.builder().userId(userId).build());

        user.setUsername(update.username());
        user.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.applyTo(update, profile);

        UserProfileJpaEntity savedProfile = userProfileJpaRepository.save(profile);
        return toAccount(flush(user, update.username()), savedProfile);
    }

    @Override
    public UserAccount replaceAvatar(UUID userId, AvatarKey avatarKey) {
        UserJpaEntity user = loadUser(userId);
        user.setAvatarKey(avatarKey == null ? null : avatarKey.value());
        user.setUpdatedAt(LocalDateTime.now());
        return toAccount(flush(user, null), userProfileJpaRepository.findByUserId(userId).orElse(null));
    }

    private UserJpaEntity loadUser(UUID userId) {
        return userJpaRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    /** Flushes now so a lost username race surfaces here (as a 409) and the version is final. */
    private UserJpaEntity flush(UserJpaEntity user, String requestedUsername) {
        try {
            return userJpaRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (requestedUsername != null) {
                throw new AccountDomainException(DuplicateUsername.of(requestedUsername));
            }
            throw e;
        }
    }

    private UserAccount toAccount(UserJpaEntity user, UserProfileJpaEntity profile) {
        return new UserAccount(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                profile == null ? null : profile.getFirstName(),
                profile == null ? null : profile.getLastName(),
                profile == null ? null : profile.getPhoneNumber(),
                avatarKeyOf(user),
                user.isEnabled(),
                user.isAccountNonLocked(),
                user.isTwoFactorEnabled(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                user.getLastLoginAt(),
                user.getPasswordChangedAt(),
                user.getRoles().stream().map(RoleJpaEntity::getCode).collect(Collectors.toUnmodifiableSet()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion() == null ? 0L : user.getVersion());
    }

    /** A malformed stored key must not make the whole profile unreadable. */
    private static AvatarKey avatarKeyOf(UserJpaEntity user) {
        try {
            return AvatarKey.ofNullable(user.getAvatarKey());
        } catch (DomainValidationException e) {
            log.warn("Ignoring malformed avatar_key on user {}", user.getId());
            return null;
        }
    }
}
