package com.ninsky.cronos.presentation.controller.core.auth;

import com.ninsky.cronos.account.shared.api.AccountMessages;
import com.ninsky.cronos.account.shared.application.port.CurrentUserProvider;
import com.ninsky.cronos.application.request.core.auth.ChangePasswordRequest;
import com.ninsky.cronos.application.request.core.auth.LoginRequest;
import com.ninsky.cronos.application.request.core.auth.RefreshTokenRequest;
import com.ninsky.cronos.application.response.auth.LoginResponse;
import com.ninsky.cronos.application.response.auth.TokenResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.auth.AuthenticationService;
import com.ninsky.cronos.application.service.auth.UserService;
import com.ninsky.cronos.infrastructure.annotation.RateLimitEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;
    private final AccountMessages messages;

    @PostMapping("/login")
    @RateLimitEndpoint(key = "login")
    @Operation(summary = "Sign in", description = "identifier = username or email (username is the deprecated alias). With 2FA "
            + "enabled the answer is requiresTwoFactor + challengeId (answer it at /auth/login/two-factor); errors carry "
            + "INVALID_CREDENTIALS (remainingAttempts), ACCOUNT_LOCKED (lockedUntil, retryAfterSeconds), RATE_LIMITED, "
            + "ACCOUNT_PENDING_APPROVAL, EMAIL_NOT_VERIFIED, ACCOUNT_REJECTED or ACCOUNT_DISABLED.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authenticationService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    @RateLimitEndpoint(key = "refresh")
    @Operation(summary = "Refresh access token", description = "Generates a new access token using an opaque refresh token")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authenticationService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change my password",
            description = "newPassword must differ from currentPassword and equal confirmPassword; errors are per field "
                    + "(currentPassword / newPassword / confirmPassword). Existing sessions stay valid.")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUserProvider.currentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(messages.get("account.password.changed"), null));
    }
}
