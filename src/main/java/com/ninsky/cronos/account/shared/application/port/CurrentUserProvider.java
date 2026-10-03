package com.ninsky.cronos.account.shared.application.port;

import java.util.UUID;

/**
 * The ONLY source of "who is acting" for account use cases: the authenticated principal's user id.
 * Never a request body or path value — the account endpoints have no {@code {id}} to tamper with.
 */
public interface CurrentUserProvider {

    /** @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException when unauthenticated */
    UUID currentUserId();
}
