package com.ninsky.cronos.application.service.auth;

import com.ninsky.cronos.application.request.core.auth.LoginRequest;
import com.ninsky.cronos.application.request.core.auth.RefreshTokenRequest;
import com.ninsky.cronos.application.request.core.auth.TwoFactorLoginRequest;
import com.ninsky.cronos.application.request.core.auth.UnlockRequest;
import com.ninsky.cronos.application.response.auth.LoginResponse;
import com.ninsky.cronos.application.response.auth.TokenResponse;
import com.ninsky.cronos.application.response.menu.MenuItemResponse;
import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.domain.model.auth.RefreshToken;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserSession;
import com.ninsky.cronos.domain.model.menu.MenuNode;
import com.ninsky.cronos.domain.port.auth.RefreshTokenRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserAuthLookupPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserSessionRepositoryPort;
import com.ninsky.cronos.domain.port.menu.MenuPort;
import com.ninsky.cronos.eventing.application.DomainEventPublisher;
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
import com.ninsky.cronos.iam.token.TokenCodec;
import com.ninsky.cronos.iam.twofactor.SecondFactor;
import com.ninsky.cronos.iam.twofactor.TwoFactorAccountService;
import com.ninsky.cronos.iam.user.UserStatus;
import com.ninsky.cronos.identity.domain.IdentityEvents;
import com.ninsky.cronos.identity.domain.LoginIdentifier;
import com.ninsky.cronos.identity.domain.RegistrationStatus;
import com.ninsky.cronos.identity.domain.SignInOutcome;
import com.ninsky.cronos.identity.domain.SignInPolicy;
import com.ninsky.cronos.identity.infrastructure.AuthProperties;
import com.ninsky.cronos.identity.infrastructure.AuthRateLimiter;
import com.ninsky.cronos.identity.infrastructure.DeviceRecognition;
import com.ninsky.cronos.identity.infrastructure.RegistrationCustomRepository;
import com.ninsky.cronos.identity.infrastructure.SessionLockCustomRepository;
import com.ninsky.cronos.identity.infrastructure.SignInPolicyProvider;
import com.ninsky.cronos.identity.infrastructure.TwoFactorChallengeCustomRepository;
import com.ninsky.cronos.infrastructure.config.security.JwtConfig;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.exception.UserNotFoundException;
import com.ninsky.cronos.infrastructure.security.JwtService;
import com.ninsky.cronos.infrastructure.security.blacklist.TokenBlacklistService;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.infrastructure.security.dpop.DpopProofValidator;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import com.ninsky.cronos.infrastructure.web.ServerTiming;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sign-in, second factor, refresh, sign-out and screen unlock (§2.1–§2.3, §2.7).
 * <p>
 * Order of checks after the account is found (first match wins, every branch is journaled): rate limit → lock (before
 * the password, so a locked account never confirms a correct one) → password → pending approval → unverified email
 * (self-registered accounts) → rejected → disabled → second factor → tokens. Unknown identifiers still run one
 * password hash (timing equalisation) and never learn whether the account exists. Side effects (lock email, new-device
 * alert, security notifications) are domain events, not work done here.
 */
@Slf4j
@Service
public class AuthenticationService {

    static final int MAX_TWO_FACTOR_ATTEMPTS = 5;
    static final Duration NEW_DEVICE_MEMORY = Duration.ofDays(90);

