package com.ninsky.cronos.infrastructure.observability;

import java.util.Optional;

/**
 * Database round trips of the current request: how many JDBC statements ran and how long they took. A request opens
 * a window with {@link #open()} (done by {@code ServerTimingFilter}) and closes it with {@link #close()}; the
 * datasource-proxy listener adds every statement executed on that thread in between. Work on other threads (outbox
 * relay, scheduled jobs, async dispatches) is not attributed to any request.
 * <p>
 * Also used by tests to assert the number of statements an endpoint runs (performance-latency §4).
 */
public final class SqlStatementStats {

    private static final ThreadLocal<SqlStatementStats> CURRENT = new ThreadLocal<>();

    private int statements;
    private long nanos;

    private SqlStatementStats() {
    }

    /** Starts counting on this thread, discarding any window left open. */
    public static SqlStatementStats open() {
        SqlStatementStats stats = new SqlStatementStats();
        CURRENT.set(stats);
        return stats;
    }

    /** Stops counting on this thread. */
    public static void close() {
        CURRENT.remove();
    }

    /** The open window of this thread, if any. */
    public static Optional<SqlStatementStats> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Adds one executed statement (a JDBC batch counts once: it is one round trip). */
    static void record(long elapsedNanos) {
        SqlStatementStats stats = CURRENT.get();
        if (stats != null) {
            stats.statements++;
            stats.nanos += elapsedNanos;
        }
    }

    /** Statements executed since {@link #open()}. */
    public int statements() {
        return statements;
    }

    /** Time spent waiting on those statements, in nanoseconds. */
    public long nanos() {
        return nanos;
    }
}
