package com.ninsky.cronos.kitchen.shared;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/** {@code user_kitchen_settings}: the per-user seeding ledger (baking-studio §3.4, §4.6, B4). */
@Repository
@RequiredArgsConstructor
public class KitchenSettingsCustomRepository {

    /** What was already seeded for the user, read under a row lock. */
    public record Seeded(boolean sections, boolean fixedCosts) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the row if missing and locks it ({@code FOR UPDATE}) until the caller's transaction ends, so two
     * first requests (two tabs) seed exactly once.
     */
    public Seeded lock(UUID userId) {
        Map<String, Object> params = Map.of("user", userId);
        jdbc.update("INSERT INTO user_kitchen_settings (user_id) VALUES (:user) ON CONFLICT (user_id) DO NOTHING", params);
        return jdbc.queryForObject("""
                SELECT sections_seeded_at IS NOT NULL AS sections, fixed_costs_seeded_at IS NOT NULL AS fixed_costs
                FROM user_kitchen_settings WHERE user_id = :user FOR UPDATE""", params,
                (rs, i) -> new Seeded(rs.getBoolean("sections"), rs.getBoolean("fixed_costs")));
    }

    /** Unlocked check for the fast path; the caller re-checks under {@link #lock} before seeding. */
    public Seeded peek(UUID userId) {
        return jdbc.query("""
                SELECT sections_seeded_at IS NOT NULL AS sections, fixed_costs_seeded_at IS NOT NULL AS fixed_costs
                FROM user_kitchen_settings WHERE user_id = :user""", Map.of("user", userId),
                (rs, i) -> new Seeded(rs.getBoolean("sections"), rs.getBoolean("fixed_costs"))).stream().findFirst()
                .orElse(new Seeded(false, false));
    }

    public void markSectionsSeeded(UUID userId, Instant at) {
        jdbc.update("UPDATE user_kitchen_settings SET sections_seeded_at = :at WHERE user_id = :user",
                new MapSqlParameterSource().addValue("user", userId).addValue("at", at.atOffset(ZoneOffset.UTC)));
    }

    public void markFixedCostsSeeded(UUID userId, Instant at) {
        jdbc.update("UPDATE user_kitchen_settings SET fixed_costs_seeded_at = :at WHERE user_id = :user",
                new MapSqlParameterSource().addValue("user", userId).addValue("at", at.atOffset(ZoneOffset.UTC)));
    }
}
