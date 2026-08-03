package com.ninsky.cronos.presentation.controller.core.auth;

import com.ninsky.cronos.application.request.core.auth.CreateUserRequest;
import com.ninsky.cronos.application.request.core.auth.LoginRequest;
import com.ninsky.cronos.application.request.core.auth.RefreshTokenRequest;
import com.ninsky.cronos.application.response.auth.LoginResponse;
import com.ninsky.cronos.application.response.auth.TokenResponse;
import com.ninsky.cronos.application.response.auth.UserResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.auth.AuthenticationService;
import com.ninsky.cronos.application.service.auth.UserService;
import com.ninsky.cronos.infrastructure.annotation.RateLimitEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserService userService;

    @PostMapping("/register")
    @RateLimitEndpoint(key = "register")
    @Operation(summary = "Register new user", description = "Creates a new user account")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    @RateLimitEndpoint(key = "login")
    @Operation(summary = "User login", description = "Authenticates user and returns JWT tokens")
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

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "User logout", description = "Revokes specific session or all user sessions")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication, @RequestBody(required = false) RefreshTokenRequest request) {

        // Extraemos el token opaco si el usuario quiere cerrar una sesión específica
        String refreshToken = (request != null && request.refreshToken() != null) ? request.refreshToken() : null;

        // authentication.getName() devuelve el username (o email, según tu UserDetails)
        authenticationService.logout(authentication.getName(), refreshToken);

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
}
