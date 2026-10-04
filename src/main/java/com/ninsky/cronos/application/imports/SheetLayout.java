package com.ninsky.cronos.application.imports;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Header row → column index. Matching is tolerant of presentation (case, spaces, underscores,
 * hyphens, a trailing "*" marking required columns) but not of meaning: a missing required column
 * or a column present twice is an error; an unknown column is a warning and is ignored.
 */
public final class SheetLayout {

    private static final Pattern DECORATION = Pattern.compile("[\\s_*\\-]");

    private final Map<String, Integer> indexByColumn;

    private SheetLayout(Map<String, Integer> indexByColumn) {
        this.indexByColumn = Map.copyOf(indexByColumn);
    }

    public static SheetLayout resolve(List<String> headers, List<ColumnSpec> columns, IssueCollector issues) {
        Map<String, ColumnSpec> expected = new HashMap<>();
        columns.forEach(column -> expected.put(normalize(column.name()), column));

        Map<String, Integer> found = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i) == null ? "" : headers.get(i).trim();
            if (header.isEmpty()) {
                continue;
            }
            ColumnSpec column = expected.get(normalize(header));
            if (column == null) {
                issues.warning(1, header, "import.header.unknown", header);
            } else if (found.putIfAbsent(column.name(), i) != null) {
                issues.error(1, header, "import.header.duplicated", header);
            }
        }
        columns.stream()
                .filter(column -> column.required() && !found.containsKey(column.name()))
                .forEach(column -> issues.error(1, column.name(), "import.header.missing", column.name()));
        return new SheetLayout(found);
    }

    static String normalize(String header) {
        return DECORATION.matcher(header).replaceAll("").toLowerCase(Locale.ROOT);
    }

    Optional<Integer> indexOf(String column) {
        return Optional.ofNullable(indexByColumn.get(column));
    }
}
