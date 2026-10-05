package com.ninsky.cronos.iam.audit.api;

import com.ninsky.cronos.iam.IamWebMvcTest;
import com.ninsky.cronos.domain.model.audit.AuditCategory;
import com.ninsky.cronos.iam.audit.query.AuditEventFilter;
import com.ninsky.cronos.iam.audit.query.AuditEventQueryService;
import com.ninsky.cronos.iam.audit.query.AuditEventView;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.ninsky.cronos.iam.IamWebFixtures.USER_ID;
import static com.ninsky.cronos.iam.IamWebFixtures.withPermissions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IamWebMvcTest(AuditEventController.class)
class AuditEventControllerWebTest {

    private static final String READ = "IAM.AUDIT.READ";
    private static final String EXPORT = "IAM.AUDIT.EXPORT";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuditEventQueryService audit;

    private static AuditEventView event() {
        return new AuditEventView("9007199254740993", Instant.parse("2026-10-05T15:00:00Z"), "SECURITY", "LOGIN_SUCCEEDED",
                "SUCCESS", "INFO", new UserRef(USER_ID, "admin", "Ana Admin", null),
                new AuditEventView.Target("USER", USER_ID.toString(), "admin"), "admin inició sesión", null, Map.of(),
                "10.0.0.1", "Chrome 129 / Windows", "trace-1");
    }

    @Test
    void searchRequiresAuditRead() throws Exception {
        mvc.perform(get("/iam/audit-events").with(withPermissions(EXPORT))).andExpect(status().isForbidden());
        verifyNoInteractions(audit);
    }

    @Test
    void exportRequiresAuditExport() throws Exception {
        mvc.perform(get("/iam/audit-events/export").with(withPermissions(READ))).andExpect(status().isForbidden());
        verifyNoInteractions(audit);
    }

    @Test
    void searchReturnsAPageWithStringIdsAndUtcTimes() throws Exception {
        when(audit.search(any(), any(), any(), any())).thenReturn(
                CatalogPage.of(List.of(event()), PageQuery.of(0, 20, null, Map.of("occurredAt", "x"), "occurredAt,desc"), 1));

        mvc.perform(get("/iam/audit-events").with(withPermissions(READ))
                        .param("categories", "SECURITY", "AUTHENTICATION").param("from", "2026-10-01T00:00:00Z").param("search", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.data.content[0].occurredAt").value("2026-10-05T15:00:00Z"))
                .andExpect(jsonPath("$.data.content[0].userAgent").value("Chrome 129 / Windows"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        ArgumentCaptor<AuditEventFilter> filter = ArgumentCaptor.forClass(AuditEventFilter.class);
        verify(audit).search(filter.capture(), eq(null), eq(null), any());
        assertThat(filter.getValue().categories()).containsExactly(AuditCategory.SECURITY, AuditCategory.AUTHENTICATION);
        assertThat(filter.getValue().from()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void unknownEnumValueIsAFieldError() throws Exception {
        mvc.perform(get("/iam/audit-events").with(withPermissions(READ)).param("outcomes", "MAYBE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("outcomes"));
        verifyNoInteractions(audit);
    }

    @Test
    void rangeErrorsAreReportedOnFrom() throws Exception {
        when(audit.search(any(), any(), any(), any()))
                .thenThrow(ApiException.invalid("from", "security.audit.rangeTooLong", 366L));
        mvc.perform(get("/iam/audit-events").with(withPermissions(READ)).header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .param("from", "2024-01-01T00:00:00Z").param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].message").value("The range cannot exceed 366 days"));
    }

    @Test
    void exportStreamsCsvAsAnAttachment() throws Exception {
        when(audit.export(any(), any())).thenReturn(new AuditEventQueryService.Export("bitacora-2026-10-05.csv",
                out -> out.write("﻿id\r\n1\r\n".getBytes(StandardCharsets.UTF_8))));

        MvcResult started = mvc.perform(get("/iam/audit-events/export").with(withPermissions(EXPORT)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("bitacora-2026-10-05.csv")))
                .andExpect(content().string(containsString("id\r\n1\r\n")));
    }

    @Test
    void exportRateLimitIs429() throws Exception {
        when(audit.export(any(), any())).thenThrow(ApiException.rateLimited(120));
        mvc.perform(get("/iam/audit-events/export").with(withPermissions(EXPORT)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "120"));
    }
}
