package com.ninsky.cronos.finance.web;

import com.ninsky.cronos.finance.currency.CurrencyController;
import com.ninsky.cronos.finance.currency.CurrencyOption;
import com.ninsky.cronos.finance.currency.CurrencyResponse;
import com.ninsky.cronos.finance.currency.CurrencyService;
import com.ninsky.cronos.finance.currency.SymbolPosition;
import com.ninsky.cronos.finance.shared.FinanceStatus;
import com.ninsky.cronos.finance.shared.UserRef;
import com.ninsky.cronos.iam.permission.Permissions;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.ninsky.cronos.finance.web.FinanceWebMvcTestConfig.withPermissions;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

@FinanceWebMvcTest(CurrencyController.class)
class CurrencyControllerWebTest {

    private static final String VALID = """
            {"code":"JPY","numericCode":"392","name":"Yen japonés","symbol":"¥","decimalPlaces":0,"symbolPosition":"BEFORE"}""";
    private static final CurrencyResponse JPY = new CurrencyResponse(7L, "JPY", "392", "Yen japonés", "¥", 0, SymbolPosition.BEFORE,
            false, false, FinanceStatus.ACTIVE, Instant.parse("2026-10-05T18:00:00Z"), null,
            new UserRef(UUID.randomUUID(), "aortiz", "Antonio Ortiz", null), 0L);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CurrencyService service;

