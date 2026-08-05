package com.ninsky.cronos.domain.port.auth;

import com.ninsky.cronos.domain.model.auth.AuthUserProjection;

import java.util.Optional;

/** Outbound port for the JdbcTemplate-backed, JPA-entity-graph-bypassing auth lookup. */
public interface UserAuthLookupPort {

    Optional<AuthUserProjection> findByUsernameOrEmail(String loginId);
}
