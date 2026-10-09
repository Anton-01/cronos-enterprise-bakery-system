package com.ninsky.cronos.infrastructure.web;

import com.ninsky.cronos.infrastructure.config.security.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * Adds {@code Server-Timing: <phase>;dur=…, app;dur=…} to every response, so the browser shows how much of
 * a request was spent in the server (versus network, queueing or front-end rendering). The header is
 * written when the body starts (before commit), so {@code app} is the time until the first byte.
 * Requests slower than {@value #SLOW_MS} ms are logged with their phases.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ServerTimingFilter extends OncePerRequestFilter {

    public static final String HEADER = "Server-Timing";
    static final long SLOW_MS = 1_000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String origin = request.getHeader("Origin");
        if (origin != null && Arrays.asList(SecurityConfig.DEFAULT_ALLOWED_ORIGINS).contains(origin)) {
            // Lets the SPA read the entries through the Resource Timing API as well.
            response.setHeader("Timing-Allow-Origin", origin);
        }
        TimingResponse timed = new TimingResponse(response, request, start);
        try {
            chain.doFilter(request, timed);
        } finally {
            timed.stamp();
            long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            if (millis >= SLOW_MS) {
                log.info("Slow request {} {} took {} ms [{}]", request.getMethod(), request.getRequestURI(), millis,
                        ServerTiming.header(phases(request), System.nanoTime() - start));
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Long> phases(HttpServletRequest request) {
        return (Map<String, Long>) request.getAttribute(ServerTiming.ATTRIBUTE);
    }

    /** Sets the header once, right before anything can commit the response. */
    static final class TimingResponse extends HttpServletResponseWrapper {

        private final HttpServletRequest request;
        private final long start;
        private boolean stamped;

        TimingResponse(HttpServletResponse response, HttpServletRequest request, long start) {
            super(response);
            this.request = request;
            this.start = start;
        }

        void stamp() {
            if (!stamped && !isCommitted()) {
                stamped = true;
                setHeader(HEADER, ServerTiming.header(phases(request), System.nanoTime() - start));
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
