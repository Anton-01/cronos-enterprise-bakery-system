package com.ninsky.cronos.account.shared.infrastructure.security;

import com.ninsky.cronos.account.shared.application.port.CurrentUserProvider;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reads the user id of the principal {@code JwtAuthenticationFilter} authenticated from the
 * access token's {@code userId} claim — stable across username changes, unlike {@code sub}.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CronosUserPrincipal principal
                && principal.getId() != null) {
            return principal.getId();
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated Cronos user in the security context");
    }
}
