package com.ninsky.cronos.infrastructure.persistence.core.adapter;

import com.ninsky.cronos.domain.port.core.UnitCatalogLockPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-scoped PostgreSQL advisory lock: released automatically on commit/rollback, works
 * across every app instance sharing the database, and needs no extra infrastructure (Redis may be
 * down without blocking catalog maintenance). {@code MANDATORY} turns a call outside a transaction
 * — where the lock would be released immediately — into an error instead of a silent no-op.
 */
@Repository
public class UnitCatalogLockCustomRepository implements UnitCatalogLockPort {

    /** Arbitrary but stable key, unique to the unit catalog ("UNITCAT" in ASCII). */
    static final long UNIT_CATALOG_LOCK_KEY = 0x554E4954434154L;

    private final JdbcTemplate jdbcTemplate;

    public UnitCatalogLockCustomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForWrite() {
        jdbcTemplate.queryForList("SELECT 1 FROM (SELECT pg_advisory_xact_lock(?)) AS lock", Integer.class, UNIT_CATALOG_LOCK_KEY);
    }
}
