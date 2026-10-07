package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.application.event.NewDeviceLoginEvent;
import com.ninsky.cronos.application.request.core.auth.LoginRequest;
import com.ninsky.cronos.application.request.core.auth.RefreshTokenRequest;
import com.ninsky.cronos.application.response.auth.LoginResponse;
import com.ninsky.cronos.application.response.auth.TokenResponse;
import com.ninsky.cronos.application.response.menu.MenuItemResponse;
import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.domain.model.auth.DeviceFingerprint;
import com.ninsky.cronos.domain.model.auth.RefreshToken;
import com.ninsky.cronos.domain.model.auth.SecurityNotification;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserSession;
import com.ninsky.cronos.domain.model.menu.MenuNode;
import com.ninsky.cronos.domain.port.auth.DeviceFingerprintRepositoryPort;
import com.ninsky.cronos.domain.port.auth.RefreshTokenRepositoryPort;
import com.ninsky.cronos.domain.port.auth.SecurityNotificationRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserAuthLookupPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserSessionRepositoryPort;
import com.ninsky.cronos.domain.port.menu.MenuPort;
import com.ninsky.cronos.iam.access.UserAccessService;
import com.ninsky.cronos.iam.access.UserAccessState;
import com.ninsky.cronos.iam.policy.SecurityPolicy;
import com.ninsky.cronos.iam.policy.SecurityPolicyProvider;
import com.ninsky.cronos.iam.policy.TwoFactorRequirement;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.iam.shared.UserDirectory;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.iam.signin.AccountStanding;
import com.ninsky.cronos.iam.signin.AccountStandingCustomRepository;
import com.ninsky.cronos.iam.signin.SignInJournal;
import com.ninsky.cronos.iam.twofactor.TwoFactorAccountService;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.infrastructure.config.security.JwtConfig;
import com.ninsky.cronos.infrastructure.exception.InvalidTokenException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.security.JwtService;
import com.ninsky.cronos.infrastructure.security.blacklist.TokenBlacklistService;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.infrastructure.security.dpop.DpopProofValidator;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepositoryPort userRepository;
    private final UserAuthLookupPort userAuthLookupPort;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final AccountLockoutService lockoutService;
    private final TwoFactorAccountService twoFactorAccounts;
    private final TwoFactorRequirement twoFactorRequirement;
    private final MenuPort menuPort;
    private final UserAccessService userAccessService;

    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final UserSessionRepositoryPort userSessionRepository;
    private final DeviceFingerprintRepositoryPort deviceFingerprintRepository;
    private final SecurityNotificationRepositoryPort securityNotificationRepository;

    private final RequestContextUtil requestContextUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionManagementService sessionManagementService;
    private final TokenBlacklistService tokenBlacklistService;
    private final DpopProofValidator dpopProofValidator;

    private final SecurityPolicyProvider securityPolicyProvider;
    private final AccountStandingCustomRepository accountStandings;
    private final SignInJournal signInJournal;
    private final UserDirectory userDirectory;
    private final Clock clock;

    /**
     * Sign-in with the policy enforcement points of spec §8. Commits on authentication failures
     * (noRollbackFor) so attempt counters, lockouts and sign-in history survive the 401.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResponse login(LoginRequest request) {
        log.info("Login request received");

        // Lean lookup (see UserAuthLookupPort/AuthUserProjection): never decrypts email/2FA secret
        // until the password is confirmed correct.
        AuthUserProjection authUser = userAuthLookupPort.findByUsernameOrEmail(request.username()).orElse(null);
        if (authUser == null) {
            signInJournal.failed(null, null, SignInJournal.Outcome.FAILURE, SignInJournal.Failure.UNKNOWN_ACCOUNT);
            throw new BadCredentialsException("Invalid credentials");
        }
        String label = labelOf(authUser.id(), authUser.username());
        SecurityPolicy policy = securityPolicyProvider.current();
        AccountStanding standing = lockoutService.releaseExpiredLock(accountStandings.find(authUser.id())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials")), label);
        rejectUnlessActive(standing, label);

        try {
            // Password check through Spring Security (CustomUserDetailsService, same lean lookup).
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException e) {
            boolean locked = lockoutService.registerFailure(authUser.id(), standing.status(), label);
            signInJournal.failed(authUser.id(), label, SignInJournal.Outcome.FAILURE, SignInJournal.Failure.INVALID_CREDENTIALS);
            throw locked ? new LockedException("Account is locked") : new BadCredentialsException("Invalid credentials");
        }

        // Password confirmed: only now load the full aggregate (decrypts email/2FA secret). It is never
        // saved back here; login bookkeeping is JDBC so it cannot clash with UserStatusWriter's version bumps.
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + authUser.id()));

        // A corrupted/undecryptable column degrades to a sentinel instead of throwing: reject explicitly.
        if (isDecryptionCorrupted(user)) {
            lockoutService.registerFailure(user.getId(), standing.status(), label);
            signInJournal.failed(user.getId(), label, SignInJournal.Outcome.FAILURE, SignInJournal.Failure.INVALID_CREDENTIALS);
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.isTwoFactorEnabled()) {
            if (request.twoFactorCode() == null || request.twoFactorCode().isBlank()) {
                return LoginResponse.builder().requiresTwoFactor(true)
                        .message("Two-factor authentication code required").build();
            }
            if (!twoFactorAccounts.verifySignIn(user.getId(), request.twoFactorCode()).accepted()) {
                boolean locked = lockoutService.registerFailure(user.getId(), standing.status(), label);
                signInJournal.failed(user.getId(), label, SignInJournal.Outcome.TWO_FACTOR_FAILED,
                        SignInJournal.Failure.INVALID_TWO_FACTOR_CODE);
                throw locked ? new LockedException("Account is locked") : new BadCredentialsException("Invalid two-factor authentication code");
            }
        }

        lockoutService.registerSuccess(user.getId());
        LocalDateTime now = TenantTime.nowLocal(clock);

        // Fingerprinting and Security Alerts
        String deviceFingerprint = handleDeviceFingerprinting(user);

        // DPoP binding is opt-in: a client that wants a bound token sends a DPoP proof on the login
        // request itself. No header -> unbound token. An invalid proof fails the login outright.
        String dpopProof = requestContextUtil.getHeader("DPoP");
        String dpopJkt = dpopProof != null ? dpopProofValidator.validate(dpopProof, "POST", requestContextUtil.getRequestUrl()) : null;

        UserSession session = createUserSession(user, deviceFingerprint, dpopJkt, policy, now);
        sessionManagementService.enforceConcurrencyLimit(user.getId(), policy.maxConcurrentSessions(), now);

        RoleAndPermissionNames grants = resolveRoleAndPermissionNames(user.getId());
        String accessToken = jwtService.generateAccessToken(user, session.getId(), grants.roleNames(), grants.permissionNames(), dpopJkt, grants.accessVersion());
        String opaqueRefreshToken = UUID.randomUUID().toString();

        saveRefreshToken(user, session, opaqueRefreshToken, now);
        signInJournal.succeeded(user.getId(), label, user.isTwoFactorEnabled());

        return LoginResponse.builder().accessToken(accessToken).refreshToken(opaqueRefreshToken)
                .tokenType("Bearer").expiresIn(accessTokenSeconds())
                .username(user.getUsername()).email(user.getEmail())
                .roles(grants.roleNames())
                .policies(grants.policies())
                .navigation(buildNavigation(grants.permissionNames()))
                .mustChangePassword(standing.mustChangePassword(policy, TenantTime.now(clock)))
                .requiresTwoFactorEnrollment(twoFactorRequirement.mustEnrol(user.getId()))
                .requiresTwoFactor(false).message("Login successful").build();
    }

    /** Session idle/absolute limits are checked here; an expired session is terminated (and that commits). */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshTokenStr = request.refreshToken();
        LocalDateTime now = TenantTime.nowLocal(clock);

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr).orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));
        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(now)) {
            throw new InvalidTokenException("Refresh token is expired or revoked");
        }

        // The parent session must still be active (not closed remotely) and within the policy limits.
        UserSession session = refreshToken.getSessionId() != null ? userSessionRepository.findById(refreshToken.getSessionId()).orElse(null) : null;
        if (session == null || !session.isActive()) {
            throw new InvalidTokenException("Associated session is terminated or expired");
        }
        Optional<String> expiry = sessionManagementService.expiry(session, securityPolicyProvider.current(), now);
        if (expiry.isPresent()) {
            sessionManagementService.terminate(session, expiry.get(), now);
            throw new InvalidTokenException("Associated session is terminated or expired");
        }

        // A DPoP-bound session can only be refreshed by the same key it was bound to at login.
        if (session.getDpopJkt() != null) {
            String dpopProof = requestContextUtil.getHeader("DPoP");
            String jkt = dpopProofValidator.validate(dpopProof, "POST", requestContextUtil.getRequestUrl());
            if (!session.getDpopJkt().equals(jkt)) {
                throw new InvalidTokenException("DPoP proof does not match the key this session was bound to");
            }
        }

        User user = userRepository.findById(refreshToken.getUserId()).orElseThrow(() -> new UserNotFoundException("User not found"));

        session.setLastActivityAt(now);
        userSessionRepository.save(session);

        RoleAndPermissionNames grants = resolveRoleAndPermissionNames(user.getId());
        String newAccessToken = jwtService.generateAccessToken(user, session.getId(), grants.roleNames(), grants.permissionNames(), session.getDpopJkt(), grants.accessVersion());

        // Refresh Token Rotation (OAuth 2.0 Security Best Practice)
        String newOpaqueRefreshToken = UUID.randomUUID().toString();
        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(now);
        refreshTokenRepository.save(refreshToken);
        saveRefreshToken(user, session, newOpaqueRefreshToken, now);

        return TokenResponse.builder().accessToken(newAccessToken).refreshToken(newOpaqueRefreshToken).tokenType("Bearer")
                .expiresIn(accessTokenSeconds()).build();
    }

    @Transactional
    public void logout(String username, String refreshTokenStr) {
        // Logout never needs email/2FA secret — the lean lookup avoids decrypting anything.
        AuthUserProjection authUser = userAuthLookupPort.findByUsernameOrEmail(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        UUID userId = authUser.id();
        LocalDateTime now = TenantTime.nowLocal(clock);

        if (refreshTokenStr != null) {
            // Sign out only from this specific device; another user's token is ignored.
            refreshTokenRepository.findByToken(refreshTokenStr).filter(token -> userId.equals(token.getUserId())).ifPresent(token -> {
                token.setRevoked(true);
                token.setRevokedAt(now);
                refreshTokenRepository.save(token);
                if (token.getSessionId() != null) {
                    userSessionRepository.findById(token.getSessionId())
                            .ifPresent(session -> sessionManagementService.terminate(session, "USER_LOGOUT", now));
                }
            });
        } else {
            // Sign out of all user sessions (Global logout)
            refreshTokenRepository.revokeAllUserTokens(userId, now);
            userSessionRepository.terminateAllUserSessions(userId, now, "GLOBAL_LOGOUT");
            tokenBlacklistService.blacklistUser(userId, Duration.ofMillis(jwtConfig.getAccessTokenExpiration()));
        }
        signInJournal.loggedOut(userId, labelOf(userId, authUser.username()), refreshTokenStr == null);
    }

    @Transactional
    public LoginResponse processOAuth2Login(OAuth2User oAuth2User, String provider) {
        String email = oAuth2User.getAttribute("email");
        if (email == null) {
            throw new BadCredentialsException("Email not found from OAuth2 provider");
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            // Auto-provisioning of a first-time social login
            log.info("Creating new user from OAuth2 login via {}", provider);
            User newUser = User.builder().email(email)
                    .username(email)
                    .emailVerified(true)
                    .enabled(true).accountNonLocked(true)
                    .accountNonExpired(true).credentialsNonExpired(true)
                    .build();
            return userRepository.save(newUser);
        });

        accountStandings.find(user.getId()).ifPresent(standing -> rejectUnlessActive(standing, labelOf(user.getId(), user.getUsername())));
        if (user.isCurrentlyLocked()) {
            throw new LockedException("Account is locked.");
        }

        user.updateLastLogin();
        userRepository.save(user);

        SecurityPolicy policy = securityPolicyProvider.current();
        LocalDateTime now = TenantTime.nowLocal(clock);
        String deviceFingerprint = handleDeviceFingerprinting(user);
        // DPoP binding is not offered on the OAuth2 flow: the provider-redirect callback cannot carry
        // a custom header, so OAuth2-originated logins always issue unbound tokens.
        UserSession session = createUserSession(user, deviceFingerprint, null, policy, now);
        sessionManagementService.enforceConcurrencyLimit(user.getId(), policy.maxConcurrentSessions(), now);

        RoleAndPermissionNames grants = resolveRoleAndPermissionNames(user.getId());
        String accessToken = jwtService.generateAccessToken(user, session.getId(), grants.roleNames(), grants.permissionNames(), null, grants.accessVersion());
        String opaqueRefreshToken = UUID.randomUUID().toString();

        saveRefreshToken(user, session, opaqueRefreshToken, now);
        signInJournal.succeeded(user.getId(), labelOf(user.getId(), user.getUsername()), false);

        return LoginResponse.builder().accessToken(accessToken)
                .refreshToken(opaqueRefreshToken).tokenType("Bearer")
                .expiresIn(accessTokenSeconds()).username(user.getUsername())
                .email(user.getEmail()).roles(grants.roleNames())
                .policies(grants.policies()).navigation(buildNavigation(grants.permissionNames()))
                .build();
    }

    // STATUS CHECKS

    /** Only ACTIVE accounts within their access window may sign in (spec §3.3). */
    private void rejectUnlessActive(AccountStanding standing, String label) {
        UUID userId = standing.userId();
        switch (standing.status()) {
            case ACTIVE -> {
                if (standing.accessExpired(TenantTime.today(clock))) {
                    signInJournal.failed(userId, label, SignInJournal.Outcome.FAILURE, SignInJournal.Failure.ACCESS_EXPIRED);
                    throw new AccountExpiredException("Account access expired");
                }
            }
            case LOCKED -> {
                signInJournal.failed(userId, label, SignInJournal.Outcome.LOCKED, SignInJournal.Failure.ACCOUNT_LOCKED);
                throw new LockedException(String.format("Account is locked. Try again in %d minutes",
                        lockoutService.getRemainingLockoutTime(standing.statusUntil())));
            }
            case PENDING_ACTIVATION -> {
                // Temporary-password accounts sign in once to set their own password.
                if (!standing.passwordNeedsChange() || standing.accessExpired(TenantTime.today(clock))) {
                    signInJournal.failed(userId, label, SignInJournal.Outcome.FAILURE, SignInJournal.Failure.ACCOUNT_DISABLED);
                    throw new DisabledException("Account is not active");
                }
            }
            case SUSPENDED, DEACTIVATED -> {
                signInJournal.failed(userId, label, SignInJournal.Outcome.FAILURE, SignInJournal.Failure.ACCOUNT_DISABLED);
                throw new DisabledException("Account is not active");
            }
        }
    }

    private String labelOf(UUID userId, String username) {
        return userDirectory.ref(userId).map(UserRef::displayName).orElse(username);
    }

    private int accessTokenSeconds() {
        return (int) Duration.ofMillis(jwtConfig.getAccessTokenExpiration()).toSeconds();
    }

    // ROLE/PERMISSION/MENU RESOLUTION

    private record RoleAndPermissionNames(List<String> roleNames, List<String> permissionNames, List<String> policies,
                                          long accessVersion) {
    }

    /** Role codes, effective permission codes and access version from the IAM resolver (spec §1.4.3). */
    private RoleAndPermissionNames resolveRoleAndPermissionNames(UUID userId) {
        UserAccessState access = userAccessService.current(userId);
        List<String> permissionNames = List.copyOf(access.permissionClaim());
        List<String> policies = access.access().granted().stream()
                .map(code -> code.split("\\."))
                .map(parts -> "urn:cronos:" + parts[1].toLowerCase(Locale.ROOT) + ":" + parts[2].toLowerCase(Locale.ROOT))
                .distinct().toList();
        return new RoleAndPermissionNames(access.roleCodes(), permissionNames, policies, access.accessVersion());
    }

    private List<MenuItemResponse> buildNavigation(List<String> grantedPermissionNames) {
        List<MenuNode> tree = menuPort.buildTree(Set.copyOf(grantedPermissionNames));
        return tree.stream().map(this::toMenuItemResponse).toList();
    }

    private MenuItemResponse toMenuItemResponse(MenuNode node) {
        return MenuItemResponse.builder()
                .code(node.getCode())
                .label(node.label(Locale.of("es")))
                .icon(node.getIcon())
                .path(node.getPath())
                .children(node.getChildren().stream().map(this::toMenuItemResponse).toList())
                .build();
    }

    // PRIVATE INFRASTRUCTURE METHODS (SESSIONS, FINGERPRINT)

    /** Absolute lifetime from the policy (spec §8). */
    private UserSession createUserSession(User user, String deviceId, String dpopJkt, SecurityPolicy policy, LocalDateTime now) {
        UserSession session = UserSession.builder().userId(user.getId())
                .sessionToken(UUID.randomUUID().toString())
                .deviceId(deviceId).ipAddress(requestContextUtil.getClientIp())
                .userAgent(requestContextUtil.getUserAgent())
                .browser(requestContextUtil.getBrowser())
                .operatingSystem(requestContextUtil.getOperatingSystem())
                .device(requestContextUtil.getDevice())
                .location(requestContextUtil.getLocation()).isActive(true)
                .createdAt(now).lastActivityAt(now)
                .expiresAt(now.plusHours(policy.sessionAbsoluteHours())).dpopJkt(dpopJkt).build();

        return userSessionRepository.save(session);
    }

    /** Never outlives its session. */
    private void saveRefreshToken(User user, UserSession session, String tokenStr, LocalDateTime now) {
        LocalDateTime tokenExpiry = now.plus(Duration.ofMillis(jwtConfig.getRefreshTokenExpiration()));
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr).userId(user.getId()).sessionId(session.getId())
                .expiresAt(session.getExpiresAt() != null && session.getExpiresAt().isBefore(tokenExpiry) ? session.getExpiresAt() : tokenExpiry)
                .revoked(false).ipAddress(requestContextUtil.getClientIp())
                .userAgent(requestContextUtil.getUserAgent()).createdAt(now).build();

        refreshTokenRepository.save(refreshToken);
    }

    private String handleDeviceFingerprinting(User user) {
        String browser = requestContextUtil.getBrowser();
        String os = requestContextUtil.getOperatingSystem();
        String device = requestContextUtil.getDevice();

        // The frontend may later send a FingerprintJS hash instead.
        String rawFingerprint = browser + "|" + os + "|" + device;
        String fingerprintHash = generateSha256(rawFingerprint);

        Optional<DeviceFingerprint> existingDevice = deviceFingerprintRepository.findByUserIdAndFingerprintHash(user.getId(), fingerprintHash);

        if (existingDevice.isEmpty()) {
            DeviceFingerprint newDevice = DeviceFingerprint.builder().userId(user.getId()).fingerprintHash(fingerprintHash)
                    .deviceName(os + " " + browser).userAgent(requestContextUtil.getUserAgent())
                    .browser(browser).operatingSystem(os).deviceType(device)
                    .ipAddress(requestContextUtil.getClientIp())
                    .location(requestContextUtil.getLocation())
                    .trusted(false).loginCount(1).firstSeenAt(LocalDateTime.now()).build();

            deviceFingerprintRepository.save(newDevice);
            createSecurityNotification(user, newDevice);
        } else {
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

        log.info("Publishing the NewDeviceLoginEvent for user {}", user.getId());
        eventPublisher.publishEvent(NewDeviceLoginEvent.builder().email(user.getEmail()).username(user.getUsername()).deviceName(device.getDeviceName())
                .location(device.getLocation()).ipAddress(device.getIpAddress())
                .time(LocalDateTime.now()).build());
    }

    private boolean isDecryptionCorrupted(User user) {
        return FieldEncryptionService.DECRYPTION_FAILED_SENTINEL.equals(user.getEmail());
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
