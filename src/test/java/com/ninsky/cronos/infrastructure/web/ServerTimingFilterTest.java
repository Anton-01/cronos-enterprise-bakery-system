package com.ninsky.cronos.infrastructure.web;

import com.ninsky.cronos.infrastructure.observability.SqlStatementListener;
import com.ninsky.cronos.infrastructure.observability.SqlStatementStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerTimingFilterTest {

    private final ServerTimingFilter filter = new ServerTimingFilter(new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class), false);

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void headerListsPhasesThenTheTotal() {
        Map<String, Long> phases = new LinkedHashMap<>();
        phases.put("password", 288_800_000L);
        phases.put("session", 17_500_000L);

        assertThat(ServerTiming.header(phases, 340_500_000L)).isEqualTo("password;dur=288.8, session;dur=17.5, app;dur=340.5");
        assertThat(ServerTiming.header(null, 1_000_000L)).isEqualTo("app;dur=1.0");
    }

    @Test
    void stampsTheHeaderBeforeTheBodyCommitsAndIncludesRecordedPhases() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("Origin", "http://localhost:4200");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            ServerTiming.measure("password", () -> { });
            res.getWriter().write("{}");
            res.flushBuffer();
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(ServerTimingFilter.HEADER)).matches("password;dur=\\d+\\.\\d, app;dur=\\d+\\.\\d");
        assertThat(response.getHeader("Timing-Allow-Origin")).isEqualTo("http://localhost:4200");
    }

    @Test
    void unknownOriginsGetNoTimingAllowOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/recipes");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("Timing-Allow-Origin")).isNull();
        assertThat(response.getHeader(ServerTimingFilter.HEADER)).startsWith("app;dur=");
    }

    @Test
    void reportsDatabaseTimeAndStatementCountWhenCountingIsOn() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("registry", registry);
        ServerTimingFilter counting = new ServerTimingFilter(beans.getBeanProvider(MeterRegistry.class), true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/recipes");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/recipes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SqlStatementListener listener = new SqlStatementListener(registry);

        counting.doFilter(request, response, (req, res) -> {
            listener.beforeQuery(null, List.of());
            listener.afterQuery(null, List.of());
            listener.beforeQuery(null, List.of());
            listener.afterQuery(null, List.of());
            res.getWriter().write("{}");
        });

        assertThat(response.getHeader(ServerTimingFilter.HEADER)).matches("db;dur=\\d+\\.\\d;desc=\"2 stmts\", app;dur=\\d+\\.\\d");
        assertThat(registry.get("cronos.http.db.statements").tag("uri", "/recipes").summary().totalAmount()).isEqualTo(2.0);
        assertThat(SqlStatementStats.current()).isEmpty();
    }

    @Test
    void measuringOutsideARequestJustRunsTheWork() {
        assertThat(ServerTiming.measure("x", () -> 42)).isEqualTo(42);
    }
}
