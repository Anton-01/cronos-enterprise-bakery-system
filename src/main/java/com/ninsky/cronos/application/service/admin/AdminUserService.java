package com.ninsky.cronos.application.service.admin;

import com.ninsky.cronos.application.request.core.auth.CreateUserRequest;
import com.ninsky.cronos.application.request.core.auth.UpdateUserRequest;
import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.request.user.AdminUserCreateRequest;
import com.ninsky.cronos.application.response.auth.UserResponse;
import com.ninsky.cronos.application.service.mail.MailService;
import com.ninsky.cronos.infrastructure.storage.StoragePort;
import com.ninsky.cronos.domain.model.auth.PasswordResetToken;
import com.ninsky.cronos.domain.model.auth.Role;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.port.auth.*;
import com.ninsky.cronos.infrastructure.config.security.JwtConfig;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.security.blacklist.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepositoryPort userRepository;
    private final UserProfileRepositoryPort userProfileRepository;
    private final RoleRepositoryPort roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final UserSessionRepositoryPort userSessionRepository;
    private final PasswordResetTokenRepositoryPort passwordResetTokenRepository;
    private final MailService mailService;
    private final StoragePort fileStorageService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtConfig jwtConfig;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(String roleName, Boolean enabled, String search, Pageable pageable) {
        log.info("Admin fetching users with filters - Role: {}, Enabled: {}, Search: {}", roleName, enabled, search);

        UserSearchCriteria criteria = new UserSearchCriteria(roleName, enabled, search);
        return userRepository.search(criteria, pageable).map(this::mapToUserResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Admin creating new user: {}", request.email());

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already exists");
        }

        Set<Role> roles = getRolesByNames(request.roles());

        User user = User.builder().username(request.username()).email(request.email()).password(passwordEncoder.encode(request.password())).emailVerified(true)
                .enabled(true).accountNonLocked(true).accountNonExpired(true).credentialsNonExpired(true)
                .twoFactorEnabled(false).failedLoginAttempts(0)
                .roleIds(roles.stream().map(Role::getId).collect(Collectors.toSet())).build();

        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder().userId(user.getId()).firstName(request.firstName())
                .lastName(request.lastName()).phoneNumber(request.phoneNumber()).build();
        userProfileRepository.save(profile);

        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsernameAndIdNot(request.username(), id)) {
                throw new DuplicateResourceException("Username already exists");
            }
            user.setUsername(request.username());
        }

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmailAndIdNot(request.email(), id)) {
                throw new DuplicateResourceException("Email already exists");
            }
            user.setEmail(request.email());
            user.setEmailVerified(false);
        }

        if (request.roles() != null && !request.roles().isEmpty()) {
            Set<Role> newRoles = getRolesByNames(request.roles());
            user.setRoleIds(newRoles.stream().map(Role::getId).collect(Collectors.toSet()));
        }

        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }

        user = userRepository.save(user);

        userProfileRepository.findByUserId(id).ifPresent(profile -> {
            boolean profileUpdated = false;
            if (request.firstName() != null) {
                profile.setFirstName(request.firstName());
                profileUpdated = true;
            }
            if (request.lastName() != null) {
                profile.setLastName(request.lastName());
                profileUpdated = true;
            }
            if (request.phoneNumber() != null) {
                profile.setPhoneNumber(request.phoneNumber());
                profileUpdated = true;
            }
            if (profileUpdated) {
                userProfileRepository.save(profile);
            }
        });

        log.info("User updated successfully by admin: {}", user.getUsername());
        return mapToUserResponse(user);
    }

    // State operations and manage Roles
    @Transactional
    public UserResponse updateUserStatus(UUID id, boolean isUnlocked) {
        log.info("Updating lock status for user {}: {}", id, isUnlocked ? "UNLOCKED" : "LOCKED");

        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        user.setAccountNonLocked(isUnlocked);
        if (isUnlocked) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        user = userRepository.save(user);
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse assignRoles(UUID id, Set<String> roleNames) {
        log.info("Admin assigning roles {} to user {}", roleNames, id);
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));

        Set<Role> roles = getRolesByNames(roleNames);
        user.setRoleIds(roles.stream().map(Role::getId).collect(Collectors.toSet()));

        userRepository.save(user);

        // Require the user to log in again so that the JWT is regenerated with the new roles
        forceGlobalLogout(id);
        return mapToUserResponse(user);
    }


    // Operations for technical support - Admin
    @Transactional
    public void unlockAccount(UUID id) {
        log.warn("Admin manually unlocking account for user {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setAccountNonLocked(true);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);

        userRepository.save(user);
    }

    @Transactional
    public void forceGlobalLogout(UUID id) {
        log.warn("Admin executing FORCE GLOBAL LOGOUT for user {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));

        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.revokeAllUserTokens(user.getId(), now);
        userSessionRepository.terminateAllUserSessions(user.getId(), now, "ADMIN_FORCED_LOGOUT");
        tokenBlacklistService.blacklistUser(user.getId(), Duration.ofMillis(jwtConfig.getAccessTokenExpiration()));
    }

    @Transactional
    public void disableTwoFactorAuthentication(UUID id) {
        log.warn("Admin EMERGENCY 2FA DISABLE for user {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);

        userRepository.save(user);
    }

    @Transactional
    public void initiatePasswordReset(UUID id) {
        log.info("Admin initiated password reset flow for user {}", id);
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));

        String resetToken = UUID.randomUUID().toString();

        PasswordResetToken entity = PasswordResetToken.builder()
                .token(resetToken).userId(user.getId())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false).createdAt(LocalDateTime.now())
                .build();
        passwordResetTokenRepository.save(entity);

        mailService.sendHtmlEmail(EmailRequest.builder().to(user.getEmail()).subject("Restablecimiento de Contraseña").templateName("auth/password-reset")
                .variables(Map.of("resetLink", frontendUrl + "/auth/reset-password?token=" + resetToken, "username", user.getUsername()))
                .build());
    }

    // Private methods. Mapper and validations
    private Set<Role> getRolesByNames(Set<String> roleNames) {
        Set<Role> roles = roleRepository.findByNameIn(roleNames);
        if (roles.size() != roleNames.size()) {
            throw new ResourceNotFoundException("One or more roles were not found in the database");
        }
        return roles;
    }

    private UserResponse mapToUserResponse(User user) {
        Set<String> roleNames = getRoleNames(user);

        UserResponse.UserResponseBuilder responseBuilder = UserResponse.builder().id(user.getId()).username(user.getUsername())
                .email(user.getEmail()).enabled(user.isEnabled()).accountNonLocked(user.isAccountNonLocked())
                .twoFactorEnabled(user.isTwoFactorEnabled()).failedLoginAttempts(user.getFailedLoginAttempts())
                .lockedUntil(user.getLockedUntil()).lastLoginAt(user.getLastLoginAt())
                .passwordChangedAt(user.getPasswordChangedAt()).roles(roleNames).createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt());

        userProfileRepository.findByUserId(user.getId()).ifPresent(profile ->
                responseBuilder.firstName(profile.getFirstName()).lastName(profile.getLastName()).phoneNumber(profile.getPhoneNumber()));

        return responseBuilder.build();
    }

    private Set<String> getRoleNames(User user) {
        return user.getRoleIds() != null && !user.getRoleIds().isEmpty()
                ? roleRepository.findAllById(user.getRoleIds()).stream().map(Role::getName).collect(Collectors.toSet())
                : Set.of();
    }

    @Transactional
    public void createUserFromAdmin(AdminUserCreateRequest request, MultipartFile profilePicture) throws IOException {
        log.info("Admin creating user: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("El email ya está registrado.");
        }

        String defaultPassword = "Cronos" + UUID.randomUUID().toString().substring(0, 8) + "!";

        Set<Long> roleIds = roleRepository.findAllById(request.roleIds()).stream().map(Role::getId).collect(Collectors.toSet());

        User user = User.builder().username(request.username()).email(request.email())
                .password(passwordEncoder.encode(defaultPassword))
                .passwordNeedsChange(true).emailVerified(true).enabled(true)
                .roleIds(roleIds).build();

        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder().userId(user.getId()).firstName(request.firstName())
                .lastName(request.lastName()).phoneNumber(request.phone()).build();

        if (profilePicture != null && !profilePicture.isEmpty()) {
            String pictureUrl = fileStorageService.uploadFile(profilePicture, "profiles/");
            profile.setProfilePictureUrl(pictureUrl);
        }

        userProfileRepository.save(profile);

        // 4. Notificación (Opcional pero recomendado)
        // mailService.sendWelcomeAdminEmail(user.getEmail(), defaultPassword);

        log.info("User {} created successfully with default password.", request.email());
    }
}
