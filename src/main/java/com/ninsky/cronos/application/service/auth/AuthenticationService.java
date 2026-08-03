package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.application.event.NewDeviceLoginEvent;
import com.ninsky.cronos.application.request.core.auth.LoginRequest;
import com.ninsky.cronos.application.request.core.auth.RefreshTokenRequest;
import com.ninsky.cronos.application.response.auth.LoginResponse;
import com.ninsky.cronos.application.response.auth.TokenResponse;
import com.ninsky.cronos.domain.entity.auth.*;
import com.ninsky.cronos.infrastructure.exception.InvalidTokenException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.persistence.auth.*;
import com.ninsky.cronos.infrastructure.security.JwtService;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AccountLockoutService lockoutService;
    private final TwoFactorService twoFactorService;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final DeviceFingerprintRepository deviceFingerprintRepository;
    private final SecurityNotificationRepository securityNotificationRepository;

    private final RequestContextUtil requestContextUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionManagementService sessionManagementService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Login request for user/email: {}", request.username());

        User user = userRepository.findByUsernameWithRoles(request.username()).orElseGet(() -> userRepository.findByEmailWithRoles(request.username())
                        .orElseThrow(() -> new BadCredentialsException("Invalid credentials")));

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

            // Create the Physical Session in the Database
            UserSession session = createUserSession(user, deviceFingerprint);

            // Generate Tokens (JWT for Access, OPAQUE UUID for Refresh)
            String accessToken = jwtService.generateAccessToken(user);
            String opaqueRefreshToken = UUID.randomUUID().toString();

            saveRefreshToken(user, session, opaqueRefreshToken);
            recordSuccessfulLogin(user, user.isTwoFactorEnabled());

            return LoginResponse.builder().accessToken(accessToken).refreshToken(opaqueRefreshToken)
                    .tokenType("Bearer").expiresIn(900) // 15 minutos (Debe coincidir con jwtConfig)
                    .username(user.getUsername()).email(user.getEmail())
                    .roles(user.getRoles().stream().map(Role::getName).toList())
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
        UserSession session = refreshToken.getSession();
        if (session == null || !session.isActive() || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Associated session is terminated or expired");
        }

        User user = refreshToken.getUser();

        // Refresh session activity
        session.setLastActivityAt(LocalDateTime.now());
        userSessionRepository.save(session);


        String newAccessToken = jwtService.generateAccessToken(user);

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

                if (token.getSession() != null) {
                    UserSession session = token.getSession();
                    session.setActive(false);
                    session.setTerminatedAt(LocalDateTime.now());
                    session.setTerminationReason("USER_LOGOUT");
                    userSessionRepository.save(session);
                }
            });
        } else {
            // Sign out of all user sessions (Global logout)
            refreshTokenRepository.revokeAllUserTokens(user.getId(), LocalDateTime.now());
            userSessionRepository.terminateAllUserSessions(user.getId(), LocalDateTime.now(), "GLOBAL_LOGOUT");
        }
    }

    @Transactional
    public LoginResponse processOAuth2Login(OAuth2User oAuth2User, String provider) {
        // 1. Extraer datos del proveedor (Google/Facebook)
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String providerId = oAuth2User.getAttribute("sub"); // 'sub' es el ID en Google

        if (email == null) {
            throw new BadCredentialsException("Email not found from OAuth2 provider");
        }

        // 2. Buscar si el usuario ya existe en nuestra BD
        User user = userRepository.findByEmailWithRoles(email).orElseGet(() -> {
            // 3. Si NO existe, lo registramos automáticamente (Auto-Provisioning)
            log.info("Creating new user from OAuth2 login: {}", email);
            User newUser = User.builder().email(email)
                    .username(email) // O generar un username único basado en el nombre
                    .emailVerified(true) // Confiamos en Google
                    .enabled(true).accountNonLocked(true)
                    .accountNonExpired(true).credentialsNonExpired(true)
                    // .password(null) -> ¡Por esto permitimos contraseñas nulas en la BD!
                    .build();

            // Aquí deberías asignarle un Rol por defecto buscando en RoleRepository

            return userRepository.save(newUser);
        });

        // NOTA: Aquí deberías guardar/validar en la tabla UserSocialConnection (para el providerId)
        // para tener el histórico de qué cuentas de Google están vinculadas.

        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account is locked.");
        }

        lockoutService.handleSuccessfulLogin(user);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String deviceFingerprint = handleDeviceFingerprinting(user);
        UserSession session = createUserSession(user, deviceFingerprint);

        String accessToken = jwtService.generateAccessToken(user);
        String opaqueRefreshToken = UUID.randomUUID().toString();

        saveRefreshToken(user, session, opaqueRefreshToken);
        recordSuccessfulLogin(user, false); // false because OAuth2 bypassed our native 2FA

        return LoginResponse.builder().accessToken(accessToken)
                .refreshToken(opaqueRefreshToken).tokenType("Bearer")
                .expiresIn(900).username(user.getUsername())
                .email(user.getEmail()).build();
    }

    // PRIVATE INFRASTRUCTURE METHODS (SESSIONS, FINGERPRINT, AND AUDIT)
    private UserSession createUserSession(User user, String deviceId) {
        UserSession session = UserSession.builder().user(user)
                .sessionToken(UUID.randomUUID().toString())
                .deviceId(deviceId).ipAddress(requestContextUtil.getClientIp())
                .userAgent(requestContextUtil.getUserAgent())
                .browser(requestContextUtil.getBrowser())
                .operatingSystem(requestContextUtil.getOperatingSystem())
                .device(requestContextUtil.getDevice())
                .location(requestContextUtil.getLocation()).isActive(true)
                .createdAt(LocalDateTime.now()).lastActivityAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30)).build();

        return userSessionRepository.save(session);
    }

    private void saveRefreshToken(User user, UserSession session, String tokenStr) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr).user(user).session(session)
                .expiresAt(LocalDateTime.now().plusDays(7))
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
