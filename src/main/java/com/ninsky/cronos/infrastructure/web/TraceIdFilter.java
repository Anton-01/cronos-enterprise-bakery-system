package com.ninsky.cronos.infrastructure.web;

import com.ninsky.cronos.eventing.application.TraceContext;
import com.ninsky.cronos.eventing.application.TraceContexts;
import com.ninsky.cronos.eventing.domain.TimeOrderedIds;
import com.ninsky.cronos.infrastructure.aop.ErrorMdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter in the chain: assigns (or propagates) the request's trace and correlation ids, binds them as the
 * thread's {@link TraceContext} (so every domain event published by the request carries them), puts them in MDC
 * so every log line carries them, echoes them on the response, and clears them afterwards so pool-reused threads
 * never leak stale ids into an unrelated request.
 * <p>
 * Trace id precedence: W3C {@code traceparent} → {@code X-Trace-Id} → a new 32-hex id.
 * Correlation id: {@code X-Correlation-Id} when it is a UUID, otherwise a new UUIDv7.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = TraceContexts.MDC_TRACE_ID;
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
    private static final Clock ID_CLOCK = Clock.systemUTC();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = traceIdOf(request);
        UUID correlationId = correlationIdOf(request.getHeader(CORRELATION_ID_HEADER));

        TraceContexts.bind(new TraceContext(correlationId, null, traceId, null, null));
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId.toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContexts.clear();
            MDC.remove(ErrorMdcKeys.ERROR_CATEGORY);
            MDC.remove(ErrorMdcKeys.ERROR_CODE);
            MDC.remove(ErrorMdcKeys.EXECUTION_TIME_MS);
            MDC.remove(ErrorMdcKeys.FAILED_METHOD);
        }
    }

    static String traceIdOf(HttpServletRequest request) {
        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        if (traceparent != null) {
            var matcher = TRACEPARENT.matcher(traceparent.trim());
            if (matcher.matches() && !matcher.group(1).chars().allMatch(c -> c == '0')) {
                return matcher.group(1);
            }
        }
        String incoming = request.getHeader(TRACE_ID_HEADER);
        return StringUtils.hasText(incoming) && SAFE_TRACE_ID.matcher(incoming.trim()).matches()
                ? incoming.trim() : TraceContexts.newTraceId();
    }

    static UUID correlationIdOf(String header) {
        if (StringUtils.hasText(header)) {
            try {
                return UUID.fromString(header.trim());
            } catch (IllegalArgumentException ignored) {
                // Not a UUID: start a new chain rather than trusting arbitrary text.
            }
        }
        return TimeOrderedIds.next(ID_CLOCK);
    }
}
