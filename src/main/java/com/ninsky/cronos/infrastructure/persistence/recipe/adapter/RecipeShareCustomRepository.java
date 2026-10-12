package com.ninsky.cronos.infrastructure.persistence.recipe.adapter;

import com.ninsky.cronos.application.response.recipe.RecipeShareAccessLogResponse;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of recipe share links in plain SQL. Each read answers the visibility/ownership check and the data in the
 * same statement, so a foreign or invisible target is simply "no row" (404) without loading the recipe aggregate.
 */
@Repository
public class RecipeShareCustomRepository {

    /** One link as the share list shows it. */
    public record ShareRow(UUID id, String shareToken, LocalDateTime expiresAt, int viewsCount, boolean revoked, LocalDateTime createdAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public RecipeShareCustomRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The caller's links of a recipe they can see (own or SYSTEM library, not deleted), newest first; empty when the
     * recipe is not visible to them.
     */
    public Optional<List<ShareRow>> sharesOfVisibleRecipe(UUID recipeId, UUID userId) {
        List<ShareRow> rows = new ArrayList<>();
        boolean[] visible = {false};
        jdbc.query("""
                        SELECT s.id, s.share_token, s.expires_at, s.views_count, s.is_revoked, s.created_at
                        FROM recipes r
                        LEFT JOIN recipe_shares s ON s.recipe_id = r.id AND s.user_id = :user
                        WHERE r.id = :recipe AND r.deleted_at IS NULL AND (r.owner_id IS NULL OR r.owner_id = :user)
                        ORDER BY s.created_at DESC NULLS LAST""",
                new MapSqlParameterSource("recipe", recipeId).addValue("user", userId), (RowCallbackHandler) rs -> {
                    visible[0] = true;
                    UUID id = rs.getObject("id", UUID.class);
                    if (id != null) {
                        rows.add(new ShareRow(id, rs.getString("share_token"), local(rs.getTimestamp("expires_at")),
                                rs.getInt("views_count"), rs.getBoolean("is_revoked"), local(rs.getTimestamp("created_at"))));
                    }
                });
        return visible[0] ? Optional.of(List.copyOf(rows)) : Optional.empty();
    }

    /** Access log of one of the caller's links of {@code recipeId}, newest first; empty when the link is not theirs. */
    public Optional<List<RecipeShareAccessLogResponse>> accessLog(UUID recipeId, UUID shareId, UUID userId) {
        List<RecipeShareAccessLogResponse> rows = new ArrayList<>();
        boolean[] own = {false};
        jdbc.query("""
                        SELECT l.id, l.accessed_at, l.ip_address, l.user_agent
                        FROM recipe_shares s
                        LEFT JOIN recipe_share_access_logs l ON l.recipe_share_id = s.id
                        WHERE s.id = :share AND s.user_id = :user AND s.recipe_id = :recipe
                        ORDER BY l.accessed_at DESC NULLS LAST""",
                new MapSqlParameterSource("share", shareId).addValue("user", userId).addValue("recipe", recipeId), (RowCallbackHandler) rs -> {
                    own[0] = true;
                    UUID id = rs.getObject("id", UUID.class);
                    if (id != null) {
                        rows.add(RecipeShareAccessLogResponse.builder().id(id).accessedAt(local(rs.getTimestamp("accessed_at")))
                                .ipAddress(rs.getString("ip_address")).userAgent(rs.getString("user_agent")).build());
                    }
                });
        return own[0] ? Optional.of(List.copyOf(rows)) : Optional.empty();
    }

    private static LocalDateTime local(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
