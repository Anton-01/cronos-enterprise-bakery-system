package com.ninsky.cronos.account.web;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.profile.api.ProfileController;
import com.ninsky.cronos.account.profile.application.GetMyProfileUseCase;
import com.ninsky.cronos.account.profile.application.UpdateMyProfileUseCase;
import com.ninsky.cronos.account.profile.domain.ProfileUpdate;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AccountWebMvcTest(ProfileController.class)
class ProfileControllerWebTest {

    private static final UUID USER_ID = UUID.fromString("3f9c1c9e-8f6a-4a8e-9a57-1f7a2b1c9d10");
    private static final String VALID_BODY = """
            {"username": "admin_cronos", "firstName": "Antón", "lastName": null, "phoneNumber": "+14155552671"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetMyProfileUseCase getMyProfileUseCase;
    @MockitoBean
    private UpdateMyProfileUseCase updateMyProfileUseCase;
    @MockitoBean
    private AvatarStorage avatarStorage;

    private final AvatarKey avatarKey = AvatarKey.forContent(USER_ID, new byte[]{1});

    @BeforeEach
    void setUp() {
        when(avatarStorage.publicUrl(any())).thenAnswer(inv -> URI.create("https://cdn.test/" + inv.getArgument(0, AvatarKey.class).value()));
        when(getMyProfileUseCase.execute()).thenReturn(account(4));
        when(updateMyProfileUseCase.execute(any())).thenReturn(account(5));
    }

    @Test
    void getReturnsTheContractShapeWithExplicitNullsAndETag() throws Exception {
        mvc.perform(get("/users/me").with(user("admin_cronos")).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Profile retrieved successfully"))
                .andExpect(jsonPath("$.meta.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.test/" + avatarKey.value()))
                .andExpect(jsonPath("$.data.phoneNumber").value("+525512345678"))
                .andExpect(jsonPath("$.data.lockedUntil").value(nullValue()))
                .andExpect(jsonPath("$.data.roles[0]").value("SUPER_ADMIN"));
    }

    @Test
    void successMessageIsLocalized() throws Exception {
        mvc.perform(get("/users/me").with(user("admin_cronos")).header(HttpHeaders.ACCEPT_LANGUAGE, "es-MX"))
                .andExpect(jsonPath("$.message").value("Perfil obtenido correctamente"));
    }

    @Test
    void unauthenticatedIs401AndNeverReachesTheUseCase() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        verify(getMyProfileUseCase, never()).execute();
    }

    @Test
    void putPassesIfMatchAsExpectedVersionAndReturnsNewETag() throws Exception {
        mvc.perform(putJson(VALID_BODY).header(HttpHeaders.IF_MATCH, "\"4\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"5\""))
                .andExpect(jsonPath("$.data.username").value("admin_cronos"));

        ArgumentCaptor<ProfileUpdate> update = ArgumentCaptor.forClass(ProfileUpdate.class);
        verify(updateMyProfileUseCase).execute(update.capture());
        assertThat(update.getValue().expectedVersion()).isEqualTo(ExpectedVersion.of(4));
        assertThat(update.getValue().lastName()).isNull();
        assertThat(update.getValue().phoneNumber().value()).isEqualTo("+14155552671");
    }

    @Test
    void invalidPhoneIsAFieldErrorOnPhoneNumber() throws Exception {
        mvc.perform(putJson("""
                        {"username": "admin_cronos", "firstName": null, "lastName": null, "phoneNumber": "5512345678"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_FIELD_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("phoneNumber"));
        verify(updateMyProfileUseCase, never()).execute(any());
    }

    @Test
    void reportsEveryInvalidFieldAtOnceOnePerField() throws Exception {
        mvc.perform(putJson("""
                        {"username": "", "firstName": "%s", "lastName": null, "phoneNumber": "+0000"}""".formatted("x".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("username", "firstName", "phoneNumber")));
    }

    @Test
    void massAssignmentOfEmailOrRolesIsRejected() throws Exception {
        mvc.perform(putJson("""
                        {"username": "admin_cronos", "firstName": null, "lastName": null, "phoneNumber": null, "roles": ["SUPER_ADMIN"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("roles"));
        verify(updateMyProfileUseCase, never()).execute(any());
    }

    @Test
    void duplicateUsernameIs409OnUsername() throws Exception {
        when(updateMyProfileUseCase.execute(any())).thenThrow(new AccountDomainException(AccountDomainError.DuplicateUsername.of("admin_cronos")));

        mvc.perform(putJson(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Duplicate Resource"))
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.errors[0].field").value("username"))
                .andExpect(jsonPath("$.errors[0].message").value("Username 'admin_cronos' is already taken"));
    }

    @Test
    void staleIfMatchIs412() throws Exception {
        when(updateMyProfileUseCase.execute(any())).thenThrow(new AccountDomainException(AccountDomainError.VersionMismatch.of()));

        mvc.perform(putJson(VALID_BODY).header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.errors[0].code").value("SYSTEM_RESOURCE_CONFLICT"));
    }

    @Test
    void lostUpdateAtFlushIs409SystemResourceConflict() throws Exception {
        when(updateMyProfileUseCase.execute(any())).thenThrow(new ObjectOptimisticLockingFailureException(Object.class, USER_ID));

        mvc.perform(putJson(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SYSTEM_RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.errors[0].field").value(nullValue()));
    }

    @Test
    void tooManyWritesIs429WithRetryAfter() throws Exception {
        when(updateMyProfileUseCase.execute(any())).thenThrow(new AccountDomainException(AccountDomainError.WriteRateLimited.of(1800)));

        mvc.perform(putJson(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1800"))
                .andExpect(content().string(containsString("1800 seconds")));
    }

    private MockHttpServletRequestBuilder putJson(String body) {
        return put("/users/me").with(user("admin_cronos")).with(csrf())
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private UserAccount account(long version) {
        return new UserAccount(USER_ID, "admin_cronos", "admin@cronos.com", "Antón", "Admin", "+525512345678", avatarKey,
                true, true, false, 0, null, null, null, Set.of("SUPER_ADMIN"), LocalDateTime.now(), LocalDateTime.now(), version);
    }
}
