package com.ninsky.cronos.presentation.controller.admin;

import com.ninsky.cronos.application.request.core.auth.CreateUserRequest;
import com.ninsky.cronos.application.request.core.auth.UpdateUserRequest;
import com.ninsky.cronos.application.request.user.AdminUserCreateRequest;
import com.ninsky.cronos.application.response.auth.UserResponse;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'MANAGE_USERS')")
@Tag(name = "Admin User Management", description = "Enterprise endpoints for super admins to manage users")
public class AdminController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "Get all users (Paginated)", description = "Retrieves a paginated and filtered list of users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(@RequestParam(required = false) String role, @RequestParam(required = false) Boolean enabled, @RequestParam(required = false) String search, Pageable pageable) {
        Page<UserResponse> users = adminUserService.getAllUsers(role, enabled, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves full details of a specific user")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        UserResponse user = adminUserService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));
    }

    @PostMapping
    @Operation(summary = "Create new user", description = "Admin creation of a user (bypasses self-registration limits)")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = adminUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User created successfully", user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Updates user core data and profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        UserResponse user = adminUserService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    // ==========================================
    // OPERACIONES DE ESTADO Y ROLES
    // ==========================================

    @PostMapping("/{id}/block")
    @Operation(summary = "Block user (Soft Disable)", description = "Prevents the user from logging in")
    public ResponseEntity<ApiResponse<UserResponse>> blockUser(@PathVariable UUID id) {
        UserResponse updatedUser = adminUserService.updateUserStatus(id, false);
        return ResponseEntity.ok(ApiResponse.success("User blocked successfully", updatedUser));
    }

    @PostMapping("/{id}/unblock")
    @Operation(summary = "Unblock user", description = "Restores login access for a disabled user")
    public ResponseEntity<ApiResponse<UserResponse>> unblockUser(@PathVariable UUID id) {
        UserResponse updatedUser = adminUserService.updateUserStatus(id, true);
        return ResponseEntity.ok(ApiResponse.success("User unblocked successfully", updatedUser));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Assign Roles", description = "Overrides the current roles of a user")
    public ResponseEntity<ApiResponse<UserResponse>> assignRoles(@PathVariable UUID id, @RequestBody Set<String> roles) {
        UserResponse user = adminUserService.assignRoles(id, roles);
        return ResponseEntity.ok(ApiResponse.success("Roles assigned successfully", user));
    }

    // ==========================================
    // OPERACIONES ENTERPRISE DE SOPORTE TÉCNICO
    // ==========================================

    @PostMapping("/{id}/unlock-account")
    @Operation(summary = "Unlock account (Brute Force)", description = "Unlocks an account that was locked by the brute-force prevention system")
    public ResponseEntity<ApiResponse<Void>> unlockAccount(@PathVariable UUID id) {
        adminUserService.unlockAccount(id);
        return ResponseEntity.ok(ApiResponse.success("User account unlocked successfully", null));
    }

    @PostMapping("/{id}/force-logout")
    @Operation(summary = "Force global logout", description = "Terminates all active sessions and revokes all refresh tokens for the user")
    public ResponseEntity<ApiResponse<Void>> forceUserLogout(@PathVariable UUID id) {
        adminUserService.forceGlobalLogout(id);
        return ResponseEntity.ok(ApiResponse.success("All sessions terminated for user", null));
    }

    @PostMapping("/{id}/force-password-reset")
    @Operation(summary = "Force password reset", description = "Sends an email to the user with a secure link to reset their password")
    public ResponseEntity<ApiResponse<Void>> forcePasswordReset(@PathVariable UUID id) {
        adminUserService.initiatePasswordReset(id);
        return ResponseEntity.ok(ApiResponse.success("Password reset email dispatched", null));
    }

    @PostMapping("/{id}/disable-2fa")
    @Operation(summary = "Disable 2FA (Emergency)", description = "Disables two-factor authentication if the user lost their device")
    public ResponseEntity<ApiResponse<Void>> emergencyDisable2FA(@PathVariable UUID id) {
        adminUserService.disableTwoFactorAuthentication(id);
        return ResponseEntity.ok(ApiResponse.success("2FA disabled for user", null));
    }

    @PostMapping(path = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create user from admin panel")
    public ResponseEntity<ApiResponse<String>> createUser(@RequestPart("userData") @Valid AdminUserCreateRequest request, @RequestPart(value = "profilePicture", required = false) MultipartFile file) throws IOException {
        adminUserService.createUserFromAdmin(request, file);
        return ResponseEntity.ok(ApiResponse.success("Usuario creado. Se requiere cambio de contraseña al ingresar.", null));
    }
}
