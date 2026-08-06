package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.application.event.NewDeviceLoginEvent;
import com.ninsky.cronos.application.request.core.auth.LoginRequest;
import com.ninsky.cronos.application.request.core.auth.RefreshTokenRequest;
import com.ninsky.cronos.application.response.auth.LoginResponse;
import com.ninsky.cronos.application.response.auth.TokenResponse;
import com.ninsky.cronos.application.response.menu.MenuItemResponse;
import com.ninsky.cronos.domain.model.auth.DeviceFingerprint;
import com.ninsky.cronos.domain.model.auth.LoginHistory;
import com.ninsky.cronos.domain.model.auth.SecurityNotification;
import com.ninsky.cronos.domain.model.auth.Permission;
import com.ninsky.cronos.domain.model.auth.RefreshToken;
import com.ninsky.cronos.domain.model.auth.Role;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserSession;
import com.ninsky.cronos.domain.model.menu.MenuNode;
import com.ninsky.cronos.domain.port.auth.DeviceFingerprintRepositoryPort;
import com.ninsky.cronos.domain.port.auth.LoginHistoryRepositoryPort;
import com.ninsky.cronos.domain.port.auth.PermissionRepositoryPort;
import com.ninsky.cronos.domain.port.auth.RefreshTokenRepositoryPort;
import com.ninsky.cronos.domain.port.auth.RoleRepositoryPort;
import com.ninsky.cronos.domain.port.auth.SecurityNotificationRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserSessionRepositoryPort;
import com.ninsky.cronos.domain.port.menu.MenuPort;
import com.ninsky.cronos.infrastructure.config.security.JwtConfig;
import com.ninsky.cronos.infrastructure.exception.InvalidTokenException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.security.JwtService;
import com.ninsky.cronos.infrastructure.security.blacklist.TokenBlacklistService;
import com.ninsky.cronos.infrastructure.security.dpop.DpopProofValidator;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepositoryPort userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final AccountLockoutService lockoutService;
    private final TwoFactorService twoFactorService;
    private final RoleRepositoryPort roleRepository;
    private final PermissionRepositoryPort permissionRepository;
    private final MenuPort menuPort;

    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final UserSessionRepositoryPort userSessionRepository;
    private final LoginHistoryRepositoryPort loginHistoryRepository;
    private final DeviceFingerprintRepositoryPort deviceFingerprintRepository;
    private final SecurityNotificationRepositoryPort securityNotificationRepository;

    private final RequestContextUtil requestContextUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionManagementService sessionManagementService;
    private final TokenBlacklistService tokenBlacklistService;
    private final DpopProofValidator dpopProofValidator;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Login request for user/email: {}", request.username());

        User user = userRepository.findByUsername(request.username())
                .or(() -> userRepository.findByEmail(request.username()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // Check if the account is blocked
        if (lockoutService.isAccountLocked(user)) {
            long remainingMinutes = lockoutService.getRemainingLockoutTime(user);
            recordFailedLogin(user, "Account locked");
            throw new LockedException(String.format("Account is locked. Try again in %d minutes", remainingMinutes));
        }

        try {
            // Authenticating credentials with Spring Security
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));

            // Verification 2FA
            if (user.isTwoFactorEnabled()) {
                if (request.twoFactorCode() == null) {
                    return LoginResponse.builder().requiresTwoFactor(true)
                            .message("Two-factor authentication code required").build();
                }

                if (!twoFactorService.isCodeValid(user, request.twoFactorCode())) {
                    lockoutService.handleFailedLogin(user);
                    recordFailedLogin(user, "Invalid 2FA code");
                    throw new BadCredentialsException("Invalid two-factor authentication code");
                }
            }

            // Login successful -> Reset lockout counters
            lockoutService.handleSuccessfulLogin(user);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            // Fingerprinting and Security Alerts
            String deviceFingerprint = handleDeviceFingerprinting(user);

            // (Fire-and-Forget)
            sessionManagementService.cleanupConcurrentSessionsAsync(user, 3);

            // DPoP binding is opt-in: a client that wants a bound token sends a DPoP proof on the
            // login request itself. No header -> unbound token, today's exact behavior. An invalid
            // proof fails the login outright rather than silently downgrading (the client explicitly
            // signaled intent to bind).
            String dpopProof = requestContextUtil.getHeader("DPoP");
            String dpopJkt = dpopProof != null ? dpopProofValidator.validate(dpopProof, "POST", requestContextUtil.getRequestUrl()) : null;

            // Create the Physical Session in the Database
            UserSession session = createUserSession(user, deviceFingerprint, dpopJkt);

            RoleAndPermissionNames grants = resolveRoleAndPermissionNames(user.getRoleIds());

            // Generate Tokens (JWT for Access, OPAQUE UUID for Refresh)
            String accessToken = jwtService.generateAccessToken(user, session.getId(), grants.roleNames(), grants.permissionNames(), dpopJkt);
            String opaqueRefreshToken = UUID.randomUUID().toString();

            saveRefreshToken(user, session, opaqueRefreshToken);
            recordSuccessfulLogin(user, user.isTwoFactorEnabled());

            List<MenuItemResponse> navigation = buildNavigation(grants.permissionNames());

            return LoginResponse.builder().accessToken(accessToken).refreshToken(opaqueRefreshToken)
                    .tokenType("Bearer").expiresIn(900) // 15 minutos (Debe coincidir con jwtConfig)
                    .username(user.getUsername()).email(user.getEmail())
                    .roles(grants.roleNames())
                    .policies(grants.policies())
                    .navigation(navigation)
                    .requiresTwoFactor(false).message("Login successful").build();

        } catch (BadCredentialsException e) {
            lockoutService.handleFailedLogin(user);
            recordFailedLogin(user, "Invalid credentials");
            throw e;
        }
    }

    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshTokenStr = request.refreshToken();

        // Search for the opaque token in the database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr).orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        // Verify that it has not been revoked or expired
        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Refresh token is expired or revoked");
        }

        // Verify that the Parent Session is still active (the user did not close it remotely)
        UserSession session = refreshToken.getSessionId() != null ? userSessionRepository.findById(refreshToken.getSessionId()).orElse(null) : null;
        if (session == null || !session.isActive() || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Associated session is terminated or expired");
        }

        // A DPoP-bound session can only be refreshed by the same key it was bound to at login —
        // otherwise a stolen refresh token could mint a newly-bound access token under an attacker's key.
        if (session.getDpopJkt() != null) {
            String dpopProof = requestContextUtil.getHeader("DPoP");
            String jkt = dpopProofValidator.validate(dpopProof, "POST", requestContextUtil.getRequestUrl());
            if (!session.getDpopJkt().equals(jkt)) {
                throw new InvalidTokenException("DPoP proof does not match the key this session was bound to");
            }
        }

        User user = userRepository.findById(refreshToken.getUserId()).orElseThrow(() -> new UserNotFoundException("User not found"));

        // Refresh session activity
        session.setLastActivityAt(LocalDateTime.now());
        userSessionRepository.save(session);

        RoleAndPermissionNames grants = resolveRoleAndPermissionNames(user.getRoleIds());
        String newAccessToken = jwtService.generateAccessToken(user, session.getId(), grants.roleNames(), grants.permissionNames(), session.getDpopJkt());

        // Refresh Token Rotation (OAuth 2.0 Security Best Practice)
        String newOpaqueRefreshToken = UUID.randomUUID().toString();

        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        saveRefreshToken(user, session, newOpaqueRefreshToken);

        return TokenResponse.builder().accessToken(newAccessToken).refreshToken(newOpaqueRefreshToken).tokenType("Bearer").expiresIn(900).build();
    }

    @Transactional
    public void logout(String username, String refreshTokenStr) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (refreshTokenStr != null) {
            // Sign out only from this specific device
            refreshTokenRepository.findByToken(refreshTokenStr).ifPresent(token -> {
                token.setRevoked(true);
                token.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(token);

                if (token.getSessionId() != null) {
                    userSessionRepository.findById(token.getSessionId()).ifPresent(session -> {
                        session.setActive(false);
                        session.setTerminatedAt(LocalDateTime.now());
                        session.setTerminationReason("USER_LOGOUT");
                        userSessionRepository.save(session);
                        tokenBlacklistService.blacklistSession(session.getId(), Duration.ofMillis(jwtConfig.getAccessTokenExpiration()));
                    });
                }
            });
        } else {
            // Sign out of all user sessions (Global logout)
            refreshTokenRepository.revokeAllUserTokens(user.getId(), LocalDateTime.now());
            userSessionRepository.terminateAllUserSessions(user.getId(), LocalDateTime.now(), "GLOBAL_LOGOUT");
            tokenBlacklistService.blacklistUser(user.getId(), Duration.ofMillis(jwtConfig.getAccessTokenExpiration()));
        }
    }

    @Transactional
    public LoginResponse processOAuth2Login(OAuth2User oAuth2User, String provider) {
        // 1. Extraer datos del proveedor (Google/Facebook)
        String email = oAuth2User.getAttribute("email");
        String providerId = oAuth2User.getAttribute("sub"); // 'sub' es el ID en Google

        if (email == null) {
            throw new BadCredentialsException("Email not found from OAuth2 provider");
        }

        // 2. Buscar si el usuario ya existe en nuestra BD
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            // 3. Si NO existe, lo registramos automáticamente (Auto-Provisioning)
            log.info("Creating new user from OAuth2 login: {}", email);
            User newUser = User.builder().email(email)
                    .username(email) // O generar un username único basado en el nombre
                    .emailVerified(true) // Confiamos en Google
                    .enabled(true).accountNonLocked(true)
                    .accountNonExpired(true).credentialsNonExpired(true)
                    // .password(null) -> ¡Por esto permitimos contraseñas nulas en la BD!
                    .build();

            // Aquí deberías asignarle un Rol por defecto buscando en RoleRepositoryPort

            return userRepository.save(newUser);
        });

        // NOTA: Aquí deberías guardar/validar en la tabla UserSocialConnection (para el providerId)
        // para tener el histórico de qué cuentas de Google están vinculadas.

        if (user.isCurrentlyLocked()) {
            throw new LockedException("Account is locked.");
        }

        lockoutService.handleSuccessfulLogin(user);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String deviceFingerprint = handleDeviceFingerprinting(user);
        // DPoP binding is deliberately not offered on the OAuth2 flow: the provider-redirect
        // callback isn't something an SPA can attach a custom header to, so OAuth2-originated
        // logins always issue unbound tokens.
        UserSession session = createUserSession(user, deviceFingerprint, null);

        RoleAndPermissionNames grants = resolveRoleAndPermissionNames(user.getRoleIds());
        String accessToken = jwtService.generateAccessToken(user, session.getId(), grants.roleNames(), grants.permissionNames(), null);
        String opaqueRefreshToken = UUID.randomUUID().toString();

        saveRefreshToken(user, session, opaqueRefreshToken);
        recordSuccessfulLogin(user, false); // false because OAuth2 bypassed our native 2FA

        return LoginResponse.builder().accessToken(accessToken)
                .refreshToken(opaqueRefreshToken).tokenType("Bearer")
                .expiresIn(900).username(user.getUsername())
                .email(user.getEmail()).roles(grants.roleNames())
                .policies(grants.policies()).navigation(buildNavigation(grants.permissionNames()))
                .build();
    }

    // ROLE/PERMISSION/MENU RESOLUTION

    private record RoleAndPermissionNames(List<String> roleNames, List<String> permissionNames, List<String> policies) {
    }

    private RoleAndPermissionNames resolveRoleAndPermissionNames(Set<Long> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        Set<Long> permissionIds = roles.stream().flatMap(r -> r.getPermissionIds().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);

        List<String> roleNames = roles.stream().map(Role::getName).toList();
        List<String> permissionNames = permissions.stream().map(Permission::getName).distinct().toList();
        List<String> policies = permissions.stream().map(Permission::toUrn).distinct().toList();

        return new RoleAndPermissionNames(roleNames, permissionNames, policies);
    }

    private List<MenuItemResponse> buildNavigation(List<String> grantedPermissionNames) {
        List<MenuNode> tree = menuPort.buildTree(Set.copyOf(grantedPermissionNames));
        return tree.stream().map(this::toMenuItemResponse).toList();
    }

    private MenuItemResponse toMenuItemResponse(MenuNode node) {
        return MenuItemResponse.builder()
                .code(node.getCode())
                .label(node.label(java.util.Locale.of("es")))
                .icon(node.getIcon())
                .path(node.getPath())
                .children(node.getChildren().stream().map(this::toMenuItemResponse).toList())
                .build();
    }

    // PRIVATE INFRASTRUCTURE METHODS (SESSIONS, FINGERPRINT, AND AUDIT)
    private UserSession createUserSession(User user, String deviceId, String dpopJkt) {
        UserSession session = UserSession.builder().userId(user.getId())
                .sessionToken(UUID.randomUUID().toString())
                .deviceId(deviceId).ipAddress(requestContextUtil.getClientIp())
                .userAgent(requestContextUtil.getUserAgent())
                .browser(requestContextUtil.getBrowser())
                .operatingSystem(requestContextUtil.getOperatingSystem())
                .device(requestContextUtil.getDevice())
                .location(requestContextUtil.getLocation()).isActive(true)
                .createdAt(LocalDateTime.now()).lastActivityAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30)).dpopJkt(dpopJkt).build();

        return userSessionRepository.save(session);
    }

    private void saveRefreshToken(User user, UserSession session, String tokenStr) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr).userId(user.getId()).sessionId(session.getId())
                .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(jwtConfig.getRefreshTokenExpiration())))
                .revoked(false).ipAddress(requestContextUtil.getClientIp())
                .userAgent(requestContextUtil.getUserAgent()).createdAt(LocalDateTime.now()).build();

        refreshTokenRepository.save(refreshToken);
    }

    private String handleDeviceFingerprinting(User user) {
        String browser = requestContextUtil.getBrowser();
        String os = requestContextUtil.getOperatingSystem();
        String device = requestContextUtil.getDevice();

        // En un entorno real, el front puede enviar un FingerprintJS hash.
        String rawFingerprint = browser + "|" + os + "|" + device;
        String fingerprintHash = generateSha256(rawFingerprint);

        Optional<DeviceFingerprint> existingDevice = deviceFingerprintRepository.findByUserIdAndFingerprintHash(user.getId(), fingerprintHash);

        if (existingDevice.isEmpty()) {
            // NEW DEVICE DETECTED! We'll save it and send you a notification
            DeviceFingerprint newDevice = DeviceFingerprint.builder().userId(user.getId()).fingerprintHash(fingerprintHash)
                    .deviceName(os + " " + browser).userAgent(requestContextUtil.getUserAgent())
                    .browser(browser).operatingSystem(os).deviceType(device)
                    .ipAddress(requestContextUtil.getClientIp())
                    .location(requestContextUtil.getLocation())
                    .trusted(false).loginCount(1).firstSeenAt(LocalDateTime.now()).build();

            deviceFingerprintRepository.save(newDevice);

            // Trigger a security alert (to be processed asynchronously or sent via email)
            createSecurityNotification(user, newDevice);
        } else {
            // Existing device; we are updating the counters
            DeviceFingerprint knownDevice = existingDevice.get();
            knownDevice.setLoginCount(knownDevice.getLoginCount() + 1);
            knownDevice.setLastSeenAt(LocalDateTime.now());
            deviceFingerprintRepository.save(knownDevice);
        }

        return fingerprintHash;
    }

    private void createSecurityNotification(User user, DeviceFingerprint device) {
        SecurityNotification notification = SecurityNotification.builder()
                .userId(user.getId()).type("NEW_DEVICE_LOGIN").title("New login detected")
                .message("We've detected a login from a new device: " + device.getDeviceName())
                .severity("WARNING").deviceName(device.getDeviceName())
                .ipAddress(device.getIpAddress()).location(device.getLocation())
                .browser(device.getBrowser()).operatingSystem(device.getOperatingSystem())
                .read(false).emailSent(false).createdAt(LocalDateTime.now()).build();

        securityNotificationRepository.save(notification);

        log.info("Publishing the NewDeviceLoginEvent for the user: {}", user.getEmail());
        eventPublisher.publishEvent(NewDeviceLoginEvent.builder().email(user.getEmail()).username(user.getUsername()).deviceName(device.getDeviceName())
                .location(device.getLocation()).ipAddress(device.getIpAddress())
                .time(LocalDateTime.now()).build());
    }

    private void recordSuccessfulLogin(User user, boolean twoFactorUsed) {
        LoginHistory loginHistory = LoginHistory.builder().userId(user.getId()).ipAddress(requestContextUtil.getClientIp())
                .userAgent(requestContextUtil.getUserAgent()).browser(requestContextUtil.getBrowser()).status("SUCCESS")
                .operatingSystem(requestContextUtil.getOperatingSystem()).device(requestContextUtil.getDevice())
                .location(requestContextUtil.getLocation()).successful(true).twoFactorUsed(twoFactorUsed).loginAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now()).build();

        loginHistoryRepository.save(loginHistory);
    }

    private void recordFailedLogin(User user, String reason) {
        log.warn("Failed login attempt for user: {}. Reason: {}", user.getUsername(), reason);
        LoginHistory loginHistory = LoginHistory.builder().userId(user.getId()).status("FAILED")
                .ipAddress(requestContextUtil.getClientIp()).userAgent(requestContextUtil.getUserAgent())
                .browser(requestContextUtil.getBrowser()).operatingSystem(requestContextUtil.getOperatingSystem())
                .device(requestContextUtil.getDevice()).successful(false)
                .failureReason(reason).loginAt(LocalDateTime.now()).createdAt(LocalDateTime.now())
                .build();

        loginHistoryRepository.save(loginHistory);
    }

    private String generateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }
}
