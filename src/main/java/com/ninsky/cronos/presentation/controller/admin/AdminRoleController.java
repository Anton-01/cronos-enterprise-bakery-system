package com.ninsky.cronos.presentation.controller.admin;

import com.ninsky.cronos.application.request.roles.RoleRequest;
import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.application.response.roles.PermissionResponse;
import com.ninsky.cronos.application.response.roles.RoleResponse;
import com.ninsky.cronos.application.service.admin.AdminRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
@Tag(name = "Admin - Roles & Permissions", description = "Endpoints for Super Admins to manage RBAC")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    @GetMapping("/permissions")
    @Operation(summary = "Get all permissions", description = "List all available permissions in the system to render checkboxes")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved", adminRoleService.getAllPermissions()));
    }

    @GetMapping
    @Operation(summary = "Get all roles", description = "List all roles with their assigned permissions")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved", adminRoleService.getAllRoles()));
    }

    @PostMapping
    @Operation(summary = "Create a new role", description = "Creates a role and links the selected permission IDs")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role created successfully", adminRoleService.createRole(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update role", description = "Updates role details and overrides its permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", adminRoleService.updateRole(id, request)));
    }
}
