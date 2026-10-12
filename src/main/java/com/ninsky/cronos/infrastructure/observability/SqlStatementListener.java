package com.ninsky.cronos.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * datasource-proxy listener: attributes every executed statement to the current request
 * ({@link SqlStatementStats}) and records it in the {@code cronos.db.statement} timer, whose percentiles Grafana
 * shows next to {@code http.server.requests}.
 */
public class SqlStatementListener implements QueryExecutionListener {

    private static final ThreadLocal<Long> STARTED = new ThreadLocal<>();

    private final Timer timer;

    /** {@code registry} may be null (test slices without metrics): only the per-request counts are kept then. */
    public SqlStatementListener(MeterRegistry registry) {
        this.timer = registry == null ? null : Timer.builder("cronos.db.statement")
                .description("JDBC statements executed (one per round trip; a batch counts once)")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        STARTED.set(System.nanoTime());
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        Long started = STARTED.get();
        STARTED.remove();
        long elapsed = started == null ? TimeUnit.MILLISECONDS.toNanos(execInfo.getElapsedTime()) : System.nanoTime() - started;
        SqlStatementStats.record(elapsed);
        if (timer != null) {
            timer.record(elapsed, TimeUnit.NANOSECONDS);
        }
    }
}
