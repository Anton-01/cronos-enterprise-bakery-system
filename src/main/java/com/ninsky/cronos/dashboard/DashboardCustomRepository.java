package com.ninsky.cronos.dashboard;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** The dashboard counters in one statement (four scalar sub-selects, each on its own index). */
@Repository
public class DashboardCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DashboardSummary summary(UUID owner) {
        return jdbc.queryForObject("""
                        SELECT (SELECT count(*) FROM recipes r WHERE r.owner_id = :owner AND r.deleted_at IS NULL) AS recipes,
                               (SELECT count(*) FROM quotes q WHERE q.user_id = :owner AND q.deleted_at IS NULL) AS quotes,
                               (SELECT count(*) FROM ingredients i WHERE (i.owner_id IS NULL OR i.owner_id = :owner)
                                                                    AND i.status = 'ACTIVE') AS ingredients,
                               (SELECT count(*) FROM user_fixed_costs f WHERE f.user_id = :owner) AS fixed_costs""",
                new MapSqlParameterSource("owner", owner),
                (rs, row) -> new DashboardSummary(rs.getLong("recipes"), rs.getLong("quotes"), rs.getLong("ingredients"),
                        rs.getLong("fixed_costs")));
    }
}
