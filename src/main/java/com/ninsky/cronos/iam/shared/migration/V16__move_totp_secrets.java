package com.ninsky.cronos.iam.shared.migration;

import com.ninsky.cronos.iam.twofactor.Base32;
import com.ninsky.cronos.iam.twofactor.TwoFactorSecretCipher;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contract §8.2: TOTP secrets move from {@code users.two_factor_secret} to {@code user_two_factor}
 * (AES-GCM, KMS key) and the old column is dropped. Pending set-ups are discarded. A secret that
 * cannot be read turns 2FA off for that user, so the gate asks them to enrol again.
 */
@Slf4j
@Component
@SuppressWarnings("java:S101") // Flyway derives the version from this class name.
public class V16__move_totp_secrets extends BaseJavaMigration {

    /** Authenticator secrets are upper-case Base32; AES-GCM ciphertext in Base64 never looks like that. */
    static final Pattern LEGACY_PLAINTEXT = Pattern.compile("[A-Z2-7]{16,}=*");

    private record Row(UUID id, String stored) {
    }

    private final FieldEncryptionService encryption;
    private final TwoFactorSecretCipher cipher;

    public V16__move_totp_secrets(FieldEncryptionService encryption, TwoFactorSecretCipher cipher) {
        this.encryption = encryption;
        this.cipher = cipher;
    }

    @Override
    public void migrate(Context context) {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
        List<Row> rows = jdbc.query("SELECT id, two_factor_secret FROM users WHERE two_factor_enabled AND two_factor_secret IS NOT NULL",
                (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("two_factor_secret")));
        int moved = 0;
        int unreadable = 0;
        for (Row row : rows) {
            Optional<byte[]> secret = secretOf(row.stored());
            if (secret.isPresent()) {
                jdbc.update("INSERT INTO user_two_factor (user_id, secret_enc) VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING",
                        row.id(), cipher.seal(secret.get()));
                moved++;
            } else {
                jdbc.update("UPDATE users SET two_factor_enabled = FALSE WHERE id = ?", row.id());
                unreadable++;
            }
        }
        jdbc.execute("ALTER TABLE users DROP COLUMN two_factor_secret");
        log.info("TOTP secrets: {} moved, {} unreadable (2FA turned off for re-enrolment)", moved, unreadable);
    }

    /** Ciphertext from the old converter, or legacy plaintext Base32 written before encryption existed. */
    Optional<byte[]> secretOf(String stored) {
        String plain = encryption.decrypt(stored);
        if (!FieldEncryptionService.DECRYPTION_FAILED_SENTINEL.equals(plain) && Base32.isValid(plain)) {
            return Optional.of(Base32.decode(plain));
        }
        return LEGACY_PLAINTEXT.matcher(stored).matches() ? Optional.of(Base32.decode(stored)) : Optional.empty();
    }
}
