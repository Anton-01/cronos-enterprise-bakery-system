package com.ninsky.cronos.infrastructure.config.seed;

import com.ninsky.cronos.domain.model.auth.Role;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.domain.model.auth.UserProfile;
import com.ninsky.cronos.domain.port.auth.RoleRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserProfileRepositoryPort;
import com.ninsky.cronos.domain.port.auth.UserRepositoryPort;
import com.ninsky.cronos.iam.access.UserAccessCustomRepository;
import com.ninsky.cronos.iam.role.SystemRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepositoryPort roleRepository;
    private final UserAccessCustomRepository userAccess;
    private final UserRepositoryPort userRepository;
    private final UserProfileRepositoryPort userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info(":: CRONOS :: Starting seed data verification ...");

        // Roles and permissions are seeded by Flyway (V11); only the first root account is created here.
        if (userAccess.anyHolder(SystemRole.SUPER_ADMIN_CODE)) {
            log.info(":: CRONOS :: A SUPER_ADMIN account already exists. The seeder is skipped.");
            return;
        }
        seedSuperAdmin();
        log.info(":: CRONOS :: Seed data has been successfully entered.");
    }

    private void seedSuperAdmin() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Rol SUPER_ADMIN no encontrado"));

        // 3. Create an Authentication User
        User adminUser = User.builder().username("admin_cronos")
                .email("admin@cronos.com").password(passwordEncoder.encode("SuperAdmin2026!"))
                .emailVerified(true).enabled(true).accountNonLocked(true).accountNonExpired(true).credentialsNonExpired(true)
                .twoFactorEnabled(false).roleIds(Set.of(superAdminRole.getId())).build();

        adminUser = userRepository.save(adminUser);

        // 4. Create an Admin Administrator
        UserProfile adminProfile = UserProfile.builder().userId(adminUser.getId()).firstName("Antón")
                .lastName("Admin").businessName("Cronos System").businessType("Software")
                .emailNotifications(true).pushNotifications(true).build();

        userProfileRepository.save(adminProfile);
    }
}
