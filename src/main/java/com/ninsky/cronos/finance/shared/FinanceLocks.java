package com.ninsky.cronos.finance.shared;

import com.ninsky.cronos.infrastructure.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/** Serialises default switches (both catalogs) on the single finance_settings row. */
@Component
@RequiredArgsConstructor
public class FinanceLocks {

    private final NamedParameterJdbcTemplate jdbc;

    /** Row lock held until the caller's transaction ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockDefaults() {
        jdbc.query("SELECT id FROM finance_settings WHERE id = 1 FOR UPDATE", Map.of(), rs -> { });
    }

    /** Stale {@code version} → 409 CONCURRENT_MODIFICATION (N3). */
    public static void requireVersion(Long requested, Long current) {
        if (!Objects.equals(requested, current)) {
            throw ApiException.concurrentModification();
        }
    }
}
