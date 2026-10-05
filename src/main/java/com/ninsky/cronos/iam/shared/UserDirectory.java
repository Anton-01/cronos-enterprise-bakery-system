package com.ninsky.cronos.iam.shared;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves {@link UserRef}s in bulk (one query for any number of ids). */
@Component
@RequiredArgsConstructor
public class UserDirectory {

    private final NamedParameterJdbcTemplate jdbc;
    private final AvatarStorage avatarStorage;

    public Map<UUID, UserRef> refs(Collection<UUID> ids) {
        var distinct = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("""
                        SELECT u.id, u.username, u.avatar_key, p.first_name, p.last_name
                        FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id WHERE u.id IN (:ids)""",
                new MapSqlParameterSource("ids", distinct),
                (rs, i) -> new UserRef(rs.getObject(1, UUID.class), rs.getString(2),
                        UserRef.displayName(rs.getString(4), rs.getString(5), rs.getString(2)), avatarUrl(rs.getString(3))))
                .stream().collect(Collectors.toMap(UserRef::id, Function.identity()));
    }

    public Optional<UserRef> ref(UUID id) {
        return Optional.ofNullable(id).map(i -> refs(List.of(i)).get(i));
    }

    public String avatarUrl(String avatarKey) {
        return avatarKey == null ? null : avatarStorage.publicUrl(AvatarKey.ofNullable(avatarKey)).toString();
    }
}
