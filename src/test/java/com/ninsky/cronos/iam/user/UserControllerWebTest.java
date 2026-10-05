package com.ninsky.cronos.iam.user;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.web.AccountWebMvcTest;
import com.ninsky.cronos.domain.model.auth.AuthUserProjection;
import com.ninsky.cronos.infrastructure.exception.ApiErrorCode;
import com.ninsky.cronos.infrastructure.exception.ApiException;
import com.ninsky.cronos.infrastructure.security.CronosUserPrincipal;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.infrastructure.web.paging.PageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Permission gates and the strict error envelope of {@code /iam/users}. */
@AccountWebMvcTest(UserController.class)
class UserControllerWebTest {

    private static final UUID ACTOR = UUID.fromString("6d1f6f0e-2d55-4c1a-9a0c-3b8f4f6f2a11");
    private static final UUID TARGET = UUID.fromString("7b1e2c4a-0000-4000-8000-000000000001");

    @TestConfiguration
    @EnableMethodSecurity
    static class Config {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService users;
    @MockitoBean
    private UserStatusService statuses;
    @MockitoBean
    private UserCredentialService credentials;
    @MockitoBean
    private UserAccessEditor access;
    @MockitoBean
    private UserSessionService sessions;
    @MockitoBean
    private UserAvatarService avatars;
    @MockitoBean
    private UserExportService exports;
    @MockitoBean
    private AvatarStorage avatarStorage;

    private static RequestPostProcessor holding(String... permissions) {
        CronosUserPrincipal principal = new CronosUserPrincipal(new AuthUserProjection(ACTOR, "aortiz", "a@b.c", "x",
                true, true, true, true, false, null, Set.of("ADMIN"), Set.of()), Set.of("ADMIN"), Set.of(permissions), false);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void listingNeedsUserRead() throws Exception {
        mvc.perform(get("/iam/users").with(holding("IAM.ROLE.READ"))).andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void listsWithUserRead() throws Exception {
        when(users.list(any(UserSearch.class), any(PageQuery.class)))
                .thenAnswer(inv -> CatalogPage.of(List.of(), inv.getArgument(1), 0));
        mvc.perform(get("/iam/users").param("statuses", "ACTIVE").param("sort", "username,asc").with(holding("IAM.USER.READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void unknownSortFieldIsAValidationError() throws Exception {
        mvc.perform(get("/iam/users").param("sort", "password,asc").with(holding("IAM.USER.READ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusChangeNeedsChangeStatus() throws Exception {
        mvc.perform(post("/iam/users/{id}/status", TARGET).with(csrf()).with(holding("IAM.USER.UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"reason\":\"ADMIN_REQUEST\",\"version\":1}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(statuses);
    }

    @Test
    void selfModificationIsReportedWithItsCode() throws Exception {
        when(statuses.change(eq(TARGET), any()))
                .thenThrow(ApiException.of(ApiErrorCode.SELF_MODIFICATION_FORBIDDEN, "status", "iam.user.selfModification"));
        mvc.perform(post("/iam/users/{id}/status", TARGET).with(csrf()).with(holding("IAM.USER.CHANGE_STATUS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"reason\":\"ADMIN_REQUEST\",\"version\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("SELF_MODIFICATION_FORBIDDEN"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }
}
