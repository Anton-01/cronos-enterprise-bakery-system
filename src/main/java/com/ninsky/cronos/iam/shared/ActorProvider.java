package com.ninsky.cronos.iam.shared;

import java.util.Optional;

/** Who is acting: the authenticated principal, or empty for system jobs. */
public interface ActorProvider {

    Optional<Actor> current();

    /** @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException when unauthenticated */
    default Actor require() {
        return current().orElseThrow(() ->
                new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("No authenticated actor"));
    }
}
