package com.ninsky.cronos.account.it;

import com.ninsky.cronos.account.avatar.domain.AvatarKey;
import com.ninsky.cronos.account.avatar.domain.AvatarObjectReleased;
import com.ninsky.cronos.account.profile.application.port.UserAccountRepository;
import com.ninsky.cronos.account.profile.domain.E164Phone;
import com.ninsky.cronos.account.profile.domain.ProfileUpdate;
import com.ninsky.cronos.account.profile.domain.UserAccount;
import com.ninsky.cronos.account.profile.infrastructure.migration.V7__normalize_legacy_phone_numbers;
import com.ninsky.cronos.account.shared.domain.AccountDomainError;
import com.ninsky.cronos.account.shared.domain.AccountDomainException;
import com.ninsky.cronos.account.shared.domain.ExpectedVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ProfileAndAvatarPersistenceIT extends AccountPostgresIT {

    @Autowired
    private UserAccountRepository accounts;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private DataSource dataSource;

    @Test
    void profileWriteIsAFullReplaceAndBumpsTheUserVersion() {
        UUID userId = insertUser("profile_" + System.nanoTime(), "+525512345678");
        long before = accounts.findById(userId).orElseThrow().version();

        UserAccount updated = tx.execute(s -> accounts.applyProfile(userId,
                new ProfileUpdate("renamed_" + System.nanoTime(), "Antón", null, new E164Phone("+14155552671"), ExpectedVersion.ANY)));

        assertThat(updated.version()).isGreaterThan(before);
        assertThat(updated.lastName()).isNull();
        assertThat(updated.phoneNumber()).isEqualTo("+14155552671");
        assertThat(accounts.findById(userId).orElseThrow().firstName()).isEqualTo("Antón");
    }

    @Test
    void usernameUniquenessIsCaseInsensitiveEvenUnderARace() {
        String name = "Cronos_" + System.nanoTime();
        insertUser(name, null);
        UUID other = insertUser("other_" + System.nanoTime(), null);

        assertThat(accounts.isUsernameTakenByOther(name.toLowerCase(), other)).isTrue();
        assertThatThrownBy(() -> insertUser(name.toUpperCase(), null)).isInstanceOf(DataIntegrityViolationException.class);

        // The use case's pre-check lost the race: the unique index still yields a DuplicateUsername (409).
        var thrown = catchThrowableOfType(AccountDomainException.class, () -> tx.execute(s -> accounts.applyProfile(other,
                new ProfileUpdate(name.toLowerCase(), null, null, null, ExpectedVersion.ANY))));
        assertThat(thrown.primary()).isInstanceOf(AccountDomainError.DuplicateUsername.class);
    }

    @Test
    void previousAvatarObjectIsDeletedOnlyAfterCommit() {
        UUID userId = insertUser("avatar_" + System.nanoTime(), null);
        AvatarKey oldKey = AvatarKey.forContent(userId, new byte[]{1});
        AvatarKey newKey = AvatarKey.forContent(userId, new byte[]{2});
        avatarStorage.put(oldKey, new byte[]{1});
        avatarStorage.put(newKey, new byte[]{2});
        tx.execute(s -> accounts.replaceAvatar(userId, oldKey));

        tx.execute(s -> {
            accounts.replaceAvatar(userId, newKey);
            events.publishEvent(new AvatarObjectReleased(userId, oldKey));
            assertThat(avatarStorage.exists(oldKey)).as("still there before commit").isTrue();
            return null;
        });

        assertThat(avatarStorage.exists(oldKey)).isFalse();
        assertThat(avatarStorage.exists(newKey)).isTrue();
        assertThat(accounts.findById(userId).orElseThrow().avatarKey()).isEqualTo(newKey);
    }

    @Test
    void rolledBackAvatarChangeKeepsThePreviousObject() {
        UUID userId = insertUser("avatar_rb_" + System.nanoTime(), null);
        AvatarKey oldKey = AvatarKey.forContent(userId, new byte[]{3});
        avatarStorage.put(oldKey, new byte[]{3});
        tx.execute(s -> accounts.replaceAvatar(userId, oldKey));

        tx.execute(s -> {
            accounts.replaceAvatar(userId, null);
            events.publishEvent(new AvatarObjectReleased(userId, oldKey));
            s.setRollbackOnly();
            return null;
        });

        assertThat(avatarStorage.exists(oldKey)).isTrue();
        assertThat(accounts.findById(userId).orElseThrow().avatarKey()).isEqualTo(oldKey);
    }

    @Test
    void legacyPhoneMigrationConvertsNationalDigitsAndSkipsGarbage() throws Exception {
        UUID legacy = insertUser("legacy_" + System.nanoTime(), "5512345678");
        UUID alreadyE164 = insertUser("e164_" + System.nanoTime(), "+14155552671");
        UUID garbage = insertUser("garbage_" + System.nanoTime(), "123");

        V7__normalize_legacy_phone_numbers.Result result;
        try (Connection connection = dataSource.getConnection()) {
            result = new V7__normalize_legacy_phone_numbers(fieldEncryptionService).normalize(connection);
        }

        assertThat(result.converted()).isGreaterThanOrEqualTo(1);
        assertThat(result.skipped()).isGreaterThanOrEqualTo(1);
        assertThat(phoneOf(legacy)).isEqualTo("+525512345678");
        assertThat(phoneOf(alreadyE164)).isEqualTo("+14155552671");
        assertThat(phoneOf(garbage)).isEqualTo("123");
    }

    private String phoneOf(UUID userId) {
        return jdbc.queryForObject("SELECT phone_number FROM user_profiles WHERE user_id = ?", String.class, userId);
    }
}
