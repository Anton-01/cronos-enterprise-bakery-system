package com.ninsky.cronos.infrastructure.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerTimingFilterTest {

    private final ServerTimingFilter filter = new ServerTimingFilter();

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
    void measuringOutsideARequestJustRunsTheWork() {
        assertThat(ServerTiming.measure("x", () -> 42)).isEqualTo(42);
    }
}
