package com.ninsky.cronos.iam.shared;

import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Reads the actor from the Spring Security context. */
@Component
public class SecurityActorProvider implements ActorProvider {

    @Override
    public Optional<Actor> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CronosUserPrincipal principal) || principal.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(new Actor(principal.getId(), principal.getUsername(), principal.getPermissions(), principal.isSuperAdmin()));
    }
}
