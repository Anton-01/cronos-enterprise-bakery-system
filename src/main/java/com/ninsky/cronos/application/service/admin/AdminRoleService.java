package com.ninsky.cronos.application.service.admin;

import com.ninsky.cronos.application.request.roles.RoleRequest;
import com.ninsky.cronos.application.response.roles.PermissionResponse;
import com.ninsky.cronos.application.response.roles.RoleResponse;
import com.ninsky.cronos.domain.model.auth.Permission;
import com.ninsky.cronos.domain.model.auth.Role;
import com.ninsky.cronos.domain.port.auth.PermissionRepositoryPort;
import com.ninsky.cronos.domain.port.auth.RoleRepositoryPort;
import com.ninsky.cronos.infrastructure.exception.BusinessException;
import com.ninsky.cronos.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private final RoleRepositoryPort roleRepository;
    private final PermissionRepositoryPort permissionRepository;

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::mapToPermissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToRoleResponse)
                .toList();
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        log.info("Creating new role: {}", request.name());

        if (roleRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException("Ya existe un rol con ese nombre.");
        }

        Set<Long> permissionIds = new HashSet<>();
        if (request.permissionIds() != null && !request.permissionIds().isEmpty()) {
            permissionIds.addAll(permissionRepository.findAllById(request.permissionIds()).stream().map(Permission::getId).collect(Collectors.toSet()));
        }

        Role role = Role.builder().name(request.name().toUpperCase())
                .description(request.description()).permissionIds(permissionIds)
                .build();

        role = roleRepository.save(role);
        return mapToRoleResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(Long roleId, RoleRequest request) {
        log.info("Updating role ID: {}", roleId);

        Role role = roleRepository.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));

        // Protección arquitectónica: No dejar que nadie le cambie el nombre al SUPER_ADMIN
        if ("SUPER_ADMIN".equals(role.getName()) && !request.name().toUpperCase().equals("SUPER_ADMIN")) {
            throw new BusinessException("El nombre del rol SUPER_ADMIN no puede ser modificado por seguridad del sistema.");
        }

        role.setName(request.name().toUpperCase());
        role.setDescription(request.description());

        Set<Long> permissionIds = new HashSet<>();
        if (request.permissionIds() != null && !request.permissionIds().isEmpty()) {
            permissionIds.addAll(permissionRepository.findAllById(request.permissionIds()).stream().map(Permission::getId).collect(Collectors.toSet()));
        }
        role.setPermissionIds(permissionIds);

        role = roleRepository.save(role);
        return mapToRoleResponse(role);
    }

    private PermissionResponse mapToPermissionResponse(Permission p) {
        return PermissionResponse.builder().id(p.getId()).name(p.getName()).description(p.getDescription())
                .resource(p.getResource()).action(p.getAction()).build();
    }

    private RoleResponse mapToRoleResponse(Role r) {
        Set<PermissionResponse> permissions = r.getPermissionIds() == null || r.getPermissionIds().isEmpty()
                ? Set.of()
                : permissionRepository.findAllById(r.getPermissionIds()).stream().map(this::mapToPermissionResponse).collect(Collectors.toSet());

        return RoleResponse.builder().id(r.getId()).name(r.getName()).description(r.getDescription())
                .permissions(permissions)
                .build();
    }
}
