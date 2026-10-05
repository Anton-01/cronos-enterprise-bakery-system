package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.domain.model.auth.EffectivePermissions;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Bridges the pure {@code AuthUserProjection} read model to Spring Security's {@code UserDetails}
 * — the domain layer never implements framework interfaces, so this small adapter is what
 * actually sits in the {@code SecurityContext}. Authorities are {@code ROLE_<code>} per active role
 * plus every effective permission code, resolved by the IAM module when available.
 */
public class CronosUserPrincipal implements UserDetails {

    private static final Set<String> BLOCKED_STATUSES = Set.of("SUSPENDED", "DEACTIVATED");

    private final AuthUserProjection projection;
    private final Set<String> permissions;
    private final Set<String> roleCodes;
    private final boolean superAdmin;

    /** Legacy resolution from the projection's role permissions. */
    public CronosUserPrincipal(AuthUserProjection projection) {
        this(projection, projection.roleNames(),
                Set.copyOf(EffectivePermissions.of(projection.roleNames(), projection.permissionNames())),
                projection.roleNames().stream().anyMatch(EffectivePermissions.SUPER_ADMIN_ROLE::equalsIgnoreCase));
    }

    public CronosUserPrincipal(AuthUserProjection projection, Collection<String> roleCodes, Collection<String> permissions, boolean superAdmin) {
        this.projection = projection;
        this.roleCodes = Set.copyOf(roleCodes);
        this.permissions = Set.copyOf(permissions);
        this.superAdmin = superAdmin;
    }

    public UUID getId() {
        return projection.id();
    }

    public String getEmail() {
        return projection.email();
    }

    public boolean isTwoFactorEnabled() {
        return projection.twoFactorEnabled();
    }

    public long getAccessVersion() {
        return projection.accessVersion();
    }

    public String getStatus() {
        return projection.status();
    }

    public boolean isMustChangePassword() {
        return projection.mustChangePassword();
    }

    /** Effective permission codes (plus transitional aliases). */
    public Set<String> getPermissions() {
        return permissions;
    }

    public Set<String> getRoleCodes() {
        return roleCodes;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        Stream.concat(roleCodes.stream().map(code -> "ROLE_" + code.toUpperCase(Locale.ROOT)), permissions.stream())
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return authorities;
    }

    @Override
    public String getPassword() {
        return projection.password();
    }

    @Override
    public String getUsername() {
        return projection.username();
    }

    @Override
    public boolean isAccountNonExpired() {
        return projection.accountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return !projection.isEffectivelyLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return projection.credentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return projection.enabled() && !BLOCKED_STATUSES.contains(projection.status());
    }
}
