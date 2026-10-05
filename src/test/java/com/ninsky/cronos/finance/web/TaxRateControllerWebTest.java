package com.ninsky.cronos.finance.web;

import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.taxrate.TaxRateController;
import com.ninsky.cronos.finance.taxrate.TaxRateOption;
import com.ninsky.cronos.finance.taxrate.TaxRateRequest;
import com.ninsky.cronos.finance.taxrate.TaxRateResponse;
import com.ninsky.cronos.finance.taxrate.TaxRateService;
import com.ninsky.cronos.iam.permission.Permissions;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static com.ninsky.cronos.finance.web.FinanceWebMvcTestConfig.withPermissions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@FinanceWebMvcTest(TaxRateController.class)
class TaxRateControllerWebTest {

    private static final String VALID = """
            {"code":"IVA_16","name":"IVA general 16%","factorType":"TASA","ratePercent":16,"validFrom":"2010-01-01"}""";
    private static final TaxRateResponse IVA_16 = new TaxRateResponse(1L, "IVA_16", "IVA general 16%", "Tasa general nacional", "002",
            TaxFactorType.TASA, new BigDecimal("16.0000"), LocalDate.of(2010, 1, 1), null, true, true, FinanceStatus.ACTIVE,
            Instant.parse("2026-10-04T18:00:00Z"), null, null, 1L);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaxRateService service;

