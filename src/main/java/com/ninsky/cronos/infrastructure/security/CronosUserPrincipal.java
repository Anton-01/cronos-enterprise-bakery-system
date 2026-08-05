package com.ninsky.cronos.infrastructure.security;

import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges the pure {@code AuthUserProjection} read model to Spring Security's {@code UserDetails}
 * — the domain layer never implements framework interfaces, so this small adapter is what
 * actually sits in the {@code SecurityContext}. Replaces the previous design where the JPA
 * {@code User} entity implemented {@code UserDetails} directly.
 */
public class CronosUserPrincipal implements UserDetails {

    private final AuthUserProjection projection;

    public CronosUserPrincipal(AuthUserProjection projection) {
        this.projection = projection;
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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        projection.roleNames().forEach(roleName -> authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase())));
        projection.permissionNames().forEach(permissionName -> authorities.add(new SimpleGrantedAuthority(permissionName.toUpperCase())));
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
        return projection.enabled();
    }
}
