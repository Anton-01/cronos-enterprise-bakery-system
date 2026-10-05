package com.ninsky.cronos.iam.policy.api;

import com.ninsky.cronos.iam.IamWebMvcTest;
import com.ninsky.cronos.iam.policy.PolicyFixtures;
import com.ninsky.cronos.iam.policy.SecurityPolicyRequest;
import com.ninsky.cronos.iam.policy.SecurityPolicyRules;
import com.ninsky.cronos.iam.policy.SecurityPolicyService;
import com.ninsky.cronos.iam.shared.UserRef;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static com.ninsky.cronos.iam.IamWebFixtures.USER_ID;
import static com.ninsky.cronos.iam.IamWebFixtures.withPermissions;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IamWebMvcTest(SecurityPolicyController.class)
class SecurityPolicyControllerWebTest {

    private static final String READ = "IAM.SECURITY_POLICY.READ";
    private static final String UPDATE = "IAM.SECURITY_POLICY.UPDATE";
    private static final String BODY = """
            {"passwordMinLength":12,"passwordRequireUppercase":true,"passwordRequireLowercase":true,
             "passwordRequireDigit":true,"passwordRequireSymbol":true,"passwordHistory":5,"passwordMaxAgeDays":90,
             "maxFailedAttempts":5,"lockoutMinutes":15,"sessionIdleMinutes":30,"sessionAbsoluteHours":12,
             "maxConcurrentSessions":3,"invitationTtlHours":72,"twoFactorRequiredRoleIds":[1,2],"version":3}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SecurityPolicyService service;

    private static SecurityPolicyView view() {
        return SecurityPolicyView.of(PolicyFixtures.policy(), new UserRef(USER_ID, "admin", "Ana Admin", null));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/iam/security-policy")).andExpect(status().isUnauthorized());
    }

    @Test
    void readingRequiresThePermission() throws Exception {
        mvc.perform(get("/iam/security-policy").with(withPermissions(UPDATE))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void updatingRequiresThePermission() throws Exception {
        mvc.perform(put("/iam/security-policy").with(withPermissions(READ))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void returnsThePolicy() throws Exception {
        when(service.get()).thenReturn(view());
        mvc.perform(get("/iam/security-policy").with(withPermissions(READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordMinLength").value(12))
                .andExpect(jsonPath("$.data.twoFactorRequiredRoleIds[1]").value(2))
                .andExpect(jsonPath("$.data.updatedBy.username").value("admin"))
                .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    void updateReturnsTheNewPolicyAndALocalisedMessage() throws Exception {
        when(service.update(any())).thenReturn(view());
        mvc.perform(put("/iam/security-policy").with(withPermissions(UPDATE))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security policy updated"))
                .andExpect(jsonPath("$.data.lockoutMinutes").value(15));
    }

    @Test
    void fieldErrorsAreReportedTogether() throws Exception {
        SecurityPolicyRequest invalid = new SecurityPolicyRequest(4, true, true, true, true, 5, 90, 5, 15, 600, 1, 3, 72,
                java.util.List.of(9L), 3L);
        when(service.update(any())).thenAnswer(call -> {
            SecurityPolicyRules.validate(invalid, Set.of(1L)).throwIfAny();
            return view();
        });
        mvc.perform(put("/iam/security-policy").with(withPermissions(UPDATE))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("passwordMinLength", "sessionIdleMinutes",
                        "twoFactorRequiredRoleIds[0]")))
                .andExpect(jsonPath("$.errors[0].message").value("Must be between 8 and 128"));
    }

    @Test
    void malformedBodyIsRejected() throws Exception {
        mvc.perform(put("/iam/security-policy").with(withPermissions(UPDATE))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"version\":3", "\"version\":\"three\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        verifyNoInteractions(service);
    }

    @Test
    void staleVersionIsAConflict() throws Exception {
        when(service.update(any())).thenThrow(ApiException.concurrentModification());
        mvc.perform(put("/iam/security-policy").with(withPermissions(UPDATE))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }
}
