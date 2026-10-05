package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.iam.shared.RoleRef;
import com.ninsky.cronos.iam.shared.TenantTime;
import com.ninsky.cronos.infrastructure.security.crypto.BlindIndexService;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read side of IAM users: search, detail, roles per user, export stream (JDBC, decrypting in-process). */
@Repository
@RequiredArgsConstructor
public class UserReadRepository {

    /** API sort field → SQL expression; {@code email} is sorted in memory (ciphertext column). */
    public static final Map<String, String> SORTS = Map.of(
            "displayName", "lower(unaccent(coalesce(NULLIF(btrim(concat_ws(' ', p.first_name, p.last_name)), ''), u.username)))",
            "username", "lower(u.username)",
            "email", "u.email_blind_index",
            "status", "u.status",
            "lastLoginAt", "u.last_login_at",
            "createdAt", "u.created_at");
    public static final String DEFAULT_SORT = "createdAt,desc";

    private static final String COLUMNS = """
            SELECT u.id, u.username, u.email, p.first_name, p.last_name, u.avatar_key, u.job_title, u.department,
                   u.employee_number, p.phone_number, u.locale, u.status, u.status_reason, u.status_comment, u.status_until,
                   u.status_changed_at, u.status_changed_by, u.access_expires_at, u.require_two_factor, u.two_factor_enabled,
                   u.password_needs_change, u.email_verified, u.failed_login_attempts, u.last_login_at, u.password_changed_at,
                   u.created_at, u.created_by_id, u.updated_at, u.updated_by_id, u.version
            FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id
            """;

    private static final String EMAIL_CONTEXT = "email";

    private final NamedParameterJdbcTemplate jdbc;
    private final FieldEncryptionService encryption;
    private final BlindIndexService blindIndex;

    public record Page(List<UserRow> rows, long total) {
    }

    public Page search(UserSearch search, PageQuery page) {
        var params = new MapSqlParameterSource();
        String where = where(search, params);
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT count(*) FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id" + where, params, Long.class)).orElse(0L);
        if ("email".equals(page.sortField())) {
            return new Page(pageByEmail(where, params, page), total);
        }
        params.addValue("limit", page.size()).addValue("offset", page.offset());
        List<UserRow> rows = jdbc.query(COLUMNS + where + " ORDER BY " + page.orderBySql() + ", u.id LIMIT :limit OFFSET :offset",
                params, this::row);
        return new Page(rows, total);
    }

    /** Every match in sort order, streamed row by row (export); call inside a transaction so the cursor is used. */
    public void stream(UserSearch search, String orderBySql, Consumer<UserRow> consumer) {
        var params = new MapSqlParameterSource();
        String where = where(search, params);
        JdbcTemplate streaming = new JdbcTemplate(jdbc.getJdbcTemplate().getDataSource());
        streaming.setFetchSize(500);
        new NamedParameterJdbcTemplate(streaming).query(COLUMNS + where + " ORDER BY " + orderBySql + ", u.id", params,
                (RowCallbackHandler) rs -> consumer.accept(row(rs, 0)));
    }

    public Optional<UserRow> find(UUID id) {
        return jdbc.query(COLUMNS + " WHERE u.id = :id", Map.of("id", id), this::row).stream().findFirst();
    }

