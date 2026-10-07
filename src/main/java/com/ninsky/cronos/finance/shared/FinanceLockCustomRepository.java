package com.ninsky.cronos.finance.shared;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** Row lock on the single {@code finance_settings} row. */
@Repository
@RequiredArgsConstructor
public class FinanceLockCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public void lockSettingsRow() {
        jdbc.query("SELECT id FROM finance_settings WHERE id = 1 FOR UPDATE", Map.of(), rs -> { });
    }
}
