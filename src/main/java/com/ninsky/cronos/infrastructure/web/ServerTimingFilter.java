package com.ninsky.cronos.infrastructure.web;

import com.ninsky.cronos.infrastructure.config.security.SecurityConfig;
import com.ninsky.cronos.infrastructure.observability.SqlStatementStats;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * Adds {@code Server-Timing: <phase>;dur=…, db;dur=…;desc="n stmts", app;dur=…} to every response, so the browser
 * shows how much of a request was spent in the server (versus network, queueing or front-end rendering) and how many
 * database round trips it made. The header is written when the body starts (before commit), so {@code app} is the
 * time until the first byte. {@code db} appears when statement counting is on ({@code app.observability.sql.enabled});
 * each request's count also feeds the {@code cronos.http.db.statements} distribution (per method and route).
 * Requests slower than {@value #SLOW_MS} ms are logged with their phases.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ServerTimingFilter extends OncePerRequestFilter {

    public static final String HEADER = "Server-Timing";
    static final long SLOW_MS = 1_000;

    private final ObjectProvider<MeterRegistry> meterRegistry;
    private final boolean countStatements;

    public ServerTimingFilter(ObjectProvider<MeterRegistry> meterRegistry,
                              @Value("${app.observability.sql.enabled:false}") boolean countStatements) {
        this.meterRegistry = meterRegistry;
        this.countStatements = countStatements;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        SqlStatementStats sql = countStatements ? SqlStatementStats.open() : null;
        String origin = request.getHeader("Origin");
        if (origin != null && Arrays.asList(SecurityConfig.DEFAULT_ALLOWED_ORIGINS).contains(origin)) {
            // Lets the SPA read the entries through the Resource Timing API as well.
            response.setHeader("Timing-Allow-Origin", origin);
        }
        TimingResponse timed = new TimingResponse(response, request, start, sql);
        try {
            chain.doFilter(request, timed);
        } finally {
            timed.stamp();
            if (sql != null) {
                SqlStatementStats.close();
                recordStatements(request, sql);
            }
            long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            if (millis >= SLOW_MS) {
                log.info("Slow request {} {} took {} ms [{}]", request.getMethod(), request.getRequestURI(), millis,
                        ServerTiming.header(phases(request), sql, System.nanoTime() - start));
            }
        }
    }

    /** Statements per request, by method and route template (never the raw URI: unbounded cardinality). */
    private void recordStatements(HttpServletRequest request, SqlStatementStats sql) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return;
        }
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        DistributionSummary.builder("cronos.http.db.statements")
                .description("JDBC statements executed per HTTP request")
                .tag("method", request.getMethod())
                .tag("uri", route == null ? "UNKNOWN" : route.toString())
                .publishPercentileHistogram()
                .register(registry)
                .record(sql.statements());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Long> phases(HttpServletRequest request) {
        return (Map<String, Long>) request.getAttribute(ServerTiming.ATTRIBUTE);
    }

    /** Sets the header once, right before anything can commit the response. */
    static final class TimingResponse extends HttpServletResponseWrapper {

        private final HttpServletRequest request;
        private final long start;
        private final SqlStatementStats sql;
        private boolean stamped;

        TimingResponse(HttpServletResponse response, HttpServletRequest request, long start, SqlStatementStats sql) {
            super(response);
            this.request = request;
            this.start = start;
            this.sql = sql;
        }

        void stamp() {
            if (!stamped && !isCommitted()) {
                stamped = true;
                setHeader(HEADER, ServerTiming.header(phases(request), sql, System.nanoTime() - start));
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            stamp();
            return super.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            stamp();
            return super.getWriter();
        }

        @Override
        public void flushBuffer() throws IOException {
            stamp();
            super.flushBuffer();
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            stamp();
            super.sendError(sc, msg);
        }

        @Override
        public void sendError(int sc) throws IOException {
            stamp();
            super.sendError(sc);
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            stamp();
            super.sendRedirect(location);
        }
    }
}
