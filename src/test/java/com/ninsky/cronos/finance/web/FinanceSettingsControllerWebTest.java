package com.ninsky.cronos.finance.web;

import com.ninsky.cronos.finance.currency.CurrencyOption;
import com.ninsky.cronos.finance.currency.SymbolPosition;
import com.ninsky.cronos.finance.pricing.FinanceRoundingMode;
import com.ninsky.cronos.finance.pricing.TaxFactorType;
import com.ninsky.cronos.finance.settings.FinanceSettingsController;
import com.ninsky.cronos.finance.settings.FinanceSettingsRequest;
import com.ninsky.cronos.finance.settings.FinanceSettingsResponse;
import com.ninsky.cronos.finance.settings.FinanceSettingsService;
import com.ninsky.cronos.finance.taxrate.TaxRateOption;
import com.ninsky.cronos.iam.permission.Permissions;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static com.ninsky.cronos.finance.web.FinanceWebMvcTestConfig.withPermissions;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@FinanceWebMvcTest(FinanceSettingsController.class)
class FinanceSettingsControllerWebTest {

    private static final FinanceSettingsResponse SETTINGS = new FinanceSettingsResponse(
            new CurrencyOption(1L, "MXN", "Peso mexicano", "$", 2, SymbolPosition.BEFORE, true),
            new TaxRateOption(1L, "IVA_16", "IVA general 16%", TaxFactorType.TASA, new BigDecimal("16.0000"), true),
            false, FinanceRoundingMode.HALF_UP, null, null, 1L);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FinanceSettingsService service;

    @Test
    void anyAuthenticatedUserReadsTheSettings() throws Exception {
        when(service.current()).thenReturn(SETTINGS);

        mvc.perform(get("/finance/settings").with(withPermissions()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultCurrency.code").value("MXN"))
                .andExpect(jsonPath("$.data.defaultCurrency.isDefault").value(true))
                .andExpect(jsonPath("$.data.defaultTaxRate.ratePercent").value(16.0))
                .andExpect(jsonPath("$.data.pricesIncludeTax").value(false))
                .andExpect(jsonPath("$.data.roundingMode").value("HALF_UP"))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(get("/finance/settings")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateNeedsTheSettingsPermission() throws Exception {
        mvc.perform(put("/finance/settings").with(withPermissions(Permissions.FINANCE_TAX_RATE_MANAGE, Permissions.FINANCE_CURRENCY_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pricesIncludeTax\":true,\"roundingMode\":\"HALF_EVEN\",\"version\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED"));
        verifyNoInteractions(service);
    }

    @Test
    void updateReturnsTheNewSettings() throws Exception {
        when(service.update(any())).thenReturn(SETTINGS);

        mvc.perform(put("/finance/settings").with(withPermissions(Permissions.FINANCE_SETTINGS_UPDATE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pricesIncludeTax\":true,\"roundingMode\":\"HALF_EVEN\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reglas de cálculo actualizadas"));
        verify(service).update(new FinanceSettingsRequest(true, FinanceRoundingMode.HALF_EVEN, 1L));
    }

    @Test
    void everyFieldIsRequired() throws Exception {
        mvc.perform(put("/finance/settings").with(withPermissions(Permissions.FINANCE_SETTINGS_UPDATE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("pricesIncludeTax", "roundingMode", "version")));
        verifyNoInteractions(service);
    }

    @Test
    void unknownRoundingModeIsAFieldError() throws Exception {
        mvc.perform(put("/finance/settings").with(withPermissions(Permissions.FINANCE_SETTINGS_UPDATE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pricesIncludeTax\":true,\"roundingMode\":\"CEILING\",\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("roundingMode"));
    }

    @Test
    void staleVersionIsAConflict() throws Exception {
        when(service.update(any())).thenThrow(ApiException.concurrentModification());

        mvc.perform(put("/finance/settings").with(withPermissions(Permissions.FINANCE_SETTINGS_UPDATE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pricesIncludeTax\":true,\"roundingMode\":\"UP\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.errors[0].field").value("version"));
    }
}
