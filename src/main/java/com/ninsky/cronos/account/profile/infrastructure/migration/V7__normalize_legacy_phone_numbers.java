package com.ninsky.cronos.account.profile.infrastructure.migration;

import com.ninsky.cronos.account.profile.domain.E164Phone;
import com.ninsky.cronos.account.shared.domain.PiiMasker;
import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * One-off data migration: legacy {@code user_profiles.phone_number} values stored as bare national
 * digits ("5512345678") become E.164 ("+525512345678"), interpreting them as Mexican (region MX).
 * <p>
 * A Java (not SQL) migration because the column is AES-GCM ciphertext written by
 * {@link FieldEncryptionService} — it has to be decrypted, normalized and re-encrypted in-process.
 * Registered as a Spring bean: Spring Boot hands {@code JavaMigration} beans to Flyway, which is how
 * this class gets the encryption service. Rows that cannot be decrypted or parsed are logged (masked)
 * and left untouched — never fail the deploy over one bad phone number.
 */
@Slf4j
@Component
@SuppressWarnings("java:S101") // Flyway derives the version from this class name.
public class V7__normalize_legacy_phone_numbers extends BaseJavaMigration {

    static final String DEFAULT_REGION = "MX";

    private final FieldEncryptionService fieldEncryptionService;
    private final PiiMasker piiMasker = new PiiMasker();

    public V7__normalize_legacy_phone_numbers(FieldEncryptionService fieldEncryptionService) {
        this.fieldEncryptionService = fieldEncryptionService;
    }

    @Override
    public void migrate(Context context) throws SQLException {
        Result result = normalize(context.getConnection());
        log.info("Legacy phone migration: {} converted, {} already E.164, {} skipped (unparsable/undecryptable)",
                result.converted(), result.alreadyValid(), result.skipped());
    }

    public Result normalize(Connection connection) throws SQLException {
        int converted = 0;
        int alreadyValid = 0;
        int skipped = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, phone_number FROM user_profiles WHERE phone_number IS NOT NULL");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE user_profiles SET phone_number = ? WHERE id = ?");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                UUID id = rows.getObject("id", UUID.class);
                String plaintext = fieldEncryptionService.decrypt(rows.getString("phone_number"));
                if (plaintext == null || plaintext.isBlank()
                        || FieldEncryptionService.DECRYPTION_FAILED_SENTINEL.equals(plaintext)) {
                    log.warn("Skipping phone of profile {}: value could not be decrypted", id);
                    skipped++;
                    continue;
                }
                if (E164Phone.isValid(plaintext)) {
                    alreadyValid++;
                    continue;
                }
                var normalized = E164Phone.fromLegacy(plaintext, DEFAULT_REGION);
                if (normalized.isEmpty()) {
                    log.warn("Skipping phone of profile {}: '{}' is not a valid {} number",
                            id, piiMasker.maskPhone(plaintext), DEFAULT_REGION);
                    skipped++;
                    continue;
                }
                update.setString(1, fieldEncryptionService.encrypt(normalized.get().value()));
                update.setObject(2, id);
                update.addBatch();
                converted++;
            }
            if (converted > 0) {
                update.executeBatch();
            }
        }
        return new Result(converted, alreadyValid, skipped);
    }

    public record Result(int converted, int alreadyValid, int skipped) {
    }
}
