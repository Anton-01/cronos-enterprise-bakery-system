package com.ninsky.cronos.account.web;

import com.ninsky.cronos.account.avatar.api.AvatarController;
import com.ninsky.cronos.account.avatar.api.PublicAvatarController;
import com.ninsky.cronos.account.avatar.application.RemoveAvatarUseCase;
import com.ninsky.cronos.account.avatar.application.UploadAvatarUseCase;
import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainError.ImageRejected.Reason;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AccountWebMvcTest({AvatarController.class, PublicAvatarController.class})
class AvatarControllerWebTest {

    private static final UUID USER_ID = UUID.fromString("3f9c1c9e-8f6a-4a8e-9a57-1f7a2b1c9d10");
    private static final MockMultipartFile AVATAR =
            new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UploadAvatarUseCase uploadAvatarUseCase;
    @MockitoBean
    private RemoveAvatarUseCase removeAvatarUseCase;
    @MockitoBean
    private AvatarStorage avatarStorage;

    private final AvatarKey key = AvatarKey.forContent(USER_ID, new byte[]{7});

    @BeforeEach
    void setUp() {
        when(avatarStorage.publicUrl(any())).thenAnswer(inv -> URI.create("https://cdn.test/" + inv.getArgument(0, AvatarKey.class).value()));
        when(uploadAvatarUseCase.execute(any(), any())).thenReturn(new UserAccount(USER_ID, "admin_cronos", "a@b.c", null, null, null, key,
                true, true, false, 0, null, null, null, Set.of(), LocalDateTime.now(), LocalDateTime.of(2026, 9, 26, 18, 0), 6));
    }

    @Test
    void uploadReturnsContentAddressedUrlAndUpdatedAt() throws Exception {
        mvc.perform(upload(AVATAR))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"6\""))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.test/" + key.value()))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-09-26T18:00:00"));
    }

    @Test
    void missingFilePartIsAFieldErrorOnFile() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/users/me/avatar").with(user("u")).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
        verify(uploadAvatarUseCase, never()).execute(any(), any());
    }

    @Test
    void tooSmallIs400OnFile() throws Exception {
        rejectWith(AccountDomainError.ImageRejected.of(Reason.INVALID, "account.avatar.file.tooSmall", 128));
        mvc.perform(upload(AVATAR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_FIELD_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value("Image must be at least 128 px on its shorter side"));
    }

    @Test
    void oversizedIs413() throws Exception {
        rejectWith(AccountDomainError.ImageRejected.of(Reason.TOO_LARGE, "account.avatar.file.tooLarge", 2));
        mvc.perform(upload(AVATAR)).andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.errors[0].field").value("file"));
    }

    @Test
    void wrongMagicBytesIs415() throws Exception {
        rejectWith(AccountDomainError.ImageRejected.of(Reason.UNSUPPORTED_TYPE, "account.avatar.file.unsupportedType"));
        mvc.perform(upload(AVATAR)).andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.errors[0].field").value("file"));
    }

    @Test
    void eleventhUploadInAnHourIs429() throws Exception {
        rejectWith(AccountDomainError.UploadRateLimited.of(3000));
        mvc.perform(upload(AVATAR))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "3000"))
                .andExpect(jsonPath("$.errors[0].field").value(nullValue()));
    }

    @Test
    void deleteIsIdempotent200WithNullData() throws Exception {
        mvc.perform(delete("/users/me/avatar").with(user("u")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"data\":null")));
        verify(removeAvatarUseCase).execute();
    }

    @Test
    void unauthenticatedUploadIs401() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/users/me/avatar").file(AVATAR)).andExpect(status().isUnauthorized());
        verify(uploadAvatarUseCase, never()).execute(any(), any());
    }

    @Test
    void publicAvatarIsServedImmutableInlineAndNosniffWithoutAuthentication() throws Exception {
        when(avatarStorage.read(key)).thenReturn(Optional.of(new byte[]{(byte) 0xFF, (byte) 0xD8}));

        mvc.perform(get("/public/avatars/{userId}/{file}", USER_ID, key.fileName()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    @Test
    void publicAvatarRejectsNonKeyPaths() throws Exception {
        mvc.perform(get("/public/avatars/{userId}/{file}", USER_ID, "passwd")).andExpect(status().isNotFound());
    }

    private void rejectWith(AccountDomainError error) {
        when(uploadAvatarUseCase.execute(any(), any())).thenThrow(new AccountDomainException(error));
    }

    private MockHttpServletRequestBuilder upload(MockMultipartFile file) {
        return multipart(HttpMethod.PUT, "/users/me/avatar").file(file).with(user("u")).header(HttpHeaders.ACCEPT_LANGUAGE, "en");
    }
}
