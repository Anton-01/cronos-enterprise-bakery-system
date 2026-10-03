package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.AuthUserProjection;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for the JdbcTemplate-backed, JPA-entity-graph-bypassing auth lookup. */
public interface UserAuthLookupPort {

    Optional<AuthUserProjection> findByUsernameOrEmail(String loginId);

    /** Lookup by the stable identity carried in the access token's {@code userId} claim. */
    Optional<AuthUserProjection> findById(UUID userId);
}
