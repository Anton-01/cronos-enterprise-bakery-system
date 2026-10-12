package com.ninsky.cronos.infrastructure.web;

import com.ninsky.cronos.infrastructure.observability.SqlStatementStats;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Named phases of the current request, reported by {@link ServerTimingFilter} in the standard
 * {@code Server-Timing} header (browser DevTools → Network → Timing). Outside a request it only runs the work.
 */
public final class ServerTiming {

    static final String ATTRIBUTE = ServerTiming.class.getName();

    private ServerTiming() {
    }

    public static <T> T measure(String phase, Supplier<T> work) {
        long start = System.nanoTime();
        try {
            return work.get();
        } finally {
            record(phase, System.nanoTime() - start);
        }
    }

    public static void measure(String phase, Runnable work) {
        measure(phase, () -> {
            work.run();
            return null;
        });
    }

    /** Adds {@code nanos} to {@code phase} (repeated phases accumulate). */
    public static void record(String phase, long nanos) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Long> phases = (Map<String, Long>) attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (phases == null) {
            phases = new LinkedHashMap<>();
            attributes.setAttribute(ATTRIBUTE, phases, RequestAttributes.SCOPE_REQUEST);
        }
        phases.merge(phase, nanos, Long::sum);
    }

    /** {@code name;dur=ms} entries, milliseconds with one decimal. */
    static String header(Map<String, Long> phases, long totalNanos) {
        return header(phases, null, totalNanos);
    }

    /**
     * Same as {@link #header(Map, long)} plus {@code db;dur=ms;desc="n stmts"} before {@code app} when the request's
     * statements were counted ({@code sql} not null).
     */
    static String header(Map<String, Long> phases, SqlStatementStats sql, long totalNanos) {
        Map<String, Long> all = new LinkedHashMap<>(phases == null ? Map.of() : phases);
        String entries = all.entrySet().stream()
                .map(e -> entry(e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
        String db = sql == null ? null : entry("db", sql.nanos()) + ";desc=\"" + sql.statements() + " stmts\"";
        String app = entry("app", totalNanos);
        return Stream.of(entries, db, app).filter(part -> part != null && !part.isEmpty()).collect(Collectors.joining(", "));
    }

    private static String entry(String name, long nanos) {
        return name + ";dur=" + String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0);
    }
}
