package com.ninsky.cronos.infrastructure.persistence.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** PostgreSQL advisory locks shared by jobs and writers. */
@Repository
@RequiredArgsConstructor
public class AdvisoryLockCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Blocks until {@code key} is free; held until the current transaction ends. */
    public void lockForTransaction(long key) {
        jdbc.query("SELECT pg_advisory_xact_lock(:key)", Map.of("key", key), rs -> { });
    }

    /** Non-blocking variant; false when another transaction holds {@code key}. */
    public boolean tryLockForTransaction(long key) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(:key)", Map.of("key", key), Boolean.class));
    }
}