    static Stream<Arguments> guardedEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/finance/currencies", null, Permissions.FINANCE_CURRENCY_READ),
                Arguments.of(HttpMethod.POST, "/finance/currencies", VALID, Permissions.FINANCE_CURRENCY_MANAGE),
                Arguments.of(HttpMethod.PUT, "/finance/currencies/1", VALID, Permissions.FINANCE_CURRENCY_MANAGE),
                Arguments.of(HttpMethod.PATCH, "/finance/currencies/1/status", "{\"status\":\"INACTIVE\",\"version\":1}",
                        Permissions.FINANCE_CURRENCY_MANAGE),
                Arguments.of(HttpMethod.PATCH, "/finance/currencies/1/default", "{\"version\":1}", Permissions.FINANCE_SETTINGS_UPDATE),
                Arguments.of(HttpMethod.DELETE, "/finance/currencies/1", null, Permissions.FINANCE_CURRENCY_MANAGE));
    }

    @ParameterizedTest(name = "{0} {1} needs {3}")
    @MethodSource("guardedEndpoints")
    void everyEndpointIsGuardedByItsPermission(HttpMethod method, String path, String body, String permission) throws Exception {
        // Holding every other finance permission is not enough.
        String[] others = Stream.of(Permissions.FINANCE_CURRENCY_READ, Permissions.FINANCE_CURRENCY_MANAGE,
                Permissions.FINANCE_TAX_RATE_READ, Permissions.FINANCE_TAX_RATE_MANAGE, Permissions.FINANCE_SETTINGS_UPDATE)
                .filter(code -> !code.equals(permission)).toArray(String[]::new);
        var request = request(method, path).with(withPermissions(others)).contentType(MediaType.APPLICATION_JSON);

        mvc.perform(body == null ? request : request.content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED"));
        mvc.perform(request(method, path).contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void catalogIsOpenToAnyAuthenticatedUser() throws Exception {
        when(service.catalog()).thenReturn(List.of(new CurrencyOption(1L, "MXN", "Peso mexicano", "$", 2, SymbolPosition.BEFORE, true)));

        mvc.perform(get("/finance/currencies/catalog").with(withPermissions()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("MXN"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[0].default").doesNotExist());
        mvc.perform(get("/finance/currencies/catalog")).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsACatalogPage() throws Exception {
        when(service.page(eq("yen"), eq(FinanceStatus.ACTIVE), eq(0), eq(20), eq("name,desc")))
                .thenReturn(new CatalogPage<>(List.of(JPY), 0, 20, 1, 1, true));

        mvc.perform(get("/finance/currencies?page=0&size=20&sort=name,desc&search=yen&status=ACTIVE")
                        .with(withPermissions(Permissions.FINANCE_CURRENCY_READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code").value("JPY"))
                .andExpect(jsonPath("$.data.content[0].isDefault").value(false))
                .andExpect(jsonPath("$.data.content[0].inUse").value(false))
                .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-10-05T18:00:00Z"))
                .andExpect(jsonPath("$.data.content[0].updatedBy.displayName").value("Antonio Ortiz"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void unknownSortFieldIsAFieldError() throws Exception {
        when(service.page(any(), any(), any(), any(), eq("symbol,asc"))).thenThrow(ApiException.invalid("sort", "api.validation.sortField", "symbol"));

        mvc.perform(get("/finance/currencies?sort=symbol,asc").with(withPermissions(Permissions.FINANCE_CURRENCY_READ))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void invalidStatusFilterIsAFieldError() throws Exception {
        mvc.perform(get("/finance/currencies?status=DELETED").with(withPermissions(Permissions.FINANCE_CURRENCY_READ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(service.create(any())).thenReturn(JPY);

        mvc.perform(post("/finance/currencies").with(withPermissions(Permissions.FINANCE_CURRENCY_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/finance/currencies/7")))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Moneda JPY creada"))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.numericCode").value("392"));
    }

    @Test
    void everyFieldErrorIsReturnedAtOnce() throws Exception {
        String body = """
                {"code":"mx","numericCode":"","name":"%s","symbol":"TOOLONG","decimalPlaces":7}""".formatted("x".repeat(61));

        mvc.perform(post("/finance/currencies").with(withPermissions(Permissions.FINANCE_CURRENCY_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La solicitud contiene errores de validación"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("code", "numericCode", "name", "symbol", "decimalPlaces", "symbolPosition")))
                .andExpect(jsonPath("$.errors[*].code").value(containsInAnyOrder(
                        "VALIDATION_ERROR", "VALIDATION_ERROR", "VALIDATION_ERROR", "VALIDATION_ERROR", "VALIDATION_ERROR", "VALIDATION_ERROR")))
                .andExpect(jsonPath("$.errors[?(@.field == 'numericCode')].message").value("Este campo es obligatorio"));
        verifyNoInteractions(service);
    }

    @Test
    void isoRulesAreReportedOnTheirFieldsInTheCallersLanguage() throws Exception {
        mvc.perform(post("/finance/currencies").with(withPermissions(Permissions.FINANCE_CURRENCY_MANAGE))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"MXN","numericCode":"840","name":"Peso","symbol":"$","decimalPlaces":2,"symbolPosition":"BEFORE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("numericCode"))
                .andExpect(jsonPath("$.errors[0].message").value("The numeric code does not match the currency ISO code"));

        mvc.perform(post("/finance/currencies").with(withPermissions(Permissions.FINANCE_CURRENCY_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ABC","numericCode":"999","name":"Nope","symbol":"$","decimalPlaces":2,"symbolPosition":"BEFORE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].message").value("El código no corresponde a una moneda ISO 4217"));
        verifyNoInteractions(service);
    }

    @Test
    void invalidEnumIsReportedOnItsField() throws Exception {
        mvc.perform(post("/finance/currencies").with(withPermissions(Permissions.FINANCE_CURRENCY_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("BEFORE", "MIDDLE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("symbolPosition"));
    }

    @Test
    void businessConflictsKeepTheirCodeAndField() throws Exception {
        when(service.update(anyLong(), any())).thenThrow(ApiException.of(ApiErrorCode.DUPLICATE_RESOURCE, "name",
                "finance.currency.name.duplicate"));

        mvc.perform(put("/finance/currencies/1").with(withPermissions(Permissions.FINANCE_CURRENCY_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Ya existe una moneda con ese nombre"));
    }

    @Test
    void staleVersionIsAConflictOnVersion() throws Exception {
        when(service.makeDefault(eq(2L), any())).thenThrow(ApiException.concurrentModification());

        mvc.perform(request(HttpMethod.PATCH, "/finance/currencies/2/default").with(withPermissions(Permissions.FINANCE_SETTINGS_UPDATE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.errors[0].field").value("version"));
    }

    @Test
    void defaultLockedIsAConflict() throws Exception {
        when(service.changeStatus(eq(1L), any())).thenThrow(ApiException.of(ApiErrorCode.DEFAULT_LOCKED, "status",
                "finance.currency.defaultLocked.deactivate"));

        mvc.perform(request(HttpMethod.PATCH, "/finance/currencies/1/status").with(withPermissions(Permissions.FINANCE_CURRENCY_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\",\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("DEFAULT_LOCKED"));
    }

    @Test
    void statusAndDefaultBodiesRequireVersion() throws Exception {
        mvc.perform(request(HttpMethod.PATCH, "/finance/currencies/1/status").with(withPermissions(Permissions.FINANCE_CURRENCY_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("status", "version")));
        mvc.perform(request(HttpMethod.PATCH, "/finance/currencies/1/default").with(withPermissions(Permissions.FINANCE_SETTINGS_UPDATE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        verifyNoInteractions(service);
    }

    @Test
    void deleteReturnsTheEnvelopeWithNullData() throws Exception {
        mvc.perform(request(HttpMethod.DELETE, "/finance/currencies/3").with(withPermissions(Permissions.FINANCE_CURRENCY_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(service).delete(3L);
    }
}
