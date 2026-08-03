package com.ninsky.cronos.infrastructure.web;

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
import java.util.UUID;

/**
 * First filter in the chain: assigns (or propagates) a request traceId, puts it in MDC so every
 * log line for this request carries it, echoes it on the response, and clears it afterward so
 * thread-pool-reused threads never leak a stale traceId into an unrelated request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove(ErrorMdcKeys.ERROR_CATEGORY);
            MDC.remove(ErrorMdcKeys.ERROR_CODE);
            MDC.remove(ErrorMdcKeys.EXECUTION_TIME_MS);
            MDC.remove(ErrorMdcKeys.FAILED_METHOD);
        }
    }
}
