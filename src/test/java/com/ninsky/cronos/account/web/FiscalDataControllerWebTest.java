package com.ninsky.cronos.account.web;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.fiscal.api.FiscalDataController;
import com.ninsky.cronos.account.fiscal.application.GetMyFiscalDataUseCase;
import com.ninsky.cronos.account.fiscal.application.UpsertMyFiscalDataUseCase;
import com.ninsky.cronos.account.fiscal.domain.UpsertFiscalDataCommand;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AccountWebMvcTest(FiscalDataController.class)
class FiscalDataControllerWebTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String VALID_BODY = """
            {"legalName": "Pasteleria Cronos", "taxId": "GODE561231GR8", "taxRegime": "626",
             "address": {"street": "Av. Reforma", "exteriorNumber": "222", "interiorNumber": null, "neighborhood": "Juárez",
                         "municipality": "Cuauhtémoc", "state": "CMX", "zipCode": "06600", "country": "MEX"}}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetMyFiscalDataUseCase getMyFiscalDataUseCase;
    @MockitoBean
    private UpsertMyFiscalDataUseCase upsertMyFiscalDataUseCase;
    @MockitoBean
    private AvatarStorage avatarStorage;

    @BeforeEach
    void setUp() {
        when(upsertMyFiscalDataUseCase.execute(any())).thenAnswer(inv ->
                inv.getArgument(0, UpsertFiscalDataCommand.class).toFiscalData(USER_ID, 0L, LocalDateTime.of(2026, 9, 26, 18, 0)));
    }

    @Test
    void notRegisteredIs200WithExplicitNullDataAndNoStore() throws Exception {
        when(getMyFiscalDataUseCase.execute()).thenReturn(Optional.empty());

        mvc.perform(get("/users/me/fiscal").with(user("u")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    void upsertReturnsDerivedTaxpayerTypeCodesAndNullInteriorNumber() throws Exception {
        mvc.perform(putJson(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.data.legalName").value("PASTELERIA CRONOS"))
                .andExpect(jsonPath("$.data.taxId").value("GODE561231GR8"))
                .andExpect(jsonPath("$.data.taxRegime").value("626"))
                .andExpect(jsonPath("$.data.taxpayerType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.data.address.state").value("CMX"))
                .andExpect(jsonPath("$.data.address.zipCode").value("06600"))
                .andExpect(jsonPath("$.data.address.interiorNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.address.country").value("MEX"));
    }

    @Test
    void ifMatchBecomesTheExpectedVersion() throws Exception {
        mvc.perform(putJson(VALID_BODY).header(HttpHeaders.IF_MATCH, "\"3\"")).andExpect(status().isOk());

        ArgumentCaptor<UpsertFiscalDataCommand> command = ArgumentCaptor.forClass(UpsertFiscalDataCommand.class);
        verify(upsertMyFiscalDataUseCase).execute(command.capture());
        assertThat(command.getValue().expectedVersion()).isEqualTo(ExpectedVersion.of(3));
    }

    @Test
    void everyViolationIsReportedAtOnceWithRequestBodyJsonPaths() throws Exception {
        mvc.perform(putJson("""
                        {"legalName": "Pasteleria Cronos, S.A. de C.V.", "taxId": "GODE561231GR9", "taxRegime": "999",
                         "address": {"street": "", "exteriorNumber": "222", "interiorNumber": null, "neighborhood": "Juárez",
                                     "municipality": "Cuauhtémoc", "state": "DIF", "zipCode": "6600", "country": "USA"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder(
                        "legalName", "taxId", "taxRegime", "address.street", "address.state", "address.zipCode", "address.country")))
                .andExpect(jsonPath("$.errors[?(@.field == 'taxId')].message").value("The RFC check digit is invalid"));
        verify(upsertMyFiscalDataUseCase, never()).execute(any());
    }

    @Test
    void regimeNotApplicableToTheRfcIsReportedOnTaxRegime() throws Exception {
        mvc.perform(putJson(VALID_BODY.replace("\"626\"", "\"601\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("taxRegime"))
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_FIELD_ERROR"));
    }

    @Test
    void genericRfcIsRejected() throws Exception {
        mvc.perform(putJson(VALID_BODY.replace("GODE561231GR8", "XAXX010101000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("taxId"));
    }

    @Test
    void taxpayerTypeMustNotBeSent() throws Exception {
        mvc.perform(putJson(VALID_BODY.replace("\"taxRegime\": \"626\",", "\"taxRegime\": \"626\", \"taxpayerType\": \"LEGAL_ENTITY\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("taxpayerType"));
    }

    @Test
    void spanishMessagesForEsMx() throws Exception {
        mvc.perform(putJson(VALID_BODY.replace("06600", "6600"), "es-MX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Error de Validación"))
                .andExpect(jsonPath("$.errors[0].field").value("address.zipCode"))
                .andExpect(jsonPath("$.errors[0].message").value("El código postal debe tener 5 dígitos (p. ej. 06600)"));
    }

    private MockHttpServletRequestBuilder putJson(String body) {
        return putJson(body, "en");
    }

    private MockHttpServletRequestBuilder putJson(String body, String language) {
        return put("/users/me/fiscal").with(user("u")).header(HttpHeaders.ACCEPT_LANGUAGE, language)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
