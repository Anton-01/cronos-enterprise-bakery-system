package com.ninsky.cronos.application.service.auth;

import java.util.Map;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.iam.shared.Changes;
import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.InvalidField;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import com.ninsky.cronos.application.request.core.auth.ChangePasswordRequest;
import com.ninsky.cronos.application.request.core.auth.CreateUserRequest;
import com.ninsky.cronos.application.request.core.auth.UpdateUserRequest;
import com.ninsky.cronos.application.response.auth.UserResponse;
import com.ninsky.cronos.domain.model.audit.AuditAction;
import com.ninsky.cronos.domain.model.auth.PasswordHistory;
import com.ninsky.cronos.domain.model.auth.Role;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.port.auth.PasswordHistoryRepositoryPort;
import com.ninsky.cronos.domain.port.auth.RoleRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.iam.audit.AuditEvent;
import com.ninsky.cronos.iam.audit.AuditRecorder;
import com.ninsky.cronos.iam.audit.AuditTargets;
import com.ninsky.cronos.iam.policy.PasswordPolicy;
import com.ninsky.cronos.iam.policy.PasswordRules;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.signin.CredentialWriter;
import com.ninsky.cronos.infrastructure.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepositoryPort userRepository;
    private final UserProfileRepositoryPort userProfileRepository;
    private final RoleRepositoryPort roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityPolicyProvider securityPolicies;
    private final PasswordHistoryRepositoryPort passwordHistory;
    private final CredentialWriter credentials;
    private final UserDirectory userDirectory;
    private final AuditRecorder auditRecorder;
    private final AvatarStorage avatarStorage;

    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public UserResponse createUser(CreateUserRequest request) {

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already exists");
        }

        List<String> passwordErrors = passwordPolicy.violations(request.password(), request.username(), request.email(), null);
        if (!passwordErrors.isEmpty()) {
            PasswordRules.toViolations(new Violations(), "password", passwordErrors, securityPolicies.current()).throwIfAny();
        }

        Set<Role> roles = getRolesByNames(request.roles());
        if (roles.isEmpty()) {
            throw new ValidationException("At least one role must be specified");
        }

        // 3. Crear el ente de Autenticación (Motor Spring Security)
        User user = User.builder().username(request.username()).email(request.email())
                .password(passwordEncoder.encode(request.password())).emailVerified(false)
                .enabled(true).accountNonLocked(true).accountNonExpired(true).credentialsNonExpired(true)
                .twoFactorEnabled(false).failedLoginAttempts(0).passwordChangedAt(LocalDateTime.now())
                .roleIds(roles.stream().map(Role::getId).collect(Collectors.toSet())).build();

        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder().userId(user.getId()).firstName(request.firstName())
                .lastName(request.lastName()).phoneNumber(request.phoneNumber())
                .emailNotifications(true).smsNotifications(false).pushNotifications(true).build();

        userProfileRepository.save(profile);

        passwordHistory.save(PasswordHistory.builder().userId(user.getId()).passwordHash(user.getPassword())
                .changedAt(LocalDateTime.now()).build());

        log.info("User created successfully: {}", user.getUsername());

        return mapToUserResponse(user);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsernameAndIdNot(request.username(), userId)) {
                throw new DuplicateResourceException("Username already exists");
            }
            user.setUsername(request.username());
        }

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmailAndIdNot(request.email(), userId)) {
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

        userRepository.save(user);

        UserProfile profile = userProfileRepository.findByUserId(userId).orElseThrow(() -> new IllegalStateException("Profile not found for user: " + userId));

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

        log.info("User updated successfully: {}", user.getUsername());

        return mapToUserResponse(user);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));

        // Field-level 400s (not 401): a wrong *current* password on this form must not look like an
        // expired session to the frontend's auth interceptor.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new AccountDomainException(InvalidField.of("currentPassword", "account.password.currentIncorrect"));
        }

        // Also enforced by @PasswordChange at the API edge; kept for non-HTTP callers.
        if (request.newPassword().equals(request.currentPassword())) {
            throw new AccountDomainException(InvalidField.of("newPassword", "account.password.sameAsCurrent"));
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new AccountDomainException(InvalidField.of("confirmPassword", "account.password.confirmationMismatch"));
        }

        // Security policy (spec §8); the account error shape carries one error per field: the first broken rule.
        List<String> passwordErrors = passwordPolicy.violations(request.newPassword(), user.getUsername(), user.getEmail(), userId);
        if (!passwordErrors.isEmpty()) {
            String key = passwordErrors.getFirst();
            throw new AccountDomainException(InvalidField.of("newPassword", key, PasswordRules.args(key, securityPolicies.current())));
        }

        credentials.setPassword(userId, request.newPassword(), false);
        String label = userDirectory.ref(userId).map(UserRef::displayName).orElse(user.getUsername());
        auditRecorder.record(AuditEvent.of(AuditAction.PASSWORD_CHANGED, AuditTargets.USER, userId, label).build());
        if (credentials.activateIfPending(userId)) {
            auditRecorder.record(AuditEvent.of(AuditAction.USER_STATUS_CHANGED, AuditTargets.USER, userId, label)
                    .changes(Changes.of("status", UserStatus.PENDING_ACTIVATION.name(), UserStatus.ACTIVE.name()))
                    .params(Map.of("status", UserStatus.ACTIVE.name())).build());
        }
        log.info("Password changed for user {}", userId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#userId")
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    private Set<Role> getRolesByNames(Set<String> roleNames) {
        Set<Role> roles = roleRepository.findByNameIn(roleNames);

        if (roles.size() != roleNames.size()) {
            Set<String> foundNames = roles.stream().map(Role::getName).collect(Collectors.toSet());
            Set<String> notFound = roleNames.stream().filter(name -> !foundNames.contains(name)).collect(Collectors.toSet());
            throw new ResourceNotFoundException("Roles not found: " + notFound);
        }
        return roles;
    }

    private UserResponse mapToUserResponse(User user) {
        if (user == null) {
            return null;
        }

        Set<String> roleNames = user.getRoleIds() != null && !user.getRoleIds().isEmpty()
                ? roleRepository.findAllById(user.getRoleIds()).stream().map(Role::getName).collect(Collectors.toSet())
                : Set.of();

        UserResponse.UserResponseBuilder responseBuilder = UserResponse.builder().id(user.getId())
                .username(user.getUsername()).email(user.getEmail()).enabled(user.isEnabled())
                .accountNonLocked(user.isAccountNonLocked()).twoFactorEnabled(user.isTwoFactorEnabled())
                .failedLoginAttempts(user.getFailedLoginAttempts()).lockedUntil(user.getLockedUntil())
                .lastLoginAt(user.getLastLoginAt()).passwordChangedAt(user.getPasswordChangedAt())
                .roles(roleNames).createdAt(user.getCreatedAt()).updatedAt(user.getUpdatedAt());

        userProfileRepository.findByUserId(user.getId()).ifPresent(profile -> responseBuilder.firstName(profile.getFirstName()).lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber()));

        responseBuilder.avatarUrl(avatarUrlOf(user));
        return responseBuilder.build();
    }

    private String avatarUrlOf(User user) {
        try {
            AvatarKey key = AvatarKey.ofNullable(user.getAvatarKey());
            return key == null ? null : avatarStorage.publicUrl(key).toString();
        } catch (DomainValidationException e) {
            return null;
        }
    }
}