    private final UserRepositoryPort userRepository;
    private final UserAuthLookupPort userAuthLookupPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final AccountLockoutService lockoutService;
    private final TwoFactorAccountService twoFactorAccounts;
    private final TwoFactorRequirement twoFactorRequirement;
    private final MenuPort menuPort;
    private final UserAccessService userAccessService;
    private final RefreshTokenRepositoryPort refreshTokenRepository;
    private final UserSessionRepositoryPort userSessionRepository;
    private final RequestContextUtil requestContextUtil;
    private final SessionManagementService sessionManagementService;
    private final TokenBlacklistService tokenBlacklistService;
    private final DpopProofValidator dpopProofValidator;
    private final SecurityPolicyProvider securityPolicyProvider;
    private final SignInPolicyProvider signInPolicies;
    private final AccountStandingCustomRepository accountStandings;
    private final RegistrationCustomRepository registrations;
    private final TwoFactorChallengeCustomRepository challenges;
    private final SessionLockCustomRepository sessionLocks;
    private final DeviceRecognition devices;
    private final AuthRateLimiter rateLimiter;
    private final AuthProperties authProperties;
    private final SignInJournal signInJournal;
    private final UserDirectory userDirectory;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final String dummyHash;

    public AuthenticationService(UserRepositoryPort userRepository, UserAuthLookupPort userAuthLookupPort, PasswordEncoder passwordEncoder,
                                 JwtService jwtService, JwtConfig jwtConfig, AccountLockoutService lockoutService,
                                 TwoFactorAccountService twoFactorAccounts, TwoFactorRequirement twoFactorRequirement, MenuPort menuPort,
                                 UserAccessService userAccessService, RefreshTokenRepositoryPort refreshTokenRepository,
                                 UserSessionRepositoryPort userSessionRepository, RequestContextUtil requestContextUtil,
                                 SessionManagementService sessionManagementService, TokenBlacklistService tokenBlacklistService,
                                 DpopProofValidator dpopProofValidator, SecurityPolicyProvider securityPolicyProvider,
                                 SignInPolicyProvider signInPolicies, AccountStandingCustomRepository accountStandings,
                                 RegistrationCustomRepository registrations, TwoFactorChallengeCustomRepository challenges,
                                 SessionLockCustomRepository sessionLocks, DeviceRecognition devices, AuthRateLimiter rateLimiter,
                                 AuthProperties authProperties, SignInJournal signInJournal, UserDirectory userDirectory,
                                 DomainEventPublisher events, Clock clock) {
        this.userRepository = userRepository;
        this.userAuthLookupPort = userAuthLookupPort;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtConfig = jwtConfig;
        this.lockoutService = lockoutService;
        this.twoFactorAccounts = twoFactorAccounts;
        this.twoFactorRequirement = twoFactorRequirement;
        this.menuPort = menuPort;
        this.userAccessService = userAccessService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userSessionRepository = userSessionRepository;
        this.requestContextUtil = requestContextUtil;
        this.sessionManagementService = sessionManagementService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.dpopProofValidator = dpopProofValidator;
        this.securityPolicyProvider = securityPolicyProvider;
        this.signInPolicies = signInPolicies;
        this.accountStandings = accountStandings;
        this.registrations = registrations;
        this.challenges = challenges;
        this.sessionLocks = sessionLocks;
        this.devices = devices;
        this.rateLimiter = rateLimiter;
        this.authProperties = authProperties;
        this.signInJournal = signInJournal;
        this.userDirectory = userDirectory;
        this.events = events;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("cronos-timing-equalisation");
    }

