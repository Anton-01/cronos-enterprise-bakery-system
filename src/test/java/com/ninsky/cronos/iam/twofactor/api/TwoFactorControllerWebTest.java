package com.ninsky.cronos.iam.twofactor.api;

import com.ninsky.cronos.iam.IamWebMvcTest;
import com.ninsky.cronos.iam.shared.SecurityActorProvider;
import com.ninsky.cronos.iam.twofactor.TwoFactorAccountService;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ninsky.cronos.iam.IamWebFixtures.USER_ID;
import static com.ninsky.cronos.iam.IamWebFixtures.withPermissions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IamWebMvcTest(TwoFactorController.class)
@Import(SecurityActorProvider.class)
class TwoFactorControllerWebTest {

    private static final TwoFactorStatus OFF = new TwoFactorStatus(false, true, List.of("Administrador"), null, null, 0);
    private static final TwoFactorStatus ON = new TwoFactorStatus(true, true, List.of("Administrador"), "TOTP",
            Instant.parse("2026-10-05T19:15:00Z"), 10);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TwoFactorAccountService service;

    @Test
    void everyEndpointNeedsAuthentication() throws Exception {
        mvc.perform(get("/users/me/two-factor")).andExpect(status().isUnauthorized());
        mvc.perform(post("/users/me/two-factor/enrollment")).andExpect(status().isUnauthorized());
        mvc.perform(post("/users/me/two-factor/enrollment/confirm").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/users/me/two-factor/disable").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/users/me/two-factor/recovery-codes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void statusNeedsNoPermissionAndIsNeverCached() throws Exception {
        when(service.status(USER_ID)).thenReturn(OFF);
        mvc.perform(get("/users/me/two-factor").with(withPermissions()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.meta.traceId").exists())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.required").value(true))
                .andExpect(jsonPath("$.data.requiredBy[0]").value("Administrador"))
                .andExpect(jsonPath("$.data.recoveryCodesRemaining").value(0));
    }

    @Test
    void enrolmentReturnsTheContractShape() throws Exception {
        UUID id = UUID.fromString("6c1d0e2f-3a4b-4c5d-8e9f-0a1b2c3d4e5f");
        when(service.startEnrollment(USER_ID)).thenReturn(new TwoFactorEnrollment(id, "JBSWY3DPEHPK3PXP", "otpauth://totp/x",
                "data:image/png;base64,iVBOR", "Cronos", "admin@cronos.com", 6, 30, Instant.parse("2026-10-05T19:24:00Z")));
        mvc.perform(post("/users/me/two-factor/enrollment").with(withPermissions()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.enrollmentId").value(id.toString()))
                .andExpect(jsonPath("$.data.qrCodeDataUri").value("data:image/png;base64,iVBOR"))
                .andExpect(jsonPath("$.data.digits").value(6))
                .andExpect(jsonPath("$.data.periodSeconds").value(30))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-10-05T19:24:00Z"));
    }

    @Test
    void confirmReturnsCodesOnceWithoutCaching() throws Exception {
        when(service.confirm(eq(USER_ID), any())).thenReturn(new TwoFactorRecoveryCodes(List.of("7KQ4-M2XD"), ON));
        mvc.perform(post("/users/me/two-factor/enrollment/confirm").with(withPermissions())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"6c1d0e2f-3a4b-4c5d-8e9f-0a1b2c3d4e5f\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.recoveryCodes[0]").value("7KQ4-M2XD"))
                .andExpect(jsonPath("$.data.status.method").value("TOTP"))
                .andExpect(jsonPath("$.data.status.enrolledAt").value("2026-10-05T19:15:00Z"));
    }

    @Test
    void wrongCodeUsesTheErrorEnvelopeWithField() throws Exception {
        when(service.confirm(eq(USER_ID), any()))
                .thenThrow(ApiException.of(ApiErrorCode.INVALID_TOTP_CODE, "code", "security.twoFactor.invalidTotp"));
        mvc.perform(post("/users/me/two-factor/enrollment/confirm").with(withPermissions())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"6c1d0e2f-3a4b-4c5d-8e9f-0a1b2c3d4e5f\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TOTP_CODE"))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].message").value("The verification code is invalid"));
    }

    @Test
    void mandatoryTwoFactorCannotBeDisabled() throws Exception {
        when(service.disable(eq(USER_ID), any()))
                .thenThrow(ApiException.of(ApiErrorCode.TWO_FACTOR_REQUIRED_BY_ROLE, null, "security.twoFactor.requiredByRole"));
        mvc.perform(post("/users/me/two-factor/disable").with(withPermissions())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\",\"code\":\"123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("TWO_FACTOR_REQUIRED_BY_ROLE"));
    }

    @Test
    void recoveryCodesAreRegenerated() throws Exception {
        when(service.regenerateRecoveryCodes(eq(USER_ID), any())).thenReturn(new TwoFactorRecoveryCodes(List.of("AAAA-BBBB"), ON));
        mvc.perform(post("/users/me/two-factor/recovery-codes").with(withPermissions())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"7KQ4-M2XD\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.recoveryCodes[0]").value("AAAA-BBBB"));
    }
}