    static Stream<Arguments> guardedEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/finance/tax-rates", null, Permissions.FINANCE_TAX_RATE_READ),
                Arguments.of(HttpMethod.POST, "/finance/tax-rates", VALID, Permissions.FINANCE_TAX_RATE_MANAGE),
                Arguments.of(HttpMethod.PUT, "/finance/tax-rates/1", VALID, Permissions.FINANCE_TAX_RATE_MANAGE),
                Arguments.of(HttpMethod.PATCH, "/finance/tax-rates/1/status", "{\"status\":\"INACTIVE\",\"version\":1}",
                        Permissions.FINANCE_TAX_RATE_MANAGE),
                Arguments.of(HttpMethod.PATCH, "/finance/tax-rates/1/default", "{\"version\":1}", Permissions.FINANCE_SETTINGS_UPDATE),
                Arguments.of(HttpMethod.DELETE, "/finance/tax-rates/1", null, Permissions.FINANCE_TAX_RATE_MANAGE));
    }

    @ParameterizedTest(name = "{0} {1} needs {3}")
    @MethodSource("guardedEndpoints")
    void everyEndpointIsGuardedByItsPermission(HttpMethod method, String path, String body, String permission) throws Exception {
        String[] others = Stream.of(Permissions.FINANCE_CURRENCY_READ, Permissions.FINANCE_CURRENCY_MANAGE,
                Permissions.FINANCE_TAX_RATE_READ, Permissions.FINANCE_TAX_RATE_MANAGE, Permissions.FINANCE_SETTINGS_UPDATE)
                .filter(code -> !code.equals(permission)).toArray(String[]::new);
        var request = request(method, path).with(withPermissions(others)).contentType(MediaType.APPLICATION_JSON);

        mvc.perform(body == null ? request : request.content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED"));
        verifyNoInteractions(service);
    }

    @Test
    void catalogIsOpenToAnyAuthenticatedUser() throws Exception {
        when(service.catalog()).thenReturn(List.of(
                new TaxRateOption(1L, "IVA_16", "IVA general 16%", TaxFactorType.TASA, new BigDecimal("16.0000"), true),
                new TaxRateOption(4L, "IVA_EXENTO", "Exento de IVA", TaxFactorType.EXENTO, null, false)));

        mvc.perform(get("/finance/tax-rates/catalog").with(withPermissions()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[0].ratePercent").value(16.0))
                .andExpect(jsonPath("$.data[1].factorType").value("EXENTO"))
                .andExpect(jsonPath("$.data[1].ratePercent").doesNotExist());
    }

    @Test
    void listUsesTheSpecShape() throws Exception {
        when(service.page(any(), any(), any(), any(), any()))
                .thenReturn(new com.ninsky.cronos.infrastructure.web.paging.CatalogPage<>(List.of(IVA_16), 0, 10, 1, 1, true));

        mvc.perform(get("/finance/tax-rates").with(withPermissions(Permissions.FINANCE_TAX_RATE_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].satTaxCode").value("002"))
                .andExpect(jsonPath("$.data.content[0].validFrom").value("2010-01-01"))
                .andExpect(jsonPath("$.data.content[0].validTo").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].inUse").value(true))
                .andExpect(jsonPath("$.data.content[0].isDefault").value(true));
    }

    @Test
    void createIgnoresSatTaxCodeAndReturns201() throws Exception {
        when(service.create(any())).thenReturn(IVA_16);

        mvc.perform(post("/finance/tax-rates").with(withPermissions(Permissions.FINANCE_TAX_RATE_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("{", "{\"satTaxCode\":\"003\",")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/finance/tax-rates/1")))
                .andExpect(jsonPath("$.data.satTaxCode").value("002"));

        ArgumentCaptor<TaxRateRequest> captor = ArgumentCaptor.forClass(TaxRateRequest.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().ratePercent()).isEqualByComparingTo("16");
    }

    @Test
    void satRulesAndFieldErrorsComeBackTogether() throws Exception {
        String body = """
                {"code":"iva","name":"","factorType":"EXENTO","ratePercent":0,"validFrom":"2026-02-01","validTo":"2026-01-31"}""";

        mvc.perform(post("/finance/tax-rates").with(withPermissions(Permissions.FINANCE_TAX_RATE_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("code", "name", "ratePercent", "validTo")))
                .andExpect(jsonPath("$.errors[?(@.field == 'ratePercent')].message").value("Un tipo exento no lleva tasa; deja el campo vacío"))
                .andExpect(jsonPath("$.errors[?(@.field == 'validTo')].message")
                        .value("La fecha final no puede ser anterior a la fecha inicial"));
        verifyNoInteractions(service);
    }

    @Test
    void tasaNeedsARateWithinRange() throws Exception {
        mvc.perform(post("/finance/tax-rates").with(withPermissions(Permissions.FINANCE_TAX_RATE_MANAGE))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("\"ratePercent\":16", "\"ratePercent\":100.00001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ratePercent"))
                .andExpect(jsonPath("$.errors[0].message").value("The rate must be between 0 and 100 with at most 4 decimals"));
        mvc.perform(post("/finance/tax-rates").with(withPermissions(Permissions.FINANCE_TAX_RATE_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace(",\"ratePercent\":16", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ratePercent"));
    }

    @Test
    void malformedDateIsReportedOnItsField() throws Exception {
        mvc.perform(post("/finance/tax-rates").with(withPermissions(Permissions.FINANCE_TAX_RATE_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("2010-01-01", "01/01/2010")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("validFrom"));
    }

    @Test
    void inUseImmutabilityIsAConflictPerField() throws Exception {
        when(service.update(anyLong(), any())).thenThrow(new ApiException(List.of(
                new ApiException.Violation(ApiErrorCode.RESOURCE_IN_USE, "ratePercent", "finance.taxRate.immutableInUse", List.of()),
                new ApiException.Violation(ApiErrorCode.RESOURCE_IN_USE, "validFrom", "finance.taxRate.immutableInUse", List.of())), null));

        mvc.perform(put("/finance/tax-rates/1").with(withPermissions(Permissions.FINANCE_TAX_RATE_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("}", ",\"version\":1}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[*].code").value(containsInAnyOrder("RESOURCE_IN_USE", "RESOURCE_IN_USE")))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("ratePercent", "validFrom")));
    }

    @Test
    void defaultNotSelectableIsAConflict() throws Exception {
        when(service.makeDefault(anyLong(), any())).thenThrow(ApiException.of(ApiErrorCode.INVALID_STATE_TRANSITION, null,
                "finance.taxRate.default.notSelectable"));

        mvc.perform(request(HttpMethod.PATCH, "/finance/tax-rates/2/default").with(withPermissions(Permissions.FINANCE_SETTINGS_UPDATE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.errors[0].field").doesNotExist());
    }
}
