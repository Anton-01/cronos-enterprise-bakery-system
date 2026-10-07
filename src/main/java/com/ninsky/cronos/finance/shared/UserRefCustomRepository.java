package com.ninsky.cronos.finance.shared;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.shared.domain.DomainValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads a {@link UserRef} from the columns produced by {@link #columns(String)} / {@link #join(String)}. */
@Repository
@RequiredArgsConstructor
public class UserRefCustomRepository {

    private final AvatarStorage avatarStorage;
    private final NamedParameterJdbcTemplate jdbc;

    public Optional<UserRef> find(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return jdbc.query("SELECT " + columns("ub.id") + " FROM users ub LEFT JOIN user_profiles ubp ON ubp.user_id = ub.id WHERE ub.id = :id",
                Map.of("id", userId), (rs, rowNum) -> map(rs)).stream().filter(Objects::nonNull).findFirst();
    }

    /** SELECT list for the user joined on {@code fkColumn}, aliased {@code ub_*}. */
    public static String columns(String fkColumn) {
        return fkColumn + " AS ub_id, ub.username AS ub_username, ubp.first_name AS ub_first_name, "
                + "ubp.last_name AS ub_last_name, ub.avatar_key AS ub_avatar_key";
    }

    public static String join(String fkColumn) {
        return " LEFT JOIN users ub ON ub.id = " + fkColumn + " LEFT JOIN user_profiles ubp ON ubp.user_id = ub.id ";
    }

    /** Null when the row has no user or the user no longer exists. */
    public UserRef map(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("ub_id", UUID.class);
        String username = rs.getString("ub_username");
        if (id == null || username == null) {
            return null;
        }
        String fullName = Stream.of(rs.getString("ub_first_name"), rs.getString("ub_last_name"))
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
        return new UserRef(id, username, fullName.isEmpty() ? username : fullName, avatarUrl(rs.getString("ub_avatar_key")));
    }

    private String avatarUrl(String key) {
        try {
            return Optional.ofNullable(AvatarKey.ofNullable(key)).map(k -> avatarStorage.publicUrl(k).toString()).orElse(null);
        } catch (DomainValidationException malformed) {
            return null;
        }
    }
}
