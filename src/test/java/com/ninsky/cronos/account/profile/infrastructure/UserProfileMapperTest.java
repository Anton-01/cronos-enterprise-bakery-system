package com.ninsky.cronos.account.profile.infrastructure;

import com.ninsky.cronos.account.avatar.application.port.AvatarStorage;
import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.avatar.infrastructure.AvatarUrlMapping;
import com.ninsky.cronos.account.profile.api.UpdateProfileRequest;
import com.ninsky.cronos.account.profile.domain.ProfileUpdate;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import com.ninsky.cronos.application.response.auth.UserResponse;
import com.ninsky.cronos.infrastructure.persistence.auth.entity.UserProfileJpaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringJUnitConfig({UserProfileMapperImpl.class, AvatarUrlMapping.class})
class UserProfileMapperTest {

    private static final UUID USER_ID = UUID.fromString("3f9c1c9e-8f6a-4a8e-9a57-1f7a2b1c9d10");

    @Autowired
    private UserProfileMapper mapper;

    @MockitoBean
    private AvatarStorage avatarStorage;

    @Test
    void requestBecomesNormalizedCommand() {
        ProfileUpdate update = mapper.toUpdate(new UpdateProfileRequest(" admin_cronos ", " Antón ", "  ", "+14155552671"), ExpectedVersion.of(3));

        assertThat(update.username()).isEqualTo("admin_cronos");
        assertThat(update.firstName()).isEqualTo("Antón");
        assertThat(update.lastName()).isNull();
        assertThat(update.phoneNumber().value()).isEqualTo("+14155552671");
        assertThat(update.expectedVersion()).isEqualTo(ExpectedVersion.of(3));
    }

    @Test
    void responseResolvesAvatarUrlThroughTheStoragePort() {
        AvatarKey key = AvatarKey.forContent(USER_ID, new byte[]{1});
        when(avatarStorage.publicUrl(any())).thenAnswer(inv -> URI.create("https://cdn.example/" + inv.getArgument(0, AvatarKey.class).value()));

        UserResponse response = mapper.toResponse(account(key));

        assertThat(response.avatarUrl()).isEqualTo("https://cdn.example/" + key.value());
        assertThat(response.roles()).containsExactly("SUPER_ADMIN");
        assertThat(mapper.toResponse(account(null)).avatarUrl()).isNull();
        assertThat(mapper.toAvatarResponse(account(key)).avatarUrl()).endsWith(key.fileName());
    }

    @Test
    void applyToIsAFullReplaceWhereNullClears() {
        UserProfileJpaEntity profile = UserProfileJpaEntity.builder().userId(USER_ID)
                .firstName("Old").lastName("Name").phoneNumber("+525512345678").businessName("Keep me").build();

        mapper.applyTo(new ProfileUpdate("admin_cronos", "Antón", null, null, ExpectedVersion.ANY), profile);

        assertThat(profile.getFirstName()).isEqualTo("Antón");
        assertThat(profile.getLastName()).isNull();
        assertThat(profile.getPhoneNumber()).isNull();
        assertThat(profile.getBusinessName()).as("fields outside the contract are untouched").isEqualTo("Keep me");
        assertThat(profile.getUserId()).isEqualTo(USER_ID);
    }

    private static UserAccount account(AvatarKey key) {
        return new UserAccount(USER_ID, "admin_cronos", "admin@cronos.com", "Antón", "Admin", "+525512345678", key,
                true, true, false, 0, null, null, null, Set.of("SUPER_ADMIN"), LocalDateTime.now(), LocalDateTime.now(), 1);
    }
}