    public List<UserRow> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(COLUMNS + " WHERE u.id IN (:ids)", Map.of("ids", ids), this::row);
    }

    /** Roles of each user, sorted by name. */
    public Map<UUID, List<RoleRef>> roles(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<RoleRef>> roles = new LinkedHashMap<>();
        jdbc.query("""
                        SELECT ur.user_id, r.id, r.code, r.name, r.color FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                        WHERE ur.user_id IN (:ids) ORDER BY lower(r.name)""", Map.of("ids", userIds),
                (RowCallbackHandler) rs -> roles.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                        .add(new RoleRef(rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5))));
        return roles;
    }

    public boolean usernameTaken(String username, UUID excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE lower(username) = lower(:value) AND id <> coalesce(CAST(:exclude AS UUID), '00000000-0000-0000-0000-000000000000'))",
                new MapSqlParameterSource("value", username).addValue("exclude", excludeId), Boolean.class));
    }

    public boolean emailTaken(String email, UUID excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE email_blind_index = :value AND id <> coalesce(CAST(:exclude AS UUID), '00000000-0000-0000-0000-000000000000'))",
                new MapSqlParameterSource("value", blindIndex.hmac(EMAIL_CONTEXT, email)).addValue("exclude", excludeId), Boolean.class));
    }

    public boolean employeeNumberTaken(String employeeNumber, UUID excludeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE upper(employee_number) = upper(:value) AND id <> coalesce(CAST(:exclude AS UUID), '00000000-0000-0000-0000-000000000000'))",
                new MapSqlParameterSource("value", employeeNumber).addValue("exclude", excludeId), Boolean.class));
    }

    private List<UserRow> pageByEmail(String where, MapSqlParameterSource params, PageQuery page) {
        Map<UUID, String> emails = new LinkedHashMap<>();
        jdbc.query("SELECT u.id, u.email FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id" + where, params,
                (RowCallbackHandler) rs -> emails.put(rs.getObject(1, UUID.class), decrypt(rs.getString(2))));
        Comparator<Map.Entry<UUID, String>> byEmail = Comparator.comparing(e -> e.getValue() == null ? "" : e.getValue());
        List<UUID> ids = emails.entrySet().stream()
                .sorted(page.ascending() ? byEmail : byEmail.reversed())
                .skip(page.offset()).limit(page.size())
                .map(Map.Entry::getKey).toList();
        Map<UUID, UserRow> rows = findAll(ids).stream().collect(Collectors.toMap(UserRow::id, Function.identity()));
        return ids.stream().map(rows::get).toList();
    }

    private String where(UserSearch search, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        if (search.search() != null) {
            clauses.add("""
                    (unaccent(lower(u.username)) LIKE unaccent(lower(:q)) OR unaccent(lower(coalesce(p.first_name, ''))) LIKE unaccent(lower(:q))
                     OR unaccent(lower(coalesce(p.last_name, ''))) LIKE unaccent(lower(:q))
                     OR unaccent(lower(concat_ws(' ', p.first_name, p.last_name))) LIKE unaccent(lower(:q))
                     OR lower(coalesce(u.employee_number, '')) LIKE lower(:q) OR u.email_blind_index = :emailIndex)""");
            params.addValue("q", "%" + search.search().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
                    .addValue("emailIndex", blindIndex.hmac(EMAIL_CONTEXT, search.search()));
        }
        if (!search.statuses().isEmpty()) {
            clauses.add("u.status IN (:statuses)");
            params.addValue("statuses", search.statuses().stream().map(Enum::name).toList());
        }
        if (!search.roleIds().isEmpty()) {
            clauses.add("EXISTS (SELECT 1 FROM user_roles fr WHERE fr.user_id = u.id AND fr.role_id IN (:roleIds))");
            params.addValue("roleIds", search.roleIds());
        }
        if (search.twoFactorEnabled() != null) {
            clauses.add("u.two_factor_enabled = :twoFactor");
            params.addValue("twoFactor", search.twoFactorEnabled());
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    private UserRow row(ResultSet rs, int rowNum) throws SQLException {
        String reason = rs.getString("status_reason");
        return new UserRow(
                rs.getObject("id", UUID.class), rs.getString("username"), decrypt(rs.getString("email")),
                rs.getString("first_name"), rs.getString("last_name"), rs.getString("avatar_key"),
                rs.getString("job_title"), rs.getString("department"), rs.getString("employee_number"),
                decrypt(rs.getString("phone_number")), rs.getString("locale"),
                UserStatus.valueOf(rs.getString("status")), reason == null ? null : StatusReason.valueOf(reason),
                rs.getString("status_comment"), utc(rs.getObject("status_until", OffsetDateTime.class)),
                utc(rs.getObject("status_changed_at", OffsetDateTime.class)), rs.getObject("status_changed_by", UUID.class),
                rs.getObject("access_expires_at", LocalDate.class), rs.getBoolean("require_two_factor"),
                rs.getBoolean("two_factor_enabled"), rs.getBoolean("password_needs_change"), rs.getBoolean("email_verified"),
                rs.getInt("failed_login_attempts"), local(rs.getTimestamp("last_login_at")), local(rs.getTimestamp("password_changed_at")),
                local(rs.getTimestamp("created_at")), rs.getObject("created_by_id", UUID.class),
                local(rs.getTimestamp("updated_at")), rs.getObject("updated_by_id", UUID.class), rs.getLong("version"));
    }

    private String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        String plain = encryption.decrypt(ciphertext);
        return FieldEncryptionService.DECRYPTION_FAILED_SENTINEL.equals(plain) ? null : plain;
    }

    private static Instant utc(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static Instant local(Timestamp value) {
        return value == null ? null : TenantTime.toInstant(value.toLocalDateTime());
    }
}
