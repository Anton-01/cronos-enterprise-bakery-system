package com.ninsky.cronos.infrastructure.persistence.crypto;

import com.ninsky.cronos.infrastructure.security.crypto.FieldEncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Transparent field-level encryption at the JPA boundary. Applied explicitly per-column via
 * {@code @Convert(converter = EncryptedStringConverter.class)} (deliberately not {@code autoApply}
 * — encryption must be an opt-in choice per field, not a blanket default). Everything above the
 * entity layer (mappers, services, DTOs) sees plain Java strings; only the DB column holds ciphertext.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptionService fieldEncryptionService;

    public EncryptedStringConverter(FieldEncryptionService fieldEncryptionService) {
        this.fieldEncryptionService = fieldEncryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return fieldEncryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return fieldEncryptionService.decrypt(dbData);
    }
}
