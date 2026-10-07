package com.ninsky.cronos.iam.shared;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Bulk user identity rows for {@link UserDirectory}. */
@Repository
@RequiredArgsConstructor
public class UserDirectoryCustomRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record Row(UUID id, String username, String avatarKey, String firstName, String lastName) {
    }

    public List<Row> find(Collection<UUID> ids) {
        return jdbc.query("""
                        SELECT u.id, u.username, u.avatar_key, p.first_name, p.last_name
                        FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id WHERE u.id IN (:ids)""",
                new MapSqlParameterSource("ids", ids),
                (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
    }
}
