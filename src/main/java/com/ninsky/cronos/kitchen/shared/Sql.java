package com.ninsky.cronos.kitchen.shared;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** JDBC helpers of the kitchen read/write models. */
public final class Sql {

    /** {@code owner_key} of SYSTEM rows. */
    public static final UUID SYSTEM_OWNER = new UUID(0L, 0L);

    private Sql() {
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    public static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String::valueOf).toList();
    }

    public static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** {@code %term%} folded like {@code kitchen_fold}, LIKE wildcards escaped; null for blank. */
    public static String likeFolded(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String folded = TextNormalizer.fold(search);
        return "%" + folded.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
