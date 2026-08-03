package com.ninsky.cronos.infrastructure.config.seed;

import com.ninsky.cronos.domain.entity.auth.Permission;
import com.ninsky.cronos.domain.entity.auth.Role;
import com.ninsky.cronos.domain.entity.auth.User;
import com.ninsky.cronos.domain.entity.auth.UserProfile;
import com.ninsky.cronos.infrastructure.persistence.auth.PermissionRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.RoleRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.UserProfileRepository;
import com.ninsky.cronos.infrastructure.persistence.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info(":: CRONOS :: Starting seed data verification ...");

        // We only populate the table if there are no records in the database
        if (roleRepository.count() == 0) {
            seedSecurityData();
            seedSuperAdmin();
            log.info(":: CRONOS :: Seed data has been successfully entered.");
        } else {
            log.info(":: CRONOS :: The database already contains data. The seeder is skipped.");
        }
    }

    private void seedSecurityData() {
        // 1. Create basic permissions
        Permission allAccess = createPermission("ALL_ACCESS", "Acceso total al sistema", "SYSTEM", "ALL");
        Permission manageUsers = createPermission("MANAGE_USERS", "Gestión de usuarios", "USERS", "ALL");
        Permission viewDashboard = createPermission("VIEW_DASHBOARD", "Ver panel principal", "DASHBOARD", "READ");

        // 2. Create Roles and assign permissions
        Role superAdminRole = Role.builder().name("SUPER_ADMIN").description("Administrador maestro del sistema")
                .permissions(new HashSet<>(Set.of(allAccess, manageUsers, viewDashboard))).build();

        roleRepository.save(superAdminRole);

        Role userRole = Role.builder().name("USER").description("Usuario estándar del sistema").permissions(new HashSet<>(Set.of(viewDashboard))).build();
        roleRepository.save(userRole);
    }

    private void seedSuperAdmin() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Rol SUPER_ADMIN no encontrado"));

        // 3. Create an Authentication User
        User adminUser = User.builder().username("admin_cronos")
                .email("admin@cronos.com").password(passwordEncoder.encode("SuperAdmin2026!"))
                .emailVerified(true).enabled(true).accountNonLocked(true).accountNonExpired(true).credentialsNonExpired(true)
                .twoFactorEnabled(false).roles(new HashSet<>(Set.of(superAdminRole))).build();

        adminUser = userRepository.save(adminUser);

        // 4. Create an Admin Administrator
        UserProfile adminProfile = UserProfile.builder().user(adminUser).firstName("Antón")
                .lastName("Admin").businessName("Cronos System").businessType("Software")
                .emailNotifications(true).pushNotifications(true).build();

        userProfileRepository.save(adminProfile);
    }

    private Permission createPermission(String name, String description, String resource, String action) {
        Permission permission = Permission.builder().name(name).description(description)
                .resource(resource).action(action).build();

        return permissionRepository.save(permission);
    }
}