    /**
     * Password step of the sign-in.
     *
     * @throws ApiException RATE_LIMITED, INVALID_CREDENTIALS (with {@code remainingAttempts} for lock-eligible accounts),
     *                      ACCOUNT_LOCKED, ACCOUNT_PENDING_APPROVAL, EMAIL_NOT_VERIFIED, ACCOUNT_REJECTED, ACCOUNT_DISABLED,
     *                      INVALID_TWO_FACTOR_CODE (legacy single-request form)
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest request) {
        LoginIdentifier identifier = LoginIdentifier.resolve(request.identifier(), request.username())
                .orElseThrow(() -> ApiException.invalid("identifier", "auth.identifier.required"));
        String ip = requestContextUtil.getClientIp();
        try {
            rateLimiter.acquire(AuthRateLimiter.Limit.SIGN_IN_IP, ip);
            rateLimiter.acquire(AuthRateLimiter.Limit.SIGN_IN_IDENTIFIER, identifier.value());
        } catch (ApiException limited) {
            signInJournal.attempt(null, null, identifier.value(), SignInOutcome.RATE_LIMITED, null, false);
            throw limited;
        }

        // Lean lookup (UserAuthLookupPort): never decrypts email/2FA secret until the password is confirmed.
        AuthUserProjection authUser = ServerTiming.measure("lookup",
                () -> userAuthLookupPort.findByUsernameOrEmail(identifier.value()).orElse(null));
        if (authUser == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            signInJournal.attempt(null, null, identifier.value(), SignInOutcome.INVALID_CREDENTIALS, "UNKNOWN_ACCOUNT", false);
            throw invalidCredentials(null);
        }
        String label = labelOf(authUser.id(), authUser.username());
        AccountStanding standing = lockoutService.releaseExpiredLock(accountStandings.find(authUser.id())
                .orElseThrow(() -> invalidCredentials(null)), label);
        if (standing.status() == UserStatus.LOCKED) {
            signInJournal.attempt(authUser.id(), label, identifier.value(), SignInOutcome.LOCKED, null, false);
            throw accountLocked(standing.statusUntil());
        }

        boolean correct = ServerTiming.measure("password",
                () -> authUser.password() != null && passwordEncoder.matches(request.password(), authUser.password()));
        if (!correct) {
            throw wrongSecret(authUser.id(), standing.status(), label, identifier.value(), SignInOutcome.INVALID_CREDENTIALS);
        }
        requireMaySignIn(standing, authUser.id(), label, identifier.value());

        // Password confirmed: only now load the full aggregate (decrypts email/2FA secret).
        User user = ServerTiming.measure("user", () -> userRepository.findById(authUser.id()))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + authUser.id()));
        if (FieldEncryptionService.DECRYPTION_FAILED_SENTINEL.equals(user.getEmail())) {
            throw wrongSecret(user.getId(), standing.status(), label, identifier.value(), SignInOutcome.INVALID_CREDENTIALS);
        }

        boolean twoFactorUsed = false;
        if (user.isTwoFactorEnabled()) {
            if (request.twoFactorCode() == null || request.twoFactorCode().isBlank()) {
                UUID challengeId = challenges.create(user.getId(), request.remember(), ip, TenantTime.now(clock),
                        authProperties.twoFactorChallengeTtl());
                signInJournal.attempt(user.getId(), label, identifier.value(), SignInOutcome.TWO_FACTOR_REQUIRED, null, false);
                return LoginResponse.builder().requiresTwoFactor(true).challengeId(challengeId)
                        .message("Two-factor authentication code required").build();
            }
            if (!twoFactorAccounts.verifySignIn(user.getId(), request.twoFactorCode()).accepted()) {
                throw wrongSecret(user.getId(), standing.status(), label, identifier.value(), SignInOutcome.TWO_FACTOR_FAILED);
            }
            twoFactorUsed = true;
        }
        return issue(user, standing, label, identifier.value(), request.remember(), twoFactorUsed);
    }

    /**
     * Second step: a TOTP (±1 step) or a single-use recovery code against {@code challengeId}.
     *
     * @throws ApiException TWO_FACTOR_CHALLENGE_EXPIRED (unknown, expired, consumed or out of attempts),
     *                      INVALID_TWO_FACTOR_CODE with {@code remainingAttempts}, RATE_LIMITED, or the account checks of
     *                      {@link #login} when the account changed since the first step
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse loginWithTwoFactor(TwoFactorLoginRequest request) {
        rateLimiter.acquire(AuthRateLimiter.Limit.SIGN_IN_IP, requestContextUtil.getClientIp());
        Instant now = TenantTime.now(clock);
        TwoFactorChallengeCustomRepository.Challenge challenge = challenges.lock(request.challengeId())
                .filter(c -> c.open(now))
                .orElseThrow(() -> ApiException.of(ApiErrorCode.TWO_FACTOR_CHALLENGE_EXPIRED, "challengeId", "auth.twoFactor.challengeExpired"));
        UUID userId = challenge.userId();
        AuthUserProjection authUser = userAuthLookupPort.findById(userId).orElseThrow(() -> invalidCredentials(null));
        String label = labelOf(userId, authUser.username());
        AccountStanding standing = accountStandings.find(userId).orElseThrow(() -> invalidCredentials(null));
        if (standing.status() == UserStatus.LOCKED) {
            challenges.consume(challenge.id(), now);
            throw accountLocked(standing.statusUntil());
        }
        requireMaySignIn(standing, userId, label, authUser.username());

        SecondFactor.Result result = twoFactorAccounts.verifySignIn(userId, request.code().trim());
        if (!result.accepted()) {
            int used = challenges.fail(challenge.id(), MAX_TWO_FACTOR_ATTEMPTS, now);
            AccountLockoutService.FailureResult failure = lockoutService.registerFailure(userId, standing.status(), label);
            signInJournal.attempt(userId, label, authUser.username(), SignInOutcome.TWO_FACTOR_FAILED, null, true);
            if (failure.locked()) {
                throw accountLocked(failure.lockedUntil());
            }
            int remaining = Math.max(0, MAX_TWO_FACTOR_ATTEMPTS - used);
            if (remaining == 0) {
                throw ApiException.of(ApiErrorCode.TWO_FACTOR_CHALLENGE_EXPIRED, "challengeId", "auth.twoFactor.challengeExpired");
            }
            throw ApiException.withDetails(ApiErrorCode.INVALID_TWO_FACTOR_CODE, "code", Map.of("remainingAttempts", remaining),
                    "auth.twoFactor.invalidCode");
        }
        challenges.consume(challenge.id(), now);
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        boolean remember = request.rememberMe() != null ? request.rememberMe() : challenge.rememberMe();
        return issue(user, standing, label, authUser.username(), remember, true);
    }

    /**
     * Rotates a refresh token (reuse of a rotated token fails). Idle/absolute limits are checked here; an expired
     * session is terminated and that commits.
     *
     * @throws ApiException INVALID_TOKEN (401) for an unknown, rotated, expired or revoked token; SESSION_LOCKED while the
     *                      screen of the session is locked
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        LocalDateTime now = TenantTime.nowLocal(clock);
        RefreshToken refreshToken = findRefreshToken(request.refreshToken())
                .filter(token -> !token.isRevoked() && token.getExpiresAt().isAfter(now))
                .orElseThrow(AuthenticationService::invalidSessionToken);
        UserSession session = activeSession(refreshToken).orElseThrow(AuthenticationService::invalidSessionToken);
        Optional<String> expiry = sessionManagementService.expiry(session, securityPolicyProvider.current(), now);
        if (expiry.isPresent() && !sessionLocks.isRememberMe(session.getId())) {
            sessionManagementService.terminate(session, expiry.get(), now);
            events.publish(new IdentityEvents.SessionRevoked(session.getUserId(), session.getId(), expiry.get()));
            throw invalidSessionToken();
        }
        if (sessionLocks.isLocked(session.getId())) {
            throw ApiException.of(ApiErrorCode.SESSION_LOCKED, null, "auth.session.locked");
        }
        // A DPoP-bound session can only be refreshed by the same key it was bound to at login.
        if (session.getDpopJkt() != null) {
            String jkt = dpopProofValidator.validate(requestContextUtil.getHeader("DPoP"), "POST", requestContextUtil.getRequestUrl());
            if (!session.getDpopJkt().equals(jkt)) {
                throw invalidSessionToken();
            }
        }
        User user = userRepository.findById(refreshToken.getUserId()).orElseThrow(AuthenticationService::invalidSessionToken);
        return rotate(user, session, refreshToken, now);
    }

    /**
     * Ends the session of {@code refreshToken}; works without an access token (a locked screen signs out). Unknown or
     * already revoked tokens are ignored (idempotent).
     */
    @Transactional
    public void logout(String refreshTokenRaw) {
        if (refreshTokenRaw == null || refreshTokenRaw.isBlank()) {
            return;
        }
        LocalDateTime now = TenantTime.nowLocal(clock);
        findRefreshToken(refreshTokenRaw).filter(token -> !token.isRevoked()).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
            if (token.getSessionId() != null) {
                userSessionRepository.findById(token.getSessionId())
                        .ifPresent(session -> sessionManagementService.terminate(session, "USER_LOGOUT", now));
            }
            events.publish(new IdentityEvents.SessionRevoked(token.getUserId(), token.getSessionId(), "USER_LOGOUT"));
            signInJournal.loggedOut(token.getUserId(), labelOf(token.getUserId(), null), false);
        });
    }

    /** Signs {@code userId} out of every session (authenticated logout without a refresh token). */
    @Transactional
    public void logoutEverywhere(UUID userId) {
        LocalDateTime now = TenantTime.nowLocal(clock);
        refreshTokenRepository.revokeAllUserTokens(userId, now);
        userSessionRepository.terminateAllUserSessions(userId, now, "GLOBAL_LOGOUT");
        tokenBlacklistService.blacklistUser(userId, Duration.ofMillis(jwtConfig.getAccessTokenExpiration()));
        events.publish(new IdentityEvents.SessionRevoked(userId, null, "GLOBAL_LOGOUT"));
        signInJournal.loggedOut(userId, labelOf(userId, null), true);
    }

    /** Locks the screen of the caller's session (idempotent). */
    @Transactional
    public void lock(UUID userId, UUID sessionId) {
        if (sessionId == null) {
            throw ApiException.invalid(null, "auth.session.unknown");
        }
        if (sessionLocks.lock(sessionId, TenantTime.now(clock))) {
            events.publish(new IdentityEvents.SessionLocked(sessionId, userId));
        }
    }

    /**
     * Unlocks a locked (or active) session with the password of its user and rotates the refresh token. Failures count
     * toward the account lockout.
     *
     * @throws ApiException INVALID_TOKEN (401) for a revoked/expired/unknown session; INVALID_CREDENTIALS with
     *                      {@code remainingAttempts}; ACCOUNT_LOCKED when this failure locked the account
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse unlock(UnlockRequest request) {
        rateLimiter.acquire(AuthRateLimiter.Limit.SIGN_IN_IP, requestContextUtil.getClientIp());
        LocalDateTime now = TenantTime.nowLocal(clock);
        RefreshToken refreshToken = findRefreshToken(request.refreshToken())
                .filter(token -> !token.isRevoked() && token.getExpiresAt().isAfter(now))
                .orElseThrow(AuthenticationService::invalidSessionToken);
        UserSession session = activeSession(refreshToken).orElseThrow(AuthenticationService::invalidSessionToken);
        UUID userId = session.getUserId();
        AuthUserProjection authUser = userAuthLookupPort.findById(userId).orElseThrow(AuthenticationService::invalidSessionToken);
        String label = labelOf(userId, authUser.username());
        AccountStanding standing = accountStandings.find(userId).orElseThrow(AuthenticationService::invalidSessionToken);
        if (standing.status() == UserStatus.LOCKED) {
            throw accountLocked(standing.statusUntil());
        }
        if (authUser.password() == null || !passwordEncoder.matches(request.password(), authUser.password())) {
            throw wrongSecret(userId, standing.status(), label, authUser.username(), SignInOutcome.INVALID_CREDENTIALS);
        }
        lockoutService.registerSuccess(userId);
        sessionLocks.unlock(session.getId());
        events.publish(new IdentityEvents.SessionUnlocked(session.getId(), userId));
        User user = userRepository.findById(userId).orElseThrow(AuthenticationService::invalidSessionToken);
        return rotate(user, session, refreshToken, now);
    }

    // ─── account checks ────────────────────────────────────────────────────────────────

    /** Pending approval → unverified email (self-registered) → rejected → disabled/expired. */
    private void requireMaySignIn(AccountStanding standing, UUID userId, String label, String identifier) {
        Optional<RegistrationCustomRepository.RegistrationRow> registration = registrations.findByUser(userId);
        switch (standing.status()) {
            case PENDING_APPROVAL -> reject(userId, label, identifier, SignInOutcome.PENDING_APPROVAL,
                    ApiErrorCode.ACCOUNT_PENDING_APPROVAL, "auth.account.pendingApproval");
            case DEACTIVATED -> {
                if (registration.map(r -> r.status() == RegistrationStatus.REJECTED).orElse(false)) {
                    reject(userId, label, identifier, SignInOutcome.REJECTED, ApiErrorCode.ACCOUNT_REJECTED, "auth.account.rejected");
                }
                reject(userId, label, identifier, SignInOutcome.DISABLED, ApiErrorCode.ACCOUNT_DISABLED, "auth.account.disabled");
            }
            case SUSPENDED -> reject(userId, label, identifier, SignInOutcome.DISABLED, ApiErrorCode.ACCOUNT_DISABLED,
                    "auth.account.disabled");
            case PENDING_ACTIVATION -> {
                // Temporary-password accounts sign in once to set their own password.
                if (!standing.passwordNeedsChange() || standing.accessExpired(TenantTime.today(clock))) {
                    reject(userId, label, identifier, SignInOutcome.DISABLED, ApiErrorCode.ACCOUNT_DISABLED, "auth.account.disabled");
                }
            }
            case ACTIVE, LOCKED -> {
                if (standing.accessExpired(TenantTime.today(clock))) {
                    reject(userId, label, identifier, SignInOutcome.DISABLED, ApiErrorCode.ACCOUNT_DISABLED, "auth.account.accessExpired");
                }
            }
        }
        if (registration.isPresent() && signInPolicies.current().requireEmailVerification() && !emailVerified(userId)) {
            reject(userId, label, identifier, SignInOutcome.EMAIL_NOT_VERIFIED, ApiErrorCode.EMAIL_NOT_VERIFIED, "auth.account.emailNotVerified");
        }
    }

    private boolean emailVerified(UUID userId) {
        return userRepository.findById(userId).map(User::isEmailVerified).orElse(false);
    }

    private void reject(UUID userId, String label, String identifier, SignInOutcome outcome, ApiErrorCode code, String key) {
        signInJournal.attempt(userId, label, identifier, outcome, null, false);
        throw ApiException.of(code, null, key);
    }

    /** Counts a wrong password/code and answers INVALID_CREDENTIALS (or ACCOUNT_LOCKED when this failure locked it). */
    private ApiException wrongSecret(UUID userId, UserStatus status, String label, String identifier, SignInOutcome outcome) {
        AccountLockoutService.FailureResult failure = lockoutService.registerFailure(userId, status, label);
        signInJournal.attempt(userId, label, identifier, failure.locked() ? SignInOutcome.LOCKED : outcome, null,
                outcome == SignInOutcome.TWO_FACTOR_FAILED);
        if (failure.locked()) {
            return accountLocked(failure.lockedUntil());
        }
        if (outcome == SignInOutcome.TWO_FACTOR_FAILED) {
            return ApiException.withDetails(ApiErrorCode.INVALID_TWO_FACTOR_CODE, "twoFactorCode",
                    Map.of("remainingAttempts", failure.remainingAttempts()), "auth.twoFactor.invalidCode");
        }
        return invalidCredentials(status == UserStatus.ACTIVE ? failure.remainingAttempts() : null);
    }

    private ApiException accountLocked(Instant until) {
        long retryAfter = lockoutService.secondsUntil(until);
        return new ApiException(List.of(new ApiException.Violation(ApiErrorCode.ACCOUNT_LOCKED, null, "auth.account.locked",
                List.of(), until == null ? Map.of("retryAfterSeconds", retryAfter)
                : Map.of("lockedUntil", until.toString(), "retryAfterSeconds", retryAfter))), retryAfter > 0 ? retryAfter : null);
    }

    private static ApiException invalidCredentials(Integer remainingAttempts) {
        return remainingAttempts == null
                ? ApiException.of(ApiErrorCode.INVALID_CREDENTIALS, null, "auth.credentials.invalid")
                : ApiException.withDetails(ApiErrorCode.INVALID_CREDENTIALS, null, Map.of("remainingAttempts", remainingAttempts),
                "auth.credentials.invalid");
    }

    private static ApiException invalidSessionToken() {
        return ApiException.of(ApiErrorCode.INVALID_TOKEN, "refreshToken", "auth.session.invalid").withStatus(HttpStatus.UNAUTHORIZED);
    }

    // ─── tokens and sessions ───────────────────────────────────────────────────────────

    /** Completes a sign-in: session, tokens, journal, device recognition and the signed-in event. */
    private LoginResponse issue(User user, AccountStanding standing, String label, String identifier, boolean rememberMe,
                                boolean twoFactorUsed) {
        lockoutService.registerSuccess(user.getId());
        LocalDateTime now = TenantTime.nowLocal(clock);
        SecurityPolicy policy = securityPolicyProvider.current();
        SignInPolicy signIn = signInPolicies.current();

        long sessionStart = System.nanoTime();
        String fingerprint = devices.recognise(user.getId(), TenantTime.now(clock), NEW_DEVICE_MEMORY);
        // DPoP binding is opt-in: a client that wants a bound token sends a DPoP proof on the sign-in request itself.
        String dpopProof = requestContextUtil.getHeader("DPoP");
        String dpopJkt = dpopProof != null ? dpopProofValidator.validate(dpopProof, "POST", requestContextUtil.getRequestUrl()) : null;
        LocalDateTime sessionEnd = rememberMe ? now.plus(signIn.rememberMe()) : now.plusHours(policy.sessionAbsoluteHours());
        UserSession session = createUserSession(user, fingerprint, dpopJkt, now, sessionEnd);
        sessionLocks.rememberMe(session.getId(), rememberMe);
        sessionManagementService.enforceConcurrencyLimit(user.getId(), policy.maxConcurrentSessions(), now);
        ServerTiming.record("session", System.nanoTime() - sessionStart);

        RoleAndPermissionNames grants = ServerTiming.measure("access", () -> resolveRoleAndPermissionNames(user.getId()));
        String accessToken = jwtService.generateAccessToken(user, session.getId(), grants.roleNames(), grants.permissionNames(), dpopJkt,
                grants.accessVersion());
        String refreshToken = saveRefreshToken(user.getId(), session, now, rememberMe ? signIn.rememberMe() : null);
        ServerTiming.measure("journal", () -> signInJournal.attempt(user.getId(), label, identifier, SignInOutcome.SUCCESS, null,
                twoFactorUsed));
        events.publish(new IdentityEvents.UserSignedIn(user.getId(), session.getId(), rememberMe, twoFactorUsed,
                requestContextUtil.getClientIp()));

        boolean mustChange = standing.mustChangePassword(policy, TenantTime.now(clock));
        return LoginResponse.builder().accessToken(accessToken).refreshToken(refreshToken)
                .tokenType("Bearer").expiresIn(accessTokenSeconds())
                .username(user.getUsername()).email(user.getEmail())
                .roles(grants.roleNames())
                .policies(grants.policies())
                .navigation(ServerTiming.measure("navigation", () -> buildNavigation(grants.permissionNames())))
                .mustChangePassword(mustChange)
                .passwordChangeRequired(mustChange)
                .screenLockMinutes(signIn.screenLockMinutes())
                .requiresTwoFactorEnrollment(twoFactorRequirement.mustEnrol(user.getId()))
                .requiresTwoFactor(false).message("Login successful").build();
    }

    /** Refresh-token rotation (OAuth 2.0 security BCP): the old token dies, a new one is minted. */
    private TokenResponse rotate(User user, UserSession session, RefreshToken current, LocalDateTime now) {
        session.setLastActivityAt(now);
        userSessionRepository.save(session);
        RoleAndPermissionNames grants = resolveRoleAndPermissionNames(user.getId());
        String accessToken = jwtService.generateAccessToken(user, session.getId(), grants.roleNames(), grants.permissionNames(),
                session.getDpopJkt(), grants.accessVersion());
        current.setRevoked(true);
        current.setRevokedAt(now);
        refreshTokenRepository.save(current);
        Duration rememberFor = sessionLocks.isRememberMe(session.getId()) ? signInPolicies.current().rememberMe() : null;
        String refreshToken = saveRefreshToken(user.getId(), session, now, rememberFor);
        return TokenResponse.builder().accessToken(accessToken).refreshToken(refreshToken).tokenType("Bearer")
                .expiresIn(accessTokenSeconds()).build();
    }

    private Optional<UserSession> activeSession(RefreshToken token) {
        return Optional.ofNullable(token.getSessionId()).flatMap(userSessionRepository::findById).filter(UserSession::isActive);
    }

    /** Tokens are stored as SHA-256 digests; tokens issued before V26 were stored raw and still resolve. */
    private Optional<RefreshToken> findRefreshToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByToken(TokenCodec.hash(raw)).or(() -> refreshTokenRepository.findByToken(raw));
    }

    private UserSession createUserSession(User user, String deviceId, String dpopJkt, LocalDateTime now, LocalDateTime expiresAt) {
        UserSession session = UserSession.builder().userId(user.getId())
                .sessionToken(UUID.randomUUID().toString())
                .deviceId(deviceId).ipAddress(requestContextUtil.getClientIp())
                .userAgent(requestContextUtil.getUserAgent())
                .browser(requestContextUtil.getBrowser())
                .operatingSystem(requestContextUtil.getOperatingSystem())
                .device(requestContextUtil.getDevice())
                .location(requestContextUtil.getLocation()).isActive(true)
                .createdAt(now).lastActivityAt(now)
                .expiresAt(expiresAt).dpopJkt(dpopJkt).build();
        return userSessionRepository.save(session);
    }

    /** Stores the digest of a new opaque token and returns the raw value (never outlives its session). */
    private String saveRefreshToken(UUID userId, UserSession session, LocalDateTime now, Duration rememberFor) {
        String raw = TokenCodec.generate();
        LocalDateTime tokenExpiry = now.plus(rememberFor != null ? rememberFor : Duration.ofMillis(jwtConfig.getRefreshTokenExpiration()));
        RefreshToken refreshToken = RefreshToken.builder()
                .token(TokenCodec.hash(raw)).userId(userId).sessionId(session.getId())
                .expiresAt(session.getExpiresAt() != null && session.getExpiresAt().isBefore(tokenExpiry) ? session.getExpiresAt() : tokenExpiry)
                .revoked(false).ipAddress(requestContextUtil.getClientIp())
                .userAgent(requestContextUtil.getUserAgent()).createdAt(now).build();
        refreshTokenRepository.save(refreshToken);
        return raw;
    }

    private String labelOf(UUID userId, String username) {
        return userDirectory.ref(userId).map(UserRef::displayName).orElse(username);
    }

    private int accessTokenSeconds() {
        return (int) Duration.ofMillis(jwtConfig.getAccessTokenExpiration()).toSeconds();
    }

    // ─── role / permission / menu resolution ───────────────────────────────────────────

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
}
