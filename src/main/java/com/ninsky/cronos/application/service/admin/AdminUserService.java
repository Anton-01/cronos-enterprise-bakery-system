package com.ninsky.cronos.application.service.admin;

import com.ninsky.cronos.application.request.core.auth.CreateUserRequest;
import com.ninsky.cronos.application.request.core.auth.UpdateUserRequest;
import com.ninsky.cronos.application.request.core.mail.EmailRequest;
import com.ninsky.cronos.application.request.user.AdminUserCreateRequest;
import com.ninsky.cronos.application.response.auth.UserResponse;
import com.ninsky.cronos.application.service.mail.MailService;
import com.ninsky.cronos.application.service.storage.CloudStorageService;
import com.ninsky.cronos.domain.entity.auth.PasswordResetToken;
import com.ninsky.cronos.domain.entity.auth.Role;
import com.ninsky.cronos.domain.entity.auth.User;
import com.ninsky.cronos.domain.entity.auth.UserProfile;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.DuplicateResourceException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.persistence.auth.*;
import com.ninsky.cronos.infrastructure.storage.LocalFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    private final CloudStorageService fileStorageService;

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(String roleName, Boolean enabled, String search, Pageable pageable) {
        log.info("Admin fetching users with filters - Role: {}, Enabled: {}, Search: {}", roleName, enabled, search);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }

            if (StringUtils.hasText(roleName)) {
                Join<User, Role> rolesJoin = root.join("roles", JoinType.INNER);
                predicates.add(cb.equal(rolesJoin.get("name"), roleName.toUpperCase()));
            }

            if (StringUtils.hasText(search)) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                Predicate usernameMatch = cb.like(cb.lower(root.get("username")), searchPattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), searchPattern);
                predicates.add(cb.or(usernameMatch, emailMatch));
            }

            assert query != null;
            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, pageable).map(this::mapToUserResponse);
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
                .twoFactorEnabled(false).failedLoginAttempts(0).roles(roles).build();

        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder().user(user).firstName(request.firstName())
                .lastName(request.lastName()).phoneNumber(request.phoneNumber()).build();
        userProfileRepository.save(profile);

        user.setProfile(profile);
        return mapToUserResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        // Implementación similar a tu UserService.updateUser, pero exclusiva para el Admin
        // Aquí podrías permitir cambiar el username/email sin validación de 2FA
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));

        // ... Lógica de actualización de campos ...
        // (Reutiliza la lógica de actualización que ya tienes)

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
        user.getRoles().clear();
        user.getRoles().addAll(roles);

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

        PasswordResetToken entity = new PasswordResetToken(resetToken, user, LocalDateTime.now().plusHours(1));
        passwordResetTokenRepository.save(entity);

        mailService.sendHtmlEmail(EmailRequest.builder().to(user.getEmail()).subject("Restablecimiento de Contraseña").templateName("auth/password-reset")
                .variables(Map.of("resetLink", "http://localhost:4200/auth/reset-password?token=" + resetToken, "username", user.getUsername()))
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
        Set<String> roleNames = getRolesByAuthenticatedUser(user);

        UserResponse.UserResponseBuilder responseBuilder = UserResponse.builder().id(user.getId()).username(user.getUsername())
                .email(user.getEmail()).enabled(user.isEnabled()).accountNonLocked(user.isAccountNonLocked())
                .twoFactorEnabled(user.isTwoFactorEnabled()).failedLoginAttempts(user.getFailedLoginAttempts())
                .lockedUntil(user.getLockedUntil()).lastLoginAt(user.getLastLoginAt())
                .passwordChangedAt(user.getPasswordChangedAt()).roles(roleNames).createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt());

        UserProfile profile = user.getProfile();
        if (profile != null) {
            responseBuilder.firstName(profile.getFirstName()).lastName(profile.getLastName()).phoneNumber(profile.getPhoneNumber());
        }

        return responseBuilder.build();
    }

    private Set<String> getRolesByAuthenticatedUser(User user) {
        return user.getRoles() != null
                ? user.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
                : Set.of();
    }

    @Transactional
    public void createUserFromAdmin(AdminUserCreateRequest request, MultipartFile profilePicture) throws IOException {
        log.info("Admin creating user: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("El email ya está registrado.");
        }

        String defaultPassword = "Cronos" + UUID.randomUUID().toString().substring(0, 8) + "!";

        User user = User.builder().username(request.username()).email(request.email())
                .password(passwordEncoder.encode(defaultPassword))
                .passwordNeedsChange(true).emailVerified(true).enabled(true)
                .roles(new HashSet<>(roleRepository.findAllById(request.roleIds()))).build();


        UserProfile profile = UserProfile.builder().user(user).firstName(request.firstName())
                .lastName(request.lastName()).phoneNumber(request.phone()).build();

        if (profilePicture != null && !profilePicture.isEmpty()) {
            String pictureUrl = fileStorageService.uploadFile(profilePicture, "profiles/");
            profile.setProfilePictureUrl(pictureUrl);
        }

        user.setProfile(profile);
        userRepository.save(user);

        // 4. Notificación (Opcional pero recomendado)
        // mailService.sendWelcomeAdminEmail(user.getEmail(), defaultPassword);

        log.info("User {} created successfully with default password.", request.email());
    }
}
