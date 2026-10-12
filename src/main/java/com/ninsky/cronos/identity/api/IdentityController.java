package com.ninsky.cronos.identity.api;

import com.ninsky.cronos.application.request.core.auth.TwoFactorLoginRequest;
import com.ninsky.cronos.application.request.core.auth.UnlockRequest;
import com.ninsky.cronos.application.response.auth.LoginResponse;
import com.ninsky.cronos.application.response.auth.TokenResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.auth.AuthenticationService;
import com.ninsky.cronos.application.service.auth.PasswordResetService;
import com.ninsky.cronos.eventing.application.TraceContext;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.iam.shared.Actor;
import com.ninsky.cronos.iam.shared.ActorProvider;
import com.ninsky.cronos.identity.application.PublicPasswordPolicyService;
import com.ninsky.cronos.identity.application.RegistrationService;
import com.ninsky.cronos.identity.infrastructure.AuthProperties;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

/**
 * Authentication endpoints of auth-quotes-calendar-notifications §2 (sign-in step two, sign-out, registration,
 * verification, recovery, screen lock). Every one except {@code /auth/lock} is reachable without a Bearer token.
 */
@RestController
@StrictApiContract
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Sign-in, registration, recovery and screen lock")
public class IdentityController {

    private final AuthenticationService authentication;
    private final RegistrationService registrations;
    private final PasswordResetService recovery;
    private final PublicPasswordPolicyService passwordPolicy;
    private final AuthProperties properties;
    private final ActorProvider actors;

    public IdentityController(AuthenticationService authentication, RegistrationService registrations, PasswordResetService recovery,
                              PublicPasswordPolicyService passwordPolicy, AuthProperties properties, ActorProvider actors) {
        this.authentication = authentication;
        this.registrations = registrations;
        this.recovery = recovery;
        this.passwordPolicy = passwordPolicy;
        this.properties = properties;
        this.actors = actors;
    }

    @PostMapping("/login/two-factor")
    @Operation(summary = "Second sign-in step", description = "TOTP or recovery code against the challenge; 5 wrong codes kill it.")
    public ResponseEntity<ApiResponse<LoginResponse>> twoFactor(@Valid @RequestBody TwoFactorLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authentication.loginWithTwoFactor(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out", description = "Ends the session of the refresh token; works without an access token. "
            + "Without a body an authenticated caller is signed out everywhere.")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody(required = false) LogoutRequest request) {
        String refreshToken = request == null ? null : request.refreshToken();
        Optional<Actor> actor = actors.current();
        if ((refreshToken == null || refreshToken.isBlank()) && actor.isPresent()) {
            authentication.logoutEverywhere(actor.get().id());
        } else {
            authentication.logout(refreshToken);
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/password-policy")
    @Operation(summary = "Password rules", description = "Public projection of the security policy.")
    public ResponseEntity<ApiResponse<PublicPasswordPolicy>> passwordPolicy() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(ApiResponse.success(null, passwordPolicy.current()));
    }

    @GetMapping("/availability")
    @Operation(summary = "Username and email availability", description = "Rate-limited; email is only ever true or null.")
    public ResponseEntity<ApiResponse<RegistrationAvailability>> availability(@RequestParam(required = false) String username,
                                                                              @RequestParam(required = false) String email) {
        return ResponseEntity.ok(ApiResponse.success(null, registrations.availability(username, email)));
    }

    @PostMapping("/register")
    @Operation(summary = "Self-registration", description = "Creates a PENDING_APPROVAL account; an administrator approves it.")
    public ResponseEntity<ApiResponse<RegistrationReceipt>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null, registrations.register(request)));
    }

    @PostMapping("/email/verify")
    @Operation(summary = "Confirm the email address")
    public ResponseEntity<ApiResponse<VerifyEmailResult>> verify(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, registrations.verifyEmail(request.token())));
    }

    @PostMapping("/email/verify/resend")
    @Operation(summary = "Resend the verification link", description = "Always 200; sends only to an unverified account.")
    public ResponseEntity<ApiResponse<Void>> resend(@Valid @RequestBody ResendVerificationRequest request) {
        long start = System.nanoTime();
        registrations.resendVerification(request.email(), request.locale());
        AnonymousTiming.padSince(start, properties.anonymousMinLatency());
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Request a reset link", description = "Username or email; always 200 after a constant minimum latency.")
    public ResponseEntity<ApiResponse<Void>> forgot(@Valid @RequestBody PasswordForgotRequest request) {
        long start = System.nanoTime();
        recovery.processForgotPassword(request.identifier(), request.locale());
        AnonymousTiming.padSince(start, properties.anonymousMinLatency());
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @GetMapping("/password/reset/{token}")
    @Operation(summary = "Check a reset link", description = "valid=false (200) for an invalid, used or expired link.")
    public ResponseEntity<ApiResponse<PasswordResetTokenInfo>> resetLink(@PathVariable String token) {
        PasswordResetService.ResetLinkInfo info = recovery.linkInfo(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(null, new PasswordResetTokenInfo(info.valid(), info.maskedEmail(), info.expiresAt())));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Set a new password with a reset link", description = "Revokes every session of the account.")
    public ResponseEntity<ApiResponse<Void>> reset(@Valid @RequestBody PasswordResetRequest request) {
        recovery.resetPassword(request.token(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @PostMapping("/lock")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lock the screen", description = "Access tokens of this session answer 423 SESSION_LOCKED until unlocked.")
    public ResponseEntity<ApiResponse<Void>> lock() {
        Actor actor = actors.require();
        authentication.lock(actor.id(), TraceContexts.current().map(TraceContext::sessionId).orElse(null));
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @PostMapping("/unlock")
    @Operation(summary = "Unlock the screen", description = "Password of the session's user; failures count toward the lockout.")
    public ResponseEntity<ApiResponse<TokenResponse>> unlock(@Valid @RequestBody UnlockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, authentication.unlock(request)));
    }
}
