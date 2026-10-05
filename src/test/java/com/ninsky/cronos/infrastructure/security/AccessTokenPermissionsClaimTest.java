package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.domain.model.auth.EffectivePermissions;
import com.ninsky.cronos.domain.model.auth.User;
import com.ninsky.cronos.infrastructure.config.security.JwtConfig;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract with the SPA (TokenService.getPermissions()): the access token carries a top-level
 * {@code permissions} claim, a JSON array of strings parallel to {@code roles}, and it holds exactly
 * the authorities {@code @PreAuthorize} checks — SUPER_ADMIN included.
 */
class AccessTokenPermissionsClaimTest {

    private final JwtService jwtService = new JwtService(jwtConfig());

    private static JwtConfig jwtConfig() {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-for-hs512-must-be-at-least-sixty-four-bytes-long-0123456789abcdef");
        config.setAccessTokenExpiration(60_000L);
        config.setIssuer("test");
        return config;
    }

    @Test
    void permissionsIsATopLevelArrayOfStringsParallelToRoles() {
        List<String> roles = List.of("SUPER_ADMIN");
        List<String> permissions = EffectivePermissions.of(roles, List.of("ALL_ACCESS"));
        User user = User.builder().id(UUID.randomUUID()).username("admin_cronos").email("admin@cronos.com").build();

        String token = jwtService.generateAccessToken(user, UUID.randomUUID(), roles, permissions, null);

        Claims claims = jwtService.extractClaim(token, c -> c);
        assertThat(claims.get("roles")).isEqualTo(List.of("SUPER_ADMIN"));
        assertThat(claims.get("permissions")).isInstanceOf(List.class).isEqualTo(List.of("ALL_ACCESS", "MANAGE_CATALOGS"));
    }

    @Test
    void serverAuthoritiesMatchTheTokenClaim() {
        CronosUserPrincipal superAdmin = new CronosUserPrincipal(new AuthUserProjection(UUID.randomUUID(), "admin_cronos", "a@b.c", "x",
                true, true, true, true, false, null, Set.of("SUPER_ADMIN"), Set.of("ALL_ACCESS")));

        assertThat(superAdmin.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "ALL_ACCESS", "MANAGE_CATALOGS");
    }
}
